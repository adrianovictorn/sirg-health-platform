package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoIndicadorProjections;

/**
 * Execucao do teto financeiro: quanto de cada teto ativo ja foi utilizado.
 *
 * <p>So ADMIN e GESTOR. O percentual nao vem pronto de proposito: a tela usa a
 * mesma conta do painel de custos (utilizado / liberado), para os dois nunca
 * mostrarem numeros diferentes.
 *
 * <p>{@code valorUtilizado} pode passar de {@code valorTotal}: agendamento de
 * ADMIN ou GESTOR debita sem ser barrado.
 *
 * @param tetos tetos ativos dos meses que o periodo cobre, do mes mais recente ao mais antigo
 * @param serie soma dos tetos ativos por mes, nos ultimos 12 meses, com zero onde nao ha teto
 */
public record TetoExecucaoViewDTO(
        LocalDate de,
        LocalDate ate,
        List<Teto> tetos,
        List<Mes> serie) {

    /** @param periodo AAAA-MM */
    public record Teto(
            Long unidadeId,
            String unidadeNome,
            String grupoNome,
            String periodo,
            BigDecimal valorTotal,
            BigDecimal valorUtilizado) {

        public static Teto from(CustoIndicadorProjections.Teto p) {
            return new Teto(p.getUnidadeId(), p.getUnidadeNome(), p.getGrupoNome(), p.getPeriodo(),
                    p.getValorTotal(), p.getValorUtilizado());
        }
    }

    /** @param mes AAAA-MM */
    public record Mes(String mes, BigDecimal liberado, BigDecimal utilizado, long tetos) {
    }
}
