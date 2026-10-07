package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoPainelLinhaProjection;

/**
 * Uma linha do painel de custos: o total geral, uma unidade ou uma especialidade.
 *
 * <p>Tres visoes, cada uma com valor, quantos itens entraram e quantos ficaram
 * de fora por nao terem preco — "sem preco" nunca aparece como R$ 0,00:
 * <ul>
 *   <li><b>estimado</b>: pedidos na fila x preco ATUAL da especialidade;</li>
 *   <li><b>agendado</b>: itens agendados x valor gravado no agendamento;</li>
 *   <li><b>concluido</b>: itens realizados x valor gravado no agendamento.</li>
 * </ul>
 *
 * @param id        id da unidade ou da especialidade; nulo no total e em "Sem unidade"
 * @param nome      nome da unidade ou da especialidade; nulo no total
 * @param codigoSus so nas linhas de especialidade
 */
public record CustoPainelLinhaViewDTO(
        Long id,
        String nome,
        String codigoSus,
        BigDecimal estimado,
        long estimadoItens,
        long estimadoSemPreco,
        BigDecimal agendado,
        long agendadoItens,
        long agendadoSemValor,
        BigDecimal concluido,
        long concluidoItens,
        long concluidoSemValor) {

    public static CustoPainelLinhaViewDTO from(CustoPainelLinhaProjection p) {
        return new CustoPainelLinhaViewDTO(
                p.getId(),
                p.getNome(),
                p.getCodigoSus(),
                zeroSeNulo(p.getEstimado()),
                zeroSeNulo(p.getEstimadoItens()),
                zeroSeNulo(p.getEstimadoSemPreco()),
                zeroSeNulo(p.getAgendado()),
                zeroSeNulo(p.getAgendadoItens()),
                zeroSeNulo(p.getAgendadoSemValor()),
                zeroSeNulo(p.getConcluido()),
                zeroSeNulo(p.getConcluidoItens()),
                zeroSeNulo(p.getConcluidoSemValor()));
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO.setScale(2);
    }

    private static long zeroSeNulo(Long valor) {
        return valor != null ? valor : 0L;
    }
}
