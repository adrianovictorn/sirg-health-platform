package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO;

import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;

public record SolicitacaoEspecialidadeViewDTO (
    Long id,
    Long solicitacaoId,
    Long agendamentoId,
    String especialidadeSolicitada,
    Long profissionalId,
    String profissionalNome,
    String status,
    String prioridade,
    LocalDateTime dataDeCadastro
) {

    public static SolicitacaoEspecialidadeViewDTO fromSolicitacaoEspecialidade(SolicitacaoEspecialidade se) {
        return new SolicitacaoEspecialidadeViewDTO(
            se.getId(),
            se.getSolicitacao() != null ? se.getSolicitacao().getId() : null,
            se.getAgendamentoSolicitacao() != null ? se.getAgendamentoSolicitacao().getId() : null,
            se.getEspecialidadeSolicitada() != null ? se.getEspecialidadeSolicitada().getCodigo() : se.getEspecialidadeCodigoLegacy(),
            se.getProfissionalSolicitante() != null ? se.getProfissionalSolicitante().getId() : null,
            se.getProfissionalSolicitante() != null ? se.getProfissionalSolicitante().getNome() : null,
            se.getStatus() != null ? se.getStatus().name() : null,
            se.getPrioridade() != null ? se.getPrioridade().name() : null,
            se.getDataDeCadastro()
        );
    }
}
