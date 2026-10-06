package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A rota do webhook e publica: o que impede um terceiro de injetar eventos e
 * so o que esta testado aqui (verify token e HMAC do corpo cru).
 */
class WhatsAppWebhookServiceTest {

    private static final String TOKEN = "token-de-verificacao";
    private static final String SEGREDO = "segredo-do-app";

    /** Payload no formato da Meta, com os campos que NAO podem ir para o log. */
    private static final String PAYLOAD = """
            {"object":"whatsapp_business_account","entry":[{"id":"2296867987498463","changes":[{"field":"messages",
            "value":{"messaging_product":"whatsapp",
            "metadata":{"display_phone_number":"557533330000","phone_number_id":"109876543210"},
            "contacts":[{"profile":{"name":"Maria da Conceição"},"wa_id":"5575999990000"}],
            "messages":[{"from":"5575999990000","id":"wamid.HBgNNTU3NTk5OTk5MDAwMA","timestamp":"1790000000",
            "type":"text","text":{"body":"Qual o preparo do exame de sangue?"}}]}}]}]}
            """;

    private final WhatsAppWebhookService service = servico(TOKEN, SEGREDO);

    private static WhatsAppWebhookService servico(String token, String segredo) {
        return new WhatsAppWebhookService(token, segredo, new ObjectMapper());
    }

    private static String assinar(byte[] corpo, String segredo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo));
    }

    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    // --- assinatura do POST ---------------------------------------------------

    @Test
    void assinaturaCorreta_aceita() throws Exception {
        byte[] corpo = bytes(PAYLOAD);

        assertThat(service.assinaturaValida(corpo, assinar(corpo, SEGREDO))).isTrue();
    }

    @Test
    void corpoComAcento_aceita_porqueOHmacUsaOsBytesCrus() throws Exception {
        byte[] corpo = bytes("{\"text\":\"regulação ç ã 😀\"}");

        assertThat(service.assinaturaValida(corpo, assinar(corpo, SEGREDO))).isTrue();
    }

    @Test
    void corpoAlterado_recusa() throws Exception {
        byte[] corpo = bytes(PAYLOAD);
        String assinatura = assinar(corpo, SEGREDO);
        corpo[10] ^= 1;

        assertThat(service.assinaturaValida(corpo, assinatura)).isFalse();
    }

    @Test
    void assinadoComOutroSegredo_recusa() throws Exception {
        byte[] corpo = bytes(PAYLOAD);

        assertThat(service.assinaturaValida(corpo, assinar(corpo, "outro-segredo"))).isFalse();
    }

    @Test
    void assinaturaAusenteOuMalformada_recusaSemLancar() throws Exception {
        byte[] corpo = bytes(PAYLOAD);
        String hex = assinar(corpo, SEGREDO).substring("sha256=".length());

        assertThat(service.assinaturaValida(corpo, null)).isFalse();
        assertThat(service.assinaturaValida(corpo, "")).isFalse();
        assertThat(service.assinaturaValida(corpo, hex)).as("sem prefixo").isFalse();
        assertThat(service.assinaturaValida(corpo, "sha1=" + hex)).as("prefixo errado").isFalse();
        assertThat(service.assinaturaValida(corpo, "sha256=")).as("hex vazio").isFalse();
        assertThat(service.assinaturaValida(corpo, "sha256=zz")).as("hex invalido").isFalse();
        assertThat(service.assinaturaValida(corpo, "sha256=abc")).as("hex impar").isFalse();
        assertThat(service.assinaturaValida(corpo, "sha256=" + hex.substring(2))).as("hex curto").isFalse();
    }

    @Test
    void corpoNulo_recusa() {
        assertThat(service.assinaturaValida(null, "sha256=00")).isFalse();
    }

    // --- verificacao de inscricao (GET) ----------------------------------------

    @Test
    void verifyTokenCorreto_aceita() {
        assertThat(service.verificarInscricao("subscribe", TOKEN, "123")).isTrue();
    }

    @Test
    void verificacaoInvalida_recusa() {
        assertThat(service.verificarInscricao("subscribe", "errado", "123")).as("token errado").isFalse();
        assertThat(service.verificarInscricao("subscribe", null, "123")).as("token ausente").isFalse();
        assertThat(service.verificarInscricao("unsubscribe", TOKEN, "123")).as("mode errado").isFalse();
        assertThat(service.verificarInscricao(null, TOKEN, "123")).as("mode ausente").isFalse();
        assertThat(service.verificarInscricao("subscribe", TOKEN, null)).as("sem challenge").isFalse();
    }

    // --- integracao desligada ---------------------------------------------------

    @Test
    void semOsDoisValores_ficaDesligadoERecusaTudo() throws Exception {
        byte[] corpo = bytes(PAYLOAD);

        for (WhatsAppWebhookService desligado : new WhatsAppWebhookService[] {
                servico("", ""), servico(TOKEN, ""), servico("", SEGREDO),
                servico(null, null), servico("  ", "  ") }) {
            assertThat(desligado.habilitado()).isFalse();
            assertThat(desligado.verificarInscricao("subscribe", "", "123")).isFalse();
            assertThat(desligado.verificarInscricao("subscribe", TOKEN, "123")).isFalse();
            assertThat(desligado.assinaturaValida(corpo, assinar(corpo, SEGREDO))).isFalse();
        }
        assertThat(service.habilitado()).isTrue();
    }

    // --- log --------------------------------------------------------------------

    @Test
    void resumoTrazSoMetadados_semDadoDoPaciente() {
        String resumo = service.resumoSeguro(bytes(PAYLOAD));

        assertThat(resumo)
                .contains("whatsapp_business_account", "2296867987498463", "campo=messages",
                        "mensagens=1", "status=0")
                .doesNotContain("5575999990000", "557533330000", "109876543210", "Maria", "wamid",
                        "preparo", "exame");
    }

    @Test
    void corpoIlegivel_naoLanca() {
        assertThatCode(() -> {
            service.registrarMetadados(bytes("isto nao e json"));
            service.registrarMetadados(new byte[0]);
            service.registrarMetadados(bytes("[]"));
            service.registrarMetadados(bytes("{\"entry\":\"x\"}"));
            service.registrarMetadados(null);
        }).doesNotThrowAnyException();
    }
}
