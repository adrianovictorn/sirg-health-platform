package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.LoginRequestDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.TokenService;

/**
 * RF07/RF08: cada categoria de falha de login devolve status e mensagem
 * proprios, em vez de cair sem corpo util (comportamento anterior, sem teste
 * nenhum cobrindo o endpoint).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenService tokenService;

    @InjectMocks private AuthController controller;

    @Test
    void usuarioDesativado_devolve403ComMensagemPropria() {
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new LockedException("conta bloqueada"));

        var resposta = controller.login(new LoginRequestDTO("12345678900", "senha"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).isEqualTo(
                java.util.Map.of("message", "Conta desativada. Entre em contato com o administrador."));
    }

    @Test
    void credenciaisIncorretas_devolve401ComMensagemUnificada() {
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        var resposta = controller.login(new LoginRequestDTO("12345678900", "senhaErrada"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody()).isEqualTo(java.util.Map.of("message", "CPF ou senha incorretos."));
    }

    @Test
    void servidorIndisponivel_devolve503ComMensagemPropria() {
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new AuthenticationServiceException("banco fora do ar"));

        var resposta = controller.login(new LoginRequestDTO("12345678900", "senha"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resposta.getBody()).isEqualTo(
                java.util.Map.of("message", "Servidor indisponível. Tente novamente em instantes."));
    }
}
