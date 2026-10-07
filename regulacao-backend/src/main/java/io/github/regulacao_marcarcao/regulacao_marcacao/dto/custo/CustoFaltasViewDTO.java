package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoIndicadorProjections;

/**
 * Custo de faltas e cancelamentos: valor (da epoca do agendamento) dos itens
 * agendados para o periodo que terminaram como cancelados ou faltosos.
 *
 * <p>O sistema grava "faltou" como cancelado; os dois nao se distinguem.
 *
 * <p>So ADMIN e GESTOR. So agregados: nenhum dado de paciente.
 */
public record CustoFaltasViewDTO(
        LocalDate de,
        LocalDate ate,
        Linha total,
        List<Linha> porEspecialidade,
        List<Linha> porUnidade) {

    /**
     * @param id            id da especialidade ou da unidade; nulo no total e em "Sem unidade"
     * @param nome          nulo no total e em "Sem unidade"
     * @param itensSemValor itens sem valor gravado (sem preco na epoca, ou anteriores a V106) —
     *                      contados a parte, nunca como R$ 0,00
     */
    public record Linha(Long id, String nome, BigDecimal valor, long itensComValor, long itensSemValor) {

        public static Linha from(CustoIndicadorProjections.Faltas p) {
            return new Linha(
                    p.getId(),
                    p.getNome(),
                    p.getValor() != null ? p.getValor() : BigDecimal.ZERO.setScale(2),
                    p.getItensComValor() != null ? p.getItensComValor() : 0L,
                    p.getItensSemValor() != null ? p.getItensSemValor() : 0L);
        }
    }
}
