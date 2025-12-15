package io.github.regulacao_marcarcao.regulacao_marcacao.dto.relatorio.producao;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSolicitacaoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoMinimalDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;

public record RelatorioProducaoViewDTO(
    SolicitacaoMinimalDTO solicitacaoSimpleViewDTO,
    AgendamentoSolicitacaoSimpleViewDTO agendamentoSolicitacaoSimpleViewDTO
) {
    

    public static RelatorioProducaoViewDTO fromEntity(AgendamentoSolicitacao agendamentoSolicitacao){
        SolicitacaoMinimalDTO solicitacao = SolicitacaoMinimalDTO.fromEntity(agendamentoSolicitacao);
        AgendamentoSolicitacaoSimpleViewDTO agendamento = AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(agendamentoSolicitacao);

        return new RelatorioProducaoViewDTO(solicitacao, agendamento);
    }
}
