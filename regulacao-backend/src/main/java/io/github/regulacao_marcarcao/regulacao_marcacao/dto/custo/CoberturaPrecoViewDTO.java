package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.time.LocalDate;

/**
 * Cobertura de preco: quanto do catalogo e do movimento tem preco — ou seja,
 * quanto os totais de custo deixam de fora.
 *
 * <p>Os numeros de fila e de agendados sao os do proprio painel de custos
 * ({@link CustoPainelLinhaViewDTO}), nao uma segunda conta.
 *
 * @param especialidadesAtivas   especialidades ativas do catalogo (nao depende de periodo nem de unidade)
 * @param especialidadesSemPreco delas, as sem valor unitario
 * @param filaComPreco           pedidos na fila hoje cuja especialidade tem preco
 * @param filaSemPreco           pedidos na fila hoje sem preco
 * @param agendadosComValor      itens agendados no periodo com valor gravado
 * @param agendadosSemValor      itens agendados no periodo sem valor — sem preco na epoca
 *                               ou agendados antes de o valor passar a ser gravado
 */
public record CoberturaPrecoViewDTO(
        LocalDate de,
        LocalDate ate,
        long especialidadesAtivas,
        long especialidadesSemPreco,
        long filaComPreco,
        long filaSemPreco,
        long agendadosComValor,
        long agendadosSemValor) {
}
