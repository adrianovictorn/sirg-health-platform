package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;

/**
 * Periodo (data inicial e final, as duas inclusive) dos indicadores gerenciais.
 *
 * <p>As datas chegam como texto e sao validadas aqui, para que formato invalido
 * vire 400 com mensagem ({@code IllegalArgumentException}) em vez do 400 sem
 * corpo que o Spring devolve quando nao consegue converter o parametro.
 *
 * <p>"Hoje" e "este mes" sao sempre os do municipio ({@link #FUSO}), nunca os
 * da JVM, que roda em UTC.
 */
public record PeriodoIndicador(LocalDate de, LocalDate ate) {

    public static final ZoneId FUSO = WhatsAppEnvioProperties.FUSO;

    /** Limite do intervalo: as consultas agregam tabelas sem indice de data. */
    public static final int MAXIMO_DE_DIAS = 366;

    private static final int DIAS_PADRAO = 30;
    private static final int MESES_DA_SERIE = 12;

    /** Sem datas, os ultimos 30 dias. Com uma so, a outra assume o padrao em torno dela. */
    public static PeriodoIndicador de(String de, String ate) {
        LocalDate fim = ler(ate, "final");
        LocalDate inicio = ler(de, "inicial");
        if (fim == null) {
            fim = inicio != null ? inicio.plusDays(DIAS_PADRAO - 1) : LocalDate.now(FUSO);
        }
        if (inicio == null) {
            inicio = fim.minusDays(DIAS_PADRAO - 1);
        }
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial do período não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > MAXIMO_DE_DIAS) {
            throw new IllegalArgumentException("O período não pode passar de " + MAXIMO_DE_DIAS + " dias.");
        }
        return new PeriodoIndicador(inicio, fim);
    }

    /** Os 12 meses fechados no mes corrente: do dia 1 de onze meses atras ao ultimo dia deste mes. */
    public static PeriodoIndicador ultimosDozeMeses() {
        YearMonth atual = YearMonth.now(FUSO);
        return new PeriodoIndicador(atual.minusMonths(MESES_DA_SERIE - 1).atDay(1), atual.atEndOfMonth());
    }

    /** Mes inicial no formato das colunas {@code periodo} (AAAA-MM). */
    public String mesInicial() {
        return YearMonth.from(de).toString();
    }

    public String mesFinal() {
        return YearMonth.from(ate).toString();
    }

    /** Inicio do primeiro dia, no fuso do municipio — para colunas {@code timestamptz}. */
    public Instant inicioInstant() {
        return de.atStartOfDay(FUSO).toInstant();
    }

    /** Inicio do dia seguinte ao ultimo: limite EXCLUSIVO. */
    public Instant fimExclusivoInstant() {
        return ate.plusDays(1).atStartOfDay(FUSO).toInstant();
    }

    private static LocalDate ler(String texto, String qual) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Data " + qual + " inválida: \"" + texto + "\". Use o formato AAAA-MM-DD.");
        }
    }
}
