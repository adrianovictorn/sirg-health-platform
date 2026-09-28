package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.JwtAuthenticationFilter;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Perfil GESTOR e alternancia entre perfis (v1.7).
 *
 * O ponto sensivel aqui e que a alternancia NAO pode virar acumulo de poder: o
 * perfil ativo vale um de cada vez, e o backend reconfere a cada requisicao se
 * aquele perfil ainda esta concedido. Estes testes cobrem justamente as bordas
 * disso — perfil nao concedido, perfil revogado depois do token emitido, e
 * token antigo sem o claim.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class PerfilMultiploIT {

    @Autowired private UserService userService;
    @Autowired private TokenService tokenService;
    @Autowired private UnidadeAcessoService unidadeAcessoService;
    @Autowired private UserRepository userRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private JwtAuthenticationFilter jwtAuthenticationFilter;

    private Unidade unidade;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "_PERFIL_IT_" + System.nanoTime();

        unidade = new Unidade();
        unidade.setNome("Unidade" + sufixo);
        unidade.setCodigo("U" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private User criarUsuario(Roles principal, Set<Roles> extras, Unidade unidadeLotacao) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome("Usuario" + sufixo);
        u.setPassword("x");
        u.setRole(principal);
        Set<Roles> perfis = new LinkedHashSet<>();
        perfis.add(principal);
        if (extras != null) {
            perfis.addAll(extras);
        }
        u.setPerfis(perfis);
        u.setAtivo(true);
        u.setUnidade(unidadeLotacao);
        return userRepository.saveAndFlush(u);
    }

    /** Executa o filtro JWT com o token dado e devolve as authorities resultantes. */
    private List<String> autoridadesApos(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        jwtAuthenticationFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return List.of();
        }
        return auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
    }

    // ==================================================================
    // GESTOR
    // ==================================================================

    @Test
    @DisplayName("GESTOR enxerga todas as unidades, mesmo tendo unidade de lotacao")
    void gestorTemEscopoGlobalMesmoComUnidade() {
        User gestor = criarUsuario(Roles.GESTOR, null, unidade);

        var ctx = unidadeAcessoService.contextoDe(gestor.getCpf());

        assertThat(ctx.isGlobal())
                .as("o gestor compara unidades; restringi-lo a lotacao esvaziaria o papel")
                .isTrue();
        assertThat(unidadeAcessoService.isAcessoGlobal(gestor.getCpf())).isTrue();
    }

    @Test
    @DisplayName("Perfil restrito continua restrito — GESTOR nao afrouxou os outros")
    void perfilRestritoContinuaRestrito() {
        User operador = criarUsuario(Roles.ADMIN_UNIDADE, null, unidade);

        var ctx = unidadeAcessoService.contextoDe(operador.getCpf());

        assertThat(ctx.isGlobal()).isFalse();
        assertThat(ctx.id()).isEqualTo(unidade.getId());
    }

    // ==================================================================
    // Alternancia de perfil
    // ==================================================================

    @Test
    @DisplayName("Troca para um perfil concedido emite token com o perfil novo")
    void trocaParaPerfilConcedido() throws Exception {
        User usuario = criarUsuario(Roles.ADMIN_UNIDADE, Set.of(Roles.GESTOR), unidade);

        String token = userService.trocarPerfilAtivo(usuario.getCpf(), Roles.GESTOR);

        assertThat(tokenService.getPerfilAtivo(token)).isEqualTo(Roles.GESTOR);
        assertThat(autoridadesApos(token)).containsExactly("ROLE_GESTOR");
    }

    @Test
    @DisplayName("Troca para perfil NAO concedido e recusada")
    void trocaParaPerfilNaoConcedidoERecusada() {
        User usuario = criarUsuario(Roles.USER, null, unidade);

        assertThatThrownBy(() -> userService.trocarPerfilAtivo(usuario.getCpf(), Roles.ADMIN))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    @DisplayName("Perfil ativo do token nao vale mais depois de revogado, sem esperar expirar")
    void perfilRevogadoPerdeEfeitoImediatamente() throws Exception {
        User usuario = criarUsuario(Roles.USER, Set.of(Roles.GESTOR), unidade);
        String token = userService.trocarPerfilAtivo(usuario.getCpf(), Roles.GESTOR);
        assertThat(autoridadesApos(token)).containsExactly("ROLE_GESTOR");

        // O ADMIN tira o perfil GESTOR — o token na mao da pessoa continua valido.
        usuario.setPerfis(new LinkedHashSet<>(Set.of(Roles.USER)));
        userRepository.saveAndFlush(usuario);

        assertThat(autoridadesApos(token))
                .as("token antigo nao pode continuar dando um perfil que foi retirado")
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Token antigo, sem claim de perfil, cai no perfil principal")
    void tokenSemClaimCaiNoPrincipal() throws Exception {
        User usuario = criarUsuario(Roles.RECEPCAO, Set.of(Roles.GESTOR), unidade);

        // generateToken(user) e o caminho do login: emite com o principal.
        String token = tokenService.generateToken(usuario);

        assertThat(autoridadesApos(token)).containsExactly("ROLE_RECEPCAO");
    }

    @Test
    @DisplayName("Usuario legado, sem linhas em usuario_perfis, mantem o acesso do cargo")
    void usuarioLegadoSemPerfisMantemAcesso() throws Exception {
        User legado = criarUsuario(Roles.MEDICO, null, unidade);
        legado.setPerfis(new LinkedHashSet<>());
        legado = userRepository.saveAndFlush(legado);

        assertThat(legado.getPerfisConcedidos()).containsExactly(Roles.MEDICO);
        assertThat(legado.podeAssumir(Roles.MEDICO)).isTrue();
        assertThat(autoridadesApos(tokenService.generateToken(legado))).containsExactly("ROLE_MEDICO");
    }

    // ==================================================================
    // O escopo segue o perfil ATIVO, nao o principal
    // ==================================================================

    @Test
    @DisplayName("Alternar para ADMIN_UNIDADE restringe de verdade quem tambem e ADMIN")
    void escopoSegueOPerfilAtivoENaoOPrincipal() {
        User duplo = criarUsuario(Roles.ADMIN, Set.of(Roles.ADMIN_UNIDADE), unidade);

        // Sem contexto de seguranca: vale o principal (ADMIN) — global.
        assertThat(unidadeAcessoService.contextoDe(duplo.getCpf()).isGlobal()).isTrue();

        // Atuando como ADMIN_UNIDADE, o escopo tem que fechar na unidade dele.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(duplo, null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN_UNIDADE"))));

        var ctx = unidadeAcessoService.contextoDe(duplo.getCpf());
        assertThat(ctx.isGlobal())
                .as("se a alternancia nao estreitar o escopo, ela seria decorativa")
                .isFalse();
        assertThat(ctx.id()).isEqualTo(unidade.getId());
    }

    @Test
    @DisplayName("Cadastro com perfil secundario ADMIN_UNIDADE exige unidade de lotacao")
    void perfilSecundarioDeUnidadeExigeLotacao() {
        var dto = new io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserCreateDTO();
        dto.setCpf(cpfUnico());
        dto.setNome("Sem lotacao" + sufixo);
        dto.setPassword("senha123");
        dto.setCargo(Roles.GESTOR);
        dto.setPerfis(Set.of(Roles.ADMIN_UNIDADE));
        dto.setUnidadeId(null);

        assertThatThrownBy(() -> userService.criarUsuario(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unidade de lotação");
    }

    @Test
    @DisplayName("Perfil principal entra sozinho no conjunto concedido")
    void principalEntraSozinhoNoConjunto() {
        var dto = new io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO.UserCreateDTO();
        dto.setCpf(cpfUnico());
        dto.setNome("Gestor" + sufixo);
        dto.setPassword("senha123");
        dto.setCargo(Roles.GESTOR);
        dto.setPerfis(null); // formulario antigo, que so manda um cargo
        dto.setUnidadeId(null);

        assertThatCode(() -> {
            var criado = userService.criarUsuario(dto);
            assertThat(criado.perfis()).containsExactly(Roles.GESTOR);
        }).doesNotThrowAnyException();
    }
}
