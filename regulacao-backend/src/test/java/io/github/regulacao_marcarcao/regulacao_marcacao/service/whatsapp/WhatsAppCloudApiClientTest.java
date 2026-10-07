package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppCloudApiClient.Situacao;

/**
 * O que sai para a Meta e como cada resposta e classificada. A classificacao
 * decide se a mensagem e repetida — repetir errado duplica mensagem no celular
 * do paciente.
 */
class WhatsAppCloudApiClientTest {

    private static final String URL = "https://graph.exemplo/v23.0/109876543210/messages";

    private MockRestServiceServer servidor;
    private WhatsAppCloudApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new WhatsAppCloudApiClient(WhatsAppTestes.configurado(), new ObjectMapper(), builder);
    }

    @Test
    void enviaOTemplateNoFormatoDaMetaEDevolveOId() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-de-teste"))
                .andExpect(content().json("""
                        {
                          "messaging_product": "whatsapp",
                          "to": "5575999990000",
                          "type": "template",
                          "template": {
                            "name": "tpl_confirmacao",
                            "language": { "code": "pt_BR" },
                            "components": [ { "type": "body", "parameters": [
                              { "type": "text", "text": "Maria" },
                              { "type": "text", "text": "Cardiologia" } ] } ]
                          }
                        }
                        """, true))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.ABC\"}]}", MediaType.APPLICATION_JSON));

        var resposta = client.enviarTemplate("5575999990000", "tpl_confirmacao", List.of("Maria", "Cardiologia"));

        assertThat(resposta.situacao()).isEqualTo(Situacao.ACEITA);
        assertThat(resposta.messageId()).isEqualTo("wamid.ABC");
        assertThat(resposta.erroCodigo()).isNull();
        servidor.verify();
    }

    @Test
    void erro4xxEDefinitivoEGuardaSoOCodigoDaMeta() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"(#131026) Message undeliverable para 5575999990000\",\"code\":131026}}"));

        var resposta = client.enviarTemplate("5575999990000", "tpl_confirmacao", List.of("Maria"));

        assertThat(resposta.situacao()).isEqualTo(Situacao.ERRO_DEFINITIVO);
        assertThat(resposta.erroCodigo()).isEqualTo("META_131026");
    }

    @Test
    void tokenInvalidoEDefinitivo() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":190}}"));

        assertThat(client.enviarTemplate("5575999990000", "t", List.of("a")).situacao())
                .isEqualTo(Situacao.ERRO_DEFINITIVO);
    }

    @Test
    void limiteDeTaxaEErroDoServidorSaoTemporarios() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("fora do ar"));

        var limite = client.enviarTemplate("5575999990000", "t", List.of("a"));
        var servidorFora = client.enviarTemplate("5575999990000", "t", List.of("a"));

        assertThat(limite.situacao()).isEqualTo(Situacao.ERRO_TEMPORARIO);
        assertThat(limite.erroCodigo()).isEqualTo("HTTP_429");
        assertThat(servidorFora.situacao()).isEqualTo(Situacao.ERRO_TEMPORARIO);
        assertThat(servidorFora.erroCodigo()).isEqualTo("HTTP_503");
    }

    @Test
    void semConexaoETemporario_aMensagemComCertezaNaoSaiu() {
        servidor.expect(requestTo(URL)).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });
        servidor.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Connect timed out");
        });

        assertThat(client.enviarTemplate("5575999990000", "t", List.of("a")).situacao())
                .isEqualTo(Situacao.ERRO_TEMPORARIO);
        assertThat(client.enviarTemplate("5575999990000", "t", List.of("a")).situacao())
                .isEqualTo(Situacao.ERRO_TEMPORARIO);
    }

    @Test
    void timeoutDeLeituraEIndeterminado_naoPodeSerRepetido() {
        servidor.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        var resposta = client.enviarTemplate("5575999990000", "t", List.of("a"));

        assertThat(resposta.situacao()).isEqualTo(Situacao.INDETERMINADO);
        assertThat(resposta.erroCodigo()).isEqualTo("TIMEOUT");
    }

    @Test
    void respostaDeSucessoSemIdEIndeterminada() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.enviarTemplate("5575999990000", "t", List.of("a")).situacao())
                .isEqualTo(Situacao.INDETERMINADO);
    }

    @Test
    void conexaoQueCaiDepoisDeEstabelecidaEIndeterminada_naoPodeSerRepetida() {
        servidor.expect(requestTo(URL)).andRespond(request -> {
            throw new IOException("Connection reset");
        });

        var resposta = client.enviarTemplate("5575999990000", "t", List.of("a"));

        assertThat(resposta.situacao()).isEqualTo(Situacao.INDETERMINADO);
        assertThat(resposta.erroCodigo()).isEqualTo("SEM_RESPOSTA");
    }

    @Test
    void hostDesconhecidoETemporario() {
        servidor.expect(requestTo(URL)).andRespond(request -> {
            throw new java.net.UnknownHostException("graph.exemplo");
        });

        assertThat(client.enviarTemplate("5575999990000", "t", List.of("a")).situacao())
                .isEqualTo(Situacao.ERRO_TEMPORARIO);
    }
}
