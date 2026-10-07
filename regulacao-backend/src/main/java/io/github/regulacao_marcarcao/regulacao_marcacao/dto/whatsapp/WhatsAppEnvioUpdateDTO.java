package io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp;

import jakarta.validation.constraints.NotNull;

/** Liga ou desliga o envio de mensagens. */
public record WhatsAppEnvioUpdateDTO(
        @NotNull(message = "Informe se o envio deve ficar ligado ou desligado.")
        Boolean ligado) {
}
