package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;

@Repository
public interface CotaUnidadeRepository extends JpaRepository<CotaUnidade, Long> {

    List<CotaUnidade> findByUnidadeIdAndPeriodo(Long unidadeId, String periodo);

    List<CotaUnidade> findByUnidadeId(Long unidadeId);

    List<CotaUnidade> findByGrupoUnidadesId(Long grupoUnidadesId);

    List<CotaUnidade> findByGrupoEspecialidadesId(Long grupoEspecialidadesId);

    // -----------------------------------------------------------------------
    // Cotas aplicaveis a um atendimento
    // -----------------------------------------------------------------------

    /**
     * Todas as cotas ativas que incidem sobre um atendimento.
     *
     * A cota tem duas dimensoes independentes, e esta consulta resolve as duas de
     * uma vez — antes eram ate 8 consultas separadas, uma por combinacao:
     *
     * <ul>
     *   <li><b>Titular</b>: a propria unidade, ou o grupo de unidades a que ela
     *       pertence (pool compartilhado).</li>
     *   <li><b>Escopo</b>: a especialidade exata, o grupo de especialidades que a
     *       contem, ou a cota geral (sem escopo).</li>
     * </ul>
     *
     * Todas as que casarem sao devolvidas, porque <b>todas incidem juntas</b>:
     * havendo cota de Hemograma e cota do grupo Laboratorio, as duas precisam ter
     * saldo. O mesmo ja valia entre cota MENSAL e cota por DATA.
     *
     * Os LEFT JOIN sao obrigatorios: navegar direto (ex.: {@code c.unidade.id})
     * geraria INNER JOIN e descartaria justamente as linhas cujo FK e nulo — que
     * aqui sao os casos validos (cota de grupo tem unidade_id nulo, cota geral tem
     * especialidade_id nulo).
     *
     * Parametros nulos simplesmente nao casam, sem precisar de consulta separada:
     * unidade sem grupo passa {@code grupoUnidadesId = null}, e a comparacao e falsa.
     */
    @Query("""
        SELECT c FROM CotaUnidade c
        LEFT JOIN c.unidade u
        LEFT JOIN c.grupoUnidades gu
        LEFT JOIN c.especialidade e
        LEFT JOIN c.grupoEspecialidades ge
        WHERE c.ativo = true
          AND (u.id = :unidadeId OR gu.id = :grupoUnidadesId)
          AND (
                e.id = :especialidadeId
             OR ge.id = :grupoEspecialidadesId
             OR (e.id IS NULL AND ge.id IS NULL)
          )
          AND (
                (c.tipoPeriodo = io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota.MENSAL
                 AND c.periodo = :periodo)
             OR (c.tipoPeriodo = io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota.DATA
                 AND c.dataEspecifica = :data)
          )
        """)
    List<CotaUnidade> buscarCotasAplicaveis(
            @Param("unidadeId") Long unidadeId,
            @Param("grupoUnidadesId") Long grupoUnidadesId,
            @Param("especialidadeId") Long especialidadeId,
            @Param("grupoEspecialidadesId") Long grupoEspecialidadesId,
            @Param("periodo") String periodo,
            @Param("data") LocalDate data);

    // -----------------------------------------------------------------------
    // Consumo atomico da cota
    // -----------------------------------------------------------------------

    /**
     * Consome uma vaga da cota de forma atomica.
     *
     * O incremento acontece direto no banco com a condicao de saldo embutida no
     * WHERE, entao duas requisicoes simultaneas nao conseguem enxergar o mesmo
     * saldo e ultrapassar o limite: a segunda simplesmente nao afeta linha
     * nenhuma. Retorna o numero de linhas atualizadas — 0 significa cota esgotada
     * (ou inativa), 1 significa vaga consumida.
     *
     * A coluna @Version nao e tocada de proposito: um bulk update JPQL ja ignora o
     * bloqueio otimista, e a condicao no WHERE e o que garante a corretude aqui.
     * O @Version continua valendo para as escritas via entidade (criar/atualizar cota).
     *
     * ATENCAO: {@code clearAutomatically} limpa o contexto de persistencia, entao
     * qualquer CotaUnidade carregada antes desta chamada fica DESANEXADA. Para ler
     * campos LAZY dela depois (unidade, grupos, especialidade) e preciso recarregar
     * — ver CotaUnidadeService#incrementarUtilizacao.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CotaUnidade c SET c.quantidadeUtilizada = c.quantidadeUtilizada + 1 "
            + "WHERE c.id = :id AND c.ativo = true AND c.quantidadeUtilizada < c.quantidadeTotal")
    int consumirVaga(@Param("id") Long id);

    /**
     * Devolve uma vaga a cota (cancelamento/remanejamento de agendamento).
     * O guard {@code quantidadeUtilizada > 0} impede saldo negativo caso a mesma
     * operacao seja estornada duas vezes.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CotaUnidade c SET c.quantidadeUtilizada = c.quantidadeUtilizada - 1 "
            + "WHERE c.id = :id AND c.quantidadeUtilizada > 0")
    int devolverVaga(@Param("id") Long id);
}
