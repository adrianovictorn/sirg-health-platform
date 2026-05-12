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
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PendenciasPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.StatusCountProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UrgenciaEmergenciaPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UsfPendentesProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UnidadePendentesProjection;


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

    @Query("SELECT s.unidade.id AS unidadeId, s.unidade.nome AS unidadeNome, COUNT(se) AS total "
         + "FROM SolicitacaoEspecialidade se JOIN se.solicitacao s "
         + "WHERE se.status = :status AND s.unidade IS NOT NULL GROUP BY s.unidade.id, s.unidade.nome")
    List<UnidadePendentesProjection> contarPorUnidadeEStatus(@Param("status") StatusDaMarcacao status);

    // --- Queries unit-aware para dashboard de operadores ---

    @Query("SELECT se.status AS status, COUNT(se) AS total "
         + "FROM SolicitacaoEspecialidade se JOIN se.solicitacao s "
         + "WHERE (s.unidade.id = :unidadeId OR (s.unidade IS NULL AND s.usfOrigem = :usfOrigem)) "
         + "GROUP BY se.status")
    List<StatusCountProjection> contarPorStatusParaUnidade(
        @Param("unidadeId") Long unidadeId,
        @Param("usfOrigem") UsfEnum usfOrigem);

    @Query("SELECT se.status AS status, COUNT(se) AS total "
         + "FROM SolicitacaoEspecialidade se JOIN se.solicitacao s "
         + "WHERE s.unidade.id = :unidadeId GROUP BY se.status")
    List<StatusCountProjection> contarPorStatusParaUnidadeSemUsf(@Param("unidadeId") Long unidadeId);

    @Query("SELECT COUNT(se) FROM SolicitacaoEspecialidade se JOIN se.solicitacao s "
         + "WHERE se.status = :status AND se.prioridade IN :prioridades "
         + "AND (s.unidade.id = :unidadeId OR (s.unidade IS NULL AND s.usfOrigem = :usfOrigem))")
    long contarPorStatusPrioridadesParaUnidade(
        @Param("status") StatusDaMarcacao status,
        @Param("prioridades") List<PrioridadeDaMarcacaoEnum> prioridades,
        @Param("unidadeId") Long unidadeId,
        @Param("usfOrigem") UsfEnum usfOrigem);

    @Query("SELECT COUNT(se) FROM SolicitacaoEspecialidade se JOIN se.solicitacao s "
         + "WHERE se.status = :status AND se.prioridade IN :prioridades AND s.unidade.id = :unidadeId")
    long contarPorStatusPrioridadesParaUnidadeSemUsf(
        @Param("status") StatusDaMarcacao status,
        @Param("prioridades") List<PrioridadeDaMarcacaoEnum> prioridades,
        @Param("unidadeId") Long unidadeId);

    @Query("SELECT COUNT(DISTINCT s) FROM Solicitacao s JOIN s.especialidades se "
         + "WHERE s.unidade.id = :unidadeId OR (s.unidade IS NULL AND s.usfOrigem = :usfOrigem)")
    long contarSolicitacoesParaUnidade(
        @Param("unidadeId") Long unidadeId,
        @Param("usfOrigem") UsfEnum usfOrigem);

    @Query("SELECT COUNT(DISTINCT s) FROM Solicitacao s WHERE s.unidade.id = :unidadeId")
    long contarSolicitacoesParaUnidadeSemUsf(@Param("unidadeId") Long unidadeId);

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

    @Query(value = """
     SELECT 
        s.id AS id,
        s.nome_paciente AS nomePaciente,
        s.cpf_paciente AS cpfPaciente,
        s.cns AS cns,
        s.usf_origem AS usfOrigem,
        s.datanascimento AS dataNascimento,
        se.id AS solicitacaoEspecialidadeId,
        STRING_AGG(DISTINCT e.nome, ', ' ORDER BY e.nome) AS especialidade,
        STRING_AGG(DISTINCT se.prioridade, ', ' ORDER BY se.prioridade DESC) AS prioridade
      FROM solicitacao s
      JOIN solicitacao_especialidade se
          ON s.id = se.solicitacao_id
      JOIN especialidade e 
          ON e.id = se.especialidade_id
      WHERE 
          se.status = :status
          AND (
              :termo IS NULL OR :termo = ''
              OR s.nome_paciente ILIKE '%' || :termo || '%'
              OR s.cpf_paciente ILIKE '%' || :termo || '%'
              OR e.nome ILIKE '%' || :termo || '%'
              OR e.categoria ILIKE '%' || :termo || '%'
          )
      GROUP BY
          s.id,
          s.nome_paciente,
          s.cpf_paciente,
          s.cns,
          s.usf_origem,
          s.datanascimento,
          se.id 
        """,nativeQuery = true)
    Page<PacienteProjection> buscarPorStatus(Pageable pageable, String termo, String status);

    @Query(value = """
      SELECT
          MIN(s.id)                                              AS id,
          MIN(s.nome_paciente)                                   AS nomePaciente,
          s.cpf_paciente                                         AS cpfPaciente,
          MIN(s.cns)                                             AS cns,
          MIN(s.usf_origem)                                      AS usfOrigem,
          MIN(s.datanascimento)                                  AS dataNascimento,
          MIN(se.id)                                             AS solicitacaoEspecialidadeId,
          STRING_AGG(DISTINCT e.nome, ', ' ORDER BY e.nome)      AS especialidade,
          ''                                                     AS prioridade
      FROM solicitacao s
      JOIN solicitacao_especialidade se ON s.id = se.solicitacao_id
      JOIN especialidade e              ON e.id = se.especialidade_id
      WHERE se.status = 'REALIZADO'
        AND (
            :termo IS NULL OR :termo = ''
            OR s.nome_paciente ILIKE '%' || :termo || '%'
            OR s.cpf_paciente  ILIKE '%' || :termo || '%'
            OR e.nome          ILIKE '%' || :termo || '%'
        )
      GROUP BY s.cpf_paciente
      ORDER BY MIN(s.nome_paciente)
      """,
      countQuery = """
      SELECT COUNT(DISTINCT s.cpf_paciente)
      FROM solicitacao s
      JOIN solicitacao_especialidade se ON s.id = se.solicitacao_id
      JOIN especialidade e              ON e.id = se.especialidade_id
      WHERE se.status = 'REALIZADO'
        AND (
            :termo IS NULL OR :termo = ''
            OR s.nome_paciente ILIKE '%' || :termo || '%'
            OR s.cpf_paciente  ILIKE '%' || :termo || '%'
            OR e.nome          ILIKE '%' || :termo || '%'
        )
      """, nativeQuery = true)
    Page<PacienteProjection> buscarConcluidosAgrupados(@Param("termo") String termo, Pageable pageable);

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
           AND(
              :usfOrigem IS NULL OR :usfOrigem = '' 
              OR s.usf_origem = :usfOrigem
            )
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
              AND (
                  :usfOrigem IS NULL OR :usfOrigem = ''  
                  OR s.usf_origem = :usfOrigem
                )
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
                STRING_AGG(sub.itens, ', ' ORDER BY sub.itens) AS itens
              FROM solicitacao s
              JOIN (
                SELECT DISTINCT
                  se.solicitacao_id,
                  (e.nome || ' - ' || se.prioridade) AS itens,
                  e.nome AS especialidade_nome,
                  e.categoria AS especialidade_categoria
                FROM solicitacao_especialidade se
                JOIN especialidade e ON se.especialidade_id = e.id
                WHERE se.status IN ('AGUARDANDO', 'RETORNO', 'RETORNO_POLICLINICA', 'GEL')
                  AND se.prioridade IN ('URGENTE', 'EMERGENCIA')
              ) sub ON sub.solicitacao_id = s.id
              WHERE
                :termo IS NULL OR :termo = ''
                OR s.nome_paciente ILIKE '%' || :termo || '%'
                OR s.cpf_paciente ILIKE '%' || :termo || '%'
                OR sub.especialidade_nome ILIKE '%' || :termo || '%'
                OR sub.especialidade_categoria ILIKE '%' || :termo || '%'
              GROUP BY
                s.id, s.nome_paciente, s.cpf_paciente, s.datanascimento, s.cns, s.usf_origem
              ORDER BY s.nome_paciente ASC
            """,
            countQuery = """
              SELECT COUNT(DISTINCT s.id)
              FROM solicitacao s
              JOIN (
                SELECT DISTINCT
                  se.solicitacao_id,
                  e.nome AS especialidade_nome,
                  e.categoria AS especialidade_categoria
                FROM solicitacao_especialidade se
                JOIN especialidade e ON se.especialidade_id = e.id
                WHERE se.status IN ('AGUARDANDO', 'RETORNO', 'RETORNO_POLICLINICA', 'GEL')
                  AND se.prioridade IN ('URGENTE', 'EMERGENCIA')
              ) sub ON sub.solicitacao_id = s.id
              WHERE
                :termo IS NULL OR :termo = ''
                OR s.nome_paciente ILIKE '%' || :termo || '%'
                OR s.cpf_paciente ILIKE '%' || :termo || '%'
                OR sub.especialidade_nome ILIKE '%' || :termo || '%'
                OR sub.especialidade_categoria ILIKE '%' || :termo || '%'
            """,
            nativeQuery = true
          )
          Page<UrgenciaEmergenciaPacienteProjection> listarPacientesUrgenteseEmergencias(Pageable pageable, @Param("termo") String termo);

}
