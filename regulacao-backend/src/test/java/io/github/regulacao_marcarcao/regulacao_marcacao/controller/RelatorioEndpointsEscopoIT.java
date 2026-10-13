package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Escopo por unidade na borda HTTP dos relatorios de producao e exportacao
 * ({@code /api/exportar/**} e {@code /api/relatorio/producao/**}): quem so
 * enxerga a propria unidade recebe 403 com mensagem ao pedir outra, e nunca 500.
 * O conteudo de cada relatorio e coberto em {@code RelatorioEscopoUnidadeIT}.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RelatorioEndpointsEscopoIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UnidadeRepository unidadeRepository;

    private String sufixo;
    private Unidade unidadeA;
    private Unidade unidadeB;

    @BeforeEach
    void setUp() {
        sufixo = "_REL_HTTP_IT_" + System.nanoTime();
        unidadeA = criarUnidade("A");
        unidadeB = criarUnidade("B");
    }

    private Unidade criarUnidade(String letra) {
        Unidade u = new Unidade();
        u.setNome("Unidade " + letra + sufixo);
        u.setCodigo("U" + letra + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    /** Usuario real no banco (os services o buscam pelo CPF) autenticado com o perfil dado. */
    private RequestPostProcessor como(Roles role, Unidade unidade) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(unidade);
        u = userRepository.saveAndFlush(u);
        return user(u.getCpf()).roles(role.name());
    }

    /** As sete rotas, com um grupo que nao existe: o escopo e decidido antes de qualquer consulta. */
    private List<MockHttpServletRequestBuilder> rotas() {
        String grupo = "GRUPO" + sufixo;
        String hoje = LocalDate.now().toString();
        return List.of(
                get("/api/exportar/planilha").param("grupo", grupo).param("data", hoje),
                get("/api/exportar/verificar-dados").param("grupo", grupo).param("data", hoje),
                get("/api/exportar/planilha-aguardando").param("grupo", grupo),
                get("/api/exportar/verificar-dados-aguardando").param("grupo", grupo),
                get("/api/exportar/planilha-gel"),
                get("/api/relatorio/producao/listar"),
                get("/api/relatorio/producao/excel"));
    }

    /**
     * Rotas que respondem 200 neste ambiente. O Excel de producao fica de fora: ele le
     * {@code images/brasao.png}, que nao existe no classpath, e falha para qualquer perfil
     * depois de o escopo ja ter sido decidido.
     */
    private List<MockHttpServletRequestBuilder> rotasAtendidas() {
        List<MockHttpServletRequestBuilder> todas = rotas();
        return todas.subList(0, todas.size() - 1);
    }

    @Test
    @DisplayName("Sem login, nenhum relatorio responde")
    void semLoginNaoResponde() throws Exception {
        for (MockHttpServletRequestBuilder rota : rotas()) {
            mockMvc.perform(rota).andExpect(status().is4xxClientError());
        }
    }

    @Test
    @DisplayName("ADMIN_UNIDADE pedindo outra unidade recebe 403 com mensagem em todas as rotas")
    void adminUnidadePedindoOutraUnidadeRecebe403() throws Exception {
        RequestPostProcessor adminUnidadeA = como(Roles.ADMIN_UNIDADE, unidadeA);
        for (MockHttpServletRequestBuilder rota : rotas()) {
            mockMvc.perform(rota.param("unidadeId", unidadeB.getId().toString()).with(adminUnidadeA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Acesso negado aos dados de outra unidade."));
        }
    }

    @Test
    @DisplayName("ADMIN_UNIDADE sem unidade de lotacao recebe 403 com mensagem, nunca 500 nem dado global")
    void adminUnidadeSemLotacaoRecebe403() throws Exception {
        RequestPostProcessor semLotacao = como(Roles.ADMIN_UNIDADE, null);
        for (MockHttpServletRequestBuilder rota : rotas()) {
            mockMvc.perform(rota.with(semLotacao))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Test
    @DisplayName("ADMIN_UNIDADE na propria unidade, ADMIN e GESTOR sao atendidos")
    void quemTemAcessoEAtendido() throws Exception {
        List<RequestPostProcessor> atendidos = List.of(
                como(Roles.ADMIN_UNIDADE, unidadeA),
                como(Roles.ADMIN, null),
                como(Roles.GESTOR, unidadeA));
        for (RequestPostProcessor usuario : atendidos) {
            for (MockHttpServletRequestBuilder rota : rotasAtendidas()) {
                mockMvc.perform(rota.with(usuario)).andExpect(status().isOk());
            }
        }
    }

    @Test
    @DisplayName("ADMIN e GESTOR podem pedir qualquer unidade")
    void adminEGestorPodemPedirQualquerUnidade() throws Exception {
        for (RequestPostProcessor usuario : List.of(como(Roles.ADMIN, null), como(Roles.GESTOR, unidadeA))) {
            for (MockHttpServletRequestBuilder rota : rotasAtendidas()) {
                mockMvc.perform(rota.param("unidadeId", unidadeB.getId().toString()).with(usuario))
                        .andExpect(status().isOk());
            }
        }
    }
}
