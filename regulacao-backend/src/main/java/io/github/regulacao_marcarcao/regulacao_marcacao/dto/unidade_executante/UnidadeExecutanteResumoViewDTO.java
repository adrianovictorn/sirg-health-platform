package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UnidadeExecutanteResumoProjection;

/**
 * Linha da lista de unidades executantes. {@code configurada} = ao menos um local e
 * ao menos um grupo ou especialidade — a partir dai ela restringe os operadores
 * vinculados.
 */
public record UnidadeExecutanteResumoViewDTO(
        Long unidadeId,
        String unidadeNome,
        String tipo,
        long grupos,
        long especialidades,
        long locais,
        long operadores,
        boolean configurada) {

    public static UnidadeExecutanteResumoViewDTO from(UnidadeExecutanteResumoProjection p) {
        long grupos = valor(p.getGrupos());
        long especialidades = valor(p.getEspecialidades());
        long locais = valor(p.getLocais());
        return new UnidadeExecutanteResumoViewDTO(p.getUnidadeId(), p.getUnidadeNome(), p.getTipo(),
                grupos, especialidades, locais, valor(p.getOperadores()),
                locais > 0 && (grupos > 0 || especialidades > 0));
    }

    private static long valor(Long n) {
        return n == null ? 0L : n;
    }
}
