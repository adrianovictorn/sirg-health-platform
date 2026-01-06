package io.github.regulacao_marcarcao.regulacao_marcacao.repository;


import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.UsfEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PendenciasPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.StatusCountProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UrgenciaEmergenciaPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UsfPendentesProjection;


public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long>, JpaSpecificationExecutor<Solicitacao> {

    boolean existsByCpfPaciente(String cpf);

    @Query(value = "SELECT * FROM solicitacao s WHERE regexp_replace(s.cpf_paciente, '[^0-9]', '', 'g') = :cpf", nativeQuery = true)
    List<Solicitacao> findByCpfPacienteSemPonto(@Param("cpf") String cpf);

    @Query("SELECT se.status AS status, COUNT(se) AS total FROM SolicitacaoEspecialidade se GROUP BY se.status")
    List<StatusCountProjection> contarPorStatus();

    @Query("SELECT COUNT(se) FROM SolicitacaoEspecialidade se WHERE se.status = :status AND se.prioridade IN :prioridades")
    long contarPorStatusPrioridades(@Param("status") StatusDaMarcacao status,
                                    @Param("prioridades") List<PrioridadeDaMarcacaoEnum> prioridades);

    @Query("SELECT s.usfOrigem AS usf, COUNT(se) AS total FROM SolicitacaoEspecialidade se JOIN se.solicitacao s "
         + "WHERE se.status = :status GROUP BY s.usfOrigem")
    List<UsfPendentesProjection> contarPorUsfEStatus(@Param("status") StatusDaMarcacao status);

    @Query("""
    select distinct new io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoResumoDTO(
            s.id,
            s.nomePaciente,
            s.cpfPaciente,
            s.cns,
            s.usfOrigem,
            null
        )
        from Solicitacao s
        join s.especialidades se
        where se.status in :statusPendentes
          and (
            :termo is null
            or :termo = ''
            or lower(s.nomePaciente) like concat('%', lower(:termo), '%')
            or lower(s.cpfPaciente) like concat('%', lower(:termo), '%')
          )
    """)
    Page<SolicitacaoResumoDTO> buscarPendentesPorTermo(
        @Param("statusPendentes") List<StatusDaMarcacao> statusPendentes,
        @Param("termo") String termo,
        Pageable pageable
    );

    Page<PendenciasPacienteProjection> findByUsfOrigem(Pageable pageable, UsfEnum usfEnum);
    Page<Solicitacao> findAll(Pageable pageable);
    Page<Solicitacao> findByNomePacienteContainingIgnoreCaseOrCpfPacienteContainingIgnoreCase(Pageable pageable, String nomePaciente, String cpfPaciente);

    @Query(value = """
        SELECT 
          s.id AS id,
          s.nome_paciente AS nomePaciente,
          s.cpf_paciente AS cpfPaciente,
          s.datanascimento AS datanascimento,
          s.cns AS cns,
          STRING_AGG(DISTINCT e.nome, ', ' ORDER BY e.nome) AS especialidades
        FROM solicitacao s
        JOIN solicitacao_especialidade se ON s.id = se.solicitacao_id
        JOIN especialidade e ON e.id = se.especialidade_id
        WHERE se.status = :status
          AND s.usf_origem = :usfOrigem
          AND (
                :termo IS NULL OR :termo = ''
                OR lower(s.nome_paciente) LIKE concat('%', lower(:termo), '%')

                OR (
                  regexp_replace(:termo, '[^0-9]', '', 'g') <> ''
                  AND regexp_replace(s.cpf_paciente, '[^0-9]', '', 'g')
                    LIKE concat('%', regexp_replace(:termo, '[^0-9]', '', 'g'), '%')
                )

                OR s.cns LIKE concat('%', :termo, '%')
                OR lower(e.nome) LIKE concat('%', lower(:termo), '%')
              )
        GROUP BY
          s.id,
          s.nome_paciente,
          s.cpf_paciente,
          s.datanascimento,
          s.cns
        ORDER BY s.nome_paciente ASC
        """,
        countQuery = """
          SELECT COUNT(DISTINCT s.id)
            FROM solicitacao s
            JOIN solicitacao_especialidade se ON s.id = se.solicitacao_id
            JOIN especialidade e ON e.id = se.especialidade_id
            WHERE se.status = :status
              AND s.usf_origem = :usfOrigem
              AND (
                :termo IS NULL OR :termo = ''
                OR lower(s.nome_paciente) LIKE concat('%', lower(:termo), '%')
                OR (
                  regexp_replace(:termo, '[^0-9]', '', 'g') <> ''
                  AND regexp_replace(s.cpf_paciente, '[^0-9]', '', 'g')
                    LIKE concat('%', regexp_replace(:termo, '[^0-9]', '', 'g'), '%')
                )
                OR s.cns LIKE concat('%', :termo, '%')
                OR lower(e.nome) LIKE concat('%', lower(:termo), '%')
              )
        """,
        nativeQuery = true)
        Page<PendenciasPacienteProjection> listarPacientesPendentes(@Param("usfOrigem") String usfOrigem, @Param("status") String status, @Param("termo") String termo, Pageable pageable);


        @Query(
            value = """
              SELECT 
                s.id AS id,
                s.nome_paciente AS nomePaciente,
                s.cpf_paciente AS cpfPaciente,
                s.datanascimento AS dataNascimento,
                s.cns AS cns,
                s.usf_origem AS usfOrigem,
                STRING_AGG(DISTINCT (e.nome || ' - ' || se.prioridade), ', ' ORDER BY e.nome || ' - ' || se.prioridade) AS itens
              FROM solicitacao s
              JOIN solicitacao_especialidade se ON s.id = se.solicitacao_id
              JOIN especialidade e ON se.especialidade_id = e.id
              WHERE se.status IN ('AGUARDANDO', 'RETORNO', 'RETORNO_POLICLINICA')
                AND se.prioridade IN ('URGENTE', 'EMERGENCIA') AND
                (:termo IS NULL OR :termo = ''
                OR s.nome_paciente ILIKE '%' || :termo || '%'
                OR s.cpf_paciente ILIKE '%' || :termo || '%'
                )
              GROUP BY
                s.id, s.nome_paciente, s.cpf_paciente, s.datanascimento, s.cns, s.usf_origem
              ORDER BY s.nome_paciente ASC
            """,
            countQuery = """
              SELECT COUNT(DISTINCT s.id)
              FROM solicitacao s
              JOIN solicitacao_especialidade se ON s.id = se.solicitacao_id
              WHERE se.status IN ('AGUARDANDO', 'RETORNO', 'RETORNO_POLICLINICA')
                AND se.prioridade IN ('URGENTE', 'EMERGENCIA') AND 
                (:termo IS NULL OR :termo = ''
                  OR s.nome_paciente ILIKE '%' || :termo || '%'
                  OR s.cpf_paciente ILIKE '%' || :termo || '%'
                )
            """,
            nativeQuery = true
          )
          Page<UrgenciaEmergenciaPacienteProjection> listarPacientesUrgenteseEmergencias(Pageable pageable, @Param("termo") String termo);

}
