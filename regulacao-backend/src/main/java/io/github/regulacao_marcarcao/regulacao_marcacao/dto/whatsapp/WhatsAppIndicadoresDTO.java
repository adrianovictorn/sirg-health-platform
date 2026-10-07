package io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp;

import java.time.LocalDate;
import java.util.Map;

/**
 * Volume de mensagens num periodo (pela data em que entraram na fila).
 *
 * @param enviadas               aceitas pela Meta (inclui as ja entregues e lidas)
 * @param entregues              chegaram ao aparelho (inclui as lidas)
 * @param pendentes              ainda na fila
 * @param naoEnviadasPorMotivo   chave = motivo
 * @param cobraveisPorCategoria  chave = categoria informada pela Meta
 * @param recebidas              mensagens escritas por pacientes; contagem aproximada
 */
public record WhatsAppIndicadoresDTO(
        LocalDate de,
        LocalDate ate,
        long total,
        long enviadas,
        long entregues,
        long lidas,
        long falhas,
        long pendentes,
        long naoEnviadas,
        Map<String, Long> naoEnviadasPorMotivo,
        long cobraveis,
        Map<String, Long> cobraveisPorCategoria,
        long recebidas) {
}
