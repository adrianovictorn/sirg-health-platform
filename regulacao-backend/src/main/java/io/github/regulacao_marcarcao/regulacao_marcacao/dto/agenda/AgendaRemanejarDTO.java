package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda;

/**
 * Move vagas nao usadas entre unidades de uma mesma ocorrencia (R1). Sempre
 * uma decisao manual do regulador — vaga sobrando nao vaza sozinha.
 */
public record AgendaRemanejarDTO(
        Long ocorrenciaId,
        Long unidadeOrigemId,
        Long unidadeDestinoId,
        Integer quantidade) {
}
