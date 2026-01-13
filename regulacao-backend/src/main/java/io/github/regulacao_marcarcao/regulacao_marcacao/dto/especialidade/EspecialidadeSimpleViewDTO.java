package io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;

public record EspecialidadeSimpleViewDTO(
    Long id,
    String codigo,
    String nome,
    ItemCategoria categoria
) {
    
}
