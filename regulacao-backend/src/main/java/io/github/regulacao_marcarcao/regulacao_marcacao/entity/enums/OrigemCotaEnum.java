package io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums;

/**
 * De onde veio uma {@link io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade}
 * (V90).
 *
 * <p>MANUAL: cadastrada direto na tela de cotas, como sempre foi.
 *
 * <p>AGENDA: materializada por uma {@code AgendaOcorrencia} — nao e editavel na
 * tela de cotas; a edicao e feita na agenda de origem (ver docs/especificacoes/
 * Agenda e Oferta.md, 4.8).
 */
public enum OrigemCotaEnum {
    MANUAL,
    AGENDA;
}
