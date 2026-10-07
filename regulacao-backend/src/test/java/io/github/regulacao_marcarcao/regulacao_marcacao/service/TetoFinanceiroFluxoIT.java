package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoFinanceiroCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoFinanceiroUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoFinanceiroViewDTO;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * FLUXO REAL do teto financeiro: passa por
 * {@code AgendamentoService.criarAgendamentoParaMultiplosExames} e
 * {@code deleteAgendamento}, o caminho da tela /agendar — nao chama
 * {@code TetoFinanceiroService.debitar} direto. Valida se o debito acontece no
 * ponto certo, se o bloqueio impede o agendamento e se o cancelamento devolve.
 *
 * <p>O operador e RECEPCAO lotado na unidade: sujeito ao teto, mas — ao
 * contrario do ADMIN_UNIDADE — nao exige cota liberada, o que isola o teto da
 * cota. A interacao entre os dois esta em {@code TetoFinanceiroRollbackIT}.
 *
 * <p>O saldo e lido por SQL: o debito e um UPDATE nativo e nao atualiza a
 * entidade ja carregada no contexto de persistencia do teste.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class TetoFinanceiroFluxoIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal PRECO_HEMOGRAMA = new BigDecimal("4.11");
    private static final BigDecimal PRECO_GLICOSE = new BigDecimal("1.85");

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoService solicitacaoService;
    @Autowired private TetoFinanceiroService tetoService;
    @Autowired private CustoEspecialidadeService custoEspecialidadeService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;

    private String sufixo;
    private Unidade unidade;
    private GrupoRelatorio laboratorio;
    private Especialidade hemograma;
    private Especialidade glicose;
    private Especialidade cardiologia;
    private Especialidade exameSemPreco;
    private User operador;
    private User admin;
    private LocalDate data;
    private String periodo;
    private int sequencia;

    @BeforeEach
    void setUp() {
        sufixo = "_TETO_IT_" + System.nanoTime();

        unidade = new Unidade();
        unidade.setNome("Unidade Teto" + sufixo);
        unidade.setCodigo("UT" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        laboratorio = new GrupoRelatorio();
        laboratorio.setCodigo("LAB" + sufixo);
        laboratorio.setNome("Laboratorio" + sufixo);
        laboratorio.setAtivo(true);
        laboratorio = grupoRelatorioRepository.saveAndFlush(laboratorio);

        hemograma = especialidade("HEMO", laboratorio, PRECO_HEMOGRAMA);
        glicose = especialidade("GLIC", laboratorio, PRECO_GLICOSE);
        exameSemPreco = especialidade("SEMPRECO", laboratorio, null);
        // Fora do grupo do teto: tem preco, mas nao debita.
        cardiologia = especialidade("CARDIO", null, new BigDecimal("10.00"));

        operador = usuario(Roles.RECEPCAO, unidade);
        admin = usuario(Roles.ADMIN, null);

        data = LocalDate.now();
        periodo = data.format(PERIODO_MENSAL);

        // Cota em quantidade do laboratorio, com folga: e o cenario real (unidade
        // tem cota E teto) e nunca e o que barra aqui. Tambem faz o agendamento e o
        // cancelamento passarem pelo consumo/estorno de cota, que limpa o contexto
        // de persistencia — o caminho em que o codigo de custo precisa funcionar.
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setGrupoEspecialidades(laboratorio);
        cota.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cota.setPeriodo(periodo);
        cota.setQuantidadeTotal(1000);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cotaRepository.saveAndFlush(cota);
    }

    private Especialidade especialidade(String prefixo, GrupoRelatorio grupo, BigDecimal preco) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        e.setGrupoRelatorio(grupo);
        e.setValorUnitario(preco);
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

    private Solicitacao solicitacao(Unidade daUnidade, Especialidade... pedidos) {
        int seq = ++sequencia;
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente " + seq + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(daUnidade);

        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        for (Especialidade pedido : pedidos) {
            SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
            se.setSolicitacao(s);
            se.setEspecialidadeSolicitada(pedido);
            se.setEspecialidadeCodigoLegacy(pedido.getCodigo());
            se.setStatus(StatusDaMarcacao.AGUARDANDO);
            se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
            itens.add(se);
        }
        s.setEspecialidades(itens);
        return solicitacaoRepository.saveAndFlush(s);
    }

    private MultiAgendamentoCreateDTO dto(LocalDate quando, Especialidade... exames) {
        return new MultiAgendamentoCreateDTO(
                java.util.Arrays.stream(exames).map(Especialidade::getCodigo).toList(),
                quando, null, null, TurnoEnum.MANHA, "teste de teto", null, null, null);
    }

    private Long agendar(User quem, LocalDate quando, Unidade daUnidade, Especialidade... exames) {
        Solicitacao s = solicitacao(daUnidade, exames);
        Long agendamentoId = agendamentoService
                .criarAgendamentoParaMultiplosExames(s.getId(), dto(quando, exames), quem.getCpf()).id();
        // Cada requisicao real tem o seu contexto de persistencia. Sem limpar, as
        // entidades deste agendamento continuariam gerenciadas no contexto unico do
        // teste e um cancelamento posterior falharia por um motivo que nao existe em producao.
        em.flush();
        em.clear();
        return agendamentoId;
    }

    private TetoFinanceiroViewDTO liberarTeto(String valor, String mes) {
        return tetoService.criar(new TetoFinanceiroCreateDTO(
                List.of(unidade.getId()), laboratorio.getId(), mes, new BigDecimal(valor)), admin.getCpf()).get(0);
    }

    private BigDecimal utilizado(Long tetoId) {
        em.flush();
        return (BigDecimal) em.createNativeQuery("SELECT valor_utilizado FROM teto_financeiro WHERE id = :id")
                .setParameter("id", tetoId)
                .getSingleResult();
    }

    /** Estado gravado dos itens de um agendamento: [valor_unitario_agendado, teto_financeiro_id]. */
    @SuppressWarnings("unchecked")
    private List<Object[]> itensDoAgendamento(Long agendamentoId) {
        em.flush();
        return em.createNativeQuery("SELECT valor_unitario_agendado, teto_financeiro_id "
                        + "FROM solicitacao_especialidade WHERE agendamento_id = :id ORDER BY valor_unitario_agendado")
                .setParameter("id", agendamentoId)
                .getResultList();
    }

    // ==================================================================

    @Test
    @DisplayName("Agendar debita a SOMA dos exames e grava em cada item o valor da epoca e o teto")
    void agendarDebitaASomaEGravaOValorDaEpoca() {
        TetoFinanceiroViewDTO teto = liberarTeto("100.00", periodo);

        Long agendamentoId = agendar(operador, data, unidade, hemograma, glicose);

        assertThat(utilizado(teto.id())).isEqualByComparingTo("5.96");
        List<Object[]> itens = itensDoAgendamento(agendamentoId);
        assertThat(itens).hasSize(2);
        assertThat((BigDecimal) itens.get(0)[0]).isEqualByComparingTo(PRECO_GLICOSE);
        assertThat((BigDecimal) itens.get(1)[0]).isEqualByComparingTo(PRECO_HEMOGRAMA);
        assertThat(itens).allSatisfy(item -> assertThat(((Number) item[1]).longValue()).isEqualTo(teto.id()));
    }

    @Test
    @DisplayName("Sem saldo o agendamento e recusado (409), nada e debitado e a mensagem NAO cita valores")
    void semSaldoBloqueiaSemRevelarValores() {
        TetoFinanceiroViewDTO teto = liberarTeto("5.00", periodo);
        Solicitacao s = solicitacao(unidade, hemograma, glicose);

        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                s.getId(), dto(data, hemograma, glicose), operador.getCpf()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Teto financeiro")
                .hasMessageContaining("esgotado")
                .hasMessageContaining("Unidade Teto")
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain("R$")
                        .doesNotContain("5,96").doesNotContain("5.96")
                        .doesNotContain("5,00").doesNotContain("5.00")
                        .doesNotContain("4,11").doesNotContain("4.11"));

        assertThat(utilizado(teto.id())).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("O saldo e consumido ate o centavo: cabe exatamente o teto, e o proximo e recusado")
    void saldoExatoCabeEOProximoNao() {
        TetoFinanceiroViewDTO teto = liberarTeto("8.22", periodo);

        agendar(operador, data, unidade, hemograma);
        assertThatCode(() -> agendar(operador, data, unidade, hemograma)).doesNotThrowAnyException();
        assertThat(utilizado(teto.id())).isEqualByComparingTo("8.22");

        assertThatThrownBy(() -> agendar(operador, data, unidade, glicose))
                .isInstanceOf(IllegalStateException.class);
        assertThat(utilizado(teto.id())).isEqualByComparingTo("8.22");
    }

    @Test
    @DisplayName("Cancelar o agendamento devolve o valor e destrava o proximo")
    void cancelarDevolveOValor() {
        TetoFinanceiroViewDTO teto = liberarTeto("5.00", periodo);
        Long agendamentoId = agendar(operador, data, unidade, hemograma);
        assertThat(utilizado(teto.id())).isEqualByComparingTo("4.11");
        assertThatThrownBy(() -> agendar(operador, data, unidade, hemograma)).isInstanceOf(IllegalStateException.class);

        agendamentoService.deleteAgendamento(agendamentoId, operador.getCpf());

        assertThat(utilizado(teto.id())).isEqualByComparingTo("0.00");
        assertThatCode(() -> agendar(operador, data, unidade, hemograma)).doesNotThrowAnyException();
        assertThat(utilizado(teto.id())).isEqualByComparingTo("4.11");
    }

    @Test
    @DisplayName("O estorno devolve o valor GRAVADO no agendamento, mesmo que o preco tenha mudado depois")
    void estornoUsaOValorDaEpoca() {
        TetoFinanceiroViewDTO teto = liberarTeto("100.00", periodo);
        Long agendamentoId = agendar(operador, data, unidade, hemograma);

        hemograma.setValorUnitario(new BigDecimal("9.00"));
        especialidadeRepository.saveAndFlush(hemograma);
        agendamentoService.deleteAgendamento(agendamentoId, operador.getCpf());

        assertThat(utilizado(teto.id())).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Sem teto cadastrado nao ha restricao, mas o valor da epoca e gravado mesmo assim")
    void semTetoNaoHaRestricao() {
        Long agendamentoId = agendar(operador, data, unidade, hemograma);

        List<Object[]> itens = itensDoAgendamento(agendamentoId);
        assertThat((BigDecimal) itens.get(0)[0]).isEqualByComparingTo(PRECO_HEMOGRAMA);
        assertThat(itens.get(0)[1]).isNull();
    }

    @Test
    @DisplayName("Especialidade fora do grupo do teto nao debita; exame sem preco nao debita nem bloqueia")
    void foraDoGrupoESemPrecoNaoDebitam() {
        TetoFinanceiroViewDTO teto = liberarTeto("1.00", periodo);

        Long agendamentoId = agendar(operador, data, unidade, cardiologia, exameSemPreco);

        assertThat(utilizado(teto.id())).isEqualByComparingTo("0.00");
        List<Object[]> itens = itensDoAgendamento(agendamentoId);
        // Ordenado pelo valor: cardiologia (10,00) primeiro, o sem preco (nulo) por ultimo.
        assertThat((BigDecimal) itens.get(0)[0]).isEqualByComparingTo("10.00");
        assertThat(itens.get(0)[1]).isNull();
        assertThat(itens.get(1)[0]).isNull();
        assertThat(itens.get(1)[1]).isNull();
    }

    @Test
    @DisplayName("ADMIN debita mas nao e bloqueado: o teto pode ser ultrapassado e o gasto fica registrado")
    void adminDebitaSemBloqueio() {
        TetoFinanceiroViewDTO teto = liberarTeto("1.00", periodo);

        assertThatCode(() -> agendar(admin, data, unidade, hemograma)).doesNotThrowAnyException();

        assertThat(utilizado(teto.id())).isEqualByComparingTo("4.11");
        // A unidade, essa sim, continua barrada.
        assertThatThrownBy(() -> agendar(operador, data, unidade, glicose)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("GESTOR tambem debita sem bloqueio; perfil de unidade SEM lotacao e barrado como os demais")
    void soAdminEGestorEscapamDoBloqueio() {
        TetoFinanceiroViewDTO teto = liberarTeto("1.00", periodo);
        User gestor = usuario(Roles.GESTOR, null);
        // Sem lotacao, a recepcao nao consome cota (regra antiga), mas o teto vale para ela.
        User recepcaoSemLotacao = usuario(Roles.RECEPCAO, null);

        assertThatCode(() -> agendar(gestor, data, unidade, hemograma)).doesNotThrowAnyException();
        assertThat(utilizado(teto.id())).isEqualByComparingTo("4.11");

        assertThatThrownBy(() -> agendar(recepcaoSemLotacao, data, unidade, glicose))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Teto financeiro");
        assertThat(utilizado(teto.id())).isEqualByComparingTo("4.11");
    }

    @Test
    @DisplayName("Cancelar em unidade com teto e SEM cota aplicavel devolve o valor (o flush nao falha)")
    void cancelarSemCotaAplicavelDevolveOTeto() {
        GrupoRelatorio imagem = new GrupoRelatorio();
        imagem.setCodigo("IMG" + sufixo);
        imagem.setNome("Imagem" + sufixo);
        imagem.setAtivo(true);
        imagem = grupoRelatorioRepository.saveAndFlush(imagem);
        Especialidade raioX = especialidade("RAIOX", imagem, new BigDecimal("7.00"));
        TetoFinanceiroViewDTO teto = tetoService.criar(new TetoFinanceiroCreateDTO(
                List.of(unidade.getId()), imagem.getId(), periodo, new BigDecimal("50.00")), admin.getCpf()).get(0);

        Long agendamentoId = agendar(operador, data, unidade, raioX);
        assertThat(utilizado(teto.id())).isEqualByComparingTo("7.00");

        agendamentoService.deleteAgendamento(agendamentoId, operador.getCpf());

        // utilizado() faz flush: e onde a exclusao falhava quando nenhuma cota era estornada.
        assertThat(utilizado(teto.id())).isEqualByComparingTo("0.00");
        assertThat(itensDoAgendamento(agendamentoId)).isEmpty();
    }

    @Test
    @DisplayName("Cancelar sem cota e sem teto nenhum tambem funciona, e o pedido volta para a fila")
    void cancelarSemCotaESemTeto() {
        Solicitacao s = solicitacao(unidade, cardiologia);
        Long agendamentoId = agendamentoService
                .criarAgendamentoParaMultiplosExames(s.getId(), dto(data, cardiologia), operador.getCpf()).id();
        em.flush();
        em.clear();

        agendamentoService.deleteAgendamento(agendamentoId, operador.getCpf());
        em.flush();

        Object[] item = (Object[]) em.createNativeQuery(
                        "SELECT status, agendamento_id, valor_unitario_agendado FROM solicitacao_especialidade "
                                + "WHERE solicitacao_id = :id")
                .setParameter("id", s.getId()).getSingleResult();
        assertThat(item[0]).isEqualTo("AGUARDANDO");
        assertThat(item[1]).isNull();
        assertThat(item[2]).isNull();
    }

    @Test
    @DisplayName("O mes do teto e o da DATA AGENDADA, nao o de hoje")
    void mesDoTetoEODaDataAgendada() {
        LocalDate mesQueVem = data.plusMonths(1).withDayOfMonth(15);
        TetoFinanceiroViewDTO tetoDoMesQueVem = liberarTeto("100.00", mesQueVem.format(PERIODO_MENSAL));

        agendar(operador, data, unidade, hemograma);
        assertThat(utilizado(tetoDoMesQueVem.id())).isEqualByComparingTo("0.00");

        agendar(operador, mesQueVem, unidade, hemograma);
        assertThat(utilizado(tetoDoMesQueVem.id())).isEqualByComparingTo("4.11");
    }

    @Test
    @DisplayName("Solicitacao sem unidade nao debita teto nenhum, mas tem o valor da epoca gravado")
    void solicitacaoSemUnidadeNaoDebita() {
        TetoFinanceiroViewDTO teto = liberarTeto("100.00", periodo);

        Long agendamentoId = agendar(admin, data, null, hemograma);

        assertThat(utilizado(teto.id())).isEqualByComparingTo("0.00");
        List<Object[]> itens = itensDoAgendamento(agendamentoId);
        assertThat((BigDecimal) itens.get(0)[0]).isEqualByComparingTo(PRECO_HEMOGRAMA);
        assertThat(itens.get(0)[1]).isNull();
    }

    @Test
    @DisplayName("Teto inativo nao limita nem e debitado")
    void tetoInativoNaoLimita() {
        TetoFinanceiroViewDTO teto = liberarTeto("1.00", periodo);
        tetoService.atualizar(teto.id(), new TetoFinanceiroUpdateDTO(new BigDecimal("1.00"), false, null));

        assertThatCode(() -> agendar(operador, data, unidade, hemograma)).doesNotThrowAnyException();
        assertThat(utilizado(teto.id())).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Remover da solicitacao um item agendado devolve o valor ao teto")
    void removerItemAgendadoDevolveOValor() {
        TetoFinanceiroViewDTO teto = liberarTeto("100.00", periodo);
        Solicitacao s = solicitacao(unidade, hemograma);
        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto(data, hemograma), operador.getCpf());
        assertThat(utilizado(teto.id())).isEqualByComparingTo("4.11");
        em.clear();

        Long itemId = ((Number) em.createNativeQuery(
                        "SELECT id FROM solicitacao_especialidade WHERE solicitacao_id = :id")
                .setParameter("id", s.getId()).getSingleResult()).longValue();
        solicitacaoService.removerEspecialidade(itemId, operador.getCpf());

        assertThat(utilizado(teto.id())).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Remover um item que ja nao esta AGENDADO (falta ou realizado) NAO devolve o teto")
    void removerItemFaltosoOuRealizadoNaoDevolve() {
        TetoFinanceiroViewDTO teto = liberarTeto("100.00", periodo);

        for (StatusDaMarcacao status : List.of(StatusDaMarcacao.CANCELADO, StatusDaMarcacao.REALIZADO)) {
            Solicitacao s = solicitacao(unidade, hemograma);
            agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto(data, hemograma), operador.getCpf());
            em.flush();
            em.clear();
            // Falta e conclusao trocam so o status: o teto e o valor continuam no item.
            em.createNativeQuery("UPDATE solicitacao_especialidade SET status = :status WHERE solicitacao_id = :id")
                    .setParameter("status", status.name())
                    .setParameter("id", s.getId())
                    .executeUpdate();
            Long itemId = ((Number) em.createNativeQuery(
                            "SELECT id FROM solicitacao_especialidade WHERE solicitacao_id = :id")
                    .setParameter("id", s.getId()).getSingleResult()).longValue();

            solicitacaoService.removerEspecialidade(itemId, operador.getCpf());
            em.flush();
            em.clear();
        }

        assertThat(utilizado(teto.id())).as("os dois exames continuam debitados").isEqualByComparingTo("8.22");
    }

    // ------------------------------------------------------------------
    // Cadastro do teto
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Nao se cria dois tetos para a mesma unidade, grupo e mes")
    void naoDuplicaTeto() {
        liberarTeto("100.00", periodo);

        assertThatThrownBy(() -> liberarTeto("50.00", periodo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Já existe teto");
    }

    @Test
    @DisplayName("Teto de grupo sem especialidades, mes invalido e valor invalido sao recusados (400)")
    void cadastroInvalidoERecusado() {
        GrupoRelatorio vazio = new GrupoRelatorio();
        vazio.setCodigo("VAZIO" + sufixo);
        vazio.setNome("Vazio" + sufixo);
        vazio.setAtivo(true);
        Long vazioId = grupoRelatorioRepository.saveAndFlush(vazio).getId();

        assertThatThrownBy(() -> tetoService.criar(new TetoFinanceiroCreateDTO(
                List.of(unidade.getId()), vazioId, periodo, new BigDecimal("10.00")), admin.getCpf()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não tem nenhuma especialidade");
        assertThatThrownBy(() -> liberarTeto("10.00", "2026-13"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Mês inválido");
        assertThatThrownBy(() -> liberarTeto("-1.00", periodo))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negativo");
        assertThatThrownBy(() -> liberarTeto("1.005", periodo))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duas casas");
    }

    @Test
    @DisplayName("Reduzir o teto para baixo do ja utilizado e recusado (409); aumentar e aceito")
    void reduzirAbaixoDoUtilizadoERecusado() {
        TetoFinanceiroViewDTO teto = liberarTeto("100.00", periodo);
        agendar(operador, data, unidade, hemograma);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> tetoService.atualizar(teto.id(),
                new TetoFinanceiroUpdateDTO(new BigDecimal("4.00"), true, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("menor do que o já utilizado");

        TetoFinanceiroViewDTO maior = tetoService.atualizar(teto.id(),
                new TetoFinanceiroUpdateDTO(new BigDecimal("200.00"), true, null));
        assertThat(maior.valorTotal()).isEqualByComparingTo("200.00");
        // Editar o teto nao pode regravar (e apagar) o que ja foi debitado.
        assertThat(utilizado(teto.id())).isEqualByComparingTo("4.11");
    }

    @Test
    @DisplayName("Alterar o preco pela tela vale para o proximo agendamento, nao para o ja feito")
    void reajusteValeSoParaOsProximos() {
        TetoFinanceiroViewDTO teto = liberarTeto("100.00", periodo);
        Long primeiro = agendar(operador, data, unidade, hemograma);

        custoEspecialidadeService.atualizar(hemograma.getId(),
                new io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.EspecialidadeCustoUpdateDTO(
                        null, new BigDecimal("6.00")), admin.getCpf());
        em.flush();
        Long segundo = agendar(operador, data, unidade, hemograma);

        assertThat((BigDecimal) itensDoAgendamento(primeiro).get(0)[0]).isEqualByComparingTo("4.11");
        assertThat((BigDecimal) itensDoAgendamento(segundo).get(0)[0]).isEqualByComparingTo("6.00");
        assertThat(utilizado(teto.id())).isEqualByComparingTo("10.11");
    }
}
