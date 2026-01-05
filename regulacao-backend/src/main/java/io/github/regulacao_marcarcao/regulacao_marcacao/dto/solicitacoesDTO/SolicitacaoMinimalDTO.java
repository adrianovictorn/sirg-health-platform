package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.UsfEnum;

public record  SolicitacaoMinimalDTO(
    String nomePaciente,
    String cpfPaciente,
    LocalDate dataNascimento,
    UsfEnum usfOrigem,
    List<String> especialidades
) {

    public static SolicitacaoMinimalDTO fromEntity(AgendamentoSolicitacao solicitacao){

        List<String> listarDeEspecialidades = solicitacao.getEspecialidades().stream().map(e->e.getEspecialidadeSolicitada().getNome()).toList();

        return new SolicitacaoMinimalDTO(
            solicitacao.getSolicitacao().getNomePaciente(),
            solicitacao.getSolicitacao().getCpfPaciente(), 
            solicitacao.getSolicitacao().getDataNascimento(),
            solicitacao.getSolicitacao().getUsfOrigem(),
            listarDeEspecialidades
            );
    }
}