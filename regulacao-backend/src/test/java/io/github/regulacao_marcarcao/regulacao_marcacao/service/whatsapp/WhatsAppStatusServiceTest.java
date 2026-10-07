package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppMensagemRepository;

/**
 * Status de entrega vindos da Meta. Ela reenvia e nao garante ordem: aplicar
 * precisa ser idempotente e a situacao so pode avancar.
 */
class WhatsAppStatusServiceTest {

    private static final String ID = "wamid.HBgNNTU3NTk5OTk5MDAwMA";

    private final WhatsAppMensagemRepository repository = mock(WhatsAppMensagemRepository.class);
    private WhatsAppStatusService service;
    private WhatsAppMensagem mensagem;

    @BeforeEach
    void setUp() {
        // 01:30 UTC do dia 7 = 22:30 do dia 6 na Bahia.
        service = new WhatsAppStatusService(repository, new ObjectMapper(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                Clock.fixed(Instant.parse("2026-10-07T01:30:00Z"), ZoneOffset.UTC));
        mensagem = new WhatsAppMensagem();
        mensagem.setResultado(WhatsAppResultado.ENVIADO);
        mensagem.setMetaMessageId(ID);
        when(repository.findByMetaMessageId(ID)).thenReturn(Optional.of(mensagem));
    }

    private static byte[] evento(String value) {
        return ("{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"1\",\"changes\":[{\"field\":\"messages\","
                + "\"value\":" + value + "}]}]}").getBytes(StandardCharsets.UTF_8);
    }

    private static String status(String status, long timestamp) {
        return "{\"id\":\"" + ID + "\",\"status\":\"" + status + "\",\"timestamp\":\"" + timestamp
                + "\",\"recipient_id\":\"5575999990000\"}";
    }

    private void receber(String... statuses) {
        service.processar(evento("{\"statuses\":[" + String.join(",", statuses) + "]}"));
    }

    @Test
    void entregueELidoAvancamASituacaoEGuardamAHora() {
        receber(status("delivered", 1790000000L));
        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.ENTREGUE);
        assertThat(mensagem.getEntregueEm()).isEqualTo(Instant.ofEpochSecond(1790000000L));

        receber(status("read", 1790000060L));
        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.LIDO);
        assertThat(mensagem.getLidoEm()).isEqualTo(Instant.ofEpochSecond(1790000060L));
    }

    @Test
    void statusForaDeOrdemNaoRegride() {
        receber(status("read", 1790000060L));
        receber(status("delivered", 1790000000L));
        receber(status("sent", 1789999990L));

        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.LIDO);
        assertThat(mensagem.getEntregueEm()).isEqualTo(Instant.ofEpochSecond(1790000000L));
    }

    @Test
    void statusRepetidoNaoMudaNada() {
        receber(status("delivered", 1790000000L));
        receber(status("delivered", 1790009999L));

        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.ENTREGUE);
        assertThat(mensagem.getEntregueEm()).isEqualTo(Instant.ofEpochSecond(1790000000L));
    }

    @Test
    void falhaGuardaSoOCodigoENaoERessuscitadaDepois() {
        receber("{\"id\":\"" + ID + "\",\"status\":\"failed\",\"timestamp\":\"1790000000\","
                + "\"errors\":[{\"code\":131026,\"title\":\"Message undeliverable\","
                + "\"error_data\":{\"details\":\"numero 5575999990000 sem WhatsApp\"}}]}");

        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.FALHOU);
        assertThat(mensagem.getErroCodigo()).isEqualTo("META_131026");
        assertThat(mensagem.getFalhouEm()).isEqualTo(Instant.ofEpochSecond(1790000000L));

        receber(status("delivered", 1790000100L));
        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.FALHOU);
    }

    @Test
    void falhaDepoisDeEntregueNaoRegride() {
        receber(status("delivered", 1790000000L));
        receber(status("failed", 1790000100L));

        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.ENTREGUE);
    }

    @Test
    void guardaSeAMensagemECobravelEACategoria() {
        receber("{\"id\":\"" + ID + "\",\"status\":\"sent\",\"timestamp\":\"1790000000\","
                + "\"pricing\":{\"billable\":true,\"pricing_model\":\"PMP\",\"category\":\"utility\"}}");

        assertThat(mensagem.getCobravel()).isTrue();
        assertThat(mensagem.getCategoriaCobranca()).isEqualTo("utility");
        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.ENVIADO);
    }

    @Test
    void statusDeMensagemDesconhecidaEIgnorado() {
        when(repository.findByMetaMessageId(ID)).thenReturn(Optional.empty());

        receber(status("delivered", 1790000000L));

        verify(repository, never()).save(any());
    }

    @Test
    void mensagensDePacientesSoEntramNaContagemDoDiaLocal() {
        service.processar(evento("{\"contacts\":[{\"profile\":{\"name\":\"Maria\"},\"wa_id\":\"5575999990000\"}],"
                + "\"messages\":[{\"from\":\"5575999990000\",\"id\":\"wamid.X\",\"type\":\"text\","
                + "\"text\":{\"body\":\"nao posso ir\"}},{\"from\":\"5575999990000\",\"id\":\"wamid.Y\",\"type\":\"image\"}]}"));

        verify(repository).somarRecebidasDoDia(LocalDate.of(2026, 10, 6), 2);
        verify(repository, never()).save(any());
        verify(repository, never()).findByMetaMessageId(any());
    }

    @Test
    void eventoSoDeStatusNaoMexeNaContagemDeRecebidas() {
        receber(status("delivered", 1790000000L));

        verify(repository, never()).somarRecebidasDoDia(any(), anyInt());
    }

    @Test
    void nuncaLanca_aMetaPrecisaReceberO200() {
        when(repository.findByMetaMessageId(ID)).thenThrow(new RuntimeException("banco fora do ar"));

        assertThatCode(() -> {
            receber(status("delivered", 1790000000L));
            service.processar("isto nao e json".getBytes(StandardCharsets.UTF_8));
            service.processar(new byte[0]);
            service.processar(null);
            service.processar("{\"entry\":\"x\"}".getBytes(StandardCharsets.UTF_8));
            service.processar(evento("{\"statuses\":[{\"status\":\"delivered\"}]}"));
        }).doesNotThrowAnyException();
    }

    @Test
    void umStatusComErroNaoImpedeOSeguinte() {
        WhatsAppMensagem outra = new WhatsAppMensagem();
        outra.setResultado(WhatsAppResultado.ENVIADO);
        when(repository.findByMetaMessageId("wamid.RUIM")).thenThrow(new RuntimeException("falhou"));
        when(repository.findByMetaMessageId("wamid.BOA")).thenReturn(Optional.of(outra));

        receber("{\"id\":\"wamid.RUIM\",\"status\":\"delivered\",\"timestamp\":\"1790000000\"}",
                "{\"id\":\"wamid.BOA\",\"status\":\"delivered\",\"timestamp\":\"1790000000\"}");

        assertThat(outra.getResultado()).isEqualTo(WhatsAppResultado.ENTREGUE);
    }
}
