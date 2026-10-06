package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Autentica as chamadas de webhook da WhatsApp Business Cloud API (Meta).
 *
 * <p>A rota e publica (sem JWT): quem garante a origem e o verify token, no GET
 * de inscricao, e a assinatura HMAC-SHA256 do corpo com o App Secret, no POST.
 *
 * <p>Nesta fatia nada e gravado nem processado: o evento e validado e apenas
 * resumido em log. O resumo nunca leva telefone, nome, texto nem id de mensagem
 * — o payload da Meta carrega dado de paciente.
 *
 * <p>Cada instancia (municipio) tem os proprios valores. Sem os dois
 * configurados a integracao fica desligada e a instancia sobe normalmente.
 */
@Service
@Slf4j
public class WhatsAppWebhookService {

    private static final String ALGORITMO = "HmacSHA256";
    private static final String PREFIXO_ASSINATURA = "sha256=";

    private final String verifyToken;
    private final String appSecret;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookService(@Value("${app.whatsapp.verify-token:}") String verifyToken,
                                  @Value("${app.whatsapp.app-secret:}") String appSecret,
                                  ObjectMapper objectMapper) {
        this.verifyToken = verifyToken == null ? "" : verifyToken.strip();
        this.appSecret = appSecret == null ? "" : appSecret.strip();
        this.objectMapper = objectMapper;

        if (habilitado()) {
            log.info("Webhook do WhatsApp ligado.");
        } else {
            log.info("Webhook do WhatsApp desligado: defina WHATSAPP_VERIFY_TOKEN e WHATSAPP_APP_SECRET para ligar.");
        }
    }

    /** So liga com os dois valores: configuracao pela metade conta como desligado. */
    public boolean habilitado() {
        return !verifyToken.isEmpty() && !appSecret.isEmpty();
    }

    /** Valida o GET de inscricao enviado pelo painel da Meta. */
    public boolean verificarInscricao(String mode, String token, String challenge) {
        if (!habilitado() || !"subscribe".equals(mode) || token == null || challenge == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                verifyToken.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Confere o header {@code X-Hub-Signature-256} contra o HMAC do corpo.
     *
     * <p>Recebe os bytes exatamente como chegaram: qualquer decodificacao antes
     * do calculo (String, DTO) muda os bytes e invalida a assinatura.
     */
    public boolean assinaturaValida(byte[] corpo, String assinatura) {
        if (!habilitado() || corpo == null || assinatura == null
                || !assinatura.startsWith(PREFIXO_ASSINATURA)) {
            return false;
        }
        try {
            byte[] recebida = HexFormat.of().parseHex(assinatura.substring(PREFIXO_ASSINATURA.length()));
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return MessageDigest.isEqual(mac.doFinal(corpo), recebida);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    /** Registra o evento ja autenticado. Nunca lanca: a Meta precisa receber o 200. */
    public void registrarMetadados(byte[] corpo) {
        log.info("Webhook do WhatsApp recebido: {}", resumoSeguro(corpo));
    }

    /**
     * Resumo do evento sem dado pessoal: tipo do objeto, id da conta, campo
     * alterado e quantidades. Nao incluir {@code from}, {@code wa_id},
     * {@code contacts}, {@code text} nem o id da mensagem.
     */
    String resumoSeguro(byte[] corpo) {
        try {
            JsonNode raiz = objectMapper.readTree(corpo);
            List<String> entradas = new ArrayList<>();
            for (JsonNode entry : raiz.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    entradas.add("conta=" + entry.path("id").asText("?")
                            + " campo=" + change.path("field").asText("?")
                            + " mensagens=" + value.path("messages").size()
                            + " status=" + value.path("statuses").size());
                }
            }
            return "objeto=" + raiz.path("object").asText("?") + " " + entradas;
        } catch (Exception e) {
            return "corpo ilegivel";
        }
    }
}
