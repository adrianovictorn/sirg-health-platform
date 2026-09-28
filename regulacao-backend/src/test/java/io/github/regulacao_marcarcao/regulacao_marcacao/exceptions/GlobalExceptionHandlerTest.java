package io.github.regulacao_marcarcao.regulacao_marcacao.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

/**
 * Antes deste handler, uma AccessDeniedException escapando de um controller
 * (bloqueio de UnidadeAcessoService) virava 403 sem o campo `message` que o
 * frontend le — o operador via um erro generico mesmo com o bloqueio de unidade
 * funcionando certo. Ver GlobalExceptionHandlerTest.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void acessoNegadoViraQuatrocentosETresComMensagem() {
        var resposta = handler.handleAcessoNegado(
                new AccessDeniedException("Acesso negado aos dados de outra unidade."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        @SuppressWarnings("unchecked")
        var corpo = (Map<String, String>) resposta.getBody();
        assertThat(corpo).containsEntry("message", "Acesso negado aos dados de outra unidade.");
    }
}
