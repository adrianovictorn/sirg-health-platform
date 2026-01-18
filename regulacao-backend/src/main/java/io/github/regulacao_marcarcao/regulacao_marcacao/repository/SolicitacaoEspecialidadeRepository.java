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
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PainelEspecialidadeProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.RelatorioGrupoAgendadoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.RelatorioGrupoPendenteProjection;

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

}
