package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.evento;

import java.time.LocalDate;
import java.util.List;

/**
 * Um agendamento mudou de data sem ser excluido (migracao de cota). So ids e datas
 * — nunca entidade: o evento e consumido depois do commit.
 *
 * @param itens ids de solicitacao_especialidade do agendamento
 */
public record AgendamentoRemarcadoEvent(Long agendamentoId, Long solicitacaoId,
        LocalDate dataAnterior, LocalDate dataNova, List<Long> itens) {
}
