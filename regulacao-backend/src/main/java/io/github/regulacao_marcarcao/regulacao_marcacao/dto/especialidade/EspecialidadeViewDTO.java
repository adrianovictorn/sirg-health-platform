package io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;

public record EspecialidadeViewDTO(
        Long id,
        String codigo,
        String nome,
        ItemCategoria categoria,
        GrupoRelatorioSimpleViewDTO grupoRelatorio,
        Boolean ativo
) {
    
}

