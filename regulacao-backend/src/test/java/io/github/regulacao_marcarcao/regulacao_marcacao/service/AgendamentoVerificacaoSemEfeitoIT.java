package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoVerificacaoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoVerificacaoDTO.ItemVerificadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
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
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.TetoFinanceiroRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Prova, com transacoes de verdade, que a pre-verificacao do agendamento em
 * lote nao deixa nada gravado — e que ela termina sem erro mesmo quando recusa
 * itens.
 *
 * <b>Por que esta classe NAO e @Transactional:</b> mesmo motivo de
 * {@code CotaRollbackIT}. Alem do rollback, ha um defeito que so aparece aqui:
 * se a verificacao capturasse uma excecao vinda de outro servico transacional,
 * a transacao ficaria marcada para rollback e a chamada terminaria em
 * {@code UnexpectedRollbackException} (500) — dentro de um teste
 * {@code @Transactional} isso passa despercebido.
 *
 * Nenhum agendamento chega a ser gravado nesta classe (o unico POST e barrado
 * pelo teto), entao o {@code @AfterEach} so remove o que o teste criou.
 */
@SpringBootTest
class AgendamentoVerificacaoSemEfeitoIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private TetoFinanceiroRepository tetoRepository;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;

    private final List<Long> solicitacoesCriadas = new ArrayList<>();
    private final List<Long> tetosCriados = new ArrayList<>();
    private final List<Long> cotasCriadas = new ArrayList<>();
    private final List<Long> especialidadesCriadas = new ArrayList<>();
    private final List<Long> usuariosCriados = new ArrayList<>();
    private final List<Long> unidadesCriadas = new ArrayList<>();
    private final List<Long> gruposCriados = new ArrayList<>();

    private String sfx;
    private LocalDate data;
    private Unidade unidade;
    private User operador;
    private GrupoRelatorio laboratorio;

    @BeforeEach
    void setUp() {
        sfx = "_VERIF_SE_" + System.nanoTime();
        data = LocalDate.now();

        unidade = new Unidade();
        unidade.setNome("Unidade Verificacao" + sfx);
        unidade.setCodigo("UVS" + sfx);
        unidade.setAtivo(true);
        unidade = unidadeRepository.save(unidade);
        unidadesCriadas.add(unidade.getId());

        laboratorio = new GrupoRelatorio();
        laboratorio.setCodigo("LAB" + sfx);
        laboratorio.setNome("Laboratorio" + sfx);
        laboratorio.setAtivo(true);
        laboratorio = grupoRelatorioRepository.save(laboratorio);
        gruposCriados.add(laboratorio.getId());

        operador = new User();
        operador.setCpf(cpfUnico());
        operador.setNome("Operador" + sfx);
        operador.setPassword("x");
        operador.setRole(Roles.ADMIN_UNIDADE);
        operador.setAtivo(true);
        operador.setUnidade(unidade);
        operador = userRepository.save(operador);
        usuariosCriados.add(operador.getId());
    }

    @AfterEach
    void limpar() {
        solicitacoesCriadas.forEach(id -> solicitacaoRepository.deleteById(id));
        tetosCriados.forEach(id -> tetoRepository.deleteById(id));
        cotasCriadas.forEach(id -> cotaRepository.deleteById(id));
        especialidadesCriadas.forEach(id -> especialidadeRepository.deleteById(id));
        usuariosCriados.forEach(id -> userRepository.deleteById(id));
        unidadesCriadas.forEach(id -> unidadeRepository.deleteById(id));
        gruposCriados.forEach(id -> grupoRelatorioRepository.deleteById(id));
    }

    @Test
    @DisplayName("BANCO REAL: verificar um lote com recusas nao grava nada e nao estoura rollback")
    void verificarNaoGravaNada() {
        Especialidade a = especialidade("A", null);
        Especialidade b = especialidade("B", null);
        Especialidade c = especialidade("C", null);
        Especialidade d = especialidade("D", null);
        CotaUnidade cotaA = cota(a, 2);
        CotaUnidade cotaB = cota(b, 0);
        CotaUnidade cotaC = cota(c, 1);
        cota(d, 1);
        Solicitacao s = ficha(a, b, c, d);
        long agendamentosAntes = agendamentoRepository.count();

        // B: cota esgotada. C: cota escolhida invalida (erro de entrada, resolvido
        // por excecao dentro do servico de cota). D: profissional inexistente.
        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(a.getCodigo(), b.getCodigo(), c.getCodigo(), d.getCodigo()), data, null, null,
                TurnoEnum.MANHA, "sem efeito", Map.of(c.getCodigo(), Long.MAX_VALUE), null,
                Map.of(d.getCodigo(), Long.MAX_VALUE));

        AgendamentoVerificacaoDTO resultado =
                agendamentoService.verificarAgendamentoParaMultiplosExames(s.getId(), dto, operador.getCpf());

        assertThat(resultado.itens()).extracting(ItemVerificadoDTO::podeAgendar)
                .containsExactly(true, false, false, false);
        assertThat(resultado.itens()).extracting(ItemVerificadoDTO::corrigivel)
                .containsExactly(false, false, true, true);

        assertThat(cotaRepository.findById(cotaA.getId()).orElseThrow().getQuantidadeUtilizada()).isZero();
        assertThat(cotaRepository.findById(cotaB.getId()).orElseThrow().getQuantidadeUtilizada()).isZero();
        assertThat(cotaRepository.findById(cotaC.getId()).orElseThrow().getQuantidadeUtilizada()).isZero();
        assertThat(agendamentoRepository.count()).as("nenhum agendamento novo").isEqualTo(agendamentosAntes);
        assertNadaAgendado(s);
    }

    @Test
    @DisplayName("BANCO REAL: teto barra o conjunto viavel — verificacao avisa, POST recusa, nada fica gravado")
    void tetoBarraOConjuntoViavel() {
        Especialidade a = especialidade("A", "4.11");
        Especialidade b = especialidade("B", "3.33");
        CotaUnidade cotaGrupo = new CotaUnidade();
        cotaGrupo.setUnidade(unidade);
        cotaGrupo.setGrupoEspecialidades(laboratorio);
        cotaGrupo.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cotaGrupo.setPeriodo(data.format(PERIODO_MENSAL));
        cotaGrupo.setQuantidadeTotal(10);
        cotaGrupo.setQuantidadeUtilizada(0);
        cotaGrupo.setAtivo(true);
        cotaGrupo = cotaRepository.save(cotaGrupo);
        cotasCriadas.add(cotaGrupo.getId());

        TetoFinanceiro teto = new TetoFinanceiro();
        teto.setUnidade(unidade);
        teto.setGrupoEspecialidades(laboratorio);
        teto.setPeriodo(data.format(PERIODO_MENSAL));
        teto.setValorTotal(new BigDecimal("5.00"));
        teto.setValorUtilizado(new BigDecimal("0.00"));
        teto.setAtivo(true);
        teto = tetoRepository.save(teto);
        tetosCriados.add(teto.getId());

        Solicitacao s = ficha(a, b);
        final Long solicitacaoId = s.getId();
        final String cpf = operador.getCpf();
        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(a.getCodigo(), b.getCodigo()), data, null, null, TurnoEnum.MANHA, "teto", null, null, null);

        AgendamentoVerificacaoDTO resultado =
                agendamentoService.verificarAgendamentoParaMultiplosExames(solicitacaoId, dto, cpf);

        assertThat(resultado.itens()).allMatch(ItemVerificadoDTO::podeAgendar);
        assertThat(resultado.bloqueioDoLote()).contains("Teto financeiro");
        assertThat(tetoRepository.findById(teto.getId()).orElseThrow().getValorUtilizado())
                .as("a verificacao nao debita teto").isEqualByComparingTo("0.00");

        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(solicitacaoId, dto, cpf))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(resultado.bloqueioDoLote());

        assertThat(cotaRepository.findById(cotaGrupo.getId()).orElseThrow().getQuantidadeUtilizada()).isZero();
        assertThat(tetoRepository.findById(teto.getId()).orElseThrow().getValorUtilizado())
                .isEqualByComparingTo("0.00");
        assertNadaAgendado(s);
    }

    // ------------------------------------------------------------------

    private void assertNadaAgendado(Solicitacao s) {
        for (SolicitacaoEspecialidade criado : s.getEspecialidades()) {
            SolicitacaoEspecialidade item = solicitacaoEspecialidadeRepository.findById(criado.getId()).orElseThrow();
            assertThat(item.getStatus()).as("o pedido deveria continuar na fila").isEqualTo(StatusDaMarcacao.AGUARDANDO);
            assertThat(item.getAgendamentoSolicitacao()).as("nenhum agendamento deveria ter sido gravado").isNull();
        }
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    /** Com {@code valor}, a especialidade entra no grupo Laboratorio e passa a ter preco. */
    private Especialidade especialidade(String prefixo, String valor) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sfx);
        e.setNome(prefixo + sfx);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        if (valor != null) {
            e.setGrupoRelatorio(laboratorio);
            e.setValorUnitario(new BigDecimal(valor));
        }
        e = especialidadeRepository.save(e);
        especialidadesCriadas.add(e.getId());
        return e;
    }

    private CotaUnidade cota(Especialidade especialidade, int total) {
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setEspecialidade(especialidade);
        cota.setTipoPeriodo(TipoPeriodoCota.DATA);
        cota.setDataEspecifica(data);
        cota.setQuantidadeTotal(total);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cota = cotaRepository.save(cota);
        cotasCriadas.add(cota.getId());
        return cota;
    }

    private Solicitacao ficha(Especialidade... especialidades) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sfx);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        for (Especialidade especialidade : especialidades) {
            SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
            se.setSolicitacao(s);
            se.setEspecialidadeSolicitada(especialidade);
            se.setEspecialidadeCodigoLegacy(especialidade.getCodigo());
            se.setStatus(StatusDaMarcacao.AGUARDANDO);
            se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
            itens.add(se);
        }
        s.setEspecialidades(itens);
        s = solicitacaoRepository.save(s);
        solicitacoesCriadas.add(s.getId());
        return s;
    }
}
