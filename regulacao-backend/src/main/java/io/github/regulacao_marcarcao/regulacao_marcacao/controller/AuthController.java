package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.LoginRequestDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.LoginResponseDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequestDTO data) {
        try {
            var usernamePassword = new UsernamePasswordAuthenticationToken(data.cpf(), data.password());
            var auth = this.authenticationManager.authenticate(usernamePassword);
            var user = (User) auth.getPrincipal();
            var token = tokenService.generateToken(user);
            return ResponseEntity.ok(new LoginResponseDTO(token));
        } catch (DisabledException | LockedException e) {
            // RF07: usuario desativado — na pratica hoje e LockedException, pois
            // User.isAccountNonLocked() tambem retorna `ativo` e o Spring Security
            // checa "locked" antes de "disabled"; capturamos os dois por seguranca.
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Conta desativada. Entre em contato com o administrador."));
        } catch (BadCredentialsException e) {
            // RF07: CPF inexistente e senha incorreta ja chegam aqui unificados
            // pelo proprio Spring Security (hideUserNotFoundExceptions) — mantido
            // de proposito, para nao permitir enumerar CPFs cadastrados.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "CPF ou senha incorretos."));
        } catch (AuthenticationServiceException e) {
            // RF07: falha ao consultar o banco/UserDetailsService — distinto de
            // credencial incorreta, para o operador nao pensar que digitou errado.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "Servidor indisponível. Tente novamente em instantes."));
        }
    }
}
