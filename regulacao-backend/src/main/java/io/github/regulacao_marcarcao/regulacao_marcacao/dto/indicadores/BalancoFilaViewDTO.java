package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.util.List;

/**
 * Balanco da fila nos ultimos 12 meses, em <b>pedidos</b>.
 *
 * <p>E uma fotografia do que existe hoje no banco agrupada por data, nao um
 * historico de movimentacao: pedido removido deixa de contar como novo, e
 * agendamento excluido ou remarcado deixa de contar no mes original.
 *
 * @param meses do mais antigo ao mais recente, sempre os 12, com zero onde nao houve nada
 */
public record BalancoFilaViewDTO(List<Mes> meses) {

    /**
     * @param mes        AAAA-MM
     * @param novos      pedidos cadastrados no mes
     * @param agendados  pedidos com agendamento marcado para o mes (qualquer situacao)
     * @param concluidos dos agendados, os realizados
     */
    public record Mes(String mes, long novos, long agendados, long concluidos) {
    }
}
