package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.evento;

import java.time.LocalDate;
import java.util.List;

/**
 * Um agendamento de consulta/exame foi excluido. Os dados sao fotografados
 * ANTES da exclusao, porque depois dela a linha nao existe mais.
 *
 * @param itens ids de solicitacao_especialidade que estavam no agendamento —
 *              sobrevivem a exclusao e permitem reconhecer uma remarcacao
 */
public record AgendamentoCanceladoEvent(Long agendamentoId, Long solicitacaoId, LocalDate data, List<Long> itens) {
}
