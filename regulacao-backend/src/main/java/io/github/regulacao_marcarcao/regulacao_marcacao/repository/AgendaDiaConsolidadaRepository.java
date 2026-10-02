package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AgendaDiaAgregadoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AgendaDiaCotaProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AgendaDiaItemProjection;

/**
 * Consultas somente-leitura da Agenda do Dia consolidada (visao do ADMIN). Fica a
 * parte de {@link SolicitacaoEspecialidadeRepository} para nao alterar nenhuma
 * consulta da agenda por unidade.
 *
 * LEFT JOIN em unidade e grupo_relatorio de proposito: solicitacao sem unidade e
 * especialidade sem grupo precisam aparecer (baldes "Sem unidade"/"Sem grupo"),
 * senao o total do dia seria subcontado.
 */
public interface AgendaDiaConsolidadaRepository extends JpaRepository<SolicitacaoEspecialidade, Long> {

    @Query(value = """
            SELECT
                s.unidade_id AS unidadeId,
                MAX(u.nome) AS unidadeNome,
                e.grupo_relatorio_id AS grupoId,
                MAX(gr.codigo) AS grupoCodigo,
                MAX(gr.nome) AS grupoNome,
                e.id AS especialidadeId,
                MAX(e.nome) AS especialidadeNome,
                GROUPING(s.unidade_id) AS unidadeAgregada,
                GROUPING(e.grupo_relatorio_id) AS grupoAgregado,
                GROUPING(e.id) AS especialidadeAgregada,
                COUNT(DISTINCT s.id) AS pacientes,
                COUNT(*) AS itens,
                COUNT(*) FILTER (WHERE se.status = 'AGENDADO') AS agendados,
                COUNT(*) FILTER (WHERE se.status = 'REALIZADO') AS realizados,
                COUNT(*) FILTER (WHERE se.status IN ('CANCELADO', 'FALTOU')) AS faltasCancelados
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            JOIN especialidade e ON e.id = se.especialidade_id
            LEFT JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
            LEFT JOIN unidade u ON u.id = s.unidade_id
            WHERE ag.data_agendada = :data
              AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)
              AND (CAST(:grupoId AS bigint) IS NULL OR e.grupo_relatorio_id = :grupoId)
              AND (CAST(:especialidadeId AS bigint) IS NULL OR e.id = :especialidadeId)
            GROUP BY GROUPING SETS (
                (s.unidade_id, e.grupo_relatorio_id, e.id),
                (s.unidade_id, e.grupo_relatorio_id),
                (s.unidade_id),
                (e.grupo_relatorio_id),
                ()
            )
            """, nativeQuery = true)
    List<AgendaDiaAgregadoProjection> agregarDia(
            @Param("data") LocalDate data,
            @Param("unidadeId") Long unidadeId,
            @Param("grupoId") Long grupoId,
            @Param("especialidadeId") Long especialidadeId);

    @Query(value = """
            SELECT
                c.id AS id,
                c.unidade_id AS unidadeId,
                c.grupo_unidades_id AS grupoUnidadesId,
                c.especialidade_id AS especialidadeId,
                e.nome AS especialidadeNome,
                e.grupo_relatorio_id AS especialidadeGrupoId,
                egr.codigo AS especialidadeGrupoCodigo,
                egr.nome AS especialidadeGrupoNome,
                c.grupo_especialidades_id AS grupoEspecialidadesId,
                ggr.codigo AS grupoEspecialidadesCodigo,
                ggr.nome AS grupoEspecialidadesNome,
                c.tipo_periodo AS tipoPeriodo,
                c.quantidade_total AS quantidadeTotal,
                c.quantidade_utilizada AS quantidadeUtilizada,
                c.dias_semana AS diasSemana
            FROM cota_unidade c
            LEFT JOIN especialidade e ON e.id = c.especialidade_id
            LEFT JOIN grupo_relatorio egr ON egr.id = e.grupo_relatorio_id
            LEFT JOIN grupo_relatorio ggr ON ggr.id = c.grupo_especialidades_id
            WHERE c.ativo = true
              AND ((c.tipo_periodo = 'DATA' AND c.data_especifica = :data)
                OR (c.tipo_periodo = 'MENSAL' AND c.periodo = :periodo))
            ORDER BY c.id
            """, nativeQuery = true)
    List<AgendaDiaCotaProjection> listarCotasDoDia(
            @Param("data") LocalDate data,
            @Param("periodo") String periodo);

    @Query(value = """
            SELECT
                se.id AS solicitacaoEspecialidadeId,
                s.id AS solicitacaoId,
                s.nome_paciente AS nomePaciente,
                s.cpf_paciente AS cpfPaciente,
                s.cns AS cns,
                s.datanascimento AS dataNascimento,
                u.nome AS unidadeNome,
                s.usf_origem AS usfOrigem,
                e.nome AS especialidadeNome,
                se.status AS status,
                CAST(ag.turno AS text) AS turno,
                CAST(se.hora_agendada AS text) AS horaAgendada
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            JOIN especialidade e ON e.id = se.especialidade_id
            LEFT JOIN unidade u ON u.id = s.unidade_id
            WHERE ag.data_agendada = :data
              AND e.id = :especialidadeId
              AND ((:semUnidade = true AND s.unidade_id IS NULL)
                OR (:semUnidade = false
                    AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)))
            ORDER BY s.nome_paciente, se.id
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            WHERE ag.data_agendada = :data
              AND se.especialidade_id = :especialidadeId
              AND ((:semUnidade = true AND s.unidade_id IS NULL)
                OR (:semUnidade = false
                    AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)))
            """, nativeQuery = true)
    Page<AgendaDiaItemProjection> listarItensDoDia(
            @Param("data") LocalDate data,
            @Param("especialidadeId") Long especialidadeId,
            @Param("unidadeId") Long unidadeId,
            @Param("semUnidade") boolean semUnidade,
            Pageable pageable);
}
