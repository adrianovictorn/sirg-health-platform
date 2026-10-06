package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Autorizacao na borda HTTP dos endpoints que devolvem dados nominais de
 * pacientes. Varios deles ja foram {@code permitAll}: este teste existe para
 * que voltar a abri-los (no SecurityConfiguration ou tirando um @PreAuthorize)
 * quebre o build.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SegurancaEndpointsIT {

    private static final List<String> LISTAS_NOMINAIS = List.of(
            "/api/solicitacoes/buscar/por/status/usf",
            "/api/solicitacoes/buscar/por/urgentes",
            "/api/solicitacoes/buscar/por/agendados",
            "/api/solicitacoes/buscar/por/concluido",
            "/api/solicitacoes/pacientes/gel",
            "/api/fila-espera");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;

    private String sufixo;
    private Solicitacao solicitacao;
    private SolicitacaoEspecialidade pedido;

    @BeforeEach
    void setUp() {
        sufixo = "_SEG_IT_" + System.nanoTime();

        Especialidade cardiologia = new Especialidade();
        cardiologia.setCodigo("CARDIO" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(0);
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + cpfUnico());
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        pedido = new SolicitacaoEspecialidade();
        pedido.setSolicitacao(s);
        pedido.setEspecialidadeSolicitada(cardiologia);
        pedido.setEspecialidadeCodigoLegacy(cardiologia.getCodigo());
        pedido.setStatus(StatusDaMarcacao.AGUARDANDO);
        pedido.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> especs = new ArrayList<>();
        especs.add(pedido);
        s.setEspecialidades(especs);
        solicitacao = solicitacaoRepository.saveAndFlush(s);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    /** Usuario real no banco (os services o buscam pelo CPF) autenticado com o perfil dado. */
    private RequestPostProcessor como(Roles role) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u = userRepository.saveAndFlush(u);
        return user(u.getCpf()).roles(role.name());
    }

    private int statusDe(org.springframework.test.web.servlet.RequestBuilder requisicao) throws Exception {
        return mockMvc.perform(requisicao).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("Sem login, nenhuma lista nominal de pacientes responde")
    void semLoginAsListasNaoRespondem() throws Exception {
        List<String> protegidos = new ArrayList<>(LISTAS_NOMINAIS);
        protegidos.add("/api/solicitacoes/buscar/" + solicitacao.getId());
        protegidos.add("/api/solicitacoes/buscar/por/nome/cpf");
        protegidos.add("/api/agendamentos/pendentes/buscar");

        for (String url : protegidos) {
            assertThat(statusDe(get(url))).as(url).isIn(401, 403);
        }
    }

    @Test
    @DisplayName("A consulta publica do paciente por CPF continua aberta")
    void consultaPublicaContinuaAberta() throws Exception {
        mockMvc.perform(get("/api/solicitacoes/public/cpf/00000000000")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PACIENTE nao acessa as listas nominais")
    void pacienteNaoAcessaAsListas() throws Exception {
        RequestPostProcessor paciente = como(Roles.PACIENTE);

        for (String url : LISTAS_NOMINAIS) {
            assertThat(statusDe(get(url).with(paciente))).as(url).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("GESTOR le a fila e a ficha, mas nao altera nada")
    void gestorSoConsulta() throws Exception {
        RequestPostProcessor gestor = como(Roles.GESTOR);

        mockMvc.perform(get("/api/fila-espera").with(gestor)).andExpect(status().isOk());
        mockMvc.perform(get("/api/solicitacoes/" + solicitacao.getId()).with(gestor)).andExpect(status().isOk());

        assertThat(statusDe(put("/api/solicitacoes/" + solicitacao.getId()).with(gestor)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))).isEqualTo(403);
        assertThat(statusDe(delete("/api/agendamentos/999999999").with(gestor))).isEqualTo(403);
        assertThat(statusDe(put("/api/especialidades/" + pedido.getId()).with(gestor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"prioridade\":\"URGENTE\"}"))).isEqualTo(403);
    }

    @Test
    @DisplayName("USER continua trocando status/prioridade do pedido (telas de agenda)")
    void userContinuaAtualizandoPedido() throws Exception {
        mockMvc.perform(put("/api/especialidades/" + pedido.getId()).with(como(Roles.USER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"prioridade\":\"URGENTE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Recepcao sem unidade de lotacao recebe a fila vazia, nao um erro")
    void recepcaoSemLotacaoRecebeFilaVazia() throws Exception {
        mockMvc.perform(get("/api/fila-espera").with(como(Roles.RECEPCAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Busca livre da fila aceita caracteres especiais sem erro")
    void buscaDaFilaAceitaCaracteresEspeciais() throws Exception {
        RequestPostProcessor admin = como(Roles.ADMIN);

        for (String termo : List.of("%", "_", "a'b\"c", "\\", "a\u0000b", "\u0001")) {
            mockMvc.perform(get("/api/fila-espera").param("termo", termo).with(admin))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/fila-espera").param("termo", "Paciente").with(como(Roles.RECEPCAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Parametro invalido na fila devolve 400 com mensagem")
    void parametroInvalidoDevolve400() throws Exception {
        mockMvc.perform(get("/api/fila-espera").param("status", "GEL").with(como(Roles.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
