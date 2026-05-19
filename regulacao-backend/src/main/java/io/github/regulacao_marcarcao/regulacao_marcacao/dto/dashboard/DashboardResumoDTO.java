package io.github.regulacao_marcarcao.regulacao_marcacao.dto.dashboard;

import java.util.Map;

public record DashboardResumoDTO(
    long totalSolicitacoes,
    long totalPendentes,
    long totalAgendadas,
    long totalConcluidas,
    long totalUrgentes,
    long totalGel,
    Map<Long, Long> pendentesPorUnidade
) {}
