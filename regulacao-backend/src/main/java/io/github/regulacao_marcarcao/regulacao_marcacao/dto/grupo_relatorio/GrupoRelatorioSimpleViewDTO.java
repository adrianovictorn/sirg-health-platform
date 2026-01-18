package io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;

public record GrupoRelatorioSimpleViewDTO(
    Long id,
    String codigo,
    String nome
) {
    public static GrupoRelatorioSimpleViewDTO fromEntity(GrupoRelatorio entity){
        return new GrupoRelatorioSimpleViewDTO(entity.getId(), entity.getCodigo(), entity.getNome());
    }
}
