package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;

/** Apoio dos testes de WhatsApp. */
final class WhatsAppTestes {

    static final String TEMPLATE_CONFIRMACAO = "tpl_confirmacao";
    static final String TEMPLATE_CANCELAMENTO = "tpl_cancelamento";
    static final String TEMPLATE_LEMBRETE = "tpl_lembrete";

    private WhatsAppTestes() {
    }

    static WhatsAppEnvioProperties configurado() {
        return props("token-de-teste", "109876543210", 200, "");
    }

    static WhatsAppEnvioProperties naoConfigurado() {
        return props("", "", 200, "");
    }

    static WhatsAppEnvioProperties props(String token, String phoneNumberId, int limiteDiario, String numerosTeste) {
        return new WhatsAppEnvioProperties(token, phoneNumberId, "https://graph.exemplo", "v23.0", 3000, 10000,
                limiteDiario, numerosTeste, 10, false, "pt_BR",
                new WhatsAppEnvioProperties.Template(TEMPLATE_CONFIRMACAO, TEMPLATE_CANCELAMENTO, TEMPLATE_LEMBRETE));
    }
}
