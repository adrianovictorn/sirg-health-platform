package io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;

public record ProfissionalSimpleViewDTO(Long id, String nome, String conselho, String numeroRegistro) {

    public static ProfissionalSimpleViewDTO from(Profissional p) {
        return new ProfissionalSimpleViewDTO(p.getId(), p.getNome(), p.getConselho(), p.getNumeroRegistro());
    }
}
