package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.ContagemPainelPorDataLocalDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.EspecialidadesMaisSolicitadasProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PacientesGelProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PainelEspecialidadeProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalEspecialidadeRankingProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalQuantitativoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalRankingProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalSolicitacaoDetalheProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.RelatorioGrupoAgendadoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.RelatorioGrupoPendenteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.TempoEsperaEspecialidadeProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.TempoEsperaGeralProjection;

public interface SolicitacaoEspecialidadeRepository extends JpaRepository<SolicitacaoEspecialidade, Long> {

    @Query("SELECT se FROM SolicitacaoEspecialidade se " +
           "WHERE se.agendamentoSolicitacao.dataAgendada = :data " +
           "AND se.especialidadeSolicitada.codigo IN :codigos")
    List<SolicitacaoEspecialidade> findAgendadasPorDataECodigos(
            @Param("data") LocalDate data,
            @Param("codigos") Collection<String> codigos
    );

    @Query("SELECT COUNT(DISTINCT se.solicitacao.id) FROM SolicitacaoEspecialidade se " +
           "WHERE se.agendamentoSolicitacao.dataAgendada = :data " +
           "AND se.especialidadeSolicitada.codigo IN :codigos")
    long countDistinctSolicitacoesPorDataECodigos(@Param("data") LocalDate data, @Param("codigos") Collection<String> codigos);

    @Query("SELECT COUNT(se) FROM SolicitacaoEspecialidade se " +
       "WHERE se.agendamentoSolicitacao.dataAgendada = :data " +
       "AND se.especialidadeSolicitada.codigo IN :codigos")
    long countAgendadasPorDataECodigos(@Param("data") LocalDate data, @Param("codigos") Collection<String> codigos);

    @Query("SELECT se FROM SolicitacaoEspecialidade se " +
           "WHERE se.status = :status " +
           "AND se.especialidadeSolicitada.codigo IN :codigos")
    List<SolicitacaoEspecialidade> findByStatusAndEspecialidadeCodigos(
            @Param("status") StatusDaMarcacao status,
            @Param("codigos") Collection<String> codigos
    );

    @Query("SELECT COUNT(se) FROM SolicitacaoEspecialidade se " +
       "WHERE se.status = :status " +
       "AND se.especialidadeSolicitada.codigo IN :codigos")
    long countByStatusAndEspecialidadeCodigos(
            @Param("status") StatusDaMarcacao status,
            @Param("codigos") Collection<String> codigos
    );

    List<SolicitacaoEspecialidade> findByAgendamentoSolicitacaoId(Long agendamentoId);

    @Modifying
    @Query("UPDATE SolicitacaoEspecialidade se SET se.agendamentoSolicitacao = NULL, se.status = 'AGUARDANDO' WHERE se.agendamentoSolicitacao.id = :agendamentoId")
    void desvincularAgendamento(@Param("agendamentoId") Long agendamentoId);

    @Query("SELECT se FROM SolicitacaoEspecialidade se " +
        "JOIN FETCH se.solicitacao s " +
        "WHERE se.agendamentoSolicitacao.dataAgendada = :data " +
        "AND se.especialidadeSolicitada.codigo IN :codigos " +
        "ORDER BY s.nomePaciente, se.agendamentoSolicitacao.turno")
    List<SolicitacaoEspecialidade> findAgendadasCompletasPorDataECodigos(
        @Param("data") LocalDate data,
        @Param("codigos") Collection<String> codigos
    );

    List<SolicitacaoEspecialidade> findByStatus(StatusDaMarcacao status);

    @Query("SELECT new io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.ContagemPainelPorDataLocalDTO(" +
       "se.especialidadeSolicitada.nome, " +
       "CASE WHEN ag.localAgendamento IS NOT NULL THEN ag.localAgendamento.nomeLocal ELSE CONCAT('', ag.localAgendado) END, " +
       "ag.dataAgendada, " +
       "COUNT(se)) " +
       "FROM SolicitacaoEspecialidade se " +
       "JOIN se.agendamentoSolicitacao ag " +
       "WHERE se.status = 'AGENDADO' " +
       "GROUP BY se.especialidadeSolicitada.nome, ag.localAgendamento.nomeLocal, ag.localAgendado, ag.dataAgendada")
    List<ContagemPainelPorDataLocalDTO> contarAgendamentosAgrupados();


    @Query(value = """
            SELECT 
                s.id AS solicitacaoId,
                s.nome_paciente as nomePaciente,
                s.cpf_paciente as cpfPaciente, 
                s.cns as cns,
                s.datanascimento as dataNascimento,
                s.usf_origem as usfOrigem,
                ag.data_agendada as dataAgendada,
                ag.turno as turno,
                STRING_AGG(DISTINCT e.nome, ', ' ORDER BY e.nome) as especialidades
            FROM solicitacao s 
            JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
            JOIN especialidade e ON e.id = se.especialidade_id
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
            WHERE gr.codigo = :grupo 
                AND ag.data_agendada = :data
                AND se.status = 'AGENDADO'
            GROUP BY
                s.id,
                s.nome_paciente, 
                s.cpf_paciente, 
                s.cns,
                s.datanascimento, 
                s.usf_origem,
                ag.data_agendada,
                ag.turno
            """, nativeQuery = true)
            List<RelatorioGrupoAgendadoProjection> listarAgendadosPorGrupoEData(@Param("grupo") String grupo, @Param("data") LocalDate data);

            @Query(value = """
                    SELECT 
                        COUNT(DISTINCT s.id)
                    FROM solicitacao s 
                    JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
                    JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
                    WHERE gr.codigo = :grupo and ag.data_agendada = :data

                    """, nativeQuery = true)
            long countAgendadosPorGrupoEData(@Param("grupo") String grupo, @Param("data") LocalDate data);


            @Query(value = """
                    SELECT 
                        s.id AS solicitacaoId,
                        s.nome_paciente AS nomePaciente, 
                        s.cpf_paciente AS cpfPaciente, 
                        s.cns AS cns,
                        s.datanascimento AS dataNascimento,
                        s.usf_origem AS usfOrigem,
                        s.data_malote as dataMalote,
                        se.status AS status,
                        se.prioridade AS prioridade,
                        STRING_AGG(DISTINCT e.nome, ', ' ORDER BY e.nome) as especialidades
                    FROM solicitacao s 
                    JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
                    WHERE gr.codigo = :grupo and (se.status = 'AGUARDANDO' OR se.status = 'RETORNO' OR se.status = 'RETORNO_POLICLINICA') 
                    GROUP BY
                        s.id,
                        s.nome_paciente, 
                        s.cpf_paciente, 
                        s.cns,
                        s.datanascimento, 
                        s.usf_origem,
                        s.data_malote,
                        se.status,
                        se.prioridade
                    """, nativeQuery = true)
            List<RelatorioGrupoPendenteProjection> listarPendentesPorGrupo(@Param("grupo") String grupo);


            @Query(value = """
                    SELECT 
                        COUNT(DISTINCT s.id)
                    FROM solicitacao s 
                    JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
                    WHERE gr.codigo = :grupo and (se.status = 'AGUARDANDO' OR se.status = 'RETORNO' OR se.status = 'RETORNO_POLICLINICA')
                    """, nativeQuery = true)
            long countPendentesPorGrupo(@Param("grupo") String grupo);

            @Query(value = """
                    SELECT 
                        s.id AS solicitacaoId,
                        s.nome_paciente AS nomePaciente, 
                        s.cpf_paciente AS cpfPaciente, 
                        s.cns AS cns,
                        s.datanascimento AS dataNascimento,
                        s.usf_origem AS usfOrigem,
                        STRING_AGG(DISTINCT CAST(se.id AS text), ', ' ORDER BY se.id::text) AS solicitacaoEspecialidadeId,
                        STRING_AGG(DISTINCT e.nome, ', ' ORDER BY e.nome) as especialidades 
                    FROM solicitacao s 
                    JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
                    JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id 
                    WHERE (gr.codigo = :grupo) and (se.status = 'AGENDADO') and (ag.local_agendamento_id = 3 AND ag.data_agendada = :data) 
                    GROUP BY
                        s.id,
                        s.nome_paciente, 
                        s.cpf_paciente, 
                        s.cns,
                        s.datanascimento, 
                        s.usf_origem

                    """, nativeQuery = true)
            Page<PainelEspecialidadeProjection> listarPacientesAgendadosPorDataEGrupoELocal(@Param("grupo")String grupo, @Param("data") LocalDate data, Pageable pageable);


            @Query(value = """
                SELECT 
                    count (DISTINCT s.id)
                FROM solicitacao s 
                JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
                JOIN especialidade e ON e.id = se.especialidade_id
                JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
                JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id 
                WHERE (gr.codigo = :grupo) and (se.status = 'AGENDADO') and (ag.local_agendamento_id = 3 AND ag.data_agendada = :data) 
                """, nativeQuery = true)    
            long totalPacientesAgendadosPorGrupoELocal(@Param("grupo") String grupo, @Param("data") LocalDate data);


            @Query(value = """
                     SELECT 
                        COUNT(DISTINCT s.id)
                    FROM solicitacao s 
                    JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
                    LEFT JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id 
                    WHERE (gr.id = :grupoId) and (se.status = :status) and (ag.local_agendamento_id = :localId AND ag.data_agendada = :data) 
                    """, nativeQuery = true)
                    long countAgendamentosPorGrupoEData(@Param("grupoId")Long grupoId, @Param("localId") Long localId, @Param("data") LocalDate data, @Param("status")String status);



            @Query(
                    value = """
                    SELECT
                        s.id AS solicitacaoId,
                        se.id AS solicitacaoEspecialidadeId,
                        s.nome_paciente AS nomePaciente,
                        s.cpf_paciente AS cpfPaciente,
                        s.cns AS cns,
                        s.usf_origem AS usfOrigem,
                        s.datanascimento AS dataNascimento,
                        e.nome AS especialidade,
                        se.prioridade AS prioridade,
                        s.data_malote AS dataMalote
                    FROM solicitacao s
                    JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    WHERE se.status = 'GEL'
                      AND (
                        :termo IS NULL OR :termo = ''
                        OR lower(s.nome_paciente) LIKE concat('%', lower(:termo), '%')
                        OR lower(s.usf_origem) LIKE concat('%', lower(:termo), '%')
                        OR (
                          regexp_replace(:termo, '[^0-9]', '', 'g') <> ''
                          AND regexp_replace(s.cpf_paciente, '[^0-9]', '', 'g')
                            LIKE concat('%', regexp_replace(:termo, '[^0-9]', '', 'g'), '%')
                        )
                      )
                    ORDER BY s.nome_paciente ASC
                    """,
                    countQuery = """
                    SELECT COUNT(*)
                    FROM solicitacao s
                    JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    WHERE se.status = 'GEL'
                      AND (
                        :termo IS NULL OR :termo = ''
                        OR lower(s.nome_paciente) LIKE concat('%', lower(:termo), '%')
                        OR lower(s.usf_origem) LIKE concat('%', lower(:termo), '%')
                        OR (
                          regexp_replace(:termo, '[^0-9]', '', 'g') <> ''
                          AND regexp_replace(s.cpf_paciente, '[^0-9]', '', 'g')
                            LIKE concat('%', regexp_replace(:termo, '[^0-9]', '', 'g'), '%')
                        )
                      )
                    """,
                    nativeQuery = true)
            Page<PacientesGelProjection> listarPacientesGel(@Param("termo") String termo, Pageable page);

            @Query(value = """
                    SELECT
                        s.id AS solicitacaoId,
                        se.id AS solicitacaoEspecialidadeId,
                        s.nome_paciente AS nomePaciente,
                        s.cpf_paciente AS cpfPaciente,
                        s.cns AS cns,
                        s.usf_origem AS usfOrigem,
                        s.datanascimento AS dataNascimento,
                        e.nome AS especialidade,
                        se.prioridade AS prioridade,
                        s.data_malote AS dataMalote
                    FROM solicitacao s
                    JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    WHERE se.status = 'GEL'
                    ORDER BY
                        CASE se.prioridade
                            WHEN 'EMERGENCIA' THEN 1
                            WHEN 'URGENTE'    THEN 2
                            ELSE 3
                        END,
                        s.nome_paciente ASC
                    """, nativeQuery = true)
            List<PacientesGelProjection> listarTodosPacientesGel();

            @Query(value = """
                    SELECT
                        p.id              AS id,
                        p.nome            AS nome,
                        p.conselho        AS conselho,
                        p.numero_registro AS numeroRegistro,
                        COUNT(se.id)      AS totalSolicitacoes
                    FROM solicitacao_especialidade se
                    JOIN profissional p ON p.id = se.profissional_id
                    JOIN solicitacao s  ON s.id = se.solicitacao_id
                    WHERE se.profissional_id IS NOT NULL
                      AND (CAST(:inicio AS date) IS NULL OR s.data_malote >= :inicio)
                      AND (CAST(:fim AS date)    IS NULL OR s.data_malote <= :fim)
                    GROUP BY p.id, p.nome, p.conselho, p.numero_registro
                    ORDER BY COUNT(se.id) DESC
                    LIMIT :limite
                    """, nativeQuery = true)
            List<ProfissionalRankingProjection> rankingProfissionaisPorPeriodo(
                    @Param("inicio") LocalDate inicio,
                    @Param("fim")    LocalDate fim,
                    @Param("limite") int limite);

            @Query(value = """
                    SELECT
                        e.nome       AS especialidadeNome,
                        COUNT(se.id) AS total
                    FROM solicitacao_especialidade se
                    JOIN especialidade e ON e.id = se.especialidade_id
                    JOIN solicitacao s   ON s.id = se.solicitacao_id
                    WHERE se.profissional_id = :profissionalId
                      AND (CAST(:inicio AS date) IS NULL OR s.data_malote >= :inicio)
                      AND (CAST(:fim AS date)    IS NULL OR s.data_malote <= :fim)
                    GROUP BY e.id, e.nome
                    ORDER BY COUNT(se.id) DESC
                    LIMIT 10
                    """, nativeQuery = true)
            List<ProfissionalEspecialidadeRankingProjection> topEspecialidadesPorProfissional(
                    @Param("profissionalId") Long profissionalId,
                    @Param("inicio")         LocalDate inicio,
                    @Param("fim")            LocalDate fim);

            @Query(value = """
                    SELECT
                        p.id              AS profissionalId,
                        p.nome            AS profissionalNome,
                        p.conselho        AS conselho,
                        p.numero_registro AS numeroRegistro,
                        e.categoria       AS tipo,
                        e.nome            AS especialidadeNome,
                        s.nome_paciente   AS pacienteNome,
                        s.cpf_paciente    AS cpfPaciente,
                        se.data_cadastro  AS dataSolicitacao
                    FROM solicitacao_especialidade se
                    JOIN profissional  p ON p.id = se.profissional_id
                    JOIN solicitacao   s ON s.id = se.solicitacao_id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    WHERE se.profissional_id IS NOT NULL
                      AND (CAST(:inicio AS date)   IS NULL OR CAST(se.data_cadastro AS DATE) >= :inicio)
                      AND (CAST(:fim AS date)      IS NULL OR CAST(se.data_cadastro AS DATE) <= :fim)
                      AND (CAST(:profissionalId AS bigint) IS NULL OR p.id = :profissionalId)
                      AND (CAST(:unidadeId AS bigint)      IS NULL OR s.unidade_id = :unidadeId)
                    ORDER BY se.data_cadastro DESC
                    """,
                    countQuery = """
                    SELECT COUNT(*)
                    FROM solicitacao_especialidade se
                    JOIN profissional p ON p.id = se.profissional_id
                    JOIN solicitacao  s ON s.id = se.solicitacao_id
                    WHERE se.profissional_id IS NOT NULL
                      AND (CAST(:inicio AS date)   IS NULL OR CAST(se.data_cadastro AS DATE) >= :inicio)
                      AND (CAST(:fim AS date)      IS NULL OR CAST(se.data_cadastro AS DATE) <= :fim)
                      AND (CAST(:profissionalId AS bigint) IS NULL OR p.id = :profissionalId)
                      AND (CAST(:unidadeId AS bigint)      IS NULL OR s.unidade_id = :unidadeId)
                    """,
                    nativeQuery = true)
            Page<ProfissionalSolicitacaoDetalheProjection> listarDetalhadoPorProfissional(
                    @Param("inicio")         LocalDate inicio,
                    @Param("fim")            LocalDate fim,
                    @Param("profissionalId") Long profissionalId,
                    @Param("unidadeId")      Long unidadeId,
                    Pageable pageable);

            @Query(value = """
                    SELECT
                        p.id              AS profissionalId,
                        p.nome            AS profissionalNome,
                        p.conselho        AS conselho,
                        p.numero_registro AS numeroRegistro,
                        e.categoria       AS tipo,
                        e.nome            AS especialidadeNome,
                        s.nome_paciente   AS pacienteNome,
                        s.cpf_paciente    AS cpfPaciente,
                        se.data_cadastro  AS dataSolicitacao
                    FROM solicitacao_especialidade se
                    JOIN profissional  p ON p.id = se.profissional_id
                    JOIN solicitacao   s ON s.id = se.solicitacao_id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    WHERE se.profissional_id IS NOT NULL
                      AND (CAST(:inicio AS date)   IS NULL OR CAST(se.data_cadastro AS DATE) >= :inicio)
                      AND (CAST(:fim AS date)      IS NULL OR CAST(se.data_cadastro AS DATE) <= :fim)
                      AND (CAST(:profissionalId AS bigint) IS NULL OR p.id = :profissionalId)
                      AND (CAST(:unidadeId AS bigint)      IS NULL OR s.unidade_id = :unidadeId)
                    ORDER BY p.nome ASC, se.data_cadastro DESC
                    """, nativeQuery = true)
            List<ProfissionalSolicitacaoDetalheProjection> listarDetalhadoPorProfissionalCompleto(
                    @Param("inicio")         LocalDate inicio,
                    @Param("fim")            LocalDate fim,
                    @Param("profissionalId") Long profissionalId,
                    @Param("unidadeId")      Long unidadeId);

            @Query(value = """
                    SELECT
                        p.id              AS profissionalId,
                        p.nome            AS profissionalNome,
                        p.conselho        AS conselho,
                        p.numero_registro AS numeroRegistro,
                        COUNT(*)          AS totalNoPeriodo,
                        COUNT(*) FILTER (WHERE e.categoria = 'ESPECIALIDADE_MEDICA')  AS consultas,
                        COUNT(*) FILTER (WHERE e.categoria = 'EXAME_OU_PROCEDIMENTO') AS examesProcedimentos
                    FROM solicitacao_especialidade se
                    JOIN profissional  p ON p.id = se.profissional_id
                    JOIN solicitacao   s ON s.id = se.solicitacao_id
                    JOIN especialidade e ON e.id = se.especialidade_id
                    WHERE se.profissional_id IS NOT NULL
                      AND CAST(se.data_cadastro AS DATE) >= :inicio
                      AND CAST(se.data_cadastro AS DATE) <= :fim
                      AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)
                    GROUP BY p.id, p.nome, p.conselho, p.numero_registro
                    ORDER BY COUNT(*) DESC
                    """, nativeQuery = true)
            List<ProfissionalQuantitativoProjection> listarQuantitativoPorProfissional(
                    @Param("inicio")    LocalDate inicio,
                    @Param("fim")       LocalDate fim,
                    @Param("unidadeId") Long unidadeId);

            @Query(value = """
                    SELECT
                        e.id              AS especialidadeId,
                        e.nome            AS especialidadeNome,
                        COUNT(*)          AS totalAgendados,
                        AVG(ag.data_agendada - CAST(se.data_cadastro AS DATE)) AS tempoMedioEsperaDias,
                        MIN(ag.data_agendada - CAST(se.data_cadastro AS DATE)) AS tempoMinimoEsperaDias,
                        MAX(ag.data_agendada - CAST(se.data_cadastro AS DATE)) AS tempoMaximoEsperaDias
                    FROM solicitacao_especialidade se
                    JOIN especialidade e  ON e.id = se.especialidade_id
                    JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
                    JOIN solicitacao s    ON s.id = se.solicitacao_id
                    WHERE se.agendamento_id IS NOT NULL
                      AND (CAST(:inicio AS date)      IS NULL OR CAST(se.data_cadastro AS DATE) >= :inicio)
                      AND (CAST(:fim AS date)         IS NULL OR CAST(se.data_cadastro AS DATE) <= :fim)
                      AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)
                    GROUP BY e.id, e.nome
                    ORDER BY AVG(ag.data_agendada - CAST(se.data_cadastro AS DATE)) DESC
                    """, nativeQuery = true)
            List<TempoEsperaEspecialidadeProjection> tempoEsperaPorEspecialidade(
                    @Param("inicio")    LocalDate inicio,
                    @Param("fim")       LocalDate fim,
                    @Param("unidadeId") Long unidadeId);

            @Query(value = """
                    SELECT
                        COUNT(*) AS totalAgendados,
                        AVG(ag.data_agendada - CAST(se.data_cadastro AS DATE)) AS tempoMedioEsperaDias,
                        MIN(ag.data_agendada - CAST(se.data_cadastro AS DATE)) AS tempoMinimoEsperaDias,
                        MAX(ag.data_agendada - CAST(se.data_cadastro AS DATE)) AS tempoMaximoEsperaDias
                    FROM solicitacao_especialidade se
                    JOIN especialidade e  ON e.id = se.especialidade_id
                    JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
                    JOIN solicitacao s    ON s.id = se.solicitacao_id
                    WHERE se.agendamento_id IS NOT NULL
                      AND (CAST(:inicio AS date)          IS NULL OR CAST(se.data_cadastro AS DATE) >= :inicio)
                      AND (CAST(:fim AS date)             IS NULL OR CAST(se.data_cadastro AS DATE) <= :fim)
                      AND (CAST(:unidadeId AS bigint)      IS NULL OR s.unidade_id = :unidadeId)
                      AND (CAST(:especialidadeId AS bigint) IS NULL OR e.id = :especialidadeId)
                    """, nativeQuery = true)
            TempoEsperaGeralProjection tempoEsperaGeral(
                    @Param("inicio")         LocalDate inicio,
                    @Param("fim")            LocalDate fim,
                    @Param("unidadeId")      Long unidadeId,
                    @Param("especialidadeId") Long especialidadeId);

}
