package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/** Linha da lista de unidades executantes (V110). */
public interface UnidadeExecutanteResumoProjection {
    Long getUnidadeId();
    String getUnidadeNome();
    String getTipo();
    Long getGrupos();
    Long getEspecialidades();
    Long getLocais();
    Long getOperadores();
}
