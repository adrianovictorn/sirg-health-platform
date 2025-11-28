package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.UsfEnum;

public record SolicitacaoAgendamentoViewDTO(
    Long id,
    String nomePaciente,
    String cpfPaciente,
    UsfEnum usfOrigem,
    String cns,
    List<EspecialidadeAgendamentoDTO> especialidades
) {

    public static SolicitacaoAgendamentoViewDTO fromEntity(Solicitacao solicitacao){

        List<EspecialidadeAgendamentoDTO> especialidades = solicitacao.getEspecialidades().stream()
            .filter(s -> s.getStatus() == StatusDaMarcacao.AGUARDANDO || s.getStatus() == StatusDaMarcacao.RETORNO || s.getStatus() == StatusDaMarcacao.RETORNO_POLICLINICA)
            .map(EspecialidadeAgendamentoDTO::fromEntity)
            .filter(e -> e.codigo() != null && !e.codigo().isBlank())
            .toList();

        return  new SolicitacaoAgendamentoViewDTO(
            solicitacao.getId(),
            solicitacao.getNomePaciente(), 
            solicitacao.getCpfPaciente(),
            solicitacao.getUsfOrigem(),
            solicitacao.getCns(),
            especialidades
        );
    }
    
}
