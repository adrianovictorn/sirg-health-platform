package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;

/**
 * Edicao completa de uma cota manual (V90 — antes so {@code quantidadeTotal} e
 * {@code ativo} eram editaveis, mesmo sem relacao com saldo consumido).
 *
 * <p>E uma SUBSTITUICAO TOTAL, com a mesma validacao de {@link CotaUnidadeCreateDTO}:
 * a tela sempre reenvia titular, escopo e periodo, mesmo os que nao mudaram.
 * Isso evita a ambiguidade de um PUT parcial (campo ausente = "nao mudou" ou
 * "limpar?") ao custo de exigir o payload inteiro a cada edicao.
 *
 * <p>Cota com {@code origem = AGENDA} recusa este endpoint — edita-se a agenda
 * de origem (ver {@link io.github.regulacao_marcarcao.regulacao_marcacao.service.AgendaService}).
 */
public record CotaUnidadeUpdateDTO(
        Long unidadeId,
        Long grupoUnidadesId,
        Long especialidadeId,
        Long grupoEspecialidadesId,
        TipoPeriodoCota tipoPeriodo,
        String periodo,
        LocalDate dataEspecifica,
        Integer quantidadeTotal,
        boolean ativo,
        Long profissionalId,
        Long localAgendamentoId,
        boolean horarioDinamico,
        Integer tempoMedioAtendimentoMinutos,
        LocalTime horaInicial,
        LocalTime horaFinal,
        List<String> diasSemana) {
}
