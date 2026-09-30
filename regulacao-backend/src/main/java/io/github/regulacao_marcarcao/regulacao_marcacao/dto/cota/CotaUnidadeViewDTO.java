package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;

public record CotaUnidadeViewDTO(
        Long id,
        // titular
        Long unidadeId,
        String unidadeNome,
        Long grupoUnidadesId,
        String grupoUnidadesNome,
        // escopo
        Long especialidadeId,
        String especialidadeNome,
        Long grupoEspecialidadesId,
        String grupoEspecialidadesNome,
        // periodo e saldo
        TipoPeriodoCota tipoPeriodo,
        String periodo,
        LocalDate dataEspecifica,
        Integer quantidadeTotal,
        Integer quantidadeUtilizada,
        Integer saldoDisponivel,
        boolean ativo,
        LocalDateTime criadoEm,
        // origem (V90) — AGENDA nao e editavel aqui, edita-se a agenda
        OrigemCotaEnum origem,
        Long agendaOcorrenciaId,
        Long agendaId,
        // espelho de atendimento (V92) — todos opcionais
        Long profissionalId,
        String profissionalNome,
        Long localAgendamentoId,
        String localAgendamentoNome,
        boolean horarioDinamico,
        Integer tempoMedioAtendimentoMinutos,
        LocalTime horaInicial,
        LocalTime horaFinal,
        // dias da semana (V95) — so para cota MENSAL
        List<String> diasSemana,
        List<LocalDate> proximasOcorrencias) {

    private static final Map<DayOfWeek, String> SIGLA_DIA = Map.of(
            DayOfWeek.MONDAY, "SEG",
            DayOfWeek.TUESDAY, "TER",
            DayOfWeek.WEDNESDAY, "QUA",
            DayOfWeek.THURSDAY, "QUI",
            DayOfWeek.FRIDAY, "SEX",
            DayOfWeek.SATURDAY, "SAB",
            DayOfWeek.SUNDAY, "DOM");

    /**
     * Datas futuras, dentro do proprio mes da cota, em que os dias da semana
     * marcados caem — calculado em leitura, nunca persistido (o saldo continua
     * unico para o mes inteiro). Horizonte: hoje ate o fim do periodo mensal.
     */
    private static List<LocalDate> calcularProximasOcorrencias(String periodo, String diasSemanaCsv) {
        if (diasSemanaCsv == null || diasSemanaCsv.isBlank() || periodo == null) {
            return List.of();
        }
        var siglasValidas = Arrays.stream(diasSemanaCsv.split(","))
                .map(String::trim)
                .toList();
        var dias = SIGLA_DIA.entrySet().stream()
                .filter(e -> siglasValidas.contains(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        YearMonth mes;
        try {
            mes = YearMonth.parse(periodo);
        } catch (Exception e) {
            return List.of();
        }

        LocalDate hoje = LocalDate.now();
        LocalDate primeiroDia = mes.atDay(1);
        LocalDate inicio = hoje.isAfter(primeiroDia) ? hoje : primeiroDia;
        LocalDate fim = mes.atEndOfMonth();

        List<LocalDate> resultado = new ArrayList<>();
        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            if (dias.contains(data.getDayOfWeek())) {
                resultado.add(data);
            }
        }
        return resultado;
    }

    public static CotaUnidadeViewDTO from(CotaUnidade c) {
        return new CotaUnidadeViewDTO(
                c.getId(),
                c.getUnidade() != null ? c.getUnidade().getId() : null,
                c.getUnidade() != null ? c.getUnidade().getNome() : null,
                c.getGrupoUnidades() != null ? c.getGrupoUnidades().getId() : null,
                c.getGrupoUnidades() != null ? c.getGrupoUnidades().getNome() : null,
                c.getEspecialidade() != null ? c.getEspecialidade().getId() : null,
                c.getEspecialidade() != null ? c.getEspecialidade().getNome() : null,
                c.getGrupoEspecialidades() != null ? c.getGrupoEspecialidades().getId() : null,
                c.getGrupoEspecialidades() != null ? c.getGrupoEspecialidades().getNome() : null,
                c.getTipoPeriodo(),
                c.getPeriodo(),
                c.getDataEspecifica(),
                c.getQuantidadeTotal(),
                c.getQuantidadeUtilizada(),
                c.getQuantidadeTotal() - c.getQuantidadeUtilizada(),
                c.isAtivo(),
                c.getCriadoEm(),
                c.getOrigem(),
                c.getAgendaOcorrencia() != null ? c.getAgendaOcorrencia().getId() : null,
                c.getAgendaOcorrencia() != null ? c.getAgendaOcorrencia().getAgenda().getId() : null,
                c.getProfissionalExecutante() != null ? c.getProfissionalExecutante().getId() : null,
                c.getProfissionalExecutante() != null ? c.getProfissionalExecutante().getNome() : null,
                c.getLocalAgendamento() != null ? c.getLocalAgendamento().getId() : null,
                c.getLocalAgendamento() != null ? c.getLocalAgendamento().getNomeLocal() : null,
                c.isHorarioDinamico(),
                c.getTempoMedioAtendimentoMinutos(),
                c.getHoraInicial(),
                c.getHoraFinal(),
                c.getDiasSemana() != null && !c.getDiasSemana().isBlank()
                        ? Arrays.asList(c.getDiasSemana().split(",")) : List.of(),
                calcularProximasOcorrencias(c.getPeriodo(), c.getDiasSemana()));
    }
}
