package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.TetoFinanceiroRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * As duas propriedades do teto que so aparecem com transacoes de verdade:
 *
 * <ul>
 *   <li><b>Rollback cruzado</b>: quando o teto barra o agendamento, a cota em
 *       quantidade ja consumida na mesma transacao e desfeita — senao a unidade
 *       perderia uma vaga por um agendamento que nunca aconteceu.</li>
 *   <li><b>Concorrencia</b>: dois debitos simultaneos no ultimo saldo — so um
 *       passa. E o que o UPDATE condicional garante, e nao da para provar com
 *       repositorio simulado.</li>
 * </ul>
 *
 * <b>Por que esta classe NAO e @Transactional:</b> mesmo motivo de
 * {@code CotaRollbackIT} — a transacao do teste absorveria a excecao e
 * mascararia o rollback. Aqui cada chamada ao servico abre a propria transacao.
 * Como nada e desfeito automaticamente, o {@code @AfterEach} remove tudo o que
 * foi criado, na ordem inversa das dependencias.
 */
@SpringBootTest
class TetoFinanceiroRollbackIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private TetoFinanceiroService tetoService;
    @Autowired private TetoFinanceiroRepository tetoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
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
    private Unidade unidade;
    private GrupoRelatorio laboratorio;
    private Especialidade hemograma;
    private LocalDate data;
    private String periodo;

    @BeforeEach
    void setUp() {
        sfx = "_TETO_RB_" + System.nanoTime();
        data = LocalDate.now();
        periodo = data.format(PERIODO_MENSAL);

        unidade = new Unidade();
        unidade.setNome("Unidade Teto Rollback" + sfx);
        unidade.setCodigo("UTR" + sfx);
        unidade.setAtivo(true);
        unidade = unidadeRepository.save(unidade);
        unidadesCriadas.add(unidade.getId());

        laboratorio = new GrupoRelatorio();
        laboratorio.setCodigo("LAB" + sfx);
        laboratorio.setNome("Laboratorio" + sfx);
        laboratorio.setAtivo(true);
        laboratorio = grupoRelatorioRepository.save(laboratorio);
        gruposCriados.add(laboratorio.getId());

        hemograma = new Especialidade();
        hemograma.setCodigo("HEMO" + sfx);
        hemograma.setNome("Hemograma" + sfx);
        hemograma.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        hemograma.setAtivo(true);
        hemograma.setVagas(0);
        hemograma.setGrupoRelatorio(laboratorio);
        hemograma.setValorUnitario(new BigDecimal("4.11"));
        hemograma = especialidadeRepository.save(hemograma);
        especialidadesCriadas.add(hemograma.getId());
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

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private TetoFinanceiro teto(String valorTotal) {
        TetoFinanceiro t = new TetoFinanceiro();
        t.setUnidade(unidade);
        t.setGrupoEspecialidades(laboratorio);
        t.setPeriodo(periodo);
        t.setValorTotal(new BigDecimal(valorTotal));
        t.setValorUtilizado(new BigDecimal("0.00"));
        t.setAtivo(true);
        t = tetoRepository.save(t);
        tetosCriados.add(t.getId());
        return t;
    }

    @Test
    @DisplayName("BANCO REAL: teto esgotado desfaz a cota ja consumida e nao deixa agendamento nenhum")
    void tetoEsgotadoDesfazACota() {
        User operador = new User();
        operador.setCpf(cpfUnico());
        operador.setNome("Operador" + sfx);
        operador.setPassword("x");
        operador.setRole(Roles.ADMIN_UNIDADE);
        operador.setAtivo(true);
        operador.setUnidade(unidade);
        operador = userRepository.save(operador);
        usuariosCriados.add(operador.getId());

        // Cota com saldo de sobra...
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setGrupoEspecialidades(laboratorio);
        cota.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cota.setPeriodo(periodo);
        cota.setQuantidadeTotal(10);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cota = cotaRepository.save(cota);
        cotasCriadas.add(cota.getId());

        // ...mas o teto nao comporta o exame (R$ 4,11 > R$ 1,00).
        TetoFinanceiro teto = teto("1.00");

        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sfx);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(hemograma);
        se.setEspecialidadeCodigoLegacy(hemograma.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        itens.add(se);
        s.setEspecialidades(itens);
        s = solicitacaoRepository.save(s);
        solicitacoesCriadas.add(s.getId());

        final Long solicitacaoId = s.getId();
        final Long itemId = s.getEspecialidades().get(0).getId();
        final String cpf = operador.getCpf();
        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(hemograma.getCodigo()), data, null, null, TurnoEnum.MANHA, "rollback", null, null, null);

        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(solicitacaoId, dto, cpf))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Teto financeiro");

        // O ponto do teste: a vaga da cota NAO pode ter ficado consumida.
        assertThat(cotaRepository.findById(cota.getId()).orElseThrow().getQuantidadeUtilizada())
                .as("consumo da cota deveria ter sido desfeito pelo rollback")
                .isZero();
        assertThat(tetoRepository.findById(teto.getId()).orElseThrow().getValorUtilizado())
                .isEqualByComparingTo("0.00");
        SolicitacaoEspecialidade item = solicitacaoEspecialidadeRepository.findById(itemId).orElseThrow();
        assertThat(item.getStatus()).as("o pedido deveria continuar na fila").isEqualTo(StatusDaMarcacao.AGUARDANDO);
        assertThat(item.getAgendamentoSolicitacao()).as("nenhum agendamento deveria ter sido gravado").isNull();
        assertThat(item.getValorUnitarioAgendado()).isNull();
    }

    @Test
    @DisplayName("BANCO REAL: dois debitos simultaneos no ultimo saldo — so um passa")
    void doisDebitosSimultaneosNoUltimoSaldo() throws Exception {
        // Cabe um exame de R$ 4,11, nao dois.
        TetoFinanceiro teto = teto("5.00");
        final Long tetoId = teto.getId();
        final BigDecimal valor = new BigDecimal("4.11");

        int tentativas = 8;
        ExecutorService executor = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < tentativas; i++) {
                Callable<Boolean> tentativa = () -> {
                    largada.await();
                    try {
                        tetoService.debitar(tetoId, valor, true);
                        return true;
                    } catch (IllegalStateException esgotado) {
                        return false;
                    }
                };
                resultados.add(executor.submit(tentativa));
            }
            largada.countDown();

            int passaram = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(30, TimeUnit.SECONDS)) {
                    passaram++;
                }
            }
            assertThat(passaram).as("so um debito cabe no saldo").isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(tetoRepository.findById(tetoId).orElseThrow().getValorUtilizado())
                .isEqualByComparingTo("4.11");
    }
}
