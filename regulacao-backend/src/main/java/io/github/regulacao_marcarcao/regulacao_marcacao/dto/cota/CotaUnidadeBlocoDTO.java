package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.util.List;

/**
 * Bloco de /admin/cotas: todas as cotas de UM titular que passaram pelo filtro. O titular
 * é uma unidade, um grupo de unidades (cota compartilhada) ou, por defesa contra dado
 * inconsistente, "sem titular" — assim nenhuma cota deixa de aparecer.
 *
 * {@code tipo}: UNIDADE | GRUPO | SEM_TITULAR. {@code chave} é estável e única ("u12",
 * "g3", "sem"); o nome nunca serve de chave. Os totais valem para o titular inteiro no
 * filtro, não só para a página; {@code totalCotasSemFiltro} é o total do titular sem nenhum
 * filtro. {@code saldo} é a soma bruta de total − utilizado das cotas listadas (inclusive
 * inativas), não o saldo efetivo que o motor de cota usa para agendar.
 */
public record CotaUnidadeBlocoDTO(
        String chave,
        String tipo,
        Long id,
        String nome,
        long totalCotas,
        long totalCotasSemFiltro,
        long quantidadeTotal,
        long quantidadeUtilizada,
        long saldo,
        List<CotaListaItemDTO> itens) {
}
