package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.evento;

import java.time.LocalDate;
import java.util.List;

/**
 * Um agendamento de consulta/exame foi criado. So ids e data — nunca entidade:
 * o evento e consumido depois do commit, com o contexto de persistencia ja limpo.
 *
 * @param itens ids de solicitacao_especialidade agendados
 */
public record AgendamentoCriadoEvent(Long agendamentoId, Long solicitacaoId, LocalDate data, List<Long> itens) {
}
