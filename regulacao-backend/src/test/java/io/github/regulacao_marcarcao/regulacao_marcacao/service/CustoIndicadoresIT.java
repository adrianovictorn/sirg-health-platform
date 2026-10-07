package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CoberturaPrecoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoEvolucaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoFaltasViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoPainelLinhaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoExecucaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.PeriodoIndicador;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.TetoFinanceiro;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.TetoFinanceiroRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * Indicadores de custo, com massa conhecida e o valor esperado calculado a mao.
 *
 * <p>Dois pontos centrais:
 * <ul>
 *   <li><b>Reconciliacao com o painel de custos</b>: o mes corrente da serie tem
 *       de ser o mesmo numero que {@link CustoPainelService} devolve para o mes.
 *       Dois numeros diferentes para a mesma coisa seriam o pior defeito aqui.</li>
 *   <li><b>Falta pelo fluxo real</b>: a falta e marcada por
 *       {@link SolicitacaoEspecialidadeService#faltouProcedimento}, que grava
 *       CANCELADO e mantem o agendamento e o valor.</li>
 * </ul>
 *
 * Os agendamentos passam pelo {@link AgendamentoService}, para o valor da epoca
 * ser gravado como em producao.
 */
@SpringBootTest
@Transactional
class CustoIndicadoresIT {

    @Autowired private CustoIndicadoresService indicadoresService;
    @Autowired private CustoPainelService painelService;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoEspecialidadeService especialidadeService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private TetoFinanceiroRepository tetoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private String sufixo;
    private Unidade unidadeA;
    private Unidade unidadeB;
    private GrupoRelatorio grupo;
    private Especialidade hemograma;
    private Especialidade exameSemPreco;
    private User admin;
    private LocalDate hoje;
    private YearMonth mesAtual;
    private String de;
    private String ate;

    @BeforeEach
    void setUp() {
        sufixo = "_IND_CUSTO_" + System.nanoTime();
        unidadeA = unidade("A");
        unidadeB = unidade("B");

        grupo = new GrupoRelatorio();
        grupo.setCodigo("GRP" + sufixo);
        grupo.setNome("Grupo" + sufixo);
        grupo.setAtivo(true);
        grupo = grupoRelatorioRepository.saveAndFlush(grupo);

        hemograma = especialidade("HEMO", new BigDecimal("4.11"));
        exameSemPreco = especialidade("SEMPRECO", null);
        admin = usuario(Roles.ADMIN);

        hoje = LocalDate.now(PeriodoIndicador.FUSO);
        mesAtual = YearMonth.from(hoje);
        de = mesAtual.atDay(1).toString();
        ate = mesAtual.atEndOfMonth().toString();
    }

    @Test
    @DisplayName("evolucao: agendado, concluido e faltas pelo valor da epoca — e o mes bate com o painel de custos")
    void evolucaoBateComOPainel() {
        agendar(unidadeA, hemograma);
        Solicitacao realizado = agendar(unidadeA, hemograma);
        marcarStatus(realizado, StatusDaMarcacao.REALIZADO);
        Solicitacao faltou = agendar(unidadeA, hemograma);
        especialidadeService.faltouProcedimento(faltou.getEspecialidades().get(0).getId(), null);
        // Realizado sem valor gravado: nao entra no custo nem nos pacientes atendidos.
        Solicitacao realizadoSemValor = agendar(unidadeA, exameSemPreco);
        marcarStatus(realizadoSemValor, StatusDaMarcacao.REALIZADO);
        // Outra unidade: fora do filtro.
        agendar(unidadeB, hemograma);
        limpar();

        CustoEvolucaoViewDTO resultado = indicadoresService.evolucao(unidadeA.getId(), admin.getCpf());

        assertThat(resultado.meses()).hasSize(12);
        CustoEvolucaoViewDTO.Mes doMes = resultado.meses().get(11);
        assertThat(doMes.mes()).isEqualTo(mesAtual.toString());
        assertThat(doMes.agendado()).isEqualByComparingTo("4.11");
        assertThat(doMes.concluido()).isEqualByComparingTo("4.11");
        assertThat(doMes.faltas()).isEqualByComparingTo("4.11");
        assertThat(doMes.pacientesAtendidos()).isEqualTo(1);
        assertThat(doMes.custoMedioPorPaciente()).isEqualByComparingTo("4.11");

        // Reconciliacao: mesmo numero do painel de custos para o mesmo mes.
        CustoPainelLinhaViewDTO painel = painelService.painel(unidadeA.getId(), null, null,
                mesAtual.atDay(1), mesAtual.atEndOfMonth(), admin.getCpf()).total();
        assertThat(doMes.agendado()).isEqualByComparingTo(painel.agendado());
        assertThat(doMes.concluido()).isEqualByComparingTo(painel.concluido());

        // Mes sem movimento: zeros, e custo medio nulo (nao zero).
        CustoEvolucaoViewDTO.Mes maisAntigo = resultado.meses().get(0);
        assertThat(maisAntigo.mes()).isEqualTo(mesAtual.minusMonths(11).toString());
        assertThat(maisAntigo.concluido()).isEqualByComparingTo("0");
        assertThat(maisAntigo.custoMedioPorPaciente()).isNull();
    }

    @Test
    @DisplayName("faltas e cancelamentos: soma o valor da epoca; sem valor e contado a parte; sem agendamento fica fora")
    void custoDeFaltasECancelamentos() {
        Solicitacao faltou = agendar(unidadeA, hemograma);
        especialidadeService.faltouProcedimento(faltou.getEspecialidades().get(0).getId(), null);
        Solicitacao faltouSemPreco = agendar(unidadeA, exameSemPreco);
        especialidadeService.faltouProcedimento(faltouSemPreco.getEspecialidades().get(0).getId(), null);
        // Cancelado sem nunca ter sido agendado: nao e falta de agendamento.
        Solicitacao canceladoNaFila = pedir(unidadeA, hemograma);
        marcarStatus(canceladoNaFila, StatusDaMarcacao.CANCELADO);
        // Agendado normal e falta de outra unidade: fora.
        agendar(unidadeA, hemograma);
        Solicitacao deOutraUnidade = agendar(unidadeB, hemograma);
        especialidadeService.faltouProcedimento(deOutraUnidade.getEspecialidades().get(0).getId(), null);
        limpar();

        CustoFaltasViewDTO resultado = indicadoresService.faltas(unidadeA.getId(), de, ate, admin.getCpf());

        assertThat(resultado.total().valor()).isEqualByComparingTo("4.11");
        assertThat(resultado.total().itensComValor()).isEqualTo(1);
        assertThat(resultado.total().itensSemValor()).isEqualTo(1);

        assertThat(resultado.porUnidade()).hasSize(1);
        assertThat(resultado.porUnidade().get(0).nome()).isEqualTo(unidadeA.getNome());
        assertThat(resultado.porUnidade().get(0).valor()).isEqualByComparingTo("4.11");

        // Maior valor primeiro: hemograma antes do exame sem preco.
        assertThat(resultado.porEspecialidade()).extracting(CustoFaltasViewDTO.Linha::nome)
                .containsExactly(hemograma.getNome(), exameSemPreco.getNome());
        assertThat(resultado.porEspecialidade().get(1).valor()).isEqualByComparingTo("0");
        assertThat(resultado.porEspecialidade().get(1).itensSemValor()).isEqualTo(1);

        // Sem filtro de unidade, a outra unidade entra.
        CustoFaltasViewDTO geral = indicadoresService.faltas(null, de, ate, admin.getCpf());
        assertThat(geral.porUnidade()).extracting(CustoFaltasViewDTO.Linha::nome)
                .contains(unidadeA.getNome(), unidadeB.getNome());
    }

    @Test
    @DisplayName("faltas: periodo sem nada devolve zero e listas vazias, nao erro")
    void semFaltas() {
        CustoFaltasViewDTO resultado = indicadoresService.faltas(unidadeA.getId(), de, ate, admin.getCpf());

        assertThat(resultado.total().valor()).isEqualByComparingTo("0");
        assertThat(resultado.total().itensComValor()).isZero();
        assertThat(resultado.porEspecialidade()).isEmpty();
        assertThat(resultado.porUnidade()).isEmpty();
    }

    @Test
    @DisplayName("cobertura de preco: catalogo, fila e agendados com e sem preco")
    void coberturaDePreco() {
        pedir(unidadeA, hemograma);
        pedir(unidadeA, exameSemPreco);
        agendar(unidadeA, hemograma);
        agendar(unidadeA, exameSemPreco);
        limpar();

        CoberturaPrecoViewDTO resultado = indicadoresService.coberturaDePreco(unidadeA.getId(), de, ate, admin.getCpf());

        assertThat(List.of(resultado.filaComPreco(), resultado.filaSemPreco())).containsExactly(1L, 1L);
        assertThat(List.of(resultado.agendadosComValor(), resultado.agendadosSemValor())).containsExactly(1L, 1L);

        // O catalogo e do municipio inteiro: confere contra a propria tabela.
        Number ativas = (Number) em.createNativeQuery("SELECT COUNT(*) FROM especialidade WHERE ativo = TRUE")
                .getSingleResult();
        Number semPreco = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM especialidade WHERE ativo = TRUE AND valor_unitario IS NULL").getSingleResult();
        assertThat(resultado.especialidadesAtivas()).isEqualTo(ativas.longValue()).isGreaterThanOrEqualTo(2);
        assertThat(resultado.especialidadesSemPreco()).isEqualTo(semPreco.longValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("execucao do teto: so tetos ativos, com o utilizado; serie de 12 meses sem buraco")
    void execucaoDoTeto() {
        teto(unidadeA, mesAtual, "100.00", "85.00", true);
        teto(unidadeA, mesAtual.minusMonths(1), "200.00", "250.00", true);
        // Inativo: fora da lista e da serie.
        teto(unidadeB, mesAtual, "50.00", "50.00", false);
        limpar();

        TetoExecucaoViewDTO resultado = indicadoresService.execucaoDoTeto(unidadeA.getId(), de, ate, admin.getCpf());

        assertThat(resultado.tetos()).hasSize(1);
        TetoExecucaoViewDTO.Teto doMes = resultado.tetos().get(0);
        assertThat(doMes.unidadeNome()).isEqualTo(unidadeA.getNome());
        assertThat(doMes.grupoNome()).isEqualTo(grupo.getNome());
        assertThat(doMes.periodo()).isEqualTo(mesAtual.toString());
        assertThat(doMes.valorTotal()).isEqualByComparingTo("100.00");
        assertThat(doMes.valorUtilizado()).isEqualByComparingTo("85.00");

        assertThat(resultado.serie()).hasSize(12);
        TetoExecucaoViewDTO.Mes atual = resultado.serie().get(11);
        assertThat(atual.mes()).isEqualTo(mesAtual.toString());
        assertThat(atual.liberado()).isEqualByComparingTo("100.00");
        assertThat(atual.utilizado()).isEqualByComparingTo("85.00");
        assertThat(atual.tetos()).isEqualTo(1);
        // Utilizado acima do liberado e possivel (ADMIN/GESTOR debitam sem bloqueio) e aparece como esta.
        TetoExecucaoViewDTO.Mes anterior = resultado.serie().get(10);
        assertThat(anterior.liberado()).isEqualByComparingTo("200.00");
        assertThat(anterior.utilizado()).isEqualByComparingTo("250.00");
        // Mes sem teto: zero e nenhum teto.
        assertThat(resultado.serie().get(0).tetos()).isZero();
        assertThat(resultado.serie().get(0).liberado()).isEqualByComparingTo("0");

        // Unidade sem teto ativo: lista vazia.
        assertThat(indicadoresService.execucaoDoTeto(unidadeB.getId(), de, ate, admin.getCpf()).tetos()).isEmpty();
    }

    @Test
    @DisplayName("segunda barreira: valor em reais nao sai para quem nao e ADMIN nem GESTOR")
    void soGestaoPassaPeloServico() {
        Long id = unidadeA.getId();
        String gestor = usuario(Roles.GESTOR).getCpf();
        indicadoresService.evolucao(id, gestor);
        indicadoresService.coberturaDePreco(id, de, ate, gestor);

        for (Roles role : List.of(Roles.RECEPCAO, Roles.ADMIN_UNIDADE, Roles.COORD_TRANSPORTE, Roles.MEDICO)) {
            String cpf = usuario(role).getCpf();
            assertThatThrownBy(() -> indicadoresService.evolucao(id, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> indicadoresService.faltas(id, de, ate, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> indicadoresService.coberturaDePreco(id, de, ate, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> indicadoresService.execucaoDoTeto(id, de, ate, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> indicadoresService.faltas(id, "2026-02-30", ate, admin.getCpf()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private Unidade unidade(String letra) {
        Unidade u = new Unidade();
        u.setNome("Unidade " + letra + sufixo);
        u.setCodigo("U" + letra + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private User usuario(Roles role) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        if (role == Roles.RECEPCAO || role == Roles.ADMIN_UNIDADE || role == Roles.MEDICO) {
            u.setUnidade(unidadeA);
        }
        return userRepository.saveAndFlush(u);
    }

    private Especialidade especialidade(String prefixo, BigDecimal preco) {
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

    /** Pedido na fila (AGUARDANDO). */
    private Solicitacao pedir(Unidade daUnidade, Especialidade exame) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(daUnidade);

        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(exame);
        se.setEspecialidadeCodigoLegacy(exame.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        itens.add(se);
        s.setEspecialidades(itens);
        return solicitacaoRepository.saveAndFlush(s);
    }

    /** Pedido agendado pelo fluxo real (grava o valor da epoca), para hoje. */
    private Solicitacao agendar(Unidade daUnidade, Especialidade exame) {
        Solicitacao s = pedir(daUnidade, exame);
        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(List.of(exame.getCodigo()), hoje, null, null, TurnoEnum.MANHA,
                        "indicadores de custo", null, null, null),
                admin.getCpf());
        em.flush();
        return s;
    }

    private void marcarStatus(Solicitacao s, StatusDaMarcacao status) {
        em.flush();
        em.createNativeQuery("UPDATE solicitacao_especialidade SET status = :status WHERE solicitacao_id = :id")
                .setParameter("status", status.name())
                .setParameter("id", s.getId())
                .executeUpdate();
    }

    private void teto(Unidade daUnidade, YearMonth mes, String total, String utilizado, boolean ativo) {
        TetoFinanceiro t = new TetoFinanceiro();
        t.setUnidade(daUnidade);
        t.setGrupoEspecialidades(grupo);
        t.setPeriodo(mes.toString());
        t.setValorTotal(new BigDecimal(total));
        t.setValorUtilizado(new BigDecimal(utilizado));
        t.setAtivo(ativo);
        tetoRepository.saveAndFlush(t);
    }
}
