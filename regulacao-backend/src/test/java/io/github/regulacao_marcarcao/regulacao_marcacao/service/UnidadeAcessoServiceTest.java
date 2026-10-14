package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UsuarioUnidadeExecutanteRepository;

/**
 * Regras de segregacao de dados por unidade — a base de seguranca do perfil
 * ADMIN_UNIDADE.
 */
@ExtendWith(MockitoExtension.class)
class UnidadeAcessoServiceTest {

    private static final String CPF = "11111111111";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UsuarioUnidadeExecutanteRepository usuarioUnidadeExecutanteRepository;

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

    // ==================================================================
    // isAdminUnidade (V93) — usado para bloquear agendamento sem cota
    // especificamente para ADMIN_UNIDADE, sem incluir GESTOR (que tambem e
    // "global" para isAcessoGlobal, mas nao deve ser bloqueado).
    // ==================================================================

    @Test
    void isAdminUnidadeEVerdadeiroParaOPerfilAdminUnidade() {
        dadoUsuario(usuario(Roles.ADMIN_UNIDADE, 7L));

        assertThat(service.isAdminUnidade(CPF)).isTrue();
    }

    @Test
    void isAdminUnidadeEFalsoParaAdminGlobal() {
        dadoUsuario(usuario(Roles.ADMIN, null));

        assertThat(service.isAdminUnidade(CPF)).isFalse();
    }

    @Test
    void isAdminUnidadeEFalsoParaGestor() {
        dadoUsuario(usuario(Roles.GESTOR, null));

        assertThat(service.isAdminUnidade(CPF)).isFalse();
    }

    @Test
    void isAdminUnidadeEFalsoSemCpf() {
        assertThat(service.isAdminUnidade(null)).isFalse();
    }

    // ------------------------------------------------------------------
    // escopoDeListagem: listagens e contagens nominais (fila, dashboard)
    // ------------------------------------------------------------------

    @Test
    void escopoDeListagemEGlobalParaAdminEGestor() {
        dadoUsuario(usuario(Roles.ADMIN, null));
        assertThat(service.escopoDeListagem(CPF, null).isGlobal()).isTrue();

        // Lotacao do GESTOR e so administrativa — nao estreita o que ele ve.
        dadoUsuario(usuario(Roles.GESTOR, 7L));
        assertThat(service.escopoDeListagem(CPF, null).isGlobal()).isTrue();
    }

    @Test
    void escopoDeListagemGlobalPodeFiltrarPorUmaUnidade() {
        dadoUsuario(usuario(Roles.ADMIN, null));

        var escopo = service.escopoDeListagem(CPF, 9L);

        assertThat(escopo.isGlobal()).isFalse();
        assertThat(escopo.isSemAcesso()).isFalse();
        assertThat(escopo.unidadeId()).isEqualTo(9L);
    }

    @Test
    void escopoDeListagemComLotacaoEAPropriaUnidade() {
        for (Roles role : new Roles[] {Roles.ADMIN_UNIDADE, Roles.RECEPCAO, Roles.ENFERMEIRO, Roles.MEDICO,
                Roles.USER, Roles.COORD_TRANSPORTE}) {
            dadoUsuario(usuario(role, 7L));

            var escopo = service.escopoDeListagem(CPF, null);

            assertThat(escopo.isGlobal()).as("%s", role).isFalse();
            assertThat(escopo.unidadeId()).as("%s", role).isEqualTo(7L);
            assertThat(service.escopoDeListagem(CPF, 7L).unidadeId()).as("%s", role).isEqualTo(7L);
        }
    }

    @Test
    void escopoDeListagemNegaPedidoDeOutraUnidade() {
        dadoUsuario(usuario(Roles.RECEPCAO, 7L));

        assertThatThrownBy(() -> service.escopoDeListagem(CPF, 8L))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Diferenca deliberada em relacao a contextoDe: sem lotacao nao vira global.
     * ADMIN_UNIDADE tambem cai aqui sem excecao — a tela mostra vazio e o aviso.
     */
    @Test
    void escopoDeListagemSemLotacaoFicaSemAcesso() {
        for (Roles role : new Roles[] {Roles.ADMIN_UNIDADE, Roles.RECEPCAO, Roles.ENFERMEIRO, Roles.MEDICO,
                Roles.USER}) {
            dadoUsuario(usuario(role, null));

            assertThat(service.escopoDeListagem(CPF, null).isSemAcesso()).as("%s", role).isTrue();
            assertThat(service.escopoDeListagem(CPF, 7L).isSemAcesso()).as("%s", role).isTrue();
        }
    }

    /** Excecao decidida pelo usuario: transporte opera o municipio inteiro. */
    @Test
    void escopoDeListagemMantemCoordTransporteSemLotacaoGlobal() {
        dadoUsuario(usuario(Roles.COORD_TRANSPORTE, null));

        assertThat(service.escopoDeListagem(CPF, null).isGlobal()).isTrue();
    }

    @Test
    void escopoDeListagemFalhaFechadaSemCpfOuSemUsuario() {
        assertThat(service.escopoDeListagem(null, null).isSemAcesso()).isTrue();

        when(userRepository.findByCpf(CPF)).thenReturn(Optional.empty());
        assertThat(service.escopoDeListagem(CPF, null).isSemAcesso()).isTrue();
    }

    // ------------------------------------------------------------------
    // Escopo da tela de Atendimentos por unidade executante (V110)
    // ------------------------------------------------------------------

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    private User usuarioComId(Roles role, Long id) {
        User u = usuario(role, null);
        u.setId(id);
        return u;
    }

    @Test
    void escopoDeAtendimentosDoAdminEMunicipal() {
        dadoUsuario(usuarioComId(Roles.ADMIN, 1L));

        assertThat(service.escopoDeAtendimentos(CPF).isMunicipal()).isTrue();
        verify(usuarioUnidadeExecutanteRepository, never()).findExecutantesConfiguradasDoUsuario(1L);
    }

    /** ADR 0013: USER sem executante configurada (lotacao ou lista) nao ve nada, nunca o municipio. */
    @Test
    void escopoDeAtendimentosDoUserSemExecutanteEVazio() {
        dadoUsuario(usuarioComId(Roles.USER, 1L));
        when(usuarioUnidadeExecutanteRepository.findExecutantesConfiguradasDoUsuario(1L)).thenReturn(List.of());

        var escopo = service.escopoDeAtendimentos(CPF);

        assertThat(escopo.isMunicipal()).isFalse();
        assertThat(escopo.isNenhuma()).isTrue();
    }

    @Test
    void escopoAtendimentoDistingueMunicipalDeNenhuma() {
        assertThat(UnidadeAcessoService.EscopoAtendimento.MUNICIPAL.isNenhuma()).isFalse();
        assertThat(UnidadeAcessoService.EscopoAtendimento.NENHUMA.isMunicipal()).isFalse();
        assertThat(new UnidadeAcessoService.EscopoAtendimento(List.of()).isNenhuma()).isTrue();
        assertThat(new UnidadeAcessoService.EscopoAtendimento(List.of(5L)).isNenhuma()).isFalse();
    }

    /** A consulta so devolve executante configurada: vinculo so a incompleta chega aqui vazio. */
    @Test
    void escopoDeAtendimentosDoUserVinculadoRestringeAsExecutantes() {
        dadoUsuario(usuarioComId(Roles.USER, 1L));
        when(usuarioUnidadeExecutanteRepository.findExecutantesConfiguradasDoUsuario(1L)).thenReturn(List.of(5L, 9L));

        var escopo = service.escopoDeAtendimentos(CPF);

        assertThat(escopo.isMunicipal()).isFalse();
        assertThat(escopo.executanteIds()).containsExactly(5L, 9L);
    }

    /** Quem tem ADMIN e USER e alterna para USER fica restrito; no ADMIN, nao. */
    @Test
    void escopoDeAtendimentosSegueOPerfilAtivo() {
        User u = usuarioComId(Roles.ADMIN, 1L);
        u.setPerfis(Set.of(Roles.ADMIN, Roles.USER));
        dadoUsuario(u);
        when(usuarioUnidadeExecutanteRepository.findExecutantesConfiguradasDoUsuario(1L)).thenReturn(List.of(5L));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(CPF, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThat(service.escopoDeAtendimentos(CPF).executanteIds()).containsExactly(5L);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(CPF, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThat(service.escopoDeAtendimentos(CPF).isMunicipal()).isTrue();
    }

    @Test
    void escopoDeAtendimentosSemCpfOuSemUsuarioEMunicipal() {
        assertThat(service.escopoDeAtendimentos(null).isMunicipal()).isTrue();

        when(userRepository.findByCpf(CPF)).thenReturn(Optional.empty());
        assertThat(service.escopoDeAtendimentos(CPF).isMunicipal()).isTrue();
    }
}
