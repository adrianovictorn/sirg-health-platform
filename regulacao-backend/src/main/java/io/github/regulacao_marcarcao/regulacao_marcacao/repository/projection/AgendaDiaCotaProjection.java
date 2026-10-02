package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

public interface AgendaDiaCotaProjection {
    Long getId();
    Long getUnidadeId();
    Long getGrupoUnidadesId();
    Long getEspecialidadeId();
    String getEspecialidadeNome();
    Long getEspecialidadeGrupoId();
    String getEspecialidadeGrupoCodigo();
    String getEspecialidadeGrupoNome();
    Long getGrupoEspecialidadesId();
    String getGrupoEspecialidadesCodigo();
    String getGrupoEspecialidadesNome();
    String getTipoPeriodo();
    Integer getQuantidadeTotal();
    Integer getQuantidadeUtilizada();
    String getDiasSemana();
}
