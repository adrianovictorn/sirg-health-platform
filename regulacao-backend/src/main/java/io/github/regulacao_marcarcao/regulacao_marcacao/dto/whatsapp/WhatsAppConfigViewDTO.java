package io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp;

import java.time.Instant;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppConfig;

/**
 * Estado da integracao para o painel. Nunca leva credencial nem numero de
 * teste — so se existem.
 *
 * @param configurado   a instancia tem as credenciais de envio
 * @param webhookLigado a instancia recebe eventos da Meta
 * @param modoTeste     ha lista de numeros de teste: so eles recebem
 */
public record WhatsAppConfigViewDTO(
        boolean configurado,
        boolean webhookLigado,
        boolean envioLigado,
        String alteradoPorNome,
        Instant alteradoEm,
        int limiteDiario,
        long enviadasHoje,
        boolean modoTeste) {

    public static WhatsAppConfigViewDTO from(WhatsAppConfig config, boolean configurado, boolean webhookLigado,
                                             int limiteDiario, long enviadasHoje, boolean modoTeste) {
        return new WhatsAppConfigViewDTO(
                configurado,
                webhookLigado,
                config != null && config.isEnvioLigado(),
                config != null && config.getAlteradoPor() != null ? config.getAlteradoPor().getNome() : null,
                config != null ? config.getAlteradoEm() : null,
                limiteDiario,
                enviadasHoje,
                modoTeste);
    }
}
