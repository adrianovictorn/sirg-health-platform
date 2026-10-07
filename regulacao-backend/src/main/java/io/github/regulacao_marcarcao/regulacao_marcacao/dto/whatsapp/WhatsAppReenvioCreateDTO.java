package io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;
import jakarta.validation.constraints.NotNull;

/** Reenvio manual da confirmacao ou do lembrete de um agendamento. */
public record WhatsAppReenvioCreateDTO(
        @NotNull(message = "Informe o agendamento.")
        Long agendamentoId,
        @NotNull(message = "Informe o tipo da mensagem.")
        WhatsAppTipoMensagem tipo) {
}
