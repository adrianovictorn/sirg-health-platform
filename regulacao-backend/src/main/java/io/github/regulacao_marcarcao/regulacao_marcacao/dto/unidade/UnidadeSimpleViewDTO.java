package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;

public record UnidadeSimpleViewDTO(Long id, String nome, String codigo) {

    public static UnidadeSimpleViewDTO from(Unidade u) {
        return new UnidadeSimpleViewDTO(u.getId(), u.getNome(), u.getCodigo());
    }
}
