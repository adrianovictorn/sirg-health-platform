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
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.StatusCountProjection;
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

}
