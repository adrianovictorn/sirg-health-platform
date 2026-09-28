package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cbo;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cbo;

public record CboViewDTO(Long id, String codigo, String descricao, boolean ativo) {

    public static CboViewDTO from(Cbo c) {
        return new CboViewDTO(c.getId(), c.getCodigo(), c.getDescricao(), c.isAtivo());
    }
}
