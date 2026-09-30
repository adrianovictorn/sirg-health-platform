package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;

/**
 * Criacao de cota, com duas dimensoes independentes.
 *
 * <b>Titular</b> (exatamente um):
 * {@code unidadeId} = cota de uma unidade;
 * {@code grupoUnidadesId} = pool compartilhado entre as unidades do grupo.
 *
 * <b>Escopo</b> (no maximo um):
 * {@code especialidadeId} = limita uma especialidade;
 * {@code grupoEspecialidadesId} = limita todas as especialidades do grupo, com
 * saldo unico compartilhado entre elas (evita cadastrar uma cota por
 * especialidade — o grupo "Laboratorio" tem 172);
 * ambos nulos = cota geral, vale para qualquer especialidade.
 *
 * Os dois grupos referenciam a mesma tabela {@code grupo_relatorio}, em papeis
 * distintos: um agrupa unidades, o outro agrupa especialidades.
 *
 * <h3>Espelho de atendimento (V92, opcional)</h3>
 * {@code profissionalId} e {@code localAgendamentoId} sao opcionais — cota
 * geral ou de laboratorio continua sem eles. Quando algum deles, ou o
 * horario, e informado, {@code tipoPeriodo} precisa ser {@code DATA} (a cota
 * reaproveita {@code dataEspecifica}, nao existe coluna de data separada).
 * Horario: {@code horarioDinamico = true} exige {@code horaInicial}/{@code horaFinal}
 * (V98) e calcula um horario por vaga, dividindo o periodo em
 * {@code quantidadeTotal} partes iguais; {@code false} aceita
 * {@code horaInicial}/{@code horaFinal} como periodo livre, sem calculo.
 * {@code tempoMedioAtendimentoMinutos} e metadado opcional, sem uso no calculo.
 *
 * <h3>Dias da semana (V95, opcional)</h3>
 * Para cota MENSAL, {@code diasSemana} (siglas "SEG".."DOM") substitui a data
 * unica de DATA como referencia de quando o profissional atende — obrigatorio
 * quando profissional/local/horario sao informados numa cota MENSAL, mas pode
 * ser usado sozinho (sem profissional) so para indicar os dias de atendimento.
 * Nao se aplica a cota DATA (um dia so ja e, implicitamente, um dia da semana).
 */
public record CotaUnidadeCreateDTO(
        Long unidadeId,
        Long grupoUnidadesId,
        Long especialidadeId,
        Long grupoEspecialidadesId,
        TipoPeriodoCota tipoPeriodo,
        String periodo,
        LocalDate dataEspecifica,
        Integer quantidadeTotal,
        Long profissionalId,
        Long localAgendamentoId,
        boolean horarioDinamico,
        Integer tempoMedioAtendimentoMinutos,
        LocalTime horaInicial,
        LocalTime horaFinal,
        List<String> diasSemana) {
}
