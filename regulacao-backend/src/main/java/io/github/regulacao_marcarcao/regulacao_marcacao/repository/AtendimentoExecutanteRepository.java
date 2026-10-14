package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoRelatorioProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoResumoProjection;

/**
 * Consultas da tela de Atendimentos (operador da unidade executante): o municipio
 * inteiro num dia, sem filtro obrigatorio por unidade. Fica a parte de
 * {@link AgendaDiaConsolidadaRepository} e de {@link SolicitacaoEspecialidadeRepository}
 * para nao alterar nenhuma consulta da Agenda do Dia nem da agenda por unidade.
 *
 * LEFT JOIN em unidade de proposito: solicitacao sem unidade precisa aparecer
 * (balde "Sem unidade"), na listagem e na contagem, senao o total do dia seria
 * subcontado. LEFT JOIN em especialidade pelo mesmo motivo: item legado sem
 * especialidade vinculada entra no resumo e tem de entrar na lista.
 *
 * {@code grupo} e o codigo do Grupo de Relatorio (a "agenda" do menu: Laboratorio,
 * USG...) e e opcional em todas as consultas; nulo traz todos os grupos.
 *
 * A lista e por PACIENTE (ficha), em duas consultas — mesmo desenho da fila de
 * espera: {@link #listarPacientes} pagina as fichas e {@link #listarItensDosPacientes}
 * traz os itens das fichas da pagina. Assim nenhum paciente fica dividido entre duas
 * paginas e o total da pagina conta pacientes.
 *
 * A busca compara CPF e CNS so por igualdade com o documento completo
 * ({@code termoDigitos}), nunca por trecho: um pedaco de numero listaria pacientes
 * que o operador nao esta atendendo.
 */
public interface AtendimentoExecutanteRepository extends JpaRepository<SolicitacaoEspecialidade, Long> {

    /**
     * Recorte por unidade executante (V110), igual em todas as consultas. Com
     * {@code porExecutante = false} (visao municipal) nao filtra nada e
     * {@code executanteIds} vai com uma sentinela — o IN nunca pode ir vazio.
     *
     * Um item pertence a executante X quando a especialidade dele esta no conjunto
     * efetivo de X (grupo inteiro ou avulsa) E o local do agendamento esta ligado a
     * X. Agendamento sem local pertence so pela especialidade, e so quando X e a
     * UNICA executante (ativa, EXECUTANTE/AMBOS) com aquela especialidade — grupo e
     * avulsa podem ser compartilhados (V111, ADR 0013); sem local, o item de
     * especialidade compartilhada nao e de nenhuma executante (so visao municipal).
     * A conferencia e por executante (nunca especialidade de A com local de B).
     * Item sem especialidade vinculada nao pertence a nenhuma executante.
     */
    String FILTRO_EXECUTANTE = """
              AND (:porExecutante = false OR EXISTS (
                SELECT 1 FROM unidade ux
                WHERE ux.id IN (:executanteIds)
                  AND ((ag.local_agendamento_id IS NULL
                          AND NOT EXISTS (SELECT 1 FROM unidade_executante_especialidade xe2
                                          JOIN unidade u2 ON u2.id = xe2.unidade_id
                                          WHERE xe2.especialidade_id = se.especialidade_id
                                            AND xe2.unidade_id <> ux.id
                                            AND u2.ativo = true AND u2.tipo IN ('EXECUTANTE', 'AMBOS'))
                          AND NOT EXISTS (SELECT 1 FROM unidade_executante_grupo xg2
                                          JOIN unidade u2 ON u2.id = xg2.unidade_id
                                          WHERE xg2.grupo_relatorio_id = e.grupo_relatorio_id
                                            AND xg2.unidade_id <> ux.id
                                            AND u2.ativo = true AND u2.tipo IN ('EXECUTANTE', 'AMBOS')))
                       OR EXISTS (SELECT 1 FROM local_agendamento lx
                                  WHERE lx.id = ag.local_agendamento_id AND lx.unidade_executante_id = ux.id))
                  AND (EXISTS (SELECT 1 FROM unidade_executante_especialidade xe
                               WHERE xe.unidade_id = ux.id AND xe.especialidade_id = se.especialidade_id)
                    OR EXISTS (SELECT 1 FROM unidade_executante_grupo xg
                               WHERE xg.unidade_id = ux.id AND xg.grupo_relatorio_id = e.grupo_relatorio_id))))
            """;

    @Query(value = """
            SELECT
                s.unidade_id AS unidadeId,
                MAX(u.nome) AS unidadeNome,
                GROUPING(s.unidade_id) AS totalGeral,
                COUNT(DISTINCT s.id) AS pacientes,
                COUNT(*) AS itens,
                COUNT(*) FILTER (WHERE se.status = 'AGENDADO') AS agendados,
                COUNT(*) FILTER (WHERE se.status = 'REALIZADO') AS confirmados,
                COUNT(*) FILTER (WHERE se.status = 'FALTOU') AS faltas,
                COUNT(*) FILTER (WHERE se.status = 'CANCELADO') AS cancelados
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            LEFT JOIN unidade u ON u.id = s.unidade_id
            LEFT JOIN especialidade e ON e.id = se.especialidade_id
            LEFT JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
            WHERE ag.data_agendada = :data
              AND (CAST(:grupo AS text) IS NULL OR gr.codigo = CAST(:grupo AS text))
            """ + FILTRO_EXECUTANTE + """
            GROUP BY GROUPING SETS ((s.unidade_id), ())
            """, nativeQuery = true)
    List<AtendimentoResumoProjection> resumirPorUnidade(@Param("data") LocalDate data, @Param("grupo") String grupo,
            @Param("porExecutante") boolean porExecutante,
            @Param("executanteIds") Collection<Long> executanteIds);

    /**
     * Pagina de fichas com ao menos um item no dia (e na agenda) que atenda aos
     * filtros. {@code status} seleciona o PACIENTE — quem tem algum item naquela
     * situacao —, nao os itens: o bloco dele vem inteiro em
     * {@link #listarItensDosPacientes}.
     */
    @Query(value = """
            SELECT
                s.id AS solicitacaoId,
                s.nome_paciente AS nomePaciente,
                s.cpf_paciente AS cpf,
                s.cns AS cns,
                s.datanascimento AS dataNascimento,
                u.nome AS unidadeNome
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            LEFT JOIN especialidade e ON e.id = se.especialidade_id
            LEFT JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
            LEFT JOIN unidade u ON u.id = s.unidade_id
            WHERE ag.data_agendada = :data
              AND (CAST(:grupo AS text) IS NULL OR gr.codigo = CAST(:grupo AS text))
              AND ((:semUnidade = true AND s.unidade_id IS NULL)
                OR (:semUnidade = false
                    AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)))
              AND (CAST(:status AS text) IS NULL OR se.status = CAST(:status AS text))
              AND (CAST(:termo AS text) IS NULL
                OR strpos(lower(s.nome_paciente), lower(CAST(:termo AS text))) > 0
                OR (CAST(:termoDigitos AS text) IS NOT NULL
                    AND (regexp_replace(s.cpf_paciente, '[^0-9]', '', 'g') = CAST(:termoDigitos AS text)
                      OR regexp_replace(s.cns, '[^0-9]', '', 'g') = CAST(:termoDigitos AS text))))
            """ + FILTRO_EXECUTANTE + """
            GROUP BY s.id, s.nome_paciente, s.cpf_paciente, s.cns, s.datanascimento, u.nome
            ORDER BY s.nome_paciente, s.id
            """,
            countQuery = """
            SELECT COUNT(DISTINCT s.id)
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            LEFT JOIN especialidade e ON e.id = se.especialidade_id
            LEFT JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
            WHERE ag.data_agendada = :data
              AND (CAST(:grupo AS text) IS NULL OR gr.codigo = CAST(:grupo AS text))
              AND ((:semUnidade = true AND s.unidade_id IS NULL)
                OR (:semUnidade = false
                    AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)))
              AND (CAST(:status AS text) IS NULL OR se.status = CAST(:status AS text))
              AND (CAST(:termo AS text) IS NULL
                OR strpos(lower(s.nome_paciente), lower(CAST(:termo AS text))) > 0
                OR (CAST(:termoDigitos AS text) IS NOT NULL
                    AND (regexp_replace(s.cpf_paciente, '[^0-9]', '', 'g') = CAST(:termoDigitos AS text)
                      OR regexp_replace(s.cns, '[^0-9]', '', 'g') = CAST(:termoDigitos AS text))))
            """ + FILTRO_EXECUTANTE, nativeQuery = true)
    Page<AtendimentoPacienteProjection> listarPacientes(
            @Param("data") LocalDate data,
            @Param("grupo") String grupo,
            @Param("unidadeId") Long unidadeId,
            @Param("semUnidade") boolean semUnidade,
            @Param("status") String status,
            @Param("termo") String termo,
            @Param("termoDigitos") String termoDigitos,
            @Param("porExecutante") boolean porExecutante,
            @Param("executanteIds") Collection<Long> executanteIds,
            Pageable pageable);

    /**
     * Itens do dia (e da agenda) das fichas informadas — o bloco inteiro de cada
     * paciente, em qualquer situacao. {@code ids} nunca pode ir vazio.
     */
    @Query(value = """
            SELECT
                se.id AS solicitacaoEspecialidadeId,
                se.solicitacao_id AS solicitacaoId,
                COALESCE(e.nome, CAST(se.especialidade_solicitada AS text)) AS especialidadeNome,
                e.categoria AS categoria,
                se.status AS status,
                CAST(ag.turno AS text) AS turno,
                CAST(se.hora_agendada AS text) AS horaAgendada,
                se.data_coleta AS dataColeta,
                se.atendimento_observacao AS observacao,
                ur.nome AS registradoPorNome,
                se.atendimento_registrado_em AS registradoEm,
                se.devolvido_como_item_id AS devolvidoComoItemId
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            LEFT JOIN especialidade e ON e.id = se.especialidade_id
            LEFT JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
            LEFT JOIN usuarios ur ON ur.id = se.atendimento_registrado_por_id
            WHERE se.solicitacao_id IN (:ids)
              AND ag.data_agendada = :data
              AND (CAST(:grupo AS text) IS NULL OR gr.codigo = CAST(:grupo AS text))
            """ + FILTRO_EXECUTANTE + """
            ORDER BY se.solicitacao_id, se.id
            """, nativeQuery = true)
    List<AtendimentoProjection> listarItensDosPacientes(
            @Param("ids") Collection<Long> ids,
            @Param("data") LocalDate data,
            @Param("grupo") String grupo,
            @Param("porExecutante") boolean porExecutante,
            @Param("executanteIds") Collection<Long> executanteIds);

    /**
     * Relatorio de Atendimento: itens COM desfecho (Confirmado, Faltou, Cancelado)
     * agendados no periodo, o municipio inteiro. Falta antiga gravada como CANCELADO
     * pelo fluxo legado sai como CANCELADO, com observacao e autoria nulas.
     */
    @Query(value = """
            SELECT
                ag.data_agendada AS dataAgendada,
                s.nome_paciente AS nomePaciente,
                s.cpf_paciente AS cpfPaciente,
                s.cns AS cns,
                s.datanascimento AS dataNascimento,
                u.nome AS unidadeNome,
                COALESCE(e.nome, CAST(se.especialidade_solicitada AS text)) AS especialidadeNome,
                se.status AS status,
                se.data_coleta AS dataColeta,
                se.atendimento_observacao AS observacao,
                ur.nome AS registradoPorNome,
                se.atendimento_registrado_em AS registradoEm
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            LEFT JOIN especialidade e ON e.id = se.especialidade_id
            LEFT JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
            LEFT JOIN unidade u ON u.id = s.unidade_id
            LEFT JOIN usuarios ur ON ur.id = se.atendimento_registrado_por_id
            WHERE ag.data_agendada BETWEEN :inicio AND :fim
              AND se.status IN ('REALIZADO', 'FALTOU', 'CANCELADO')
              AND (CAST(:grupo AS text) IS NULL OR gr.codigo = CAST(:grupo AS text))
            """ + FILTRO_EXECUTANTE + """
            ORDER BY ag.data_agendada, u.nome NULLS LAST, s.nome_paciente, se.id
            """, nativeQuery = true)
    List<AtendimentoRelatorioProjection> listarRelatorio(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("grupo") String grupo,
            @Param("porExecutante") boolean porExecutante,
            @Param("executanteIds") Collection<Long> executanteIds);
}
