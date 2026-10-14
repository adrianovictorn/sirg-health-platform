package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante;

import java.util.List;

/**
 * Substitui a parametrizacao inteira da executante. Lista nula ou vazia = nenhum.
 * Local ja ligado a outra executante e MOVIDO para esta.
 */
public record UnidadeExecutanteUpdateDTO(
        List<Long> grupoIds,
        List<Long> especialidadeIds,
        List<Long> localIds,
        List<Long> usuarioIds) {
}
