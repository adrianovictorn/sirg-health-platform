package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @DisplayName("Webhook do WhatsApp: publico so em GET/POST do caminho exato, e 404 se desligado")
    void webhookWhatsAppSoAbreOCaminhoExato() throws Exception {
        // Sem app.whatsapp.* configurado (padrao): a rota e alcancavel sem login, mas recusa.
        assertThat(statusDe(get("/api/webhooks/whatsapp"))).isEqualTo(404);
        assertThat(statusDe(post("/api/webhooks/whatsapp")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))).isEqualTo(404);

        assertThat(statusDe(put("/api/webhooks/whatsapp"))).isIn(401, 403);
        assertThat(statusDe(delete("/api/webhooks/whatsapp"))).isIn(401, 403);
        assertThat(statusDe(get("/api/webhooks/outra"))).isIn(401, 403);
        assertThat(statusDe(get("/api/webhooks/whatsapp/extra"))).isIn(401, 403);
    }

    @Test
    @DisplayName("Painel do WhatsApp: so ADMIN le, liga o envio ou dispara mensagem")
    void painelDoWhatsAppSoParaAdmin() throws Exception {
        List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> rotas = List.of(
                get("/api/whatsapp/config"),
                get("/api/whatsapp/mensagens"),
                get("/api/whatsapp/indicadores"),
                put("/api/whatsapp/config/envio").contentType(MediaType.APPLICATION_JSON).content("{\"ligado\":true}"),
                post("/api/whatsapp/mensagens/reenviar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agendamentoId\":1,\"tipo\":\"CONFIRMACAO\"}"),
                post("/api/whatsapp/lembretes/executar"));

        for (var rota : rotas) {
            assertThat(statusDe(rota)).as("sem login").isIn(401, 403);
        }
        for (Roles role : List.of(Roles.GESTOR, Roles.ADMIN_UNIDADE, Roles.USER, Roles.RECEPCAO, Roles.PACIENTE)) {
            RequestPostProcessor usuario = como(role);
            for (var rota : rotas) {
                assertThat(statusDe(rota.with(usuario))).as(role.name()).isEqualTo(403);
            }
        }
    }

    @Test
    @DisplayName("Painel do WhatsApp sem credenciais: ADMIN ve 'nao configurado' e nao consegue ligar nem disparar")
    void painelDoWhatsAppSemCredenciais() throws Exception {
        RequestPostProcessor admin = como(Roles.ADMIN);

        mockMvc.perform(get("/api/whatsapp/config").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(false))
                .andExpect(jsonPath("$.envioLigado").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        mockMvc.perform(get("/api/whatsapp/mensagens").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/api/whatsapp/indicadores").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber());

        mockMvc.perform(put("/api/whatsapp/config/envio").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ligado\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(post("/api/whatsapp/lembretes/executar").with(admin))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/whatsapp/mensagens/reenviar").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agendamentoId\":1,\"tipo\":\"CONFIRMACAO\"}"))
                .andExpect(status().isConflict());
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

    // ------------------------------------------------------------------
    // Custos (V105+): valor em reais so para ADMIN e GESTOR
    // ------------------------------------------------------------------

    private List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> leiturasDeCusto() {
        return List.of(
                get("/api/custos/especialidades"),
                get("/api/custos/painel"),
                get("/api/custos/tetos"));
    }

    private List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> escritasDeCusto() {
        return List.of(
                put("/api/custos/especialidades/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoSus\":null,\"valorUnitario\":1.00}"),
                multipart("/api/custos/importacao").file(new org.springframework.mock.web.MockMultipartFile(
                        "arquivo", "precos.csv", "text/csv", "CODIGO SUS;VALOR UNIT\n0202010473;1,85\n".getBytes())),
                post("/api/custos/importacao/confirmar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[{\"especialidadeId\":1,\"codigoSus\":\"0202010473\",\"valorUnitario\":1.85}]}"),
                post("/api/custos/tetos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadeIds\":[1],\"grupoEspecialidadesId\":1,\"periodo\":\"2026-10\",\"valorTotal\":10.00}"),
                put("/api/custos/tetos/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorTotal\":10.00,\"ativo\":true}"));
    }

    @Test
    @DisplayName("Custos: sem login nada responde; perfis de unidade recebem 403 em tudo")
    void custosFechadosParaPerfisDeUnidade() throws Exception {
        List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> rotas = new ArrayList<>();
        rotas.addAll(leiturasDeCusto());
        rotas.addAll(escritasDeCusto());
        for (var rota : rotas) {
            assertThat(statusDe(rota)).as("sem login").isIn(401, 403);
        }

        for (Roles role : List.of(Roles.ADMIN_UNIDADE, Roles.RECEPCAO, Roles.ENFERMEIRO, Roles.MEDICO,
                Roles.USER, Roles.PACIENTE, Roles.COORD_TRANSPORTE)) {
            RequestPostProcessor usuario = como(role);
            // Builders novos a cada perfil: with() acumula no mesmo builder.
            List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> doPerfil = new ArrayList<>();
            doPerfil.addAll(leiturasDeCusto());
            doPerfil.addAll(escritasDeCusto());
            for (var rota : doPerfil) {
                assertThat(statusDe(rota.with(usuario))).as(role.name()).isEqualTo(403);
            }
        }
    }

    @Test
    @DisplayName("Custos: GESTOR le o painel, os precos e os tetos, mas nao altera nada")
    void gestorSoLeCustos() throws Exception {
        RequestPostProcessor gestor = como(Roles.GESTOR);

        for (var rota : leiturasDeCusto()) {
            mockMvc.perform(rota.with(gestor)).andExpect(status().isOk());
        }
        for (var rota : escritasDeCusto()) {
            assertThat(statusDe(rota.with(gestor))).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("Custos: ADMIN grava o preco, e ele NAO aparece em nenhuma resposta fora de /api/custos")
    void precoNaoVazaForaDeCustos() throws Exception {
        RequestPostProcessor admin = como(Roles.ADMIN);
        Long especialidadeId = especialidadeRepository.findByCodigo("CARDIO" + sufixo).orElseThrow().getId();

        mockMvc.perform(put("/api/custos/especialidades/" + especialidadeId).with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoSus\":\"301010072\",\"valorUnitario\":987.65}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorUnitario").value(987.65))
                .andExpect(jsonPath("$.codigoSus").value("0301010072"));

        // Rotas que os perfis de unidade leem no dia a dia. Nem o ADMIN recebe valor por elas.
        for (RequestPostProcessor usuario : List.of(admin, como(Roles.RECEPCAO))) {
            for (String url : List.of(
                    "/api/catalog/especialidades/listar",
                    "/api/solicitacoes/" + solicitacao.getId(),
                    "/api/fila-espera",
                    "/api/agendamentos/pendentes/buscar?termo=Paciente")) {
                String corpo = mockMvc.perform(get(url).with(usuario))
                        .andReturn().getResponse().getContentAsString();
                assertThat(corpo).as(url)
                        .doesNotContain("987.65")
                        .doesNotContainIgnoringCase("valorUnitario")
                        .doesNotContainIgnoringCase("codigoSus")
                        .doesNotContain("0301010072");
            }
        }
        // Garante que a checagem acima olhou para uma resposta que de fato traz a especialidade.
        assertThat(mockMvc.perform(get("/api/catalog/especialidades/listar").with(admin))
                .andReturn().getResponse().getContentAsString()).contains("Cardiologia" + sufixo);
    }

    @Test
    @DisplayName("Custos: formato invalido devolve 400 com mensagem, nunca 500")
    void custoInvalidoDevolve400() throws Exception {
        RequestPostProcessor admin = como(Roles.ADMIN);
        Long especialidadeId = especialidadeRepository.findByCodigo("CARDIO" + sufixo).orElseThrow().getId();

        for (String corpo : List.of(
                "{\"codigoSus\":\"123\",\"valorUnitario\":1.00}",
                "{\"codigoSus\":null,\"valorUnitario\":-1.00}",
                "{\"codigoSus\":null,\"valorUnitario\":1.005}")) {
            mockMvc.perform(put("/api/custos/especialidades/" + especialidadeId).with(admin)
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists());
        }
        mockMvc.perform(get("/api/custos/tetos").param("periodo", "2026-13").with(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(put("/api/custos/especialidades/999999999").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valorUnitario\":1.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Pre-verificacao do agendamento: so quem opera a ficha")
    void verificacaoDoAgendamentoSoParaQuemOperaAFicha() throws Exception {
        String url = "/api/agendamentos/" + solicitacao.getId() + "/verificar";
        String corpo = "{\"examesSelecionados\":[\"CARDIO" + sufixo + "\"],\"dataAgendada\":\""
                + java.time.LocalDate.now() + "\",\"turno\":\"MANHA\"}";

        assertThat(statusDe(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo)))
                .as("sem login").isIn(401, 403);
        for (Roles role : List.of(Roles.GESTOR, Roles.PACIENTE)) {
            assertThat(statusDe(post(url).with(como(role)).contentType(MediaType.APPLICATION_JSON).content(corpo)))
                    .as(role.name()).isEqualTo(403);
        }

        // Ficha sem unidade (orfa): quem opera a tela consulta normalmente.
        mockMvc.perform(post(url).with(como(Roles.RECEPCAO)).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].codigo").value("CARDIO" + sufixo))
                .andExpect(jsonPath("$.itens[0].podeAgendar").value(true))
                .andExpect(jsonPath("$.bloqueioDoLote").doesNotExist());
    }

    // ------------------------------------------------------------------
    // Indicadores gerenciais: so ADMIN e GESTOR
    // ------------------------------------------------------------------

    /** Builders novos a cada chamada: with() acumula no mesmo builder. */
    private List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> indicadoresGerenciais() {
        return List.of(
                get("/api/indicadores/fila/envelhecimento"),
                get("/api/indicadores/fila/balanco"),
                get("/api/indicadores/agendamentos/antecedencia"),
                get("/api/indicadores/cotas/utilizacao"),
                get("/api/indicadores/cotas/ocupacao-profissional"),
                get("/api/indicadores/whatsapp/alcance"),
                get("/api/indicadores/whatsapp/telefone-invalido"),
                get("/api/custos/indicadores/teto"),
                get("/api/custos/indicadores/faltas"),
                get("/api/custos/indicadores/cobertura"),
                get("/api/custos/indicadores/evolucao"));
    }

    @Test
    @DisplayName("Indicadores gerenciais: sem login nada responde; so ADMIN e GESTOR leem")
    void indicadoresGerenciaisSoParaGestao() throws Exception {
        for (var rota : indicadoresGerenciais()) {
            assertThat(statusDe(rota)).as("sem login").isIn(401, 403);
        }

        for (Roles role : List.of(Roles.ADMIN, Roles.GESTOR)) {
            RequestPostProcessor usuario = como(role);
            for (var rota : indicadoresGerenciais()) {
                mockMvc.perform(rota.with(usuario)).andExpect(status().isOk());
            }
        }

        // Inclui quem hoje alcanca /api/fechamento (RECEPCAO, MEDICO, PACIENTE...) e
        // COORD_TRANSPORTE, que tem visao global nas listagens: aqui nenhum deles entra.
        for (Roles role : List.of(Roles.ADMIN_UNIDADE, Roles.RECEPCAO, Roles.ENFERMEIRO, Roles.MEDICO,
                Roles.USER, Roles.PACIENTE, Roles.COORD_TRANSPORTE)) {
            RequestPostProcessor usuario = como(role);
            for (var rota : indicadoresGerenciais()) {
                assertThat(statusDe(rota.with(usuario))).as(role.name()).isEqualTo(403);
            }
        }
    }

    @Test
    @DisplayName("Indicadores gerenciais: periodo invalido devolve 400 com mensagem, nunca 500")
    void indicadoresComPeriodoInvalidoDevolvem400() throws Exception {
        RequestPostProcessor admin = como(Roles.ADMIN);

        for (String rota : List.of(
                "/api/indicadores/agendamentos/antecedencia",
                "/api/indicadores/cotas/utilizacao",
                "/api/indicadores/whatsapp/alcance",
                "/api/custos/indicadores/faltas",
                "/api/custos/indicadores/teto")) {
            for (String[] periodo : List.of(
                    new String[] {"2026-13-01", "2026-12-31"},
                    new String[] {"01/10/2026", "2026-10-31"},
                    new String[] {"2026-10-31", "2026-10-01"},
                    new String[] {"2025-01-01", "2026-10-01"})) {
                mockMvc.perform(get(rota).param("de", periodo[0]).param("ate", periodo[1]).with(admin))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").exists());
            }
        }
    }

    @Test
    @DisplayName("WhatsApp: GESTOR le os agregados gerenciais, mas o painel operacional continua so do ADMIN")
    void gestorLeAlcanceMasNaoOPainelDoWhatsApp() throws Exception {
        RequestPostProcessor gestor = como(Roles.GESTOR);

        mockMvc.perform(get("/api/indicadores/whatsapp/alcance").with(gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamentos").isNumber())
                .andExpect(jsonPath("$.configurado").isBoolean())
                // So contagens: nada que identifique mensagem, agendamento ou paciente.
                .andExpect(jsonPath("$.telefoneFinal").doesNotExist())
                .andExpect(jsonPath("$.mensagens").doesNotExist());
        assertThat(statusDe(get("/api/whatsapp/indicadores").with(gestor))).isEqualTo(403);
        assertThat(statusDe(get("/api/whatsapp/mensagens").with(gestor))).isEqualTo(403);
    }

    @Test
    @DisplayName("Fechamento (caracterizacao): os indicadores antigos seguem respondendo a ADMIN e GESTOR e vedados a ADMIN_UNIDADE")
    void indicadoresAntigosSeguemComoEstavam() throws Exception {
        String hoje = java.time.LocalDate.now().toString();
        List<String> rotas = List.of(
                "/api/fechamento/total/agendados/dia?data=" + hoje,
                "/api/fechamento/total/pacientes/novos/dia?data=" + hoje,
                "/api/fechamento/total/solicitacao/especialidade/dia?data=" + hoje,
                "/api/fechamento/total/por/especialidade/por/tempo?inicio=2026-01-01&intervalo=2026-02-01",
                "/api/fechamento/especialidades/pendentes/top10",
                "/api/fechamento/profissionais/ranking",
                "/api/fechamento/tempo-espera/geral",
                "/api/fechamento/tempo-espera/por-especialidade");

        for (Roles role : List.of(Roles.ADMIN, Roles.GESTOR)) {
            RequestPostProcessor usuario = como(role);
            for (String rota : rotas) {
                mockMvc.perform(get(rota).with(usuario)).andExpect(status().isOk());
            }
        }
        RequestPostProcessor adminUnidade = como(Roles.ADMIN_UNIDADE);
        for (String rota : rotas) {
            assertThat(statusDe(get(rota).with(adminUnidade))).as(rota).isEqualTo(403);
        }

        // Nenhum valor em reais sai pelos indicadores antigos, que outros perfis alcancam.
        RequestPostProcessor recepcao = como(Roles.RECEPCAO);
        for (String rota : rotas) {
            String corpo = mockMvc.perform(get(rota).with(recepcao)).andReturn().getResponse().getContentAsString();
            assertThat(corpo).as(rota)
                    .doesNotContainIgnoringCase("valorUnitario")
                    .doesNotContainIgnoringCase("valorTotal")
                    .doesNotContainIgnoringCase("valorUtilizado")
                    .doesNotContainIgnoringCase("custoMedio");
        }
    }
}
