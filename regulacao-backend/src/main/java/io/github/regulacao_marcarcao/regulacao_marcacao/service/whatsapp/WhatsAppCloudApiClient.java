package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;

/**
 * Chamada de saida a WhatsApp Business Cloud API (Graph API): envio de
 * mensagem de template.
 *
 * <p>Nunca lanca: devolve sempre uma {@link Resposta}, porque quem chama e uma
 * tarefa agendada que precisa registrar o desfecho de cada mensagem. Nao loga
 * nada — numero, nome e texto sao dado de paciente.
 *
 * <p>O {@code RestClient} e um campo privado, NAO um bean: {@code CnesService}
 * injeta {@code RestClient} por tipo e um segundo bean o deixaria ambiguo.
 */
@Component
public class WhatsAppCloudApiClient {

    /** Desfecho de uma tentativa. */
    public enum Situacao {
        /** A Meta aceitou; {@code messageId} preenchido. */
        ACEITA,
        /** Erro que nao adianta repetir (4xx: numero invalido, template reprovado, token). */
        ERRO_DEFINITIVO,
        /** Vale tentar de novo (conexao nao estabelecida, 429, 5xx). A mensagem com certeza NAO saiu. */
        ERRO_TEMPORARIO,
        /** Nao da para saber se saiu (timeout de leitura, conexao caiu no meio). Nao repetir: duplicaria. */
        INDETERMINADO
    }

    public record Resposta(Situacao situacao, String messageId, String erroCodigo) {
    }

    private final RestClient restClient;
    private final WhatsAppEnvioProperties props;
    private final ObjectMapper objectMapper;

    @Autowired
    public WhatsAppCloudApiClient(WhatsAppEnvioProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, RestClient.builder().requestFactory(comTimeouts(props)));
    }

    /** Para teste: recebe o builder ja ligado a um servidor simulado. */
    WhatsAppCloudApiClient(WhatsAppEnvioProperties props, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = builder.baseUrl(props.baseUrl()).build();
    }

    private static SimpleClientHttpRequestFactory comTimeouts(WhatsAppEnvioProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.timeoutConexaoMs()));
        factory.setReadTimeout(Duration.ofMillis(props.timeoutLeituraMs()));
        return factory;
    }

    /**
     * @param numero    destino, so digitos, com codigo do pais
     * @param template  nome do template aprovado
     * @param variaveis valores do corpo, na ordem {{1}}, {{2}}...
     */
    public Resposta enviarTemplate(String numero, String template, List<String> variaveis) {
        List<Map<String, String>> parametros = variaveis.stream()
                .map(v -> Map.of("type", "text", "text", v))
                .toList();
        Map<String, Object> corpo = Map.of(
                "messaging_product", "whatsapp",
                "to", numero,
                "type", "template",
                "template", Map.of(
                        "name", template,
                        "language", Map.of("code", props.idioma()),
                        "components", List.of(Map.of("type", "body", "parameters", parametros))));
        try {
            JsonNode resposta = restClient.post()
                    .uri("/{versao}/{phoneNumberId}/messages", props.apiVersion(), props.phoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .body(JsonNode.class);
            String id = resposta == null ? null : resposta.path("messages").path(0).path("id").asText(null);
            if (id == null || id.isBlank()) {
                return new Resposta(Situacao.INDETERMINADO, null, "SEM_ID");
            }
            return new Resposta(Situacao.ACEITA, id, null);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean temporario = status == 429 || status >= 500;
            return new Resposta(temporario ? Situacao.ERRO_TEMPORARIO : Situacao.ERRO_DEFINITIVO,
                    null, codigoDeErro(e.getResponseBodyAsString(), status));
        } catch (ResourceAccessException e) {
            return semResposta(e);
        } catch (RestClientException e) {
            return new Resposta(Situacao.INDETERMINADO, null, "RESPOSTA_ILEGIVEL");
        }
    }

    /** Codigo de erro da Meta ({@code error.code}); sem ele, o status HTTP. Nunca a mensagem. */
    private String codigoDeErro(String corpo, int status) {
        try {
            JsonNode codigo = objectMapper.readTree(corpo).path("error").path("code");
            if (codigo.isValueNode() && !codigo.isNull()) {
                return "META_" + codigo.asText();
            }
        } catch (Exception ignorada) {
            // corpo que nao e JSON: fica o status HTTP
        }
        return "HTTP_" + status;
    }

    /**
     * So e "temporario" (e portanto repetido) o que prova que o pedido nem saiu:
     * a conexao nao chegou a ser estabelecida. Conexao resetada, EOF ou timeout
     * depois de conectar podem ter acontecido com o pedido ja entregue.
     */
    private Resposta semResposta(ResourceAccessException e) {
        Throwable causa = e.getCause();
        boolean timeout = causa instanceof SocketTimeoutException;
        boolean timeoutNaConexao = timeout && causa.getMessage() != null
                && causa.getMessage().toLowerCase().contains("connect");
        boolean naoConectou = timeoutNaConexao
                || causa instanceof ConnectException
                || causa instanceof UnknownHostException
                || causa instanceof NoRouteToHostException;
        if (naoConectou) {
            return new Resposta(Situacao.ERRO_TEMPORARIO, null, "SEM_CONEXAO");
        }
        return new Resposta(Situacao.INDETERMINADO, null, timeout ? "TIMEOUT" : "SEM_RESPOSTA");
    }
}
