package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;

public record SolicitacaoSimpleViewDTO(
    Long id,
    Long unidadeId,
    String unidadeNome,
    String dataMalote,
    String nomePaciente,
    String cpfPaciente,
    String observacoes,
    String status
) {
    public static SolicitacaoSimpleViewDTO fromSolicitacao(Solicitacao solicitacao) {
        List<SolicitacaoEspecialidade> especialidades = solicitacao.getEspecialidades();

        List<StatusDaMarcacao> status = especialidades.stream()
            .map(SolicitacaoEspecialidade::getStatus)
            .toList();

        StatusDaMarcacao statusFinal = StatusDaMarcacao.AGENDADO;
        if (status.contains(StatusDaMarcacao.AGUARDANDO)) {
            statusFinal = StatusDaMarcacao.AGUARDANDO;
        }

        return new SolicitacaoSimpleViewDTO(
            solicitacao.getId(),
            solicitacao.getUnidade() != null ? solicitacao.getUnidade().getId() : null,
            solicitacao.getUnidade() != null ? solicitacao.getUnidade().getNome() : null,
            solicitacao.getDataMalote() != null ? solicitacao.getDataMalote().toString() : null,
            solicitacao.getNomePaciente(),
            solicitacao.getCpfPaciente(),
            solicitacao.getObservacoes(),
            statusFinal.name()
        );
    }
}
