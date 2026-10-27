package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.relatorio.PlanilhaAgendadosFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.UnidadeExecutanteUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * Filtros da planilha de agendados de /relatorio: periodo, unidade solicitante,
 * unidade executante, profissional executante e "sem executante", mais a coluna
 * PROFISSIONAL EXECUTANTE.
 *
 * Fixture (todos AGENDADO no grupo criado aqui; {@code dia} no futuro, isolado):
 * - Ana: Unidade A, dia, no local da executante X, executante escolhido = Dr. Um.
 * - Bia: Unidade B, dia, em outro local, executante pela cota = Dr. Dois.
 * - Fabio: Unidade A, dia, escolhido Dr. Um E cota Dr. Dois (o escolhido vence).
 * - Caio: Unidade A, dia+5, sem executante.
 * - Duda: Unidade A, dia-1 (fora do periodo).
 * - Edu: Unidade A, dia+6 (fora do periodo).
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class PlanilhaAgendadosFiltrosIT {

    private static final int COLUNA_NOME = 0;
    private static final int COLUNA_EXECUTANTE = 8;
    private static final int LINHA_CABECALHO = 6;

    @Autowired private ExcelService excelService;
    @Autowired private UnidadeExecutanteService parametrizacao;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository itemRepository;
    @Autowired private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private LocalAgendamentoRepository localRepository;
    @Autowired private ProfissionalRepository profissionalRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private String sufixo;
    private LocalDate dia;
    private GrupoRelatorio grupo;
    private Especialidade exame;
    private Especialidade outroExame;
    private Unidade unidadeA;
    private Unidade unidadeB;
    private Unidade executanteX;
    private LocalAgendamento localX;
    private LocalAgendamento localOutro;
    private Profissional drUm;
    private Profissional drDois;
    private CotaUnidade cotaDrDois;
    private User admin;

    private String ana;
    private String bia;
    private String fabio;
    private String caio;
    private String duda;
    private String edu;

    @BeforeEach
    void setUp() {
        sufixo = "_PLAN_AG_IT_" + System.nanoTime();
        dia = LocalDate.now().plusYears(3).withDayOfMonth(10);

        grupo = new GrupoRelatorio();
        grupo.setCodigo("GRUPO" + sufixo);
        grupo.setNome("Grupo" + sufixo);
        grupo.setAtivo(true);
        grupo.setDirecionadoHospital(false);
        grupo = grupoRelatorioRepository.saveAndFlush(grupo);

        exame = especialidade("EXAME");
        outroExame = especialidade("OUTRO");

        unidadeA = unidade("A", TipoUnidadeEnum.SOLICITANTE);
        unidadeB = unidade("B", TipoUnidadeEnum.SOLICITANTE);
        executanteX = unidade("X", TipoUnidadeEnum.EXECUTANTE);
        localX = local("Local X");
        localOutro = local("Local Outro");
        parametrizacao.atualizar(executanteX.getId(), new UnidadeExecutanteUpdateDTO(List.of(grupo.getId()),
                List.of(), List.of(localX.getId()), List.of()));

        drUm = profissional("Dr. Um");
        drDois = profissional("Dr. Dois");
        cotaDrDois = cota(drDois);
        admin = usuario(Roles.ADMIN, null);

        ana = agendar("Ana", unidadeA, dia, localX, drUm, null);
        bia = agendar("Bia", unidadeB, dia, localOutro, null, cotaDrDois);
        fabio = agendar("Fabio", unidadeA, dia, localOutro, drUm, cotaDrDois);
        caio = agendar("Caio", unidadeA, dia.plusDays(5), localOutro, null, null);
        duda = agendar("Duda", unidadeA, dia.minusDays(1), localOutro, null, null);
        edu = agendar("Edu", unidadeA, dia.plusDays(6), localOutro, null, null);

        em.flush();
        em.clear();
    }

    // ------------------------------------------------------------------ fixture

    private Especialidade especialidade(String prefixo) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        e.setGrupoRelatorio(grupo);
        return especialidadeRepository.saveAndFlush(e);
    }

    private Unidade unidade(String letra, TipoUnidadeEnum tipo) {
        Unidade u = new Unidade();
        u.setNome("Unidade " + letra + sufixo);
        u.setCodigo("U" + letra + sufixo);
        u.setAtivo(true);
        u.setTipo(tipo);
        return unidadeRepository.saveAndFlush(u);
    }

    private LocalAgendamento local(String nome) {
        LocalAgendamento l = new LocalAgendamento();
        l.setNomeLocal(nome + sufixo);
        return localRepository.saveAndFlush(l);
    }

    private Profissional profissional(String nome) {
        Profissional p = new Profissional();
        p.setNome(nome + sufixo);
        p.setAtivo(true);
        return profissionalRepository.saveAndFlush(p);
    }

    private CotaUnidade cota(Profissional executante) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidadeB);
        c.setEspecialidade(exame);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(String.format("%04d-%02d", dia.getYear(), dia.getMonthValue()));
        c.setQuantidadeTotal(10);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        c.setProfissionalExecutante(executante);
        return cotaRepository.saveAndFlush(c);
    }

    private User usuario(Roles role, Unidade unidade) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo + System.nanoTime());
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(unidade);
        return userRepository.saveAndFlush(u);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private Solicitacao paciente(String nome, Unidade unidade) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + cpfUnico());
        s.setUnidade(unidade);
        return solicitacaoRepository.saveAndFlush(s);
    }

    private AgendamentoSolicitacao agendamento(Solicitacao s, LocalDate data, LocalAgendamento local) {
        AgendamentoSolicitacao ag = new AgendamentoSolicitacao();
        ag.setSolicitacao(s);
        ag.setDataAgendada(data);
        ag.setLocalAgendamento(local);
        return agendamentoRepository.saveAndFlush(ag);
    }

    private void item(Solicitacao s, AgendamentoSolicitacao ag, Especialidade e, Profissional escolhido,
            CotaUnidade cota) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(e);
        se.setEspecialidadeCodigoLegacy(e.getCodigo());
        se.setStatus(StatusDaMarcacao.AGENDADO);
        se.setAgendamentoSolicitacao(ag);
        se.setProfissionalExecutante(escolhido);
        se.setCotaUnidade(cota);
        itemRepository.saveAndFlush(se);
    }

    private String agendar(String nome, Unidade unidade, LocalDate data, LocalAgendamento local,
            Profissional escolhido, CotaUnidade cota) {
        Solicitacao s = paciente(nome, unidade);
        item(s, agendamento(s, data, local), exame, escolhido, cota);
        return s.getNomePaciente();
    }

    // ------------------------------------------------------------------ saidas

    private PlanilhaAgendadosFiltroDTO noDia() {
        return PlanilhaAgendadosFiltroDTO.porData(grupo.getCodigo(), dia, null);
    }

    private PlanilhaAgendadosFiltroDTO noPeriodo(Long unidadeId, Long executanteId, Long profissionalId,
            boolean semExecutante) {
        return new PlanilhaAgendadosFiltroDTO(grupo.getCodigo(), null, dia, dia.plusDays(5), unidadeId,
                executanteId, profissionalId, semExecutante);
    }

    /** Nome do paciente -> celula PROFISSIONAL EXECUTANTE, na ordem da planilha. */
    private Map<String, String> linhas(ByteArrayInputStream xlsx) throws IOException {
        byte[] bytes = xlsx.readAllBytes();
        Map<String, String> linhas = new LinkedHashMap<>();
        if (bytes.length == 0) {
            return linhas;
        }
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(formatter.formatCellValue(sheet.getRow(LINHA_CABECALHO).getCell(COLUNA_EXECUTANTE)))
                    .isEqualTo("PROFISSIONAL EXECUTANTE");
            for (Row row : sheet) {
                if (row.getRowNum() <= LINHA_CABECALHO) {
                    continue;
                }
                linhas.put(formatter.formatCellValue(row.getCell(COLUNA_NOME)),
                        formatter.formatCellValue(row.getCell(COLUNA_EXECUTANTE)));
            }
        }
        return linhas;
    }

    private Map<String, String> planilha(PlanilhaAgendadosFiltroDTO filtro, User chamador) throws IOException {
        return linhas(excelService.gerarPlanilhaAgendamentos(filtro, chamador.getCpf()));
    }

    private boolean haDados(PlanilhaAgendadosFiltroDTO filtro, User chamador) {
        return excelService.haDadosParaRelatorio(filtro, chamador.getCpf());
    }

    // ------------------------------------------------------------------ testes

    @Test
    @DisplayName("Sem filtros novos: mesmas linhas da assinatura antiga (compatibilidade)")
    void semFiltrosNovosMesmoResultado() throws IOException {
        Map<String, String> antiga = linhas(excelService.gerarPlanilhaAgendamentos(
                grupo.getCodigo(), dia, null, admin.getCpf()));
        Map<String, String> nova = planilha(noDia(), admin);

        assertThat(antiga.keySet()).containsExactlyInAnyOrder(ana, bia, fabio);
        assertThat(nova).isEqualTo(antiga);
        assertThat(excelService.haDadosParaRelatorio(grupo.getCodigo(), dia, null, admin.getCpf())).isTrue();
    }

    @Test
    @DisplayName("Periodo inclui as duas pontas e deixa de fora o dia anterior e o posterior")
    void periodoInclusivo() throws IOException {
        assertThat(planilha(noPeriodo(null, null, null, false), admin).keySet())
                .containsExactlyInAnyOrder(ana, bia, fabio, caio)
                .doesNotContain(duda, edu);
    }

    @Test
    @DisplayName("Linhas ordenadas por data agendada")
    void ordenadoPorData() throws IOException {
        List<String> nomes = List.copyOf(planilha(noPeriodo(null, null, null, false), admin).keySet());
        assertThat(nomes.get(nomes.size() - 1)).isEqualTo(caio);
    }

    @Test
    @DisplayName("Coluna PROFISSIONAL EXECUTANTE: escolhido, da cota, escolhido vence a cota, 'Não informado'")
    void colunaProfissionalExecutante() throws IOException {
        Map<String, String> linhas = planilha(noPeriodo(null, null, null, false), admin);

        assertThat(linhas.get(ana)).isEqualTo(drUm.getNome());
        assertThat(linhas.get(bia)).isEqualTo(drDois.getNome());
        assertThat(linhas.get(fabio)).isEqualTo(drUm.getNome());
        assertThat(linhas.get(caio)).isEqualTo(ExcelService.EXECUTANTE_NAO_INFORMADO);
    }

    @Test
    @DisplayName("Linha com itens com e sem executante mostra os nomes e 'Não informado'")
    void linhaMista() throws IOException {
        Solicitacao s = paciente("Gabi", unidadeA);
        AgendamentoSolicitacao ag = agendamento(s, dia, localOutro);
        item(s, ag, exame, drUm, null);
        item(s, ag, outroExame, null, null);
        em.flush();
        em.clear();

        assertThat(planilha(noDia(), admin).get(s.getNomePaciente()))
                .isEqualTo(drUm.getNome() + ", " + ExcelService.EXECUTANTE_NAO_INFORMADO);
    }

    @Test
    @DisplayName("Filtro por unidade solicitante")
    void filtroUnidadeSolicitante() throws IOException {
        assertThat(planilha(noPeriodo(unidadeB.getId(), null, null, false), admin).keySet())
                .containsExactly(bia);
    }

    @Test
    @DisplayName("Filtro por unidade executante: so o item no local da executante")
    void filtroUnidadeExecutante() throws IOException {
        assertThat(planilha(noPeriodo(null, executanteX.getId(), null, false), admin).keySet())
                .containsExactly(ana);
    }

    @Test
    @DisplayName("Filtro por profissional executante (escolhido e pela cota)")
    void filtroProfissional() throws IOException {
        assertThat(planilha(noPeriodo(null, null, drUm.getId(), false), admin).keySet())
                .containsExactlyInAnyOrder(ana, fabio);
        assertThat(planilha(noPeriodo(null, null, drDois.getId(), false), admin).keySet())
                .containsExactly(bia);
    }

    @Test
    @DisplayName("'Sem executante' traz so itens sem profissional registrado")
    void filtroSemExecutante() throws IOException {
        assertThat(planilha(noPeriodo(null, null, null, true), admin).keySet()).containsExactly(caio);
    }

    @Test
    @DisplayName("Verificacao e planilha concordam com os mesmos filtros")
    void verificacaoConcordaComPlanilha() throws IOException {
        List<PlanilhaAgendadosFiltroDTO> filtros = List.of(
                noPeriodo(null, null, null, false),
                noPeriodo(unidadeB.getId(), null, drUm.getId(), false),
                noPeriodo(null, executanteX.getId(), null, true),
                noPeriodo(null, null, null, true));
        for (PlanilhaAgendadosFiltroDTO filtro : filtros) {
            assertThat(haDados(filtro, admin)).as(filtro.toString())
                    .isEqualTo(!planilha(filtro, admin).isEmpty());
        }
    }

    @Test
    @DisplayName("Item nao AGENDADO nao faz a verificacao responder que ha dados")
    void verificacaoContaSoAgendado() {
        Solicitacao s = paciente("Hugo", unidadeA);
        AgendamentoSolicitacao ag = agendamento(s, dia.plusDays(20), localOutro);
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(exame);
        se.setEspecialidadeCodigoLegacy(exame.getCodigo());
        se.setStatus(StatusDaMarcacao.CANCELADO);
        se.setAgendamentoSolicitacao(ag);
        itemRepository.saveAndFlush(se);
        em.flush();
        em.clear();

        assertThat(haDados(PlanilhaAgendadosFiltroDTO.porData(grupo.getCodigo(), dia.plusDays(20), null), admin))
                .isFalse();
    }

    @Test
    @DisplayName("Filtros novos nao ampliam o escopo: ADMIN_UNIDADE A nao ve paciente de B pelo profissional")
    void filtrosNaoAmpliamEscopo() throws IOException {
        User adminUnidadeA = usuario(Roles.ADMIN_UNIDADE, unidadeA);

        assertThat(planilha(noPeriodo(null, null, drDois.getId(), false), adminUnidadeA)).isEmpty();
        assertThat(haDados(noPeriodo(null, null, drDois.getId(), false), adminUnidadeA)).isFalse();
        assertThat(planilha(noPeriodo(null, null, drUm.getId(), false), adminUnidadeA).keySet())
                .containsExactlyInAnyOrder(ana, fabio);
        assertThat(planilha(noPeriodo(null, null, null, false), adminUnidadeA).keySet())
                .containsExactlyInAnyOrder(ana, fabio, caio);
    }
}
