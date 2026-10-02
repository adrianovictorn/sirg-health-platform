package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaConsolidadaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
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
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * FLUXO REAL: Agenda do Dia consolidada (visao do ADMIN global).
 *
 * Valida que o ADMIN enxerga todas as unidades numa consulta so, com os
 * indicadores por unidade/grupo/especialidade batendo com o que foi agendado, que
 * qualquer outro perfil (inclusive GESTOR e ADMIN_UNIDADE) recebe acesso negado,
 * e que a lista nominal sai com CPF/CNS mascarados.
 *
 * As assercoes filtram pelas unidades/grupo criados aqui, nao pelo total geral —
 * a base pode ter dados reais. <b>Nao suja a base:</b> classe {@code @Transactional}.
 */
@SpringBootTest
@Transactional
class AgendaDiaConsolidadaIT {

    @Autowired private AgendaDiaConsolidadaService service;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CotaUnidadeRepository cotaUnidadeRepository;

    private Unidade unidadeA;
    private Unidade unidadeB;
    private Especialidade cardiologia;
    private GrupoRelatorio grupo;
    private User admin;
    private User adminUnidade;
    private User gestor;
    private LocalDate hoje;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "_CONS_IT_" + System.nanoTime();
        hoje = LocalDate.now();

        unidadeA = novaUnidade("Unidade A");
        unidadeB = novaUnidade("Unidade B");

        grupo = new GrupoRelatorio();
        grupo.setCodigo("cardio" + sufixo);
        grupo.setNome("Cardiologia" + sufixo);
        grupo.setAtivo(true);
        grupo.setDirecionadoHospital(true);
        grupo = grupoRelatorioRepository.saveAndFlush(grupo);

        cardiologia = new Especialidade();
        cardiologia.setCodigo("CARDIO" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(0);
        cardiologia.setGrupoRelatorio(grupo);
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        admin = novoUsuario("Admin Global", Roles.ADMIN, null);
        adminUnidade = novoUsuario("Admin da Unidade A", Roles.ADMIN_UNIDADE, unidadeA);
        gestor = novoUsuario("Gestor", Roles.GESTOR, null);
    }

    private Unidade novaUnidade(String nome) {
        Unidade u = new Unidade();
        u.setNome(nome + sufixo);
        u.setCodigo(nome.replace(" ", "") + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private User novoUsuario(String nome, Roles role, Unidade unidade) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(nome);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(unidade);
        return userRepository.saveAndFlush(u);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    /** Cria solicitacao da unidade (pode ser nula) e agenda para hoje. Devolve o item agendado. */
    private SolicitacaoEspecialidade agendar(Unidade unidade, int seq) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente " + seq + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", seq));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);

        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(cardiologia);
        se.setEspecialidadeCodigoLegacy(cardiologia.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);

        List<SolicitacaoEspecialidade> especs = new ArrayList<>();
        especs.add(se);
        s.setEspecialidades(especs);
        s = solicitacaoRepository.saveAndFlush(s);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), hoje, null, null, TurnoEnum.MANHA, "agendado no teste", null, null, null);
        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, admin.getCpf());
        return s.getEspecialidades().get(0);
    }

    private AgendaDiaConsolidadaViewDTO.Unidade unidadeDoResultado(AgendaDiaConsolidadaViewDTO view, Unidade u) {
        return view.unidades().stream().filter(x -> u.getId().equals(x.id())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("ADMIN ve todas as unidades de uma vez, com indicadores por unidade, grupo e especialidade")
    void adminVeTodasAsUnidades() {
        agendar(unidadeA, 1);
        agendar(unidadeA, 2);
        agendar(unidadeB, 3);

        var view = service.consolidar(hoje, null, null, null, admin.getCpf());

        var a = unidadeDoResultado(view, unidadeA);
        var b = unidadeDoResultado(view, unidadeB);
        assertThat(a.indicadores().pacientes()).isEqualTo(2);
        assertThat(a.indicadores().itens()).isEqualTo(2);
        assertThat(a.indicadores().agendados()).isEqualTo(2);
        assertThat(b.indicadores().pacientes()).isEqualTo(1);

        var grupoA = a.grupos().stream().filter(g -> grupo.getId().equals(g.id())).findFirst().orElseThrow();
        assertThat(grupoA.indicadores().itens()).isEqualTo(2);
        assertThat(grupoA.especialidades()).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo(cardiologia.getId());
            assertThat(e.indicadores().agendados()).isEqualTo(2);
        });

        // o total por grupo (todas as unidades) soma as duas unidades
        var grupoGeral = view.gruposGeral().stream().filter(g -> grupo.getId().equals(g.id())).findFirst().orElseThrow();
        assertThat(grupoGeral.indicadores().itens()).isEqualTo(3);
        assertThat(grupoGeral.indicadores().pacientes()).isEqualTo(3);
    }

    @Test
    @DisplayName("Status REALIZADO e CANCELADO entram nos indicadores; AGENDADO diminui")
    void contaPorStatus() {
        var realizado = agendar(unidadeA, 1);
        var cancelado = agendar(unidadeA, 2);
        agendar(unidadeA, 3);
        realizado.setStatus(StatusDaMarcacao.REALIZADO);
        cancelado.setStatus(StatusDaMarcacao.CANCELADO);
        solicitacaoRepository.flush();

        var ind = unidadeDoResultado(service.consolidar(hoje, unidadeA.getId(), null, null, admin.getCpf()), unidadeA)
                .indicadores();

        assertThat(ind.itens()).isEqualTo(3);
        assertThat(ind.agendados()).isEqualTo(1);
        assertThat(ind.realizados()).isEqualTo(1);
        assertThat(ind.faltasCancelados()).isEqualTo(1);
    }

    @Test
    @DisplayName("Solicitacao sem unidade aparece no balde 'Sem unidade'")
    void semUnidadeApareceEmBalde() {
        agendar(null, 1);

        var view = service.consolidar(hoje, null, grupo.getId(), null, admin.getCpf());

        assertThat(view.unidades()).anySatisfy(u -> {
            assertThat(u.id()).isNull();
            assertThat(u.nome()).isEqualTo("Sem unidade");
            assertThat(u.indicadores().itens()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Filtro por unidade restringe o resultado a ela")
    void filtroPorUnidade() {
        agendar(unidadeA, 1);
        agendar(unidadeB, 2);

        var view = service.consolidar(hoje, unidadeB.getId(), null, null, admin.getCpf());

        assertThat(view.unidades()).extracting(AgendaDiaConsolidadaViewDTO.Unidade::id).containsExactly(unidadeB.getId());
        assertThat(view.totais().itens()).isEqualTo(1);
    }

    @Test
    @DisplayName("Perfis que nao sao ADMIN (ADMIN_UNIDADE, GESTOR) levam acesso negado, mesmo informando unidadeId")
    void outrosPerfisSaoNegados() {
        assertThatThrownBy(() -> service.consolidar(hoje, unidadeA.getId(), null, null, adminUnidade.getCpf()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.consolidar(hoje, null, null, null, gestor.getCpf()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listarPacientes(hoje, cardiologia.getId(), null, false, 0, 10, gestor.getCpf()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.consolidar(hoje, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Lista nominal mascara CPF/CNS, so traz a data pedida e limita o tamanho da pagina")
    void listaNominalMascarada() {
        agendar(unidadeA, 1);

        var pagina = service.listarPacientes(hoje, cardiologia.getId(), unidadeA.getId(), false, 0, 1000, admin.getCpf());

        assertThat(pagina.getSize()).isLessThanOrEqualTo(AgendaDiaConsolidadaService.TAMANHO_MAXIMO_PAGINA);
        assertThat(pagina.getContent()).singleElement().satisfies(i -> {
            assertThat(i.nomePaciente()).startsWith("Paciente 1");
            assertThat(i.cpfMascarado()).startsWith("*").hasSize(11);
            assertThat(i.cnsMascarado()).startsWith("*").hasSize(15);
        });

        var outroDia = service.listarPacientes(hoje.plusDays(1), cardiologia.getId(), unidadeA.getId(), false, 0, 10,
                admin.getCpf());
        assertThat(outroDia.getContent()).isEmpty();
    }

    private CotaUnidade cota(TipoPeriodoCota tipo, LocalDate data, String diasSemana, int total, int usada) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidadeA);
        c.setEspecialidade(cardiologia);
        c.setTipoPeriodo(tipo);
        if (tipo == TipoPeriodoCota.DATA) {
            c.setDataEspecifica(data);
        } else {
            c.setPeriodo(String.format("%04d-%02d", data.getYear(), data.getMonthValue()));
            c.setDiasSemana(diasSemana);
        }
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(usada);
        c.setAtivo(true);
        return cotaUnidadeRepository.saveAndFlush(c);
    }

    @Test
    @DisplayName("Cota do dia e mensal do dia da semana aparecem; cota de outro dia nao")
    void cotasDoDia() {
        String hojeSigla = switch (hoje.getDayOfWeek()) {
            case MONDAY -> "SEG"; case TUESDAY -> "TER"; case WEDNESDAY -> "QUA"; case THURSDAY -> "QUI";
            case FRIDAY -> "SEX"; case SATURDAY -> "SAB"; case SUNDAY -> "DOM";
        };
        var doDia = cota(TipoPeriodoCota.DATA, hoje, null, 10, 4);
        cota(TipoPeriodoCota.DATA, hoje.plusDays(1), null, 99, 0);
        var mensalHoje = cota(TipoPeriodoCota.MENSAL, hoje, hojeSigla, 20, 5);

        var view = service.consolidar(hoje, unidadeA.getId(), null, null, admin.getCpf());

        var esp = unidadeDoResultado(view, unidadeA).grupos().stream()
                .filter(g -> grupo.getId().equals(g.id())).findFirst().orElseThrow()
                .especialidades().get(0);
        assertThat(esp.cotas()).extracting(AgendaDiaConsolidadaViewDTO.CotaDia::id)
                .containsExactlyInAnyOrder(doDia.getId(), mensalHoje.getId());
        assertThat(esp.cotas()).filteredOn(c -> c.id().equals(doDia.getId())).singleElement().satisfies(c -> {
            assertThat(c.tipo()).isEqualTo("DIA");
            assertThat(c.total()).isEqualTo(10);
            assertThat(c.utilizada()).isEqualTo(4);
            assertThat(c.livres()).isEqualTo(6);
        });
    }

    @Test
    @DisplayName("Cota mensal que atende so em outro dia da semana nao incide no dia")
    void mensalDeOutroDiaDaSemanaNaoIncide() {
        String hojeSigla = switch (hoje.getDayOfWeek()) {
            case MONDAY -> "SEG"; case TUESDAY -> "TER"; case WEDNESDAY -> "QUA"; case THURSDAY -> "QUI";
            case FRIDAY -> "SEX"; case SATURDAY -> "SAB"; case SUNDAY -> "DOM";
        };
        cota(TipoPeriodoCota.MENSAL, hoje, hojeSigla.equals("SEG") ? "TER" : "SEG", 77, 0);
        agendar(unidadeA, 1);

        var view = service.consolidar(hoje, unidadeA.getId(), null, null, admin.getCpf());

        var esp = unidadeDoResultado(view, unidadeA).grupos().stream()
                .filter(g -> grupo.getId().equals(g.id())).findFirst().orElseThrow()
                .especialidades().get(0);
        assertThat(esp.cotas()).isEmpty();
    }
}
