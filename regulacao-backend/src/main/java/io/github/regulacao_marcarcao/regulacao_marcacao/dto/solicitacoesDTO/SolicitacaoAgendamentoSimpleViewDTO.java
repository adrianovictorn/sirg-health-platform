package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.UsfEnum;

public record SolicitacaoAgendamentoSimpleViewDTO(
    String nomePaciente,
    UsfEnum usfOrigem,
    String cpfPaciente,
    String telefone,
    List<String> especialidades
) {


    public static SolicitacaoAgendamentoSimpleViewDTO fromEntity(AgendamentoSolicitacao agendamentoSolicitacao){

        Solicitacao solicitacao = agendamentoSolicitacao.getSolicitacao();
        
        List<String> especialidades = agendamentoSolicitacao.getEspecialidades().stream().map(se -> se.getEspecialidadeSolicitada().getNome()).toList();
        return new SolicitacaoAgendamentoSimpleViewDTO(
            solicitacao.getNomePaciente(),
            solicitacao.getUsfOrigem(),
            solicitacao.getCpfPaciente(),
            solicitacao.getTelefone(),
            especialidades
        );
    }
}