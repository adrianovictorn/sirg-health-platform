package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;

public record  SolicitacaoMinimalDTO(
    String nomePaciente,
    String cpfPaciente,
    LocalDate dataNascimento,
    String usfOrigem,
    List<String> especialidades
) {

    public static SolicitacaoMinimalDTO fromEntity(AgendamentoSolicitacao agendamentoSolicitacao){

        Solicitacao solicitacao = agendamentoSolicitacao.getSolicitacao();
        List<String> listarDeEspecialidades = agendamentoSolicitacao.getEspecialidades().stream().map(e->e.getEspecialidadeSolicitada().getNome()).toList();

        String usfOuUnidade = solicitacao.getUnidade() != null
                ? solicitacao.getUnidade().getNome()
                : (solicitacao.getUsfOrigem() != null ? solicitacao.getUsfOrigem().toString() : null);

        return new SolicitacaoMinimalDTO(
            solicitacao.getNomePaciente(),
            solicitacao.getCpfPaciente(),
            solicitacao.getDataNascimento(),
            usfOuUnidade,
            listarDeEspecialidades
            );
    }
}