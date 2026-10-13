package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * Relatorios de producao e exportacao (telas /relatorio e /relatorio/hospital):
 * planilhas de agendados, de pendentes, do GEL, "Agendamentos do Dia" e as duas
 * verificacoes de "ha dados".
 *
 * Fixture: um paciente na Unidade A (Ana), um na Unidade B (Bia) e um sem unidade
 * (Orfa). Cada um tem um pedido AGENDADO para hoje, um AGUARDANDO e um GEL, todos
 * numa especialidade de um grupo de relatorio criado aqui.
 *
 * O banco de teste nao e vazio: as saidas por grupo ficam isoladas pelo grupo da
 * fixture; GEL e producao sao globais, entao so os nomes da fixture sao conferidos.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class RelatorioEscopoUnidadeIT {

    @Autowired private ExcelService excelService;
    @Autowired private RelatorioProducaoService relatorioProducaoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    @Autowired private AgendamentoSolicitacaoRepository agendamentoSolicitacaoRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private String sufixo;
    private GrupoRelatorio grupo;
    private Especialidade especialidade;
    private Unidade unidadeA;
    private Unidade unidadeB;
    private String ana;
    private String bia;
    private String orfa;

    @BeforeEach
    void setUp() {
        sufixo = "_REL_IT_" + System.nanoTime();

        grupo = new GrupoRelatorio();
        grupo.setCodigo("GRUPO" + sufixo);
        grupo.setNome("Grupo Teste" + sufixo);
        grupo.setAtivo(true);
        grupo.setDirecionadoHospital(false);
        grupo = grupoRelatorioRepository.saveAndFlush(grupo);

        especialidade = new Especialidade();
        especialidade.setCodigo("ESP" + sufixo);
        especialidade.setNome("Especialidade Teste" + sufixo);
        especialidade.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        especialidade.setAtivo(true);
        especialidade.setVagas(0);
        especialidade.setGrupoRelatorio(grupo);
        especialidade = especialidadeRepository.saveAndFlush(especialidade);

        unidadeA = criarUnidade("A");
        unidadeB = criarUnidade("B");

        ana = criarPaciente("Ana", unidadeA);
        bia = criarPaciente("Bia", unidadeB);
        orfa = criarPaciente("Orfa", null);

        // O relatorio de producao le agendamento.especialidades: precisa vir do banco.
        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ fixture

    private Unidade criarUnidade(String letra) {
        Unidade u = new Unidade();
        u.setNome("Unidade " + letra + sufixo);
        u.setCodigo("U" + letra + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private User criarUsuario(Roles role, Unidade unidade) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(unidade);
        return userRepository.saveAndFlush(u);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    /** Devolve o nome gravado, que e o que as saidas dos relatorios carregam. */
    private String criarPaciente(String nome, Unidade unidade) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + cpfUnico());
        s.setUnidade(unidade);
        s = solicitacaoRepository.saveAndFlush(s);

        AgendamentoSolicitacao agendamento = new AgendamentoSolicitacao();
        agendamento.setSolicitacao(s);
        agendamento.setDataAgendada(LocalDate.now());
        agendamento = agendamentoSolicitacaoRepository.saveAndFlush(agendamento);

        SolicitacaoEspecialidade agendado = novoItem(s, StatusDaMarcacao.AGENDADO);
        agendado.setAgendamentoSolicitacao(agendamento);
        solicitacaoEspecialidadeRepository.saveAndFlush(agendado);
        solicitacaoEspecialidadeRepository.saveAndFlush(novoItem(s, StatusDaMarcacao.AGUARDANDO));
        solicitacaoEspecialidadeRepository.saveAndFlush(novoItem(s, StatusDaMarcacao.GEL));
        return s.getNomePaciente();
    }

    private SolicitacaoEspecialidade novoItem(Solicitacao solicitacao, StatusDaMarcacao status) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(solicitacao);
        se.setEspecialidadeSolicitada(especialidade);
        se.setEspecialidadeCodigoLegacy(especialidade.getCodigo());
        se.setStatus(status);
        return se;
    }

    // ------------------------------------------------------------------ saidas

    /** Nomes da fixture presentes em qualquer celula da planilha. */
    private List<String> nomesNaPlanilha(ByteArrayInputStream xlsx) throws IOException {
        byte[] bytes = xlsx.readAllBytes();
        List<String> encontrados = new ArrayList<>();
        if (bytes.length == 0) {
            return encontrados;
        }
        Set<String> celulas = new LinkedHashSet<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        celulas.add(formatter.formatCellValue(cell));
                    }
                }
            }
        }
        for (String nome : List.of(ana, bia, orfa)) {
            if (celulas.contains(nome)) {
                encontrados.add(nome);
            }
        }
        return encontrados;
    }

    private List<String> pendentes(User chamador, Long unidadeId) throws IOException {
        return nomesNaPlanilha(excelService.gerarPlanilhaAguardando(grupo.getCodigo(), unidadeId, chamador.getCpf()));
    }

    private List<String> agendados(User chamador, Long unidadeId) throws IOException {
        return nomesNaPlanilha(excelService.gerarPlanilhaAgendamentos(
                grupo.getCodigo(), LocalDate.now(), unidadeId, chamador.getCpf()));
    }

    private List<String> gel(User chamador, Long unidadeId) throws IOException {
        return nomesNaPlanilha(excelService.gerarPlanilhaGel(unidadeId, chamador.getCpf()));
    }

    private List<String> producao(User chamador, Long unidadeId) {
        return relatorioProducaoService.dadosDoRelatorio(unidadeId, chamador.getCpf()).stream()
                .map(dto -> dto.solicitacaoSimpleViewDTO().nomePaciente())
                .filter(nome -> nome != null && nome.endsWith(sufixo))
                .toList();
    }

    private boolean haPendentes(User chamador, Long unidadeId) {
        return excelService.haDadosParaRelatorioAguardando(grupo.getCodigo(), unidadeId, chamador.getCpf());
    }

    private boolean haAgendados(User chamador, Long unidadeId) {
        return excelService.haDadosParaRelatorio(grupo.getCodigo(), LocalDate.now(), unidadeId, chamador.getCpf());
    }

    private void assertVeTodasAsUnidades(User chamador) throws IOException {
        assertThat(pendentes(chamador, null)).as("pendentes").containsExactlyInAnyOrder(ana, bia, orfa);
        assertThat(agendados(chamador, null)).as("agendados").containsExactlyInAnyOrder(ana, bia, orfa);
        assertThat(gel(chamador, null)).as("gel").containsExactlyInAnyOrder(ana, bia, orfa);
        assertThat(producao(chamador, null)).as("producao").containsExactlyInAnyOrder(ana, bia, orfa);
        assertThat(haPendentes(chamador, null)).as("ha pendentes").isTrue();
        assertThat(haAgendados(chamador, null)).as("ha agendados").isTrue();
    }

    // ==================================================================
    // Quem ve todas as unidades (comportamento anterior ao escopo, preservado)
    // ==================================================================

    @Test
    @DisplayName("ADMIN recebe as duas unidades e a solicitacao sem unidade em todas as saidas")
    void adminVeTodasAsUnidades() throws IOException {
        assertVeTodasAsUnidades(criarUsuario(Roles.ADMIN, null));
    }

    @Test
    @DisplayName("GESTOR, mesmo lotado numa unidade, recebe todas as unidades")
    void gestorLotadoVeTodasAsUnidades() throws IOException {
        assertVeTodasAsUnidades(criarUsuario(Roles.GESTOR, unidadeA));
    }

    @Test
    @DisplayName("Perfil sem unidade de lotacao mantem a visao de todas as unidades (relatorio do hospital)")
    void perfilSemLotacaoVeTodasAsUnidades() throws IOException {
        assertVeTodasAsUnidades(criarUsuario(Roles.USER, null));
    }

    // ==================================================================
    // Quem tem unidade de lotacao so recebe a propria unidade
    // ==================================================================

    private void assertVeSo(User chamador, Long unidadeId, String esperado) throws IOException {
        assertThat(pendentes(chamador, unidadeId)).as("pendentes").containsExactly(esperado);
        assertThat(agendados(chamador, unidadeId)).as("agendados").containsExactly(esperado);
        assertThat(gel(chamador, unidadeId)).as("gel").containsExactly(esperado);
        assertThat(producao(chamador, unidadeId)).as("producao").containsExactly(esperado);
        assertThat(haPendentes(chamador, unidadeId)).as("ha pendentes").isTrue();
        assertThat(haAgendados(chamador, unidadeId)).as("ha agendados").isTrue();
    }

    private void assertNegado(User chamador, Long unidadeId, String trechoDaMensagem) {
        List<ThrowingCallable> saidas = List.of(
                () -> pendentes(chamador, unidadeId),
                () -> agendados(chamador, unidadeId),
                () -> gel(chamador, unidadeId),
                () -> producao(chamador, unidadeId),
                () -> haPendentes(chamador, unidadeId),
                () -> haAgendados(chamador, unidadeId));
        for (ThrowingCallable saida : saidas) {
            assertThatThrownBy(saida)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(trechoDaMensagem);
        }
    }

    @Test
    @DisplayName("ADMIN_UNIDADE so recebe a propria unidade: nem a outra, nem a solicitacao sem unidade")
    void adminUnidadeSoVeAPropriaUnidade() throws IOException {
        User adminUnidadeA = criarUsuario(Roles.ADMIN_UNIDADE, unidadeA);

        assertVeSo(adminUnidadeA, null, ana);
        assertVeSo(adminUnidadeA, unidadeA.getId(), ana);
    }

    @Test
    @DisplayName("Demais perfis com lotacao tambem ficam restritos a propria unidade")
    void perfisComLotacaoSoVeemAPropriaUnidade() throws IOException {
        for (Roles role : List.of(Roles.RECEPCAO, Roles.USER, Roles.MEDICO)) {
            assertVeSo(criarUsuario(role, unidadeB), null, bia);
        }
    }

    @Test
    @DisplayName("Pedir a unidade de outro e negado em todas as saidas, nunca atendido em silencio")
    void pedirOutraUnidadeENegado() {
        assertNegado(criarUsuario(Roles.ADMIN_UNIDADE, unidadeA), unidadeB.getId(), "outra unidade");
        assertNegado(criarUsuario(Roles.RECEPCAO, unidadeA), unidadeB.getId(), "outra unidade");
    }

    @Test
    @DisplayName("ADMIN_UNIDADE sem unidade de lotacao e negado, nunca recebe o municipio inteiro")
    void adminUnidadeSemLotacaoENegado() {
        assertNegado(criarUsuario(Roles.ADMIN_UNIDADE, null), null, "unidade de lota");
    }

    @Test
    @DisplayName("Unidade sem dados: a verificacao responde falso, sem revelar que outra unidade tem")
    void unidadeSemDadosNaoRevelaAOutra() throws IOException {
        User adminUnidadeVazia = criarUsuario(Roles.ADMIN_UNIDADE, criarUnidade("C"));

        assertThat(haPendentes(adminUnidadeVazia, null)).isFalse();
        assertThat(haAgendados(adminUnidadeVazia, null)).isFalse();
        assertThat(pendentes(adminUnidadeVazia, null)).isEmpty();
        assertThat(agendados(adminUnidadeVazia, null)).isEmpty();
        assertThat(gel(adminUnidadeVazia, null)).isEmpty();
        assertThat(producao(adminUnidadeVazia, null)).isEmpty();
    }

    @Test
    @DisplayName("ADMIN e GESTOR podem filtrar por uma unidade; sem filtro continuam vendo todas")
    void adminEGestorPodemFiltrarPorUnidade() throws IOException {
        assertVeSo(criarUsuario(Roles.ADMIN, null), unidadeB.getId(), bia);
        assertVeSo(criarUsuario(Roles.GESTOR, unidadeA), unidadeB.getId(), bia);
    }

    @Test
    @DisplayName("O escopo segue o perfil ATIVO: ADMIN atuando como ADMIN_UNIDADE fica restrito")
    void escopoSegueOPerfilAtivo() throws IOException {
        User duplo = criarUsuario(Roles.ADMIN, unidadeA);
        duplo.setPerfis(new LinkedHashSet<>(Set.of(Roles.ADMIN_UNIDADE)));
        duplo = userRepository.saveAndFlush(duplo);

        assertThat(pendentes(duplo, null)).containsExactlyInAnyOrder(ana, bia, orfa);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                duplo, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_UNIDADE"))));

        assertVeSo(duplo, null, ana);
    }
}
