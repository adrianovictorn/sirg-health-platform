package io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;

public record EspecialidadeCreateDTO(
    String codigo, 
    String nome, 
    ItemCategoria categoria, 
    Long grupoRelatorioId,
    Integer vagas,
    /** V102: nao citar em mensagens de WhatsApp. Nulo = false. */
    Boolean sensivel
) {
    
}
