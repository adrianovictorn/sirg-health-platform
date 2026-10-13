package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.VitrineTotaisProjection;

/**
 * Agregacoes da vitrine publica (landing do SIRG). Somente leitura e somente
 * numeros e nomes de especialidade: nenhuma destas consultas devolve dado de
 * paciente, de profissional, de usuario nem valor em reais.
 *
 * <p>Sao totais acumulados do municipio inteiro, de proposito sem quebra por
 * especialidade, unidade ou periodo — e a ausencia de quebra que impede uma
 * contagem pequena de apontar para uma pessoa. Sem periodo tambem nao ha "hoje"
 * nem "este mes", entao o fuso da JVM (UTC) nao entra na conta.
 */
@Transactional(readOnly = true)
public interface VitrinePublicaRepository extends Repository<Solicitacao, Long> {

    /**
     * Uma linha, um acesso ao banco. {@code pacientesAtendidos} sao as fichas com
     * ao menos um agendamento; {@code viagensTransporte} ignora as canceladas.
     */
    @Query(value = """
            SELECT
                (SELECT COUNT(*) FROM solicitacao_especialidade)                           AS solicitacoes,
                (SELECT COUNT(*) FROM agendamento_solicitacao)                             AS agendamentos,
                (SELECT COUNT(DISTINCT ag.solicitacao_id) FROM agendamento_solicitacao ag) AS pacientesAtendidos,
                (SELECT COUNT(*) FROM agendamento_transporte t
                  WHERE t.status_agendamento <> 'CANCELADO')                               AS viagensTransporte,
                (SELECT COUNT(*) FROM unidade u WHERE u.ativo = true)                      AS unidades,
                (SELECT COUNT(*) FROM especialidade e WHERE e.ativo = true)                AS especialidades
            """, nativeQuery = true)
    VitrineTotaisProjection totais();

    /**
     * Nomes das especialidades ativas. As marcadas como sensiveis ficam fora da
     * lista nominal (continuam na contagem de {@link #totais()}).
     */
    @Query(value = """
            SELECT e.nome FROM especialidade e
            WHERE e.ativo = true AND e.sensivel = false
            ORDER BY e.nome
            """, nativeQuery = true)
    List<String> nomesDasEspecialidadesPublicas();
}
