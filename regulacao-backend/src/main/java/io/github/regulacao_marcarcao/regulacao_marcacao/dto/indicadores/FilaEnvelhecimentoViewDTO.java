package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.util.List;

/**
 * Envelhecimento da fila de espera de hoje, em faixas de dias.
 *
 * <p>Duas unidades de contagem, de proposito:
 * <ul>
 *   <li>{@code total} e {@code porUnidade} contam <b>pacientes</b> — a faixa e a
 *       do pedido mais antigo de cada um, como a fila de espera e o dashboard;</li>
 *   <li>{@code porEspecialidade} e {@code porPrioridade} contam <b>pedidos</b> —
 *       um paciente pode ter pedidos em mais de uma especialidade ou prioridade.</li>
 * </ul>
 *
 * So agregados: nenhum dado de paciente.
 */
public record FilaEnvelhecimentoViewDTO(
        FaixasEsperaViewDTO total,
        List<FaixasEsperaViewDTO> porUnidade,
        List<FaixasEsperaViewDTO> porEspecialidade,
        List<FaixasEsperaViewDTO> porPrioridade) {
}
