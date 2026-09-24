package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Regras de segregacao de dados por unidade — a base de seguranca do perfil
 * ADMIN_UNIDADE.
 */
@ExtendWith(MockitoExtension.class)
class UnidadeAcessoServiceTest {

    private static final String CPF = "11111111111";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UnidadeAcessoService service;

    private User usuario(Roles role, Long unidadeId) {
        User u = new User();
        u.setCpf(CPF);
        u.setRole(role);
        if (unidadeId != null) {
            Unidade unidade = new Unidade();
            unidade.setId(unidadeId);
            unidade.setNome("Unidade " + unidadeId);
            u.setUnidade(unidade);
        }
        return u;
    }

    private void dadoUsuario(User u) {
        when(userRepository.findByCpf(CPF)).thenReturn(Optional.of(u));
    }

    @Test
    void adminGlobalNaoEFiltradoPorUnidade() {
        dadoUsuario(usuario(Roles.ADMIN, null));

        assertThat(service.contextoDe(CPF).isGlobal()).isTrue();
        assertThat(service.isAcessoGlobal(CPF)).isTrue();
    }

    @Test
    void adminDeUnidadeEnxergaApenasAPropriaUnidade() {
        dadoUsuario(usuario(Roles.ADMIN_UNIDADE, 7L));

        var ctx = service.contextoDe(CPF);

        assertThat(ctx.isGlobal()).isFalse();
        assertThat(ctx.id()).isEqualTo(7L);
        assertThat(ctx.permite(7L)).isTrue();
        assertThat(ctx.permite(8L)).isFalse();
        assertThat(ctx.permite(null)).isFalse();
    }

    /**
     * Sem esta regra, um ADMIN_UNIDADE sem lotacao cairia no ramo "sem unidade =
     * acesso global" e viraria, na pratica, um administrador global.
     */
    @Test
    void adminDeUnidadeSemLotacaoTemAcessoNegadoEmVezDeGlobal() {
        dadoUsuario(usuario(Roles.ADMIN_UNIDADE, null));

        assertThatThrownBy(() -> service.contextoDe(CPF))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("unidade de lota");
    }

    @Test
    void exigirAcessoBloqueiaUnidadeAlheia() {
        dadoUsuario(usuario(Roles.ADMIN_UNIDADE, 7L));

        assertThatCode(() -> service.exigirAcessoA(CPF, 7L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.exigirAcessoA(CPF, 99L))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Mesmo que o cliente envie outro unidadeId no corpo da requisicao, a operacao
     * e forcada para a unidade do proprio usuario quando ele e restrito.
     */
    @Test
    void resolverUnidadeAlvoIgnoraUnidadeEnviadaQuandoUsuarioERestrito() {
        dadoUsuario(usuario(Roles.ADMIN_UNIDADE, 7L));

        assertThat(service.resolverUnidadeAlvo(CPF, null)).isEqualTo(7L);
        assertThatThrownBy(() -> service.resolverUnidadeAlvo(CPF, 99L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resolverUnidadeAlvoRespeitaEscolhaDoAdminGlobal() {
        dadoUsuario(usuario(Roles.ADMIN, null));

        assertThat(service.resolverUnidadeAlvo(CPF, 99L)).isEqualTo(99L);
    }

    @Test
    void perfilClinicoComUnidadeContinuaRestritoAEla() {
        dadoUsuario(usuario(Roles.RECEPCAO, 3L));

        assertThat(service.contextoDe(CPF).id()).isEqualTo(3L);
    }

    /**
     * Comportamento historico preservado: perfis antigos sem unidade vinculada
     * continuam com acesso global (a segregacao so vale onde ha lotacao).
     */
    @Test
    void perfilClinicoSemUnidadeMantemComportamentoGlobal() {
        dadoUsuario(usuario(Roles.RECEPCAO, null));

        assertThat(service.contextoDe(CPF).isGlobal()).isTrue();
    }

    @Test
    void chamadaSemAutenticacaoNaoQuebra() {
        assertThat(service.contextoDe((String) null).isGlobal()).isTrue();
    }
}
