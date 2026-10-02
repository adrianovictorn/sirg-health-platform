package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Caracterizacao do fluxo de login (RF06-RF08) ANTES do ajuste — nao existia
 * nenhum teste cobrindo autenticacao. Trava o comportamento atual para
 * detectar regressao: hoje a busca de usuario e por igualdade exata de CPF
 * (sem normalizar pontuacao), e usuario desativado lanca LockedException —
 * nao DisabledException — porque {@code User.isAccountNonLocked()} tambem
 * retorna {@code ativo}, e o Spring Security checa "locked" antes de
 * "disabled" (confirmado por este teste, nao suposto).
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class LoginAutenticacaoIT {

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "" + System.nanoTime();
    }

    private User criarUsuario(String cpf, String senhaPlana, boolean ativo) {
        User u = new User();
        u.setCpf(cpf);
        u.setNome("Usuario Teste " + sufixo);
        u.setPassword(passwordEncoder.encode(senhaPlana));
        u.setRole(Roles.USER);
        u.setAtivo(ativo);
        return userRepository.saveAndFlush(u);
    }

    @Test
    @DisplayName("HOJE: login com CPF exato (so digitos) e senha correta autentica")
    void loginComCpfExatoAutentica() {
        String cpf = "1" + sufixo.substring(0, Math.min(10, sufixo.length()));
        criarUsuario(cpf, "senha123", true);

        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(cpf, "senha123"));

        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(((User) auth.getPrincipal()).getCpf()).isEqualTo(cpf);
    }

    @Test
    @DisplayName("RF06: login com CPF pontuado autentica, mesmo com o banco guardando so digitos")
    void loginComCpfPontuadoAutentica() {
        String cpfDigitos = "2" + sufixo.substring(0, Math.min(10, sufixo.length()));
        criarUsuario(cpfDigitos, "senha123", true);

        String cpfPontuado = cpfDigitos.substring(0, 3) + "." + cpfDigitos.substring(3);

        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(cpfPontuado, "senha123"));

        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(((User) auth.getPrincipal()).getCpf()).isEqualTo(cpfDigitos);
    }

    @Test
    @DisplayName("HOJE: usuario desativado lanca LockedException, nao DisabledException")
    void usuarioDesativadoLancaLockedException() {
        String cpf = "3" + sufixo.substring(0, Math.min(10, sufixo.length()));
        criarUsuario(cpf, "senha123", false);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(cpf, "senha123")))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("HOJE: CPF inexistente e senha errada geram a mesma excecao (BadCredentialsException)")
    void cpfInexistenteOuSenhaErradaGeramMesmaExcecao() {
        String cpf = "4" + sufixo.substring(0, Math.min(10, sufixo.length()));
        criarUsuario(cpf, "senha123", true);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(cpf, "senhaErrada")))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("cpf-que-nao-existe-" + sufixo, "senha123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("HOJE: login com usuario nao-CPF (padrao sirg_adm) funciona por igualdade exata de string")
    void loginComUsuarioNaoCpfFunciona() {
        // "sirg_adm" ja existe via migration V56 — usamos um login nao-CPF distinto
        // para nao colidir com a constraint de unicidade, mas caracterizando a
        // mesma classe de usuario (string nao-numerica na coluna cpf).
        String usuarioEspecial = "adm_" + sufixo.substring(0, Math.min(6, sufixo.length()));
        criarUsuario(usuarioEspecial, "senha123", true);

        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(usuarioEspecial, "senha123"));

        assertThat(auth.isAuthenticated()).isTrue();
    }
}
