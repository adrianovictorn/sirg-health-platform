package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Evolucao do custo nos ultimos 12 meses, pelo mes da data agendada e pelo
 * valor gravado no agendamento. So ADMIN e GESTOR.
 *
 * <p>"Agendado" e o que AINDA esta agendado: em mes passado o numero encolhe
 * conforme os itens viram realizados, faltas ou cancelamentos.
 *
 * @param meses do mais antigo ao mais recente, sempre os 12, com zero onde nao houve nada
 */
public record CustoEvolucaoViewDTO(List<Mes> meses) {

    /**
     * @param mes                AAAA-MM
     * @param faltas             faltas e cancelamentos (o sistema nao os distingue)
     * @param pacientesAtendidos pacientes com ao menos um item realizado com valor no mes
     * @param custoMedioPorPaciente concluido / pacientesAtendidos; nulo quando ninguem foi atendido
     */
    public record Mes(
            String mes,
            BigDecimal agendado,
            BigDecimal concluido,
            BigDecimal faltas,
            long pacientesAtendidos,
            BigDecimal custoMedioPorPaciente) {
    }
}
