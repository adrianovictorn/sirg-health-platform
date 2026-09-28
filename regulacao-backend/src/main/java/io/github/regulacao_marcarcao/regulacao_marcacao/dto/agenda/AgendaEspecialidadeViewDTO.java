package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaEspecialidade;

public record AgendaEspecialidadeViewDTO(Long id, String nome) {

    public static AgendaEspecialidadeViewDTO from(AgendaEspecialidade e) {
        return new AgendaEspecialidadeViewDTO(e.getEspecialidade().getId(), e.getEspecialidade().getNome());
    }
}
