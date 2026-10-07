package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppCloudApiClient.Resposta;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppCloudApiClient.Situacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppMensagemService.Preparo;

/** Orquestracao do despacho: quando a Meta e chamada e quando nao pode ser. */
class WhatsAppEnvioJobTest {

    private final WhatsAppMensagemService mensagens = mock(WhatsAppMensagemService.class);
    private final WhatsAppLembreteService lembretes = mock(WhatsAppLembreteService.class);
    private final WhatsAppCloudApiClient meta = mock(WhatsAppCloudApiClient.class);

    private WhatsAppEnvioJob job(WhatsAppEnvioProperties props) {
        return new WhatsAppEnvioJob(mensagens, lembretes, meta, props);
    }

    private static WhatsAppEnvioProperties comAgendador() {
        var p = WhatsAppTestes.configurado();
        return new WhatsAppEnvioProperties(p.accessToken(), p.phoneNumberId(), p.baseUrl(), p.apiVersion(),
                p.timeoutConexaoMs(), p.timeoutLeituraMs(), p.limiteDiario(), p.numerosTeste(),
                p.cancelamentoAtrasoMinutos(), true, p.idioma(), p.template());
    }

    private static Preparo pronto() {
        return new Preparo(null, "5575999990000", "0000", "tpl", List.of("Maria"));
    }

    @Test
    void enviaERegistraAResposta() {
        Resposta aceita = new Resposta(Situacao.ACEITA, "wamid.ABC", null);
        when(mensagens.proximasDaFila()).thenReturn(List.of(1L));
        when(mensagens.reivindicar(1L)).thenReturn(true);
        when(mensagens.preparar(1L)).thenReturn(pronto());
        when(meta.enviarTemplate("5575999990000", "tpl", List.of("Maria"))).thenReturn(aceita);

        job(comAgendador()).despachar();

        var ordem = inOrder(mensagens, meta);
        ordem.verify(mensagens).encerrarEnviosInterrompidos();
        ordem.verify(mensagens).reivindicar(1L);
        ordem.verify(mensagens).preparar(1L);
        ordem.verify(meta).enviarTemplate("5575999990000", "tpl", List.of("Maria"));
        ordem.verify(mensagens).registrarResposta(1L, "0000", aceita);
    }

    @Test
    void mensagemBarradaPelasRegrasNaoChamaAMeta() {
        when(mensagens.proximasDaFila()).thenReturn(List.of(1L));
        when(mensagens.reivindicar(1L)).thenReturn(true);
        when(mensagens.preparar(1L)).thenReturn(Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.OPT_OUT));

        job(comAgendador()).despachar();

        verify(mensagens).registrarNaoEnvio(1L, WhatsAppMotivoNaoEnvio.OPT_OUT);
        verifyNoInteractions(meta);
    }

    @Test
    void mensagemJaReivindicadaPorOutraExecucaoEIgnorada() {
        when(mensagens.proximasDaFila()).thenReturn(List.of(1L));
        when(mensagens.reivindicar(1L)).thenReturn(false);

        job(comAgendador()).despachar();

        verify(mensagens, never()).preparar(anyLong());
        verifyNoInteractions(meta);
    }

    @Test
    void erroAoPrepararUmaMensagemNaoImpedeAsDemais() {
        when(mensagens.proximasDaFila()).thenReturn(List.of(1L, 2L));
        when(mensagens.reivindicar(anyLong())).thenReturn(true);
        when(mensagens.preparar(1L)).thenThrow(new IllegalStateException("dado inconsistente"));
        when(mensagens.preparar(2L)).thenReturn(pronto());
        when(meta.enviarTemplate(any(), any(), any())).thenReturn(new Resposta(Situacao.ACEITA, "wamid.X", null));

        job(comAgendador()).despachar();

        verify(mensagens).registrarFalha(1L, "ERRO_INTERNO");
        verify(mensagens).registrarResposta(anyLong(), any(), any());
    }

    @Test
    void instanciaSemCredenciaisNaoTocaEmNada() {
        var job = job(WhatsAppTestes.naoConfigurado());

        job.despacharAgendado();
        job.lembretesAgendados();

        verifyNoInteractions(mensagens, lembretes, meta);
    }

    @Test
    void agendadorDesligadoNaoTocaEmNada() {
        var job = job(WhatsAppTestes.configurado()); // agendador-ligado=false

        job.despacharAgendado();
        job.lembretesAgendados();

        verifyNoInteractions(mensagens, lembretes, meta);
    }

    @Test
    void tarefasAgendadasNuncaLancam() {
        when(mensagens.proximasDaFila()).thenThrow(new RuntimeException("banco fora do ar"));
        when(lembretes.executarLote(WhatsAppOrigem.AUTOMATICO, null)).thenThrow(new RuntimeException("x"));
        var job = job(comAgendador());

        assertThatCode(job::despacharAgendado).doesNotThrowAnyException();
        assertThatCode(job::lembretesAgendados).doesNotThrowAnyException();
    }
}
