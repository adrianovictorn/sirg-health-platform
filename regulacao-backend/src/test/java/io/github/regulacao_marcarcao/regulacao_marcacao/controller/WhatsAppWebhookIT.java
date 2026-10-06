package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Webhook do WhatsApp pela cadeia real (filtro JWT, SecurityConfiguration e
 * conversores HTTP), com a integracao ligada e sem login — como a Meta chama.
 *
 * <p>O caso desligado e o que continua protegido estao em
 * {@link SegurancaEndpointsIT}. Nao toca no banco.
 */
@SpringBootTest(properties = {
        "app.whatsapp.verify-token=token-it",
        "app.whatsapp.app-secret=segredo-it" })
@AutoConfigureMockMvc
class WhatsAppWebhookIT {

    private static final String ROTA = "/api/webhooks/whatsapp";

    @Autowired private MockMvc mockMvc;

    private static String assinar(byte[] corpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("segredo-it".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo));
    }

    @Test
    @DisplayName("GET de verificacao com o token certo devolve o challenge em texto puro")
    void verificacaoDevolveOChallenge() throws Exception {
        mockMvc.perform(get(ROTA)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-it")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("1158201444"));
    }

    @Test
    @DisplayName("GET de verificacao com token errado ou ausente devolve 403 sem o challenge")
    void verificacaoComTokenErradoERecusada() throws Exception {
        mockMvc.perform(get(ROTA)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "errado")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        mockMvc.perform(get(ROTA)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST assinado e aceito mesmo com acento no corpo e sem charset")
    void eventoAssinadoEAceito() throws Exception {
        byte[] corpo = "{\"object\":\"whatsapp_business_account\",\"entry\":[],\"x\":\"regulação 😀\"}"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo)
                        .header("X-Hub-Signature-256", assinar(corpo)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST sem assinatura, com assinatura errada ou sem corpo devolve 403")
    void eventoSemAssinaturaValidaERecusado() throws Exception {
        byte[] corpo = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .header("X-Hub-Signature-256", assinar("outro corpo".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ROTA).header("X-Hub-Signature-256", "sha256=00"))
                .andExpect(status().isForbidden());
    }
}
