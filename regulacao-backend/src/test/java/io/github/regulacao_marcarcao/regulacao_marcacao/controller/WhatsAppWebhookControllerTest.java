package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import io.github.regulacao_marcarcao.regulacao_marcacao.service.WhatsAppWebhookService;

/**
 * Status devolvidos a Meta: 404 com a integracao desligada, 403 quando o token
 * ou a assinatura nao conferem, 200 so depois de autenticado.
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    private static final byte[] CORPO = "{}".getBytes();

    @Mock private WhatsAppWebhookService service;

    @InjectMocks private WhatsAppWebhookController controller;

    @Test
    void verificacaoValida_devolveOChallengeEmTextoPuro() {
        when(service.habilitado()).thenReturn(true);
        when(service.verificarInscricao("subscribe", "token", "1158201444")).thenReturn(true);

        var resposta = controller.verificar("subscribe", "token", "1158201444");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(resposta.getBody()).isEqualTo("1158201444");
    }

    @Test
    void verificacaoInvalida_devolve403SemOChallenge() {
        when(service.habilitado()).thenReturn(true);
        when(service.verificarInscricao("subscribe", "errado", "1158201444")).thenReturn(false);

        var resposta = controller.verificar("subscribe", "errado", "1158201444");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).isNull();
    }

    @Test
    void eventoComAssinaturaValida_devolve200ERegistra() {
        when(service.habilitado()).thenReturn(true);
        when(service.assinaturaValida(CORPO, "sha256=ok")).thenReturn(true);

        var resposta = controller.receber(CORPO, "sha256=ok");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).registrarMetadados(CORPO);
    }

    @Test
    void eventoComAssinaturaInvalida_devolve403ENaoRegistra() {
        when(service.habilitado()).thenReturn(true);
        when(service.assinaturaValida(CORPO, null)).thenReturn(false);

        var resposta = controller.receber(CORPO, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(service, never()).registrarMetadados(any());
    }

    @Test
    void integracaoDesligada_devolve404NosDoisMetodos() {
        when(service.habilitado()).thenReturn(false);

        assertThat(controller.verificar("subscribe", "token", "1").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.receber(CORPO, "sha256=ok").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(service, never()).registrarMetadados(any());
    }
}
