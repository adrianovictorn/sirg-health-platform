package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.FechamentoIndicadoresDia;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.GraficoGrupoPorDataProjection;

public interface FechamentoIndicadoresDiaRepository extends JpaRepository<FechamentoIndicadoresDia, Long> {
    
    Optional<FechamentoIndicadoresDia> findByDataReferenciaAndLocalAgendamentoIdAndGrupoRelatorioId(LocalDate data, Long localAgendamentoId,Long grupoId);
    
    //Contar total de pacientes agendados no dia
    @Query(value = """
            SELECT 
                COUNT(DISTINCT s.id)
            FROM solicitacao s 
            JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
            JOIN agendamento_solicitacao ag ON se.agendamento_id = ag.id
            WHERE ag.data_agendada = :data
            """,nativeQuery = true)
    long totalDeAgendadosDoDia(@Param("data") LocalDate data); 

    //Contar total de pacientes novos no dia
    @Query(value = """
            SELECT 
                COUNT(DISTINCT s.id)
            FROM solicitacao s 
            JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
            WHERE s.data_malote = :data
            """,nativeQuery = true)
    long totalDeSolicitacoesCadastradasDoDia(@Param("data") LocalDate data);

    //Total de especialidades cadastradas do dia
    @Query(value = """
            SELECT 
                count(se.id)
            FROM solicitacao s 
            JOIN solicitacao_especialidade se ON se.solicitacao_id = s.id
            WHERE se.data_cadastro::date = :data

            """, nativeQuery = true)
    long totalDeSolicitacoesEspecialidadesDoDia(@Param("data") LocalDate data);

    @Query(value = """
            SELECT 
                gr.nome,
                count(se.id) as total
            FROM solicitacao_especialidade se
            JOIN especialidade e ON e.id = se.especialidade_id
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN grupo_relatorio gr ON gr.id = e.grupo_relatorio_id
            WHERE ag.data_agendada >= :inicio AND ag.data_agendada < :intervalo 
            GROUP BY 
                gr.nome
            """,nativeQuery = true)
    Page<GraficoGrupoPorDataProjection> totalDeAgendamentoPorGrupoEPeriodo(@Param("inicio") LocalDate inicio, @Param("intervalo") LocalDate intervalo, Pageable pagina);
}
