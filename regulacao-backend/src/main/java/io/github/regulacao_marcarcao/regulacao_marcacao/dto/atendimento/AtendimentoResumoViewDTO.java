package io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoResumoProjection;

/**
 * Quantitativos de atendimento de um dia: o municipio inteiro em {@code totais} e
 * uma linha por unidade solicitante. Unidade de id nulo e o balde "Sem unidade".
 * So contagens — nenhum dado de paciente e nenhum valor em reais.
 * {@code semExecutante} = USER sem nenhuma executante configurada (lotacao ou lista
 * de operadores): tudo zerado porque ele nao ve nada, nao porque o dia esta vazio.
 */
public record AtendimentoResumoViewDTO(
        LocalDate data,
        Contagem totais,
        List<Unidade> unidades,
        boolean semExecutante) {

    public record Contagem(long pacientes, long itens, long agendados, long confirmados, long faltas,
            long cancelados) {

        public static final Contagem ZERO = new Contagem(0, 0, 0, 0, 0, 0);

        public static Contagem from(AtendimentoResumoProjection p) {
            return new Contagem(nz(p.getPacientes()), nz(p.getItens()), nz(p.getAgendados()),
                    nz(p.getConfirmados()), nz(p.getFaltas()), nz(p.getCancelados()));
        }

        private static long nz(Long v) {
            return v == null ? 0L : v;
        }
    }

    public record Unidade(Long id, String nome, Contagem contagem) {
    }
}
