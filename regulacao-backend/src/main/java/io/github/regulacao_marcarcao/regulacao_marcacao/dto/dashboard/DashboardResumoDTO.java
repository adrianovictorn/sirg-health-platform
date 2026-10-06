package io.github.regulacao_marcarcao.regulacao_marcacao.dto.dashboard;

import java.util.Map;

/**
 * Numeros do dashboard, ja no escopo de unidade do chamador.
 *
 * Os campos {@code total*} e {@code pendentesPorUnidade} contam PEDIDOS
 * (solicitacao_especialidade). Os campos {@code pacientes*} contam PACIENTES e
 * saem da mesma query da fila de espera — sao os que os cards que abrem a fila
 * exibem, para o numero do card bater com o total da lista.
 */
public record DashboardResumoDTO(
    long totalSolicitacoes,
    long totalPendentes,
    long totalAgendadas,
    long totalConcluidas,
    long totalUrgentes,
    long totalGel,
    Map<Long, Long> pendentesPorUnidade,
    long pacientesPendentes,
    long pacientesUrgentes,
    Map<Long, Long> pacientesPendentesPorUnidade
) {}
