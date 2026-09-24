package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;

/**
 * Simulacao da regra de cota numa SEQUENCIA de agendamentos, em vez de chamadas
 * isoladas.
 *
 * O repositorio e um armazenamento em memoria que reproduz a semantica do banco:
 * {@code consumirVaga} so incrementa se houver saldo (UPDATE condicional atomico)
 * e {@code buscarCotasAplicaveis} aplica o mesmo filtro do JPQL nas duas
 * dimensoes (titular e escopo).
 *
 * O valor unico desta suite e a <b>concorrencia</b>, dificil de exercitar contra
 * banco. A corretude do SQL real fica em {@code CotaUnidadeIntegracaoIT}.
 *
 * {@code agendarComTransacao} reproduz o rollback do @Transactional desfazendo
 * apenas as escritas da propria chamada — restaurar um snapshot global desfaria
 * tambem o consumo de outras threads, mascarando o comportamento real.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CotaUnidadeSimulacaoTest {

    private static final Long UNIDADE_A = 1L;
    private static final Long UNIDADE_B = 2L;
    private static final Long GRUPO_UNIDADES = 50L;
    private static final Long GRUPO_LABORATORIO = 60L;
    private static final Long HEMOGRAMA = 10L;
    private static final Long GLICOSE = 11L;
    private static final Long CARDIOLOGIA = 12L; // fora do grupo Laboratorio
    private static final LocalDate DATA = LocalDate.of(2026, 5, 20);
    private static final String PERIODO = "2026-05";

    @Mock private CotaUnidadeRepository cotaRepository;
    @Mock private UnidadeRepository unidadeRepository;
    @Mock private GrupoRelatorioRepository grupoRelatorioRepository;
    @Mock private EspecialidadeRepository especialidadeRepository;

    @InjectMocks private CotaUnidadeService service;

    /** "Banco" em memoria: id da cota -> entidade. */
    private final Map<Long, CotaUnidade> banco = new HashMap<>();

    /**
     * Ids consumidos pela chamada em curso, por thread — e o que torna o rollback
     * fiel: uma transacao desfaz apenas as PROPRIAS escritas.
     */
    private final ThreadLocal<List<Long>> consumidosNaChamada = ThreadLocal.withInitial(ArrayList::new);
    private final AtomicInteger proximoId = new AtomicInteger(100);

    private Unidade unidadeA;
    private Unidade unidadeB;
    private GrupoRelatorio grupoUnidades;
    private GrupoRelatorio grupoLaboratorio;

    @BeforeEach
    void setUp() {
        banco.clear();

        grupoUnidades = grupo(GRUPO_UNIDADES, "Unidades da Regiao X");
        grupoLaboratorio = grupo(GRUPO_LABORATORIO, "Laboratorio");

        unidadeA = novaUnidade(UNIDADE_A, "Unidade A");
        unidadeB = novaUnidade(UNIDADE_B, "Unidade B");

        lenient().when(unidadeRepository.findById(UNIDADE_A)).thenReturn(Optional.of(unidadeA));
        lenient().when(unidadeRepository.findById(UNIDADE_B)).thenReturn(Optional.of(unidadeB));

        // Hemograma e Glicose pertencem ao grupo Laboratorio; Cardiologia nao.
        lenient().when(especialidadeRepository.findById(HEMOGRAMA))
                .thenReturn(Optional.of(especialidade(HEMOGRAMA, "Hemograma", grupoLaboratorio)));
        lenient().when(especialidadeRepository.findById(GLICOSE))
                .thenReturn(Optional.of(especialidade(GLICOSE, "Glicose", grupoLaboratorio)));
        lenient().when(especialidadeRepository.findById(CARDIOLOGIA))
                .thenReturn(Optional.of(especialidade(CARDIOLOGIA, "Cardiologia", null)));

        // --- semantica atomica do consumo (espelha o WHERE do UPDATE) ---
        lenient().when(cotaRepository.consumirVaga(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            synchronized (banco) {
                CotaUnidade c = banco.get(id);
                if (c == null || !c.isAtivo() || c.getQuantidadeUtilizada() >= c.getQuantidadeTotal()) {
                    return 0;
                }
                c.setQuantidadeUtilizada(c.getQuantidadeUtilizada() + 1);
                consumidosNaChamada.get().add(id);
                return 1;
            }
        });

        lenient().when(cotaRepository.devolverVaga(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            synchronized (banco) {
                CotaUnidade c = banco.get(id);
                if (c == null || c.getQuantidadeUtilizada() <= 0) {
                    return 0;
                }
                c.setQuantidadeUtilizada(c.getQuantidadeUtilizada() - 1);
                return 1;
            }
        });

        lenient().when(cotaRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(banco.get((Long) inv.getArgument(0))));

        // --- mesma logica do JPQL buscarCotasAplicaveis ---
        lenient().when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Long unidadeId = inv.getArgument(0);
                    Long grupoUnidadesId = inv.getArgument(1);
                    Long especialidadeId = inv.getArgument(2);
                    Long grupoEspecialidadesId = inv.getArgument(3);
                    String periodo = inv.getArgument(4);
                    LocalDate data = inv.getArgument(5);

                    synchronized (banco) {
                        return banco.values().stream()
                                .filter(CotaUnidade::isAtivo)
                                .filter(c -> casaTitular(c, unidadeId, grupoUnidadesId))
                                .filter(c -> casaEscopo(c, especialidadeId, grupoEspecialidadesId))
                                .filter(c -> casaPeriodo(c, periodo, data))
                                .toList();
                    }
                });
    }

    private static boolean casaTitular(CotaUnidade c, Long unidadeId, Long grupoUnidadesId) {
        Long idUnidade = c.getUnidade() != null ? c.getUnidade().getId() : null;
        Long idGrupo = c.getGrupoUnidades() != null ? c.getGrupoUnidades().getId() : null;
        return (idUnidade != null && idUnidade.equals(unidadeId))
                || (idGrupo != null && idGrupo.equals(grupoUnidadesId));
    }

    private static boolean casaEscopo(CotaUnidade c, Long especialidadeId, Long grupoEspecialidadesId) {
        Long idEsp = c.getEspecialidade() != null ? c.getEspecialidade().getId() : null;
        Long idGrupoEsp = c.getGrupoEspecialidades() != null ? c.getGrupoEspecialidades().getId() : null;
        if (idEsp == null && idGrupoEsp == null) {
            return true; // cota geral
        }
        return (idEsp != null && idEsp.equals(especialidadeId))
                || (idGrupoEsp != null && idGrupoEsp.equals(grupoEspecialidadesId));
    }

    private static boolean casaPeriodo(CotaUnidade c, String periodo, LocalDate data) {
        return c.getTipoPeriodo() == TipoPeriodoCota.MENSAL
                ? periodo.equals(c.getPeriodo())
                : data.equals(c.getDataEspecifica());
    }

    // ------------------------------------------------------------------
    // Infra da simulacao
    // ------------------------------------------------------------------

    private GrupoRelatorio grupo(Long id, String nome) {
        GrupoRelatorio g = new GrupoRelatorio();
        g.setId(id);
        g.setNome(nome);
        g.setCodigo(nome);
        g.setAtivo(true);
        return g;
    }

    private Unidade novaUnidade(Long id, String nome) {
        Unidade u = new Unidade();
        u.setId(id);
        u.setNome(nome);
        return u;
    }

    private Especialidade especialidade(Long id, String nome, GrupoRelatorio g) {
        Especialidade e = new Especialidade();
        e.setId(id);
        e.setNome(nome);
        e.setGrupoRelatorio(g);
        return e;
    }

    private CotaUnidade novaCota(int total) {
        CotaUnidade c = new CotaUnidade();
        c.setId((long) proximoId.getAndIncrement());
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(PERIODO);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        banco.put(c.getId(), c);
        return c;
    }

    /** Cota da unidade para uma especialidade especifica (ou geral, se null). */
    private CotaUnidade cotaDaUnidade(Unidade unidade, Long especialidadeId, int total) {
        CotaUnidade c = novaCota(total);
        c.setUnidade(unidade);
        if (especialidadeId != null) {
            c.setEspecialidade(especialidade(especialidadeId, "Esp " + especialidadeId, grupoLaboratorio));
        }
        return c;
    }

    /** Cota da unidade para um GRUPO de especialidades (saldo compartilhado). */
    private CotaUnidade cotaPorGrupoDeEspecialidades(Unidade unidade, GrupoRelatorio grupoEsp, int total) {
        CotaUnidade c = novaCota(total);
        c.setUnidade(unidade);
        c.setGrupoEspecialidades(grupoEsp);
        return c;
    }

    /** Cota cujo titular e um grupo de unidades (pool entre unidades). */
    private CotaUnidade cotaDoGrupoDeUnidades(Long especialidadeId, int total) {
        CotaUnidade c = novaCota(total);
        c.setGrupoUnidades(grupoUnidades);
        if (especialidadeId != null) {
            c.setEspecialidade(especialidade(especialidadeId, "Esp " + especialidadeId, grupoLaboratorio));
        }
        return c;
    }

    /** Agenda reproduzindo o limite transacional: rollback das proprias escritas. */
    private boolean agendarComTransacao(Long unidadeId, Long especialidadeId) {
        consumidosNaChamada.get().clear();
        try {
            service.incrementarUtilizacao(unidadeId, especialidadeId, DATA);
            return true;
        } catch (IllegalStateException e) {
            synchronized (banco) {
                for (Long id : consumidosNaChamada.get()) {
                    CotaUnidade c = banco.get(id);
                    c.setQuantidadeUtilizada(c.getQuantidadeUtilizada() - 1);
                }
            }
            return false;
        } finally {
            consumidosNaChamada.get().clear();
        }
    }

    // ==================================================================
    // 1. Cota por especialidade
    // ==================================================================

    @Test
    @DisplayName("Unidade A / Hemograma / 5 vagas: os 5 primeiros passam, o 6o e bloqueado")
    void unidadeEstouraOLimiteMensal() {
        CotaUnidade cota = cotaDaUnidade(unidadeA, HEMOGRAMA, 5);

        List<Boolean> resultados = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            resultados.add(agendarComTransacao(UNIDADE_A, HEMOGRAMA));
        }

        assertThat(resultados).containsExactly(true, true, true, true, true, false);
        assertThat(cota.getQuantidadeUtilizada()).isEqualTo(5);
    }

    @Test
    @DisplayName("A cota e por unidade: esgotar a Unidade A nao afeta a Unidade B")
    void cotaDeUmaUnidadeNaoAfetaOutra() {
        cotaDaUnidade(unidadeA, HEMOGRAMA, 1);
        CotaUnidade cotaB = cotaDaUnidade(unidadeB, HEMOGRAMA, 2);

        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isFalse();

        assertThat(agendarComTransacao(UNIDADE_B, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_B, HEMOGRAMA)).isTrue();
        assertThat(cotaB.getQuantidadeUtilizada()).isEqualTo(2);
    }

    @Test
    @DisplayName("Cota geral (sem escopo) limita qualquer especialidade da unidade")
    void cotaGeralLimitaQualquerEspecialidade() {
        CotaUnidade geral = cotaDaUnidade(unidadeA, null, 2);

        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, CARDIOLOGIA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, GLICOSE)).isFalse();
        assertThat(geral.getQuantidadeUtilizada()).isEqualTo(2);
    }

    // ==================================================================
    // 2. Cota por GRUPO DE ESPECIALIDADES — saldo compartilhado
    // ==================================================================

    @Test
    @DisplayName("Cota do grupo Laboratorio vale para todas as especialidades dele, com saldo unico")
    void cotaDoGrupoDeEspecialidadesCompartilhaSaldo() {
        CotaUnidade doGrupo = cotaPorGrupoDeEspecialidades(unidadeA, grupoLaboratorio, 3);

        // Hemograma e Glicose sao do grupo Laboratorio: consomem o MESMO saldo
        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, GLICOSE)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();

        assertThat(agendarComTransacao(UNIDADE_A, GLICOSE)).isFalse();
        assertThat(doGrupo.getQuantidadeUtilizada()).isEqualTo(3);
    }

    @Test
    @DisplayName("Especialidade fora do grupo nao consome a cota do grupo")
    void especialidadeForaDoGrupoNaoConsome() {
        CotaUnidade doGrupo = cotaPorGrupoDeEspecialidades(unidadeA, grupoLaboratorio, 1);

        // Cardiologia nao pertence ao grupo Laboratorio
        assertThat(agendarComTransacao(UNIDADE_A, CARDIOLOGIA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, CARDIOLOGIA)).isTrue();
        assertThat(doGrupo.getQuantidadeUtilizada()).isZero();

        // E o saldo do grupo segue intacto para quem e do grupo
        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        assertThat(doGrupo.getQuantidadeUtilizada()).isEqualTo(1);
    }

    /**
     * O caso que justifica "todas as cotas incidem juntas":
     * 10 exames de laboratorio no mes, sendo no maximo 2 de Hemograma.
     */
    @Test
    @DisplayName("Cota da especialidade e do grupo incidem juntas: a mais restritiva manda")
    void cotaDeEspecialidadeEDeGrupoIncidemJuntas() {
        CotaUnidade doGrupo = cotaPorGrupoDeEspecialidades(unidadeA, grupoLaboratorio, 10);
        CotaUnidade deHemograma = cotaDaUnidade(unidadeA, HEMOGRAMA, 2);

        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();

        // Hemograma esgotou, mesmo com 8 vagas sobrando no grupo
        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isFalse();
        assertThat(deHemograma.getQuantidadeUtilizada()).isEqualTo(2);
        // A tentativa barrada nao deixou consumo residual no grupo
        assertThat(doGrupo.getQuantidadeUtilizada()).isEqualTo(2);

        // Glicose, do mesmo grupo, continua liberada
        assertThat(agendarComTransacao(UNIDADE_A, GLICOSE)).isTrue();
        assertThat(doGrupo.getQuantidadeUtilizada()).isEqualTo(3);
    }

    @Test
    @DisplayName("Cancelar devolve a vaga em todas as cotas incidentes")
    void estornoDevolveEmTodasAsCotas() {
        CotaUnidade doGrupo = cotaPorGrupoDeEspecialidades(unidadeA, grupoLaboratorio, 5);
        CotaUnidade deHemograma = cotaDaUnidade(unidadeA, HEMOGRAMA, 5);

        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        assertThat(doGrupo.getQuantidadeUtilizada()).isEqualTo(1);
        assertThat(deHemograma.getQuantidadeUtilizada()).isEqualTo(1);

        service.estornarUtilizacao(UNIDADE_A, HEMOGRAMA, DATA);

        assertThat(doGrupo.getQuantidadeUtilizada()).isZero();
        assertThat(deHemograma.getQuantidadeUtilizada()).isZero();
    }

    // ==================================================================
    // 3. Cota de grupo de UNIDADES — pool entre unidades
    // ==================================================================

    @Test
    @DisplayName("Grupo de unidades: o saldo e compartilhado entre as unidades membros")
    void grupoDeUnidadesCompartilhaSaldo() {
        unidadeA.setGrupoRelatorio(grupoUnidades);
        unidadeB.setGrupoRelatorio(grupoUnidades);
        CotaUnidade doGrupo = cotaDoGrupoDeUnidades(HEMOGRAMA, 3);

        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_B, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();

        assertThat(agendarComTransacao(UNIDADE_B, HEMOGRAMA)).isFalse();
        assertThat(doGrupo.getQuantidadeUtilizada()).isEqualTo(3);
    }

    @Test
    @DisplayName("Unidade fora do grupo de unidades nao sofre o limite coletivo")
    void unidadeForaDoGrupoDeUnidades() {
        unidadeA.setGrupoRelatorio(grupoUnidades);
        cotaDoGrupoDeUnidades(HEMOGRAMA, 1);
        CotaUnidade cotaB = cotaDaUnidade(unidadeB, HEMOGRAMA, 2); // unidadeB sem grupo

        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isFalse();

        assertThat(agendarComTransacao(UNIDADE_B, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_B, HEMOGRAMA)).isTrue();
        assertThat(cotaB.getQuantidadeUtilizada()).isEqualTo(2);
    }

    // ==================================================================
    // 4. Compatibilidade com o legado
    // ==================================================================

    @Test
    @DisplayName("Unidade sem cota cadastrada agenda sem restricao (comportamento legado)")
    void unidadeSemCotaNaoTemLimite() {
        for (int i = 0; i < 50; i++) {
            assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        }
        assertThat(banco).isEmpty();
    }

    @Test
    @DisplayName("Solicitacao legada sem unidade vinculada nao e bloqueada por cota")
    void solicitacaoSemUnidadeNaoConsomeCota() {
        cotaDaUnidade(unidadeA, HEMOGRAMA, 1);

        assertThatCode(() -> service.incrementarUtilizacao(null, HEMOGRAMA, DATA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Especialidade sem grupo nao quebra a busca de cota de grupo")
    void especialidadeSemGrupoNaoQuebra() {
        cotaPorGrupoDeEspecialidades(unidadeA, grupoLaboratorio, 1);

        // Cardiologia nao tem grupo: grupoEspecialidadesId vem nulo
        assertThatCode(() -> service.incrementarUtilizacao(UNIDADE_A, CARDIOLOGIA, DATA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Cota desativada deixa de limitar, sem travar a operacao")
    void cotaDesativadaDeixaDeLimitar() {
        CotaUnidade cota = cotaDaUnidade(unidadeA, HEMOGRAMA, 1);

        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isFalse();

        cota.setAtivo(false);

        assertThat(agendarComTransacao(UNIDADE_A, HEMOGRAMA)).isTrue();
    }

    // ==================================================================
    // 5. Concorrencia
    // ==================================================================

    @Test
    @DisplayName("20 agendamentos simultaneos para 5 vagas: exatamente 5 passam")
    void concorrenciaNaoUltrapassaALimite() throws Exception {
        CotaUnidade cota = cotaDaUnidade(unidadeA, HEMOGRAMA, 5);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger aceitos = new AtomicInteger();
        AtomicInteger bloqueados = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    largada.await();
                    if (agendarComTransacao(UNIDADE_A, HEMOGRAMA)) {
                        aceitos.incrementAndGet();
                    } else {
                        bloqueados.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        largada.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(aceitos.get()).isEqualTo(5);
        assertThat(bloqueados.get()).isEqualTo(15);
        assertThat(cota.getQuantidadeUtilizada()).isEqualTo(5);
    }

    @Test
    @DisplayName("Concorrencia disputando a ultima vaga do grupo de especialidades: so uma vence")
    void concorrenciaNoGrupoDeEspecialidades() throws Exception {
        CotaUnidade doGrupo = cotaPorGrupoDeEspecialidades(unidadeA, grupoLaboratorio, 1);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger aceitos = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            // Especialidades diferentes, mesmo grupo: disputam o mesmo saldo
            final Long esp = (i % 2 == 0) ? HEMOGRAMA : GLICOSE;
            pool.submit(() -> {
                try {
                    largada.await();
                    if (agendarComTransacao(UNIDADE_A, esp)) {
                        aceitos.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        largada.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(aceitos.get()).isEqualTo(1);
        assertThat(doGrupo.getQuantidadeUtilizada()).isEqualTo(1);
    }

    // ==================================================================
    // 6. Mensagem de bloqueio util ao operador
    // ==================================================================

    @Test
    @DisplayName("A mensagem de bloqueio cita o grupo de especialidades quando e ele que esgotou")
    void mensagemDeBloqueioCitaOGrupo() {
        cotaPorGrupoDeEspecialidades(unidadeA, grupoLaboratorio, 1);
        agendarComTransacao(UNIDADE_A, HEMOGRAMA);

        assertThatThrownBy(() -> service.incrementarUtilizacao(UNIDADE_A, GLICOSE, DATA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unidade A")
                .hasMessageContaining("Laboratorio")
                .hasMessageContaining(PERIODO)
                .hasMessageContaining("1/1");
    }
}
