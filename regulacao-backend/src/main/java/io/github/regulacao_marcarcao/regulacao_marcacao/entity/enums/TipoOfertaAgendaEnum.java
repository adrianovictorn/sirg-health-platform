package io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums;

/**
 * Como uma {@link io.github.regulacao_marcarcao.regulacao_marcacao.entity.Agenda}
 * escopa o procedimento ofertado (ver docs/especificacoes/Agenda e Oferta.md, 4.4).
 *
 * <p>INDIVIDUAL: uma unica especialidade, cota escopada por especialidade — a
 * metodologia atual, sem agrupamento de saldo.
 *
 * <p>GRUPO: um subconjunto de especialidades de um {@code GrupoRelatorio} (ex.:
 * "Laboratorio"), com saldo unico compartilhado entre elas (R8) — mesmo
 * mecanismo ja usado pela cota manual por grupo de especialidades (V84).
 */
public enum TipoOfertaAgendaEnum {
    INDIVIDUAL,
    GRUPO;
}
