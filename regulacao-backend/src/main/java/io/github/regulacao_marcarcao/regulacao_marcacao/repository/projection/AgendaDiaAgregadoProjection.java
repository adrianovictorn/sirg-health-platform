package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/**
 * Linha do agregado da agenda do dia consolidada. A mesma consulta devolve varios
 * niveis (GROUPING SETS); os campos {@code unidadeAgregada/grupoAgregado/especialidadeAgregada} valem 1 quando a coluna
 * correspondente foi agregada (nivel acima) e 0 quando faz parte da chave.
 */
public interface AgendaDiaAgregadoProjection {
    Long getUnidadeId();
    String getUnidadeNome();
    Long getGrupoId();
    String getGrupoCodigo();
    String getGrupoNome();
    Long getEspecialidadeId();
    String getEspecialidadeNome();
    Integer getUnidadeAgregada();
    Integer getGrupoAgregado();
    Integer getEspecialidadeAgregada();
    Long getPacientes();
    Long getItens();
    Long getAgendados();
    Long getRealizados();
    Long getFaltasCancelados();
}
