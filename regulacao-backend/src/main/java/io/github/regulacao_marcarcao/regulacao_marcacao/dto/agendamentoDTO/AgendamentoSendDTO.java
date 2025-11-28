package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoAgendamentoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;

public record AgendamentoSendDTO(
    AgendamentoSolicitacaoSimpleViewDTO agendamentoSolicitacaoSimpleViewDTO,
    SolicitacaoAgendamentoSimpleViewDTO solicitacaoAgendamentoSimpleViewDTO
) {
    

    public static AgendamentoSendDTO fromEntity(AgendamentoSolicitacao agendamentoSolicitacao){


        AgendamentoSolicitacaoSimpleViewDTO agendamentoSendDTO = AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(agendamentoSolicitacao);



        SolicitacaoAgendamentoSimpleViewDTO solicitacaoAgendamentoSimpleViewDTO = SolicitacaoAgendamentoSimpleViewDTO.fromEntity(agendamentoSolicitacao);
        return new AgendamentoSendDTO(agendamentoSendDTO, solicitacaoAgendamentoSimpleViewDTO);

        
    }
}
