package io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;

/** Uma "agenda" da tela de Atendimentos: o Grupo de Relatorio, ja no escopo do chamador (V110). */
public record AgendaGrupoViewDTO(String codigo, String nome) {

    public static AgendaGrupoViewDTO from(GrupoRelatorio grupo) {
        return new AgendaGrupoViewDTO(grupo.getCodigo(), grupo.getNome());
    }
}
