package io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;

/**
 * Linha da listagem paginada de grupos. Sem a lista de especialidades: a tela não
 * a usa e carregá-la geraria uma consulta por grupo.
 */
public record GrupoRelatorioListaDTO(
        Long id,
        String codigo,
        String nome,
        boolean ativo,
        boolean direcionadoHospital) {

    public static GrupoRelatorioListaDTO from(GrupoRelatorio g) {
        return new GrupoRelatorioListaDTO(
                g.getId(),
                g.getCodigo(),
                g.getNome(),
                Boolean.TRUE.equals(g.getAtivo()),
                g.isDirecionadoHospital());
    }
}
