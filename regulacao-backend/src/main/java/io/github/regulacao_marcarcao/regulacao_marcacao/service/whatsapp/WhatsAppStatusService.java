package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppMensagemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica ao registro de envios o que chega pelo webhook da Meta, ja autenticado.
 *
 * <ul>
 *   <li><b>statuses</b> — localiza a mensagem pelo id devolvido no envio e so
 *       AVANCA a situacao (enviado, entregue, lido). Status repetido ou fora de
 *       ordem nao faz nada: a Meta reenvia, e isto precisa ser idempotente.</li>
 *   <li><b>messages</b> — mensagens escritas por pacientes. So a QUANTIDADE do
 *       dia e somada; texto, remetente e id nao sao lidos nem gravados.</li>
 * </ul>
 *
 * <p>Nunca lanca: a Meta precisa receber o 200, senao reenvia por dias. E nunca
 * loga telefone, {@code wa_id} nem id de mensagem.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppStatusService {

    private final WhatsAppMensagemRepository mensagemRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void processar(byte[] corpo) {
        try {
            JsonNode raiz = objectMapper.readTree(corpo);
            int recebidas = 0;
            for (JsonNode entry : raiz.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    for (JsonNode status : value.path("statuses")) {
                        aplicarComSeguranca(status);
                    }
                    recebidas += value.path("messages").size();
                }
            }
            if (recebidas > 0) {
                int quantidade = recebidas;
                LocalDate hoje = LocalDate.now(clock.withZone(WhatsAppEnvioProperties.FUSO));
                transactionTemplate.executeWithoutResult(tx -> mensagemRepository.somarRecebidasDoDia(hoje, quantidade));
            }
        } catch (Throwable e) {
            log.warn("WhatsApp: evento do webhook nao processado ({}).", e.getClass().getSimpleName());
        }
    }

    /** Um status com problema nao impede os demais do mesmo evento. */
    private void aplicarComSeguranca(JsonNode status) {
        try {
            transactionTemplate.executeWithoutResult(tx -> aplicar(status));
        } catch (RuntimeException e) {
            log.warn("WhatsApp: status do webhook nao aplicado ({}).", e.getClass().getSimpleName());
        }
    }

    private void aplicar(JsonNode status) {
        String metaId = status.path("id").asText(null);
        if (metaId == null) {
            return;
        }
        WhatsAppMensagem mensagem = mensagemRepository.findByMetaMessageId(metaId).orElse(null);
        if (mensagem == null) {
            return;
        }
        Instant quando = instanteDe(status);

        JsonNode pricing = status.path("pricing");
        if (pricing.isObject()) {
            if (pricing.has("billable")) {
                mensagem.setCobravel(pricing.path("billable").asBoolean());
            }
            String categoria = pricing.path("category").asText(null);
            if (categoria != null) {
                mensagem.setCategoriaCobranca(cortar(categoria));
            }
        }

        switch (status.path("status").asText("")) {
            case "delivered" -> {
                if (mensagem.getEntregueEm() == null) {
                    mensagem.setEntregueEm(quando);
                }
                avancar(mensagem, WhatsAppResultado.ENTREGUE);
            }
            case "read" -> {
                if (mensagem.getLidoEm() == null) {
                    mensagem.setLidoEm(quando);
                }
                avancar(mensagem, WhatsAppResultado.LIDO);
            }
            case "failed" -> {
                // Falha so vale enquanto a mensagem nao chegou; depois de entregue nao regride.
                if (mensagem.getResultado() == WhatsAppResultado.ENVIADO) {
                    mensagem.setResultado(WhatsAppResultado.FALHOU);
                    mensagem.setFalhouEm(quando);
                    String codigo = status.path("errors").path(0).path("code").asText(null);
                    mensagem.setErroCodigo(codigo == null ? "META_FALHA" : cortar("META_" + codigo));
                }
            }
            default -> {
                // "sent" e status desconhecidos: a linha ja esta ENVIADO desde a resposta do envio.
            }
        }
        mensagemRepository.save(mensagem);
    }

    /** ENVIADO -> ENTREGUE -> LIDO; nunca volta, e nao ressuscita uma falha. */
    private void avancar(WhatsAppMensagem mensagem, WhatsAppResultado novo) {
        WhatsAppResultado atual = mensagem.getResultado();
        boolean podeAvancar = atual == WhatsAppResultado.ENVIADO
                || (atual == WhatsAppResultado.ENTREGUE && novo == WhatsAppResultado.LIDO);
        if (podeAvancar) {
            mensagem.setResultado(novo);
        }
    }

    private Instant instanteDe(JsonNode status) {
        long segundos = status.path("timestamp").asLong(0);
        return segundos > 0 ? Instant.ofEpochSecond(segundos) : clock.instant();
    }

    private static String cortar(String valor) {
        return valor.length() > 40 ? valor.substring(0, 40) : valor;
    }
}
