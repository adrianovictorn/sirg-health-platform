package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AgendaDiaAgregadoProjection;

/**
 * Visao consolidada da agenda do dia (ADMIN): unidade -> grupo -> especialidade,
 * mais o total geral e o total por grupo somando todas as unidades.
 *
 * {@code pacientes} conta pacientes distintos NO NIVEL do no (um paciente com duas
 * especialidades conta 1 no total da unidade e 1 em cada especialidade), por isso
 * os pacientes de um no nao sao a soma dos filhos. {@code itens} soma.
 */
public record AgendaDiaConsolidadaViewDTO(
        LocalDate data,
        Indicadores totais,
        List<Grupo> gruposGeral,
        List<Unidade> unidades) {

    public record Indicadores(
            long pacientes,
            long itens,
            long agendados,
            long realizados,
            long faltasCancelados,
            long outros) {

        public static final Indicadores ZERO = new Indicadores(0, 0, 0, 0, 0, 0);

        public static Indicadores from(AgendaDiaAgregadoProjection p) {
            long itens = nz(p.getItens());
            long agendados = nz(p.getAgendados());
            long realizados = nz(p.getRealizados());
            long faltasCancelados = nz(p.getFaltasCancelados());
            return new Indicadores(nz(p.getPacientes()), itens, agendados, realizados, faltasCancelados,
                    itens - agendados - realizados - faltasCancelados);
        }

        private static long nz(Long v) {
            return v == null ? 0 : v;
        }
    }

    /** Cota que incide sobre o dia. {@code tipo}: DIA, DIA_SEMANA ou MES (cota do mes). */
    public record CotaDia(
            Long id,
            String tipo,
            String escopo,
            String escopoNome,
            boolean compartilhada,
            int total,
            int utilizada,
            int livres) {
    }

    public record Especialidade(Long id, String nome, Indicadores indicadores, List<CotaDia> cotas) {
    }

    /** {@code id} nulo = especialidades sem grupo ("Sem grupo"). */
    public record Grupo(
            Long id,
            String codigo,
            String nome,
            Indicadores indicadores,
            List<CotaDia> cotas,
            List<Especialidade> especialidades) {
    }

    /** {@code id} nulo = solicitacoes sem unidade ("Sem unidade"). */
    public record Unidade(
            Long id,
            String nome,
            Indicadores indicadores,
            List<CotaDia> cotas,
            List<Grupo> grupos) {
    }
}
