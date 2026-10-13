package io.github.regulacao_marcarcao.regulacao_marcacao.service.federacao;

import java.time.LocalDate;

/**
 * Um paciente de outro municipio teve o agendamento remarcado (migracao de cota).
 * Carrega o mesmo que {@code AgendamentoExternoMensagemDTO} expoe — CPF mascarado e
 * nome — e nada alem disso.
 *
 * @param destinoNome municipio de origem do paciente, em maiusculas (parte da routing key)
 */
public record AgendamentoExternoRemarcadoEvent(Long solicitacaoId, String cpfMascarado, String nomePaciente,
        String destinoNome, LocalDate dataAnterior, LocalDate dataNova) {
}
