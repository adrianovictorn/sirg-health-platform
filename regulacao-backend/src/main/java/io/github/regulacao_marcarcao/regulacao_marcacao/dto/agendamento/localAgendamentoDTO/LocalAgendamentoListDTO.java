package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;

public record LocalAgendamentoListDTO(
    Long id,
    String nomeLocal,
    String endereco,
    String numero,
    Long cidadeId,
    String cidadeNome,
    String enumValue,
    String cnes
) {
    public static LocalAgendamentoListDTO fromEntity(LocalAgendamento localAgendamento) {
        return new LocalAgendamentoListDTO(
            localAgendamento.getId(),
            localAgendamento.getNomeLocal(),
            localAgendamento.getEndereco(),
            localAgendamento.getNumero(),
            localAgendamento.getCidade() != null ? localAgendamento.getCidade().getId() : null,
            localAgendamento.getCidade() != null ? localAgendamento.getCidade().getNomeCidade() : null,
            localAgendamento.getEnumValue(),
            localAgendamento.getCnes()
        );
    }
}
