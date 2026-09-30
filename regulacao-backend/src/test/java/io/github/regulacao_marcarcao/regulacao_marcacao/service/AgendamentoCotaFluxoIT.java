package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
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
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Simulacao do FLUXO REAL de agendamento pela unidade, estourando a cota.
 *
 * Aqui nao se chama {@code CotaUnidadeService} diretamente: o teste passa por
 * {@code AgendamentoService.criarAgendamentoParaMultiplosExames}, que e o caminho
 * que a tela /agendar percorre. Isso valida a ligacao inteira — se a cota e
 * consumida no ponto certo, se o bloqueio realmente impede o agendamento e se o
 * cancelamento devolve a vaga.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class AgendamentoCotaFluxoIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AgendamentoSolicitacaoRepository agendamentoSolicitacaoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    @Autowired private ProfissionalRepository profissionalRepository;
    @Autowired private CotaUnidadeService cotaUnidadeService;

    private Unidade unidade;
    private Especialidade cardiologia;
    private User operadorDaUnidade;
    private LocalDate data;
    private String periodo;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "_IT_" + System.nanoTime();

        unidade = new Unidade();
        unidade.setNome("Unidade A" + sufixo);
        unidade.setCodigo("UA" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        cardiologia = new Especialidade();
        cardiologia.setCodigo("CARDIO" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(0); // 0 = sem limite de capacidade global; quem limita e a cota
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        // Operador lotado na unidade: NAO e admin global, portanto sujeito a cota.
        operadorDaUnidade = new User();
        operadorDaUnidade.setCpf(cpfUnico());
        operadorDaUnidade.setNome("Operador da Unidade");
        operadorDaUnidade.setPassword("x");
        operadorDaUnidade.setRole(Roles.ADMIN_UNIDADE);
        operadorDaUnidade.setAtivo(true);
        operadorDaUnidade.setUnidade(unidade);
        operadorDaUnidade = userRepository.saveAndFlush(operadorDaUnidade);

        data = LocalDate.now();
        periodo = data.format(PERIODO_MENSAL);
    }

    /** CPF ficticio unico que cabe em varchar(15). */
    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    /** Cria uma solicitacao da unidade com uma especialidade pendente. */
    private Solicitacao novaSolicitacaoPendente(int seq) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente " + seq + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", seq));
        s.setNomePai("Pai " + seq);
        s.setNomeMae("Mae " + seq);
        s.setEndereco("Rua " + seq);
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

        return solicitacaoRepository.saveAndFlush(s);
    }

    private MultiAgendamentoCreateDTO dtoAgendamento() {
        return new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()),
                data,
                null,
                null,
                TurnoEnum.MANHA,
                "agendado no teste de cota",
                null,
                null,
                null);
    }

    private CotaUnidade criarCota(int total) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(cardiologia);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(periodo);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        return cotaRepository.saveAndFlush(c);
    }

    private int utilizada(Long cotaId) {
        return cotaRepository.findById(cotaId).orElseThrow().getQuantidadeUtilizada();
    }

    // ==================================================================

    @Test
    @DisplayName("FLUXO REAL: Unidade A / Cardiologia / 5 vagas — o 6o agendamento e recusado")
    void unidadeEstouraCotaNoFluxoDeAgendamento() {
        CotaUnidade cota = criarCota(5);
        String cpf = operadorDaUnidade.getCpf();

        // 5 pacientes distintos, todos agendados pela mesma unidade
        for (int i = 1; i <= 5; i++) {
            Solicitacao s = novaSolicitacaoPendente(i);
            final int n = i;
            assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                    s.getId(), dtoAgendamento(), cpf))
                    .as("agendamento %d deveria passar", n)
                    .doesNotThrowAnyException();
        }

        assertThat(utilizada(cota.getId())).isEqualTo(5);

        // 6o paciente: a unidade atingiu o limite mensal para Cardiologia
        Solicitacao sexto = novaSolicitacaoPendente(6);
        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                sexto.getId(), dtoAgendamento(), cpf))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada")
                .hasMessageContaining("Unidade A");

        assertThat(utilizada(cota.getId())).isEqualTo(5);
    }

    @Test
    @DisplayName("FLUXO REAL: cancelar um agendamento devolve a vaga e destrava o proximo")
    void cancelamentoNoFluxoDevolveVaga() {
        CotaUnidade cota = criarCota(1);
        String cpf = operadorDaUnidade.getCpf();

        Solicitacao primeira = novaSolicitacaoPendente(1);
        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(
                primeira.getId(), dtoAgendamento(), cpf);
        assertThat(utilizada(cota.getId())).isEqualTo(1);

        // Segundo paciente e barrado
        Solicitacao segunda = novaSolicitacaoPendente(2);
        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                segunda.getId(), dtoAgendamento(), cpf))
                .isInstanceOf(IllegalStateException.class);

        // Cancela o primeiro -> a vaga volta
        agendamentoService.deleteAgendamento(agendamento.id(), cpf);
        assertThat(utilizada(cota.getId())).isZero();

        // Agora o segundo consegue agendar
        Solicitacao segundaDeNovo = novaSolicitacaoPendente(3);
        assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                segundaDeNovo.getId(), dtoAgendamento(), cpf))
                .doesNotThrowAnyException();
        assertThat(utilizada(cota.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("FLUXO REAL: ADMIN global nao e limitado pela cota da unidade")
    void adminGlobalNaoSofreCota() {
        CotaUnidade cota = criarCota(1);

        User admin = new User();
        admin.setCpf(cpfUnico());
        admin.setNome("Admin Global");
        admin.setPassword("x");
        admin.setRole(Roles.ADMIN);
        admin.setAtivo(true);
        admin = userRepository.saveAndFlush(admin);

        String cpfAdmin = admin.getCpf();
        for (int i = 1; i <= 3; i++) {
            Solicitacao s = novaSolicitacaoPendente(i);
            assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                    s.getId(), dtoAgendamento(), cpfAdmin))
                    .doesNotThrowAnyException();
        }

        // Nenhum consumo: o admin global nao esta sujeito a cota
        assertThat(utilizada(cota.getId())).isZero();
    }

    /**
     * V93: ADMIN_UNIDADE deixa de poder agendar especialidade sem NENHUMA cota
     * liberada para a unidade — antes desta versao, ausencia de cota significava
     * agendamento livre para qualquer perfil (comportamento historico, ainda
     * preservado para ADMIN e GESTOR, ver testes abaixo). Mudanca de regra de
     * negocio deliberada e aprovada — nao e regressao.
     */
    @Test
    @DisplayName("V93: ADMIN_UNIDADE sem NENHUMA cota cadastrada e bloqueado ao agendar")
    void adminUnidadeSemCotaEBloqueado() {
        String cpf = operadorDaUnidade.getCpf();

        Solicitacao s = novaSolicitacaoPendente(1);
        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                s.getId(), dtoAgendamento(), cpf))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cota liberada");
    }

    /**
     * GESTOR mantem o comportamento historico ("cota ausente = sem restricao"):
     * o bloqueio novo e exclusivo de ADMIN_UNIDADE, GESTOR ja era tratado como
     * acesso global antes desta versao (UnidadeAcessoService) e continua assim.
     */
    @Test
    @DisplayName("FLUXO REAL: GESTOR continua agendando sem cota (comportamento preservado)")
    void gestorSemCotaAgendaLivremente() {
        User gestor = new User();
        gestor.setCpf(cpfUnico());
        gestor.setNome("Gestor Municipal");
        gestor.setPassword("x");
        gestor.setRole(Roles.GESTOR);
        gestor.setAtivo(true);
        gestor = userRepository.saveAndFlush(gestor);
        String cpfGestor = gestor.getCpf();

        Solicitacao s = novaSolicitacaoPendente(1);
        assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                s.getId(), dtoAgendamento(), cpfGestor))
                .doesNotThrowAnyException();
    }

    /**
     * Registro anterior a V72/V73: solicitacao sem unidade vinculada. Nao pode ser
     * barrada por cota nenhuma, nem provocar erro — continua agendavel como antes.
     */
    @Test
    @DisplayName("LEGADO no fluxo real: solicitacao sem unidade continua agendavel")
    void solicitacaoLegadaSemUnidadeContinuaAgendavel() {
        criarCota(0); // cota zerada na unidade: se fosse aplicada, barraria tudo
        String cpf = operadorDaUnidade.getCpf();

        Solicitacao orfa = novaSolicitacaoPendente(1);
        orfa.setUnidade(null);
        solicitacaoRepository.saveAndFlush(orfa);

        assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                orfa.getId(), dtoAgendamento(), cpf))
                .doesNotThrowAnyException();
    }

    /**
     * V93: o agendamento passa a registrar quem o criou (operador logado) —
     * metadado de auditoria, nunca impede o agendamento.
     */
    @Test
    @DisplayName("V93: agendamento registra o operador que o criou (autoria)")
    void agendamentoRegistraAutoria() {
        criarCota(5);
        String cpf = operadorDaUnidade.getCpf();

        Solicitacao s = novaSolicitacaoPendente(1);
        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(
                s.getId(), dtoAgendamento(), cpf);

        var salvo = agendamentoSolicitacaoRepository.findById(agendamento.id()).orElseThrow();
        assertThat(salvo.getCriadoPor()).isNotNull();
        assertThat(salvo.getCriadoPor().getCpf()).isEqualTo(cpf);
    }

    /**
     * V92/V93: quando a cota aplicavel tem profissional executante, o agendamento
     * fica rastreavel a essa cota — permite provar depois qual profissional
     * atendeu qual paciente.
     */
    @Test
    @DisplayName("V92/V93: cota com profissional executante fica rastreavel no agendamento")
    void cotaComProfissionalFicaRastreavelNoAgendamento() {
        io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional drA =
                new io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional();
        drA.setNome("Dr. A" + sufixo);
        drA.setAtivo(true);
        drA = profissionalRepository.saveAndFlush(drA);

        CotaUnidade cotaDrA = new CotaUnidade();
        cotaDrA.setUnidade(unidade);
        cotaDrA.setEspecialidade(cardiologia);
        cotaDrA.setTipoPeriodo(TipoPeriodoCota.DATA);
        cotaDrA.setDataEspecifica(data);
        cotaDrA.setQuantidadeTotal(5);
        cotaDrA.setQuantidadeUtilizada(0);
        cotaDrA.setAtivo(true);
        cotaDrA.setProfissionalExecutante(drA);
        cotaDrA = cotaRepository.saveAndFlush(cotaDrA);

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "com profissional", null, null, null);
        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf);

        var especialidadeAgendada = solicitacaoEspecialidadeRepository
                .findByAgendamentoSolicitacaoId(agendamento.id()).get(0);

        assertThat(especialidadeAgendada.getCotaUnidade()).isNotNull();
        assertThat(especialidadeAgendada.getCotaUnidade().getId()).isEqualTo(cotaDrA.getId());
        assertThat(especialidadeAgendada.getCotaUnidade().getProfissionalExecutante().getNome())
                .isEqualTo("Dr. A" + sufixo);
    }

    /**
     * V92: duas cotas da mesma especialidade/data, profissionais diferentes — sem
     * a unidade escolher qual, o sistema recusa em vez de adivinhar.
     */
    @Test
    @DisplayName("V92: duas cotas com profissionais diferentes sem escolha explicita e recusado")
    void duasCotasComProfissionaisDiferentesSemEscolhaERecusado() {
        io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional drA =
                new io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional();
        drA.setNome("Dr. A" + sufixo);
        drA.setAtivo(true);
        drA = profissionalRepository.saveAndFlush(drA);

        io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional drB =
                new io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional();
        drB.setNome("Dr. B" + sufixo);
        drB.setAtivo(true);
        drB = profissionalRepository.saveAndFlush(drB);

        CotaUnidade cotaDrA = new CotaUnidade();
        cotaDrA.setUnidade(unidade);
        cotaDrA.setEspecialidade(cardiologia);
        cotaDrA.setTipoPeriodo(TipoPeriodoCota.DATA);
        cotaDrA.setDataEspecifica(data);
        cotaDrA.setQuantidadeTotal(5);
        cotaDrA.setAtivo(true);
        cotaDrA.setProfissionalExecutante(drA);
        cotaRepository.saveAndFlush(cotaDrA);

        CotaUnidade cotaDrB = new CotaUnidade();
        cotaDrB.setUnidade(unidade);
        cotaDrB.setEspecialidade(cardiologia);
        cotaDrB.setTipoPeriodo(TipoPeriodoCota.DATA);
        cotaDrB.setDataEspecifica(data);
        cotaDrB.setQuantidadeTotal(5);
        cotaDrB.setAtivo(true);
        cotaDrB.setProfissionalExecutante(drB);
        cotaRepository.saveAndFlush(cotaDrB);

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);
        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "sem escolha", null, null, null);

        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mais de uma cota");
    }

    /**
     * DIAGNOSTICO: cancelar um agendamento com 2+ especialidades (ex.: um painel de
     * laboratorio) reproduz o 409 relatado em producao?
     *
     * Os testes acima so cobrem 1 especialidade por agendamento.
     * {@code estornarCotasDoAgendamento} itera uma lista de
     * {@code SolicitacaoEspecialidade} chamando, para cada uma,
     * {@code se.getEspecialidadeSolicitada()} (LAZY) antes de {@code estornarUtilizacao}
     * — que consome {@code devolverVaga}, anotado
     * {@code @Modifying(clearAutomatically = true)}. Esse clear desanexa TODA a
     * persistence context, inclusive as demais {@code SolicitacaoEspecialidade} da
     * lista cujo proxy de especialidade ainda nao foi tocado — exatamente o padrao ja
     * documentado (e corrigido) em {@code CotaUnidadeService#incrementarUtilizacao},
     * so que aqui, no caminho de cancelamento, ainda sem correcao.
     */
    @Test
    @DisplayName("DIAGNOSTICO: cancelar agendamento com 2 especialidades (painel)")
    void cancelarAgendamentoComDuasEspecialidades() {
        Especialidade hemograma = new Especialidade();
        hemograma.setCodigo("HEMOGRAMA" + sufixo);
        hemograma.setNome("Hemograma" + sufixo);
        hemograma.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        hemograma.setAtivo(true);
        hemograma.setVagas(0);
        hemograma = especialidadeRepository.saveAndFlush(hemograma);

        CotaUnidade cotaCardio = criarCota(5);
        CotaUnidade cotaHemograma = new CotaUnidade();
        cotaHemograma.setUnidade(unidade);
        cotaHemograma.setEspecialidade(hemograma);
        cotaHemograma.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cotaHemograma.setPeriodo(periodo);
        cotaHemograma.setQuantidadeTotal(5);
        cotaHemograma.setQuantidadeUtilizada(0);
        cotaHemograma.setAtivo(true);
        cotaHemograma = cotaRepository.saveAndFlush(cotaHemograma);

        String cpf = operadorDaUnidade.getCpf();

        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente painel" + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", 99));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);

        SolicitacaoEspecialidade se1 = new SolicitacaoEspecialidade();
        se1.setSolicitacao(s);
        se1.setEspecialidadeSolicitada(cardiologia);
        se1.setEspecialidadeCodigoLegacy(cardiologia.getCodigo());
        se1.setStatus(StatusDaMarcacao.AGUARDANDO);
        se1.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);

        SolicitacaoEspecialidade se2 = new SolicitacaoEspecialidade();
        se2.setSolicitacao(s);
        se2.setEspecialidadeSolicitada(hemograma);
        se2.setEspecialidadeCodigoLegacy(hemograma.getCodigo());
        se2.setStatus(StatusDaMarcacao.AGUARDANDO);
        se2.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);

        List<SolicitacaoEspecialidade> especs = new ArrayList<>();
        especs.add(se1);
        especs.add(se2);
        s.setEspecialidades(especs);
        s = solicitacaoRepository.saveAndFlush(s);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo(), hemograma.getCodigo()),
                data, null, null, TurnoEnum.MANHA, "painel no teste", null, null, null);

        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf);
        assertThat(utilizada(cotaCardio.getId())).isEqualTo(1);
        assertThat(utilizada(cotaHemograma.getId())).isEqualTo(1);

        assertThatCode(() -> agendamentoService.deleteAgendamento(agendamento.id(), cpf))
                .as("cancelar um agendamento com 2 especialidades nao deveria falhar")
                .doesNotThrowAnyException();

        assertThat(utilizada(cotaCardio.getId())).isZero();
        assertThat(utilizada(cotaHemograma.getId())).isZero();
    }

    // ==================================================================
    // V99: hora manual validada contra o periodo da cota; resposta expoe
    // horaAgendada (V97) para o comprovante.
    // ==================================================================

    /**
     * Cota com periodo (horaInicial/horaFinal) e profissional — profissional e
     * obrigatorio aqui porque {@code resolverCotaParaAgendamento} so rastreia
     * (e portanto so valida hora/calcula slot) cotas com profissional
     * executante definido; ver comentario do metodo em CotaUnidadeService.
     */
    private CotaUnidade criarCotaComPeriodo(int total, LocalTime inicio, LocalTime fim) {
        io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional prof =
                new io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional();
        prof.setNome("Dr. Periodo" + sufixo);
        prof.setAtivo(true);
        prof = profissionalRepository.saveAndFlush(prof);

        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(cardiologia);
        c.setTipoPeriodo(TipoPeriodoCota.DATA);
        c.setDataEspecifica(data);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        c.setProfissionalExecutante(prof);
        c.setHoraInicial(inicio);
        c.setHoraFinal(fim);
        return cotaRepository.saveAndFlush(c);
    }

    @Test
    @DisplayName("V99: hora manual dentro do periodo da cota e aceita e aparece na resposta")
    void horaManualDentroDoPeriodoEAceitaEExposta() {
        criarCotaComPeriodo(5, LocalTime.of(8, 0), LocalTime.of(12, 0));
        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "hora manual",
                null, Map.of(cardiologia.getCodigo(), LocalTime.of(9, 30)), null);

        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf);

        assertThat(agendamento.especialidades()).hasSize(1);
        assertThat(agendamento.especialidades().get(0).horaAgendada()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    @DisplayName("V99: hora manual fora do periodo da cota e recusada")
    void horaManualForaDoPeriodoERecusada() {
        criarCotaComPeriodo(5, LocalTime.of(8, 0), LocalTime.of(12, 0));
        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "hora fora",
                null, Map.of(cardiologia.getCodigo(), LocalTime.of(14, 0)), null);

        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do periodo");
    }

    @Test
    @DisplayName("V99: cota sem periodo definido nao restringe a hora manual")
    void cotaSemPeriodoNaoRestringeHoraManual() {
        criarCota(5); // sem horaInicial/horaFinal
        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "sem periodo",
                null, Map.of(cardiologia.getCodigo(), LocalTime.of(23, 0)), null);

        assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("V97: horario calculado por cota dinamica aparece na resposta do agendamento")
    void horarioDinamicoApareceNaResposta() {
        CotaUnidade cota = criarCotaComPeriodo(4, LocalTime.of(7, 0), LocalTime.of(11, 0));
        cota.setHorarioDinamico(true);
        cota = cotaRepository.saveAndFlush(cota);

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "dinamico", null, null, null);

        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf);

        assertThat(agendamento.especialidades()).hasSize(1);
        assertThat(agendamento.especialidades().get(0).horaAgendada()).isEqualTo(LocalTime.of(7, 0));
    }

    // ==================================================================
    // V100: profissional/horario informados pelo operador sobrescrevem, so
    // para este agendamento, o que a cota define — sem alterar a cota.
    // ==================================================================

    @Test
    @DisplayName("V100: hora manual sobrescreve o calculo de cota dinamica, sem alterar a cota")
    void horaManualSobrescreveCotaDinamica() {
        CotaUnidade cota = criarCotaComPeriodo(4, LocalTime.of(7, 0), LocalTime.of(11, 0));
        cota.setHorarioDinamico(true);
        cota = cotaRepository.saveAndFlush(cota);
        Long cotaId = cota.getId();

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "dinamico com override",
                null, Map.of(cardiologia.getCodigo(), LocalTime.of(9, 0)), null);

        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf);

        assertThat(agendamento.especialidades()).hasSize(1);
        assertThat(agendamento.especialidades().get(0).horaAgendada()).isEqualTo(LocalTime.of(9, 0));
        assertThat(cotaRepository.findById(cotaId).get().getHoraInicial()).isEqualTo(LocalTime.of(7, 0));
        assertThat(cotaRepository.findById(cotaId).get().getHoraFinal()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("V100: profissional informado pelo operador sobrescreve o da cota, sem alterar a cota")
    void profissionalManualSobrescreveOdaCotaSemAlterarACota() {
        io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional drDaCota =
                new io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional();
        drDaCota.setNome("Dr. Cota" + sufixo);
        drDaCota.setAtivo(true);
        drDaCota = profissionalRepository.saveAndFlush(drDaCota);

        io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional drOverride =
                new io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional();
        drOverride.setNome("Dr. Override" + sufixo);
        drOverride.setAtivo(true);
        drOverride = profissionalRepository.saveAndFlush(drOverride);
        Long overrideId = drOverride.getId();

        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setEspecialidade(cardiologia);
        cota.setTipoPeriodo(TipoPeriodoCota.DATA);
        cota.setDataEspecifica(data);
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cota.setProfissionalExecutante(drDaCota);
        cota = cotaRepository.saveAndFlush(cota);
        Long cotaId = cota.getId();

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "profissional override",
                null, null, Map.of(cardiologia.getCodigo(), overrideId));

        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf);

        var especialidadeAgendada = solicitacaoEspecialidadeRepository
                .findByAgendamentoSolicitacaoId(agendamento.id()).get(0);

        assertThat(especialidadeAgendada.getProfissionalExecutante().getId()).isEqualTo(overrideId);
        assertThat(agendamento.especialidades().get(0).profissionalExecutanteNome())
                .isEqualTo("Dr. Override" + sufixo);
        // A cota (o "espelho") continua com o profissional original — o override
        // e por agendamento, nunca reescreve o cadastro da cota.
        assertThat(cotaRepository.findById(cotaId).get().getProfissionalExecutante().getNome())
                .isEqualTo("Dr. Cota" + sufixo);
    }

    @Test
    @DisplayName("V100: sem override, o profissional exibido continua sendo o da cota")
    void semOverrideProfissionalExibidoEODaCota() {
        io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional drDaCota =
                new io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional();
        drDaCota.setNome("Dr. SemOverride" + sufixo);
        drDaCota.setAtivo(true);
        drDaCota = profissionalRepository.saveAndFlush(drDaCota);

        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setEspecialidade(cardiologia);
        cota.setTipoPeriodo(TipoPeriodoCota.DATA);
        cota.setDataEspecifica(data);
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cota.setProfissionalExecutante(drDaCota);
        cotaRepository.saveAndFlush(cota);

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "sem override",
                null, null, null);

        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf);

        var especialidadeAgendada = solicitacaoEspecialidadeRepository
                .findByAgendamentoSolicitacaoId(agendamento.id()).get(0);

        assertThat(especialidadeAgendada.getProfissionalExecutante()).isNull();
        assertThat(agendamento.especialidades().get(0).profissionalExecutanteNome())
                .isEqualTo("Dr. SemOverride" + sufixo);
    }

    // ==================================================================
    // V100: pools de cota isolados por profissional — agendar/cancelar para o
    // profissional A nao pode consumir/estornar a cota do profissional B.
    // ==================================================================

    private CotaUnidade criarCotaComProfissional(int total, io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional profissional) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(cardiologia);
        c.setTipoPeriodo(TipoPeriodoCota.DATA);
        c.setDataEspecifica(data);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        c.setProfissionalExecutante(profissional);
        return cotaRepository.saveAndFlush(c);
    }

    private io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional criarProfissional(String nome) {
        io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional p =
                new io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional();
        p.setNome(nome);
        p.setAtivo(true);
        return profissionalRepository.saveAndFlush(p);
    }

    @Test
    @DisplayName("V100: agendar para o profissional A consome so a cota de A, a de B fica intocada")
    void agendarParaProfissionalAConsomeSoACotaDeA() {
        var drA = criarProfissional("Dr. A" + sufixo);
        var drB = criarProfissional("Dr. B" + sufixo);
        CotaUnidade cotaA = criarCotaComProfissional(5, drA);
        CotaUnidade cotaB = criarCotaComProfissional(3, drB);

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "isolamento A",
                Map.of(cardiologia.getCodigo(), cotaA.getId()), null, null);

        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf);

        assertThat(utilizada(cotaA.getId())).isEqualTo(1);
        assertThat(utilizada(cotaB.getId())).isZero();
    }

    @Test
    @DisplayName("V100: cancelar agendamento do profissional A estorna so a cota de A")
    void cancelarAgendamentoDoProfissionalAEstornaSoACotaDeA() {
        var drA = criarProfissional("Dr. A" + sufixo);
        var drB = criarProfissional("Dr. B" + sufixo);
        CotaUnidade cotaA = criarCotaComProfissional(5, drA);
        CotaUnidade cotaB = criarCotaComProfissional(3, drB);

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "isolamento A cancelar",
                Map.of(cardiologia.getCodigo(), cotaA.getId()), null, null);

        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf);
        assertThat(utilizada(cotaA.getId())).isEqualTo(1);

        agendamentoService.deleteAgendamento(agendamento.id(), cpf);

        assertThat(utilizada(cotaA.getId())).isZero();
        assertThat(utilizada(cotaB.getId())).isZero();
    }

    @Test
    @DisplayName("V100: cota de B esgotada nao bloqueia agendamento para o profissional A")
    void cotaDeBEsgotadaNaoBloqueiaAgendamentoParaA() {
        var drA = criarProfissional("Dr. A" + sufixo);
        var drB = criarProfissional("Dr. B" + sufixo);
        CotaUnidade cotaA = criarCotaComProfissional(5, drA);
        CotaUnidade cotaB = criarCotaComProfissional(1, drB);
        cotaB.setQuantidadeUtilizada(1); // ja esgotada
        cotaB = cotaRepository.saveAndFlush(cotaB);

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "B esgotada, A livre",
                Map.of(cardiologia.getCodigo(), cotaA.getId()), null, null);

        assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf))
                .doesNotThrowAnyException();

        assertThat(utilizada(cotaA.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("V100: saldo por profissional reflete so a cota daquele profissional")
    void saldoPorProfissionalRefleteSoACotaDaquelePorfissional() {
        var drA = criarProfissional("Dr. A" + sufixo);
        var drB = criarProfissional("Dr. B" + sufixo);
        CotaUnidade cotaA = criarCotaComProfissional(5, drA);
        CotaUnidade cotaB = criarCotaComProfissional(1, drB);
        cotaB.setQuantidadeUtilizada(1); // esgotada
        cotaRepository.saveAndFlush(cotaB);

        var saldoDeA = cotaUnidadeService.consultarSaldoPorData(
                unidade.getId(), cardiologia.getId(), data, drA.getId());
        var saldoDeB = cotaUnidadeService.consultarSaldoPorData(
                unidade.getId(), cardiologia.getId(), data, drB.getId());

        assertThat(saldoDeA.saldoDisponivel()).isEqualTo(5);
        assertThat(saldoDeB.saldoDisponivel()).isEqualTo(0);
    }

    @Test
    @DisplayName("V100: sem escolha explicita entre 2+ cotas com profissional, nada e consumido (rollback antes do consumo)")
    void semEscolhaExplicitaNadaEConsumido() {
        var drA = criarProfissional("Dr. A" + sufixo);
        var drB = criarProfissional("Dr. B" + sufixo);
        CotaUnidade cotaA = criarCotaComProfissional(5, drA);
        CotaUnidade cotaB = criarCotaComProfissional(3, drB);

        String cpf = operadorDaUnidade.getCpf();
        Solicitacao s = novaSolicitacaoPendente(1);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), data, null, null, TurnoEnum.MANHA, "sem escolha v100",
                null, null, null);

        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mais de uma cota");

        assertThat(utilizada(cotaA.getId())).isZero();
        assertThat(utilizada(cotaB.getId())).isZero();
    }
}
