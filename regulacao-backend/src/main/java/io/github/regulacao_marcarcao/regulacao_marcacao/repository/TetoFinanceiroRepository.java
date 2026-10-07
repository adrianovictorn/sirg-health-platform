package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.TetoFinanceiro;

@Repository
public interface TetoFinanceiroRepository extends JpaRepository<TetoFinanceiro, Long> {

    /**
     * Id do teto ATIVO que incide sobre a unidade, o grupo e o mes — por consulta
     * escalar, sem carregar a entidade: no agendamento o contexto de persistencia
     * e limpo pelo consumo da cota, e uma entidade carregada aqui ficaria
     * desanexada e com o saldo desatualizado depois do debito.
     */
    @Query("""
            SELECT t.id FROM TetoFinanceiro t
            WHERE t.unidade.id = :unidadeId
              AND t.grupoEspecialidades.id = :grupoId
              AND t.periodo = :periodo
              AND t.ativo = true
            """)
    Optional<Long> buscarIdAtivo(@Param("unidadeId") Long unidadeId,
            @Param("grupoId") Long grupoId,
            @Param("periodo") String periodo);

    /** Unidades que ja tem teto (ativo ou nao) para o grupo e o mes — a chave unica nao distingue ativo. */
    @Query("""
            SELECT t.unidade.id FROM TetoFinanceiro t
            WHERE t.grupoEspecialidades.id = :grupoId
              AND t.periodo = :periodo
              AND t.unidade.id IN :unidadeIds
            """)
    List<Long> buscarUnidadesComTeto(@Param("grupoId") Long grupoId,
            @Param("periodo") String periodo,
            @Param("unidadeIds") Collection<Long> unidadeIds);

    @Query("""
            SELECT t FROM TetoFinanceiro t
            JOIN FETCH t.unidade u
            JOIN FETCH t.grupoEspecialidades g
            WHERE t.periodo = :periodo
            ORDER BY u.nome ASC, g.nome ASC
            """)
    List<TetoFinanceiro> listarPorPeriodo(@Param("periodo") String periodo);

    // -----------------------------------------------------------------------
    // Debito e estorno atomicos
    //
    // SQL nativo de proposito: valor_utilizado e somente leitura na entidade
    // (updatable = false), para que editar o teto nunca regrave o saldo.
    //
    // Sem clearAutomatically, ao contrario de CotaUnidadeRepository#consumirVaga:
    // limpar o contexto aqui desanexaria a solicitacao que o agendamento ainda
    // vai salvar. Quem precisar do saldo depois do debito deve recarregar o teto.
    // -----------------------------------------------------------------------

    /**
     * Debita o teto de forma atomica, com a condicao de saldo embutida no WHERE:
     * duas requisicoes simultaneas nao conseguem enxergar o mesmo saldo e
     * ultrapassar o limite. Retorna 0 quando o saldo nao comporta o valor (ou o
     * teto esta inativo) e 1 quando debitou.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE teto_financeiro
               SET valor_utilizado = valor_utilizado + :valor
             WHERE id = :id
               AND ativo = TRUE
               AND valor_utilizado + :valor <= valor_total
            """, nativeQuery = true)
    int debitar(@Param("id") Long id, @Param("valor") BigDecimal valor);

    /**
     * Debita sem checar saldo — agendamento feito por quem nao e barrado pelo
     * teto (ADMIN, GESTOR). O utilizado pode passar do total; o gasto continua
     * registrado, que e o objetivo.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE teto_financeiro
               SET valor_utilizado = valor_utilizado + :valor
             WHERE id = :id
               AND ativo = TRUE
            """, nativeQuery = true)
    int debitarSemBloqueio(@Param("id") Long id, @Param("valor") BigDecimal valor);

    /** Devolve valor ao teto. GREATEST impede saldo negativo se a mesma operacao for estornada duas vezes. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE teto_financeiro
               SET valor_utilizado = GREATEST(valor_utilizado - :valor, 0)
             WHERE id = :id
            """, nativeQuery = true)
    int estornar(@Param("id") Long id, @Param("valor") BigDecimal valor);
}
