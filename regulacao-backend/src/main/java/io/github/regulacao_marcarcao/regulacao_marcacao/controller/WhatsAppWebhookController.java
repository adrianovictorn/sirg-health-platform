package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.service.WhatsAppWebhookService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook da WhatsApp Business Cloud API (Meta).
 *
 * <p>Rota publica, chamada pela Meta e nao pelo frontend. Devolve o status
 * direto, sem corpo de erro: 404 quando a instancia nao tem a integracao
 * configurada, 403 quando o token ou a assinatura nao conferem.
 *
 * <p>O corpo chega como {@code byte[]} de proposito — ver
 * {@link WhatsAppWebhookService#assinaturaValida(byte[], String)}.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService whatsAppWebhookService;
    private final WhatsAppStatusService whatsAppStatusService;

    /** Verificacao de inscricao: devolve o challenge em texto puro. */
    @GetMapping
    public ResponseEntity<String> verificar(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!whatsAppWebhookService.habilitado()) {
            return ResponseEntity.notFound().build();
        }
        if (!whatsAppWebhookService.verificarInscricao(mode, token, challenge)) {
            log.warn("Webhook do WhatsApp: verificacao de inscricao recusada.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
    }

    /** Recebe os eventos (mensagens e status de entrega). */
    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestBody(required = false) byte[] corpo,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura) {
        if (!whatsAppWebhookService.habilitado()) {
            return ResponseEntity.notFound().build();
        }
        if (!whatsAppWebhookService.assinaturaValida(corpo, assinatura)) {
            log.warn("Webhook do WhatsApp: assinatura ausente ou invalida.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        whatsAppWebhookService.registrarMetadados(corpo);
        // Atualiza o registro de envios (entregue, lido, falhou). Nunca lanca.
        whatsAppStatusService.processar(corpo);
        return ResponseEntity.ok().build();
    }
}
