package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaMigracaoDataCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaMigracaoData;
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
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaMigracaoDataRepository;
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
 * Migracao de data de cota contra o banco real: agenda pelo fluxo de verdade
 * ({@code AgendamentoService}), migra e confere cotas, contadores e datas.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback. Como
 * nao ha commit, os eventos de WhatsApp e federacao (AFTER_COMMIT) nao disparam
 * aqui; o commit real fica em {@code CotaMigracaoDataRollbackIT}.
 */
@SpringBootTest
@Transactional
class CotaMigracaoDataIT {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private CotaMigracaoDataService migracaoService;
    @Autowired private CotaUnidadeService cotaUnidadeService;
    @Autowired private CotaMigracaoDataRepository migracaoRepository;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private ProfissionalRepository profissionalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository itemRepository;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LocalAgendamentoRepository localRepository;

    private Unidade unidade;
    private Especialidade cardiologia;
    private Especialidade glicose;
    private User operador;
    private User admin;
    private String sufixo;
    /** Dois dias do mesmo mes (futuro) e um de outro mes. */
    private LocalDate d1;
    private LocalDate d2;
    private LocalDate outroMes;

    @BeforeEach
    void setUp() {
        sufixo = "_MIG_" + System.nanoTime();

        unidade = new Unidade();
        unidade.setNome("Unidade A" + sufixo);
        unidade.setCodigo("UA" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        cardiologia = especialidade("CARDIO", ItemCategoria.ESPECIALIDADE_MEDICA);
        glicose = especialidade("GLIC", ItemCategoria.EXAME_OU_PROCEDIMENTO);

        operador = usuario(Roles.ADMIN_UNIDADE, unidade);
        admin = usuario(Roles.ADMIN, null);

        YearMonth mes = YearMonth.now().plusMonths(1);
        d1 = mes.atDay(10);
        d2 = mes.atDay(17);
        outroMes = mes.plusMonths(1).atDay(3);
    }

    private Especialidade especialidade(String prefixo, ItemCategoria categoria) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(categoria);
        e.setAtivo(true);
        e.setVagas(0);
        return especialidadeRepository.saveAndFlush(e);
    }

    private User usuario(Roles role, Unidade lotacao) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(lotacao);
        return userRepository.saveAndFlush(u);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private Solicitacao solicitacao(int seq, Especialidade... especialidades) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente " + seq + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", seq));
        s.setNomePai("Pai " + seq);
        s.setNomeMae("Mae " + seq);
        s.setEndereco("Rua " + seq);
        s.setUnidade(unidade);

        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        for (Especialidade e : especialidades) {
            SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
            se.setSolicitacao(s);
            se.setEspecialidadeSolicitada(e);
            se.setEspecialidadeCodigoLegacy(e.getCodigo());
            se.setStatus(StatusDaMarcacao.AGUARDANDO);
            se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
            itens.add(se);
        }
        s.setEspecialidades(itens);
        return solicitacaoRepository.saveAndFlush(s);
    }

    /** Agenda (pelo fluxo real, como a unidade) e devolve o id do agendamento. */
    private Long agendar(int seq, LocalDate data, Especialidade... especialidades) {
        Solicitacao s = solicitacao(seq, especialidades);
        List<String> codigos = new ArrayList<>();
        for (Especialidade e : especialidades) {
            codigos.add(e.getCodigo());
        }
        var dto = new MultiAgendamentoCreateDTO(codigos, data, null, null, TurnoEnum.MANHA,
                "agendado no teste de migracao", null, null, null);
        return agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, operador.getCpf()).id();
    }

    private CotaUnidade cotaMensal(Especialidade especialidade, LocalDate dia, int total) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(especialidade);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(dia.format(PERIODO));
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        return cotaRepository.saveAndFlush(c);
    }

    private CotaUnidade cotaData(Especialidade especialidade, LocalDate data, int total, Profissional profissional) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(especialidade);
        c.setTipoPeriodo(TipoPeriodoCota.DATA);
        c.setDataEspecifica(data);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        c.setProfissionalExecutante(profissional);
        return cotaRepository.saveAndFlush(c);
    }

    private Profissional profissional() {
        Profissional p = new Profissional();
        p.setNome("Profissional" + sufixo);
        p.setAtivo(true);
        return profissionalRepository.saveAndFlush(p);
    }

    private CotaUnidade recarregar(Long id) {
        em.flush();
        em.clear();
        return cotaRepository.findById(id).orElseThrow();
    }

    private AgendamentoSolicitacao agendamento(Long id) {
        em.clear();
        return agendamentoRepository.findById(id).orElseThrow();
    }

    private List<CotaMigracaoData> auditoriaDe(Long cotaOrigemId) {
        return migracaoRepository.findAll().stream().filter(m -> m.getCotaOrigemId().equals(cotaOrigemId)).toList();
    }

    // ==================================================================

    @Test
    @DisplayName("Previa conta agendamentos e itens, sem divergencia quando a contagem bate")
    void previa() {
        CotaUnidade cota = cotaData(cardiologia, d1, 5, null);
        agendar(1, d1, cardiologia);
        agendar(2, d1, cardiologia);

        var previa = migracaoService.previa(cota.getId(), d2);

        assertThat(previa.agendamentos()).isEqualTo(2);
        assertThat(previa.itens()).isEqualTo(2);
        assertThat(previa.itensAgendados()).isEqualTo(2);
        assertThat(previa.quantidadeUtilizada()).isEqualTo(2);
        assertThat(previa.divergente()).isFalse();
        assertThat(previa.existeCotaNoDestino()).isFalse();
    }

    @Test
    @DisplayName("Mesmo mes: a mensal nao muda, a cota e os agendamentos vao para a data nova")
    void mesmoMes() {
        CotaUnidade mensal = cotaMensal(cardiologia, d1, 10);
        CotaUnidade cota = cotaData(cardiologia, d1, 5, null);
        Long ag1 = agendar(1, d1, cardiologia);
        Long ag2 = agendar(2, d1, cardiologia);
        assertThat(recarregar(mensal.getId()).getQuantidadeUtilizada()).isEqualTo(2);

        var resultado = migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());

        assertThat(resultado.unificada()).isFalse();
        assertThat(resultado.agendamentosMigrados()).isEqualTo(2);
        assertThat(resultado.itensMigrados()).isEqualTo(2);

        assertThat(recarregar(mensal.getId()).getQuantidadeUtilizada()).isEqualTo(2);
        CotaUnidade movida = recarregar(cota.getId());
        assertThat(movida.getDataEspecifica()).isEqualTo(d2);
        assertThat(movida.getQuantidadeUtilizada()).isEqualTo(2);

        for (Long id : List.of(ag1, ag2)) {
            AgendamentoSolicitacao ag = agendamento(id);
            assertThat(ag.getDataAgendada()).isEqualTo(d2);
            assertThat(ag.getDataAgendadaOriginal()).isEqualTo(d1);
        }

        assertThat(auditoriaDe(cota.getId())).singleElement().satisfies(m -> {
            assertThat(m.getDataDe()).isEqualTo(d1);
            assertThat(m.getDataPara()).isEqualTo(d2);
            assertThat(m.getAgendamentos()).isEqualTo(2);
            assertThat(m.getItens()).isEqualTo(2);
            assertThat(m.isUnificada()).isFalse();
            assertThat(m.getRealizadoPor().getId()).isEqualTo(admin.getId());
        });
    }

    @Test
    @DisplayName("Outro mes: devolve na mensal antiga (-n) e consome na nova (+n)")
    void outroMes() {
        CotaUnidade mensalAntiga = cotaMensal(cardiologia, d1, 10);
        CotaUnidade mensalNova = cotaMensal(cardiologia, outroMes, 10);
        CotaUnidade cota = cotaData(cardiologia, d1, 5, null);
        agendar(1, d1, cardiologia);
        agendar(2, d1, cardiologia);

        migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(outroMes), admin.getCpf());

        assertThat(recarregar(mensalAntiga.getId()).getQuantidadeUtilizada()).isZero();
        assertThat(recarregar(mensalNova.getId()).getQuantidadeUtilizada()).isEqualTo(2);
        assertThat(recarregar(cota.getId()).getQuantidadeUtilizada()).isEqualTo(2);
    }

    @Test
    @DisplayName("Outro mes com a mensal nova esgotada: 409, nada muda")
    void outroMesEsgotado() {
        cotaMensal(cardiologia, d1, 10);
        cotaMensal(cardiologia, outroMes, 1);
        CotaUnidade cota = cotaData(cardiologia, d1, 5, null);
        agendar(1, d1, cardiologia);
        agendar(2, d1, cardiologia);

        assertThatThrownBy(() -> migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(outroMes), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada");
    }

    @Test
    @DisplayName("Cota com profissional: migra pela FK do item, que continua apontando para ela")
    void cotaComProfissional() {
        CotaUnidade cota = cotaData(cardiologia, d1, 5, profissional());
        Long ag = agendar(1, d1, cardiologia);

        migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());

        assertThat(recarregar(cota.getId()).getDataEspecifica()).isEqualTo(d2);
        assertThat(recarregar(cota.getId()).getQuantidadeUtilizada()).isEqualTo(1);
        assertThat(agendamento(ag).getDataAgendada()).isEqualTo(d2);
        assertThat(itemRepository.findByAgendamentoSolicitacaoId(ag))
                .singleElement().satisfies(se -> assertThat(se.getCotaUnidade().getId()).isEqualTo(cota.getId()));
    }

    @Test
    @DisplayName("Cota de grupo de unidades e de grupo de especialidades migra por incidencia")
    void cotaDeGrupos() {
        GrupoRelatorio gUnidades = grupo("GU");
        GrupoRelatorio gEspecialidades = grupo("GE");
        unidade.setGrupoRelatorio(gUnidades);
        unidadeRepository.saveAndFlush(unidade);
        cardiologia.setGrupoRelatorio(gEspecialidades);
        especialidadeRepository.saveAndFlush(cardiologia);

        CotaUnidade cota = new CotaUnidade();
        cota.setGrupoUnidades(gUnidades);
        cota.setGrupoEspecialidades(gEspecialidades);
        cota.setTipoPeriodo(TipoPeriodoCota.DATA);
        cota.setDataEspecifica(d1);
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cota = cotaRepository.saveAndFlush(cota);
        Long ag1 = agendar(1, d1, cardiologia);
        Long ag2 = agendar(2, d1, cardiologia);
        assertThat(recarregar(cota.getId()).getQuantidadeUtilizada()).isEqualTo(2);

        var resultado = migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());

        assertThat(resultado.agendamentosMigrados()).isEqualTo(2);
        assertThat(recarregar(cota.getId()).getDataEspecifica()).isEqualTo(d2);
        assertThat(agendamento(ag1).getDataAgendada()).isEqualTo(d2);
        assertThat(agendamento(ag2).getDataAgendada()).isEqualTo(d2);
    }

    private GrupoRelatorio grupo(String prefixo) {
        GrupoRelatorio g = new GrupoRelatorio();
        g.setCodigo(prefixo + sufixo);
        g.setNome(prefixo + sufixo);
        g.setAtivo(true);
        return grupoRelatorioRepository.saveAndFlush(g);
    }

    @Test
    @DisplayName("Agendamento com varios exames vai inteiro para a nova data")
    void variosExamesVaoInteiros() {
        CotaUnidade geral = cotaData(null, d1, 5, null);
        Long ag = agendar(1, d1, cardiologia, glicose);
        assertThat(recarregar(geral.getId()).getQuantidadeUtilizada()).isEqualTo(2);

        var resultado = migracaoService.migrar(geral.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());

        assertThat(resultado.agendamentosMigrados()).isEqualTo(1);
        assertThat(resultado.itensMigrados()).isEqualTo(2);
        assertThat(agendamento(ag).getDataAgendada()).isEqualTo(d2);
        assertThat(itemRepository.findByAgendamentoSolicitacaoId(ag)).hasSize(2);
    }

    @Test
    @DisplayName("Cota ja existente na data nova: une (soma), exclui a de origem")
    void uniao() {
        CotaUnidade origem = cotaData(cardiologia, d1, 10, null);
        CotaUnidade destino = cotaData(cardiologia, d2, 4, null);
        destino.setQuantidadeUtilizada(1);
        cotaRepository.saveAndFlush(destino);
        Long ag = agendar(1, d1, cardiologia);
        agendar(2, d1, cardiologia);

        var previa = migracaoService.previa(origem.getId(), d2);
        assertThat(previa.existeCotaNoDestino()).isTrue();
        assertThat(previa.destinoQuantidadeTotal()).isEqualTo(4);

        var resultado = migracaoService.migrar(origem.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());

        assertThat(resultado.unificada()).isTrue();
        assertThat(resultado.cotaResultanteId()).isEqualTo(destino.getId());
        CotaUnidade unida = recarregar(destino.getId());
        assertThat(unida.getQuantidadeTotal()).isEqualTo(14);
        assertThat(unida.getQuantidadeUtilizada()).isEqualTo(3);
        assertThat(cotaRepository.existsById(origem.getId())).isFalse();
        assertThat(agendamento(ag).getDataAgendada()).isEqualTo(d2);
        assertThat(auditoriaDe(origem.getId())).singleElement().satisfies(m -> {
            assertThat(m.isUnificada()).isTrue();
            assertThat(m.getCotaResultanteId()).isEqualTo(destino.getId());
        });
    }

    @Test
    @DisplayName("Uniao de cotas com profissional reaponta a FK dos itens para o destino")
    void uniaoComProfissional() {
        Profissional p = profissional();
        CotaUnidade origem = cotaData(cardiologia, d1, 10, p);
        CotaUnidade destino = cotaData(cardiologia, d2, 4, p);
        Long ag = agendar(1, d1, cardiologia);

        migracaoService.migrar(origem.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());

        assertThat(cotaRepository.existsById(origem.getId())).isFalse();
        assertThat(recarregar(destino.getId()).getQuantidadeUtilizada()).isEqualTo(1);
        assertThat(itemRepository.findByAgendamentoSolicitacaoId(ag))
                .singleElement().satisfies(se -> assertThat(se.getCotaUnidade().getId()).isEqualTo(destino.getId()));
    }

    @Test
    @DisplayName("Depois de migrar, excluir o agendamento estorna na cota e na mensal sem erro")
    void estornoDepoisDeMigrar() {
        CotaUnidade mensal = cotaMensal(cardiologia, d1, 10);
        CotaUnidade cota = cotaData(cardiologia, d1, 5, null);
        Long ag = agendar(1, d1, cardiologia);
        agendar(2, d1, cardiologia);
        migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());

        agendamentoService.deleteAgendamento(ag, operador.getCpf());

        assertThat(recarregar(cota.getId()).getQuantidadeUtilizada()).isEqualTo(1);
        assertThat(recarregar(mensal.getId()).getQuantidadeUtilizada()).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT com a data alterada e agendamentos existentes: 409 apontando a migracao")
    void putComDataAlteradaEAgendamentos() {
        CotaUnidade cota = cotaData(cardiologia, d1, 5, null);
        agendar(1, d1, cardiologia);
        agendar(2, d1, cardiologia);

        var dto = new CotaUnidadeUpdateDTO(unidade.getId(), null, cardiologia.getId(), null, TipoPeriodoCota.DATA,
                null, d2, 5, true, null, null, false, null, null, null, null);

        assertThatThrownBy(() -> cotaUnidadeService.atualizar(cota.getId(), dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 agendamento(s)");
        assertThat(recarregar(cota.getId()).getDataEspecifica()).isEqualTo(d1);
    }

    // ------------------------------------------------------------------
    // FK velha: o item cita a cota, mas o agendamento esta em outra data
    // ------------------------------------------------------------------

    /** Simula o reagendamento por ADMIN/GESTOR: a data muda e a FK da cota fica como estava. */
    private void moverAgendamentoSemMexerNaFk(Long agendamentoId, LocalDate data) {
        jdbc.update("UPDATE agendamento_solicitacao SET data_agendada = ? WHERE id = ?",
                java.sql.Date.valueOf(data), agendamentoId);
    }

    @Test
    @DisplayName("FK velha: item que cita a cota mas esta agendado em outra data nao entra na previa nem migra")
    void fkVelhaNaoMigra() {
        LocalDate d3 = d1.plusDays(14);
        CotaUnidade cota = cotaData(cardiologia, d1, 5, profissional());
        Long agNaCota = agendar(1, d1, cardiologia);
        Long agVelho = agendar(2, d1, cardiologia);
        moverAgendamentoSemMexerNaFk(agVelho, d3);

        var previa = migracaoService.previa(cota.getId(), d2);
        assertThat(previa.agendamentos()).isEqualTo(1);
        assertThat(previa.itens()).isEqualTo(1);

        var resultado = migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());

        assertThat(resultado.agendamentosMigrados()).isEqualTo(1);
        assertThat(agendamento(agNaCota).getDataAgendada()).isEqualTo(d2);
        AgendamentoSolicitacao velho = agendamento(agVelho);
        assertThat(velho.getDataAgendada()).isEqualTo(d3);
        assertThat(velho.getDataAgendadaOriginal()).isNull();
    }

    @Test
    @DisplayName("FK velha: PUT mudando a data de cota cujo unico 'consumidor' e uma FK velha nao da 409")
    void putComSoFkVelhaNaoDa409() {
        CotaUnidade cota = cotaData(cardiologia, d1, 5, profissional());
        Long ag = agendar(1, d1, cardiologia);
        moverAgendamentoSemMexerNaFk(ag, d1.plusDays(14));
        em.clear();

        var dto = new CotaUnidadeUpdateDTO(unidade.getId(), null, cardiologia.getId(), null, TipoPeriodoCota.DATA,
                null, d2, 5, true, cota.getProfissionalExecutante().getId(), null, false, null, null, null, null);

        cotaUnidadeService.atualizar(cota.getId(), dto);

        assertThat(recarregar(cota.getId()).getDataEspecifica()).isEqualTo(d2);
    }

    @Test
    @DisplayName("Segunda migracao do mesmo agendamento mantem a PRIMEIRA data em data_agendada_original")
    void segundaMigracaoMantemADataOriginal() {
        CotaUnidade cota = cotaData(cardiologia, d1, 5, null);
        Long ag = agendar(1, d1, cardiologia);

        migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());
        migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(outroMes), admin.getCpf());

        AgendamentoSolicitacao depois = agendamento(ag);
        assertThat(depois.getDataAgendada()).isEqualTo(outroMes);
        assertThat(depois.getDataAgendadaOriginal()).isEqualTo(d1);
        assertThat(auditoriaDe(cota.getId())).hasSize(2);
    }

    @Test
    @DisplayName("PUT: cota mensal com agendamentos nao muda de mes, nem troca de tipo (409)")
    void putMensalComAgendamentosEmMudancaDePeriodo() {
        CotaUnidade mensal = cotaMensal(cardiologia, d1, 10);
        agendar(1, d1, cardiologia);

        var outroMesDto = new CotaUnidadeUpdateDTO(unidade.getId(), null, cardiologia.getId(), null,
                TipoPeriodoCota.MENSAL, outroMes.format(PERIODO), null, 10, true,
                null, null, false, null, null, null, null);
        assertThatThrownBy(() -> cotaUnidadeService.atualizar(mensal.getId(), outroMesDto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 agendamento(s)");

        var trocaDeTipo = new CotaUnidadeUpdateDTO(unidade.getId(), null, cardiologia.getId(), null,
                TipoPeriodoCota.DATA, null, d1, 10, true,
                null, null, false, null, null, null, null);
        assertThatThrownBy(() -> cotaUnidadeService.atualizar(mensal.getId(), trocaDeTipo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 agendamento(s)");
    }

    @Test
    @DisplayName("O snapshot fechamento_indicadores_dia das datas de origem e destino e apagado; o de outra data fica")
    void snapshotsDeOrigemEDestinoSaoApagados() {
        LocalAgendamento local = new LocalAgendamento();
        local.setNomeLocal("Local" + sufixo);
        local = localRepository.saveAndFlush(local);
        GrupoRelatorio grupo = grupo("FECH");
        for (LocalDate dia : List.of(d1, d2, outroMes)) {
            jdbc.update("INSERT INTO fechamento_indicadores_dia (data_referencia, local_id, grupo_id, agendados_total, "
                    + "atendidos_total, faltosos_total, cancelados_total, solicitacoes_cadastradas_total, fechado_em) "
                    + "VALUES (?, ?, ?, 1, 0, 0, 0, 0, now())", java.sql.Date.valueOf(dia), local.getId(), grupo.getId());
        }
        CotaUnidade cota = cotaData(cardiologia, d1, 5, null);
        agendar(1, d1, cardiologia);

        migracaoService.migrar(cota.getId(), new CotaMigracaoDataCreateDTO(d2), admin.getCpf());

        for (LocalDate dia : List.of(d1, d2)) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fechamento_indicadores_dia WHERE local_id = ? "
                    + "AND data_referencia = ?", Integer.class, local.getId(), java.sql.Date.valueOf(dia)))
                    .as("snapshot de " + dia).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fechamento_indicadores_dia WHERE local_id = ? "
                + "AND data_referencia = ?", Integer.class, local.getId(), java.sql.Date.valueOf(outroMes))).isEqualTo(1);
    }
}
