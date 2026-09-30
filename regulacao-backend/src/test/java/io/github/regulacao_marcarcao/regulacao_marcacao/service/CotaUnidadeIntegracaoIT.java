package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;

/**
 * Validacao da regra de cota contra o PostgreSQL real.
 *
 * Enquanto {@code CotaUnidadeSimulacaoTest} reproduz a semantica do banco em
 * memoria, este teste exercita o SQL de verdade: o UPDATE condicional gerado a
 * partir do JPQL de {@code consumirVaga}/{@code devolverVaga}, o CHECK
 * {@code ck_cota_titular_exclusivo} e os indices unicos parciais da V82.
 *
 * <b>Nao suja a base:</b> a classe e {@code @Transactional}, entao tudo o que for
 * inserido aqui sofre rollback ao fim de cada teste.
 *
 * Sufixo {@code IT} para nao rodar no {@code mvn test} padrao (surefire pega
 * {@code *Test}), ja que depende de um banco no ar. Rode com:
 * {@code ./mvnw test -Dtest=CotaUnidadeIntegracaoIT}
 */
@SpringBootTest
@Transactional
class CotaUnidadeIntegracaoIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private CotaUnidadeService service;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private ProfissionalRepository profissionalRepository;

    private Unidade unidade;
    private Especialidade cardiologia;
    private LocalDate data;
    private String periodo;

    @BeforeEach
    void setUp() {
        String sufixo = "_IT_" + System.nanoTime();

        unidade = new Unidade();
        unidade.setNome("Unidade Teste" + sufixo);
        unidade.setCodigo("UT" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        cardiologia = new Especialidade();
        cardiologia.setCodigo("CARDIO" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(0);
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        data = LocalDate.now();
        periodo = data.format(PERIODO_MENSAL);
    }

    private CotaUnidade criarCotaDaUnidade(int total) {
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

    private GrupoRelatorio novoGrupo(String rotulo) {
        String sfx = "_IT_" + System.nanoTime();
        GrupoRelatorio g = new GrupoRelatorio();
        g.setCodigo(rotulo + sfx);
        g.setNome(rotulo + sfx);
        g.setAtivo(true);
        return grupoRelatorioRepository.saveAndFlush(g);
    }

    private Especialidade novaEspecialidade(String nome, GrupoRelatorio grupo) {
        String sfx = "_IT_" + System.nanoTime();
        Especialidade e = new Especialidade();
        e.setCodigo(nome + sfx);
        e.setNome(nome + sfx);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        e.setGrupoRelatorio(grupo);
        return especialidadeRepository.saveAndFlush(e);
    }

    private CotaUnidade criarCotaPorGrupoDeEspecialidades(GrupoRelatorio grupoEsp, int total) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setGrupoEspecialidades(grupoEsp);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(periodo);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        return cotaRepository.saveAndFlush(c);
    }

    private int utilizadaNoBanco(Long cotaId) {
        return cotaRepository.findById(cotaId).orElseThrow().getQuantidadeUtilizada();
    }

    // ==================================================================
    // Cenario do enunciado, contra o banco real
    // ==================================================================

    @Test
    @DisplayName("BANCO REAL: 5 vagas mensais — os 5 primeiros passam, o 6o e bloqueado")
    void cotaMensalDaUnidadeBloqueiaNoSexto() {
        CotaUnidade cota = criarCotaDaUnidade(5);

        for (int i = 1; i <= 5; i++) {
            final int n = i;
            assertThatCode(() -> service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data))
                    .as("agendamento %d deveria passar", n)
                    .doesNotThrowAnyException();
        }

        assertThat(utilizadaNoBanco(cota.getId())).isEqualTo(5);

        assertThatThrownBy(() -> service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada");

        // O bloqueio nao incrementou nada alem do limite
        assertThat(utilizadaNoBanco(cota.getId())).isEqualTo(5);
    }

    @Test
    @DisplayName("BANCO REAL: estorno devolve a vaga e destrava o proximo agendamento")
    void estornoDevolveVaga() {
        CotaUnidade cota = criarCotaDaUnidade(2);

        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);
        assertThat(utilizadaNoBanco(cota.getId())).isEqualTo(2);

        assertThatThrownBy(() -> service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data))
                .isInstanceOf(IllegalStateException.class);

        service.estornarUtilizacao(unidade.getId(), cardiologia.getId(), data);
        assertThat(utilizadaNoBanco(cota.getId())).isEqualTo(1);

        assertThatCode(() -> service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data))
                .doesNotThrowAnyException();
        assertThat(utilizadaNoBanco(cota.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("BANCO REAL: estorno nao deixa quantidade_utilizada negativa")
    void estornoNaoFicaNegativo() {
        CotaUnidade cota = criarCotaDaUnidade(3);

        service.estornarUtilizacao(unidade.getId(), cardiologia.getId(), data);
        service.estornarUtilizacao(unidade.getId(), cardiologia.getId(), data);

        assertThat(utilizadaNoBanco(cota.getId())).isZero();
    }

    @Test
    @DisplayName("BANCO REAL: unidade sem cota cadastrada nao sofre restricao")
    void semCotaNaoRestringe() {
        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("BANCO REAL: cota inativa deixa de limitar")
    void cotaInativaNaoLimita() {
        CotaUnidade cota = criarCotaDaUnidade(1);
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);

        assertThatThrownBy(() -> service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data))
                .isInstanceOf(IllegalStateException.class);

        cota.setAtivo(false);
        cotaRepository.saveAndFlush(cota);

        assertThatCode(() -> service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data))
                .doesNotThrowAnyException();
    }

    // ==================================================================
    // Cota de grupo (V82) no banco real
    // ==================================================================

    @Test
    @DisplayName("BANCO REAL: cota do grupo limita a unidade membro (pool compartilhado)")
    void cotaDoGrupoLimitaUnidadeMembro() {
        String sufixo = "_IT_" + System.nanoTime();
        GrupoRelatorio grupo = new GrupoRelatorio();
        grupo.setCodigo("GRP" + sufixo);
        grupo.setNome("Regiao Teste" + sufixo);
        grupo.setAtivo(true);
        grupo = grupoRelatorioRepository.saveAndFlush(grupo);

        unidade.setGrupoRelatorio(grupo);
        unidadeRepository.saveAndFlush(unidade);

        CotaUnidade doGrupo = new CotaUnidade();
        doGrupo.setGrupoUnidades(grupo);
        doGrupo.setEspecialidade(cardiologia);
        doGrupo.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        doGrupo.setPeriodo(periodo);
        doGrupo.setQuantidadeTotal(2);
        doGrupo.setQuantidadeUtilizada(0);
        doGrupo.setAtivo(true);
        doGrupo = cotaRepository.saveAndFlush(doGrupo);

        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);

        assertThatThrownBy(() -> service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada");

        assertThat(utilizadaNoBanco(doGrupo.getId())).isEqualTo(2);
    }

    /**
     * O CHECK ck_cota_titular_exclusivo (V82) precisa impedir, no proprio banco,
     * uma cota sem titular ou com os dois titulares.
     */
    @Test
    @DisplayName("BANCO REAL: CHECK rejeita cota sem titular")
    void checkRejeitaCotaSemTitular() {
        CotaUnidade invalida = new CotaUnidade();
        invalida.setUnidade(null);
        invalida.setGrupoUnidades(null);
        invalida.setEspecialidade(cardiologia);
        invalida.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        invalida.setPeriodo(periodo);
        invalida.setQuantidadeTotal(1);
        invalida.setQuantidadeUtilizada(0);
        invalida.setAtivo(true);

        assertThatThrownBy(() -> cotaRepository.saveAndFlush(invalida))
                .isInstanceOf(Exception.class);
    }

    // ==================================================================
    // Compatibilidade com registros legados
    // ==================================================================

    @Test
    @DisplayName("BANCO REAL: solicitacao legada sem unidade nao e barrada por cota")
    void unidadeNulaNaoConsomeCota() {
        criarCotaDaUnidade(1);

        assertThatCode(() -> service.incrementarUtilizacao(null, cardiologia.getId(), data))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BANCO REAL: saldo sem cota configurada e ilimitado, nao zero")
    void saldoSemCotaEIlimitado() {
        var saldo = service.consultarSaldo(unidade.getId(), cardiologia.getId(), periodo);

        assertThat(saldo.disponivel()).isTrue();
        assertThat(saldo.quantidadeTotal()).isNull();
        assertThat(saldo.saldoDisponivel()).isNull();
    }

    @Test
    @DisplayName("BANCO REAL: saldo reflete o consumo da cota configurada")
    void saldoRefleteConsumo() {
        criarCotaDaUnidade(5);
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);

        var saldo = service.consultarSaldo(unidade.getId(), cardiologia.getId(), periodo);

        assertThat(saldo.quantidadeTotal()).isEqualTo(5);
        assertThat(saldo.quantidadeUtilizada()).isEqualTo(2);
        assertThat(saldo.saldoDisponivel()).isEqualTo(3);
        assertThat(saldo.disponivel()).isTrue();
    }

    // ==================================================================
    // Saldo por DATA especifica (ajuste: agendamento so mostrava por mes)
    // ==================================================================

    @Test
    @DisplayName("BANCO REAL: saldo por data, so com cota MENSAL configurada, mostra o saldo do mes")
    void saldoPorDataComSoCotaMensalMostraSaldoDoMes() {
        criarCotaDaUnidade(5);
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);

        var saldoData = service.consultarSaldoPorData(unidade.getId(), cardiologia.getId(), data);

        assertThat(saldoData.quantidadeTotal()).isEqualTo(5);
        assertThat(saldoData.quantidadeUtilizada()).isEqualTo(1);
        assertThat(saldoData.saldoDisponivel()).isEqualTo(4);
        assertThat(saldoData.disponivel()).isTrue();
    }

    @Test
    @DisplayName("BANCO REAL: saldo por data, so com cota DATA configurada, e encontrado")
    void saldoPorDataComSoCotaDeDataEEncontrado() {
        CotaUnidade cotaDeData = new CotaUnidade();
        cotaDeData.setUnidade(unidade);
        cotaDeData.setEspecialidade(cardiologia);
        cotaDeData.setTipoPeriodo(TipoPeriodoCota.DATA);
        cotaDeData.setDataEspecifica(data);
        cotaDeData.setQuantidadeTotal(3);
        cotaDeData.setQuantidadeUtilizada(0);
        cotaDeData.setAtivo(true);
        cotaRepository.saveAndFlush(cotaDeData);

        var saldoData = service.consultarSaldoPorData(unidade.getId(), cardiologia.getId(), data);

        assertThat(saldoData.quantidadeTotal()).isEqualTo(3);
        assertThat(saldoData.saldoDisponivel()).isEqualTo(3);
        assertThat(saldoData.disponivel()).isTrue();

        // A consulta por MES (ja existente) nao enxerga a cota DATA, comportamento
        // que continua exatamente como antes deste ajuste.
        var saldoMensal = service.consultarSaldo(unidade.getId(), cardiologia.getId(), periodo);
        assertThat(saldoMensal.quantidadeTotal()).isNull();
        assertThat(saldoMensal.disponivel()).isTrue();
    }

    @Test
    @DisplayName("BANCO REAL: cota MENSAL e cota DATA incidem juntas no saldo por data")
    void saldoPorDataComMensalEDataJuntasUsaAMaisRestritiva() {
        criarCotaDaUnidade(10);
        CotaUnidade cotaDeData = new CotaUnidade();
        cotaDeData.setUnidade(unidade);
        cotaDeData.setEspecialidade(cardiologia);
        cotaDeData.setTipoPeriodo(TipoPeriodoCota.DATA);
        cotaDeData.setDataEspecifica(data);
        cotaDeData.setQuantidadeTotal(2);
        cotaDeData.setQuantidadeUtilizada(0);
        cotaDeData.setAtivo(true);
        cotaRepository.saveAndFlush(cotaDeData);

        var saldoData = service.consultarSaldoPorData(unidade.getId(), cardiologia.getId(), data);

        // A cota DATA (2 vagas) e mais restritiva que a MENSAL (10) e prevalece.
        assertThat(saldoData.quantidadeTotal()).isEqualTo(2);
        assertThat(saldoData.saldoDisponivel()).isEqualTo(2);

        // As duas incidem juntas no consumo: esgotar a DATA bloqueia o agendamento
        // mesmo com saldo mensal de sobra — comportamento do motor, ja coberto por
        // cotaDeEspecialidadeEDeGrupoIncidemJuntas para outra combinacao de escopos.
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);
        assertThatThrownBy(() -> service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada");
    }

    // ==================================================================
    // Cota por GRUPO DE ESPECIALIDADES (V84) no banco real
    // ==================================================================

    @Test
    @DisplayName("BANCO REAL: cota do grupo de especialidades e saldo unico compartilhado entre elas")
    void cotaPorGrupoDeEspecialidadesCompartilhaSaldo() {
        GrupoRelatorio lab = novoGrupo("Laboratorio");
        Especialidade hemograma = novaEspecialidade("Hemograma", lab);
        Especialidade glicose = novaEspecialidade("Glicose", lab);

        CotaUnidade doGrupo = criarCotaPorGrupoDeEspecialidades(lab, 3);

        service.incrementarUtilizacao(unidade.getId(), hemograma.getId(), data);
        service.incrementarUtilizacao(unidade.getId(), glicose.getId(), data);
        service.incrementarUtilizacao(unidade.getId(), hemograma.getId(), data);

        assertThat(utilizadaNoBanco(doGrupo.getId())).isEqualTo(3);

        assertThatThrownBy(() -> service.incrementarUtilizacao(unidade.getId(), glicose.getId(), data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada")
                .hasMessageContaining(lab.getNome());
    }

    @Test
    @DisplayName("BANCO REAL: especialidade fora do grupo nao consome a cota do grupo")
    void especialidadeForaDoGrupoNaoConsome() {
        GrupoRelatorio lab = novoGrupo("Laboratorio");
        novaEspecialidade("Hemograma", lab);
        CotaUnidade doGrupo = criarCotaPorGrupoDeEspecialidades(lab, 1);

        // `cardiologia` (do setUp) nao pertence ao grupo
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);
        service.incrementarUtilizacao(unidade.getId(), cardiologia.getId(), data);

        assertThat(utilizadaNoBanco(doGrupo.getId())).isZero();
    }

    @Test
    @DisplayName("BANCO REAL: cota da especialidade e do grupo incidem juntas")
    void cotaDeEspecialidadeEDeGrupoIncidemJuntas() {
        GrupoRelatorio lab = novoGrupo("Laboratorio");
        Especialidade hemograma = novaEspecialidade("Hemograma", lab);
        Especialidade glicose = novaEspecialidade("Glicose", lab);

        CotaUnidade doGrupo = criarCotaPorGrupoDeEspecialidades(lab, 10);

        CotaUnidade deHemograma = new CotaUnidade();
        deHemograma.setUnidade(unidade);
        deHemograma.setEspecialidade(hemograma);
        deHemograma.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        deHemograma.setPeriodo(periodo);
        deHemograma.setQuantidadeTotal(2);
        deHemograma.setQuantidadeUtilizada(0);
        deHemograma.setAtivo(true);
        deHemograma = cotaRepository.saveAndFlush(deHemograma);

        service.incrementarUtilizacao(unidade.getId(), hemograma.getId(), data);
        service.incrementarUtilizacao(unidade.getId(), hemograma.getId(), data);

        // Hemograma esgotou; o grupo ainda tem 8 vagas
        final Long idHemograma = hemograma.getId();
        assertThatThrownBy(() -> service.incrementarUtilizacao(unidade.getId(), idHemograma, data))
                .isInstanceOf(IllegalStateException.class);

        assertThat(utilizadaNoBanco(deHemograma.getId())).isEqualTo(2);

        // O grupo foi consumido ANTES de a cota de Hemograma falhar, e esta classe
        // e @Transactional: a excecao e capturada pelo assertThatThrownBy, entao a
        // transacao do TESTE nao e desfeita e o consumo parcial permanece visivel.
        // Em producao o fluxo inteiro e @Transactional e a excecao desfaz tudo —
        // propriedade verificada em CotaRollbackIT#consumoParcialEDesfeito.
        assertThat(utilizadaNoBanco(doGrupo.getId())).isEqualTo(3);

        // Glicose, do mesmo grupo, continua liberada
        assertThatCode(() -> service.incrementarUtilizacao(unidade.getId(), glicose.getId(), data))
                .doesNotThrowAnyException();
        assertThat(utilizadaNoBanco(doGrupo.getId())).isEqualTo(4);
    }

    @Test
    @DisplayName("BANCO REAL: CHECK rejeita cota com especialidade E grupo de especialidades")
    void checkRejeitaEscopoDuplo() {
        GrupoRelatorio lab = novoGrupo("Laboratorio");

        CotaUnidade invalida = new CotaUnidade();
        invalida.setUnidade(unidade);
        invalida.setEspecialidade(cardiologia);
        invalida.setGrupoEspecialidades(lab);
        invalida.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        invalida.setPeriodo(periodo);
        invalida.setQuantidadeTotal(1);
        invalida.setQuantidadeUtilizada(0);
        invalida.setAtivo(true);

        assertThatThrownBy(() -> cotaRepository.saveAndFlush(invalida))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("BANCO REAL: estorno devolve a vaga na cota do grupo de especialidades")
    void estornoNaCotaDeGrupoDeEspecialidades() {
        GrupoRelatorio lab = novoGrupo("Laboratorio");
        Especialidade hemograma = novaEspecialidade("Hemograma", lab);
        CotaUnidade doGrupo = criarCotaPorGrupoDeEspecialidades(lab, 1);

        service.incrementarUtilizacao(unidade.getId(), hemograma.getId(), data);
        assertThat(utilizadaNoBanco(doGrupo.getId())).isEqualTo(1);

        service.estornarUtilizacao(unidade.getId(), hemograma.getId(), data);
        assertThat(utilizadaNoBanco(doGrupo.getId())).isZero();
    }

    // ==================================================================
    // Cota MENSAL com profissionais diferentes (V95 + indice uk_cota_mensal)
    // ==================================================================

    private Profissional novoProfissional(String nome) {
        Profissional p = new Profissional();
        p.setNome(nome + "_IT_" + System.nanoTime());
        p.setAtivo(true);
        return profissionalRepository.saveAndFlush(p);
    }

    /**
     * Caracteriza o bug do indice uk_cota_mensal (V84): ele nao inclui
     * profissional_id, diferente do uk_cota_data (corrigido na V94). O service
     * ja aceita a combinacao (exigirCotaInexistente inclui profissional na
     * chave em memoria) mas o INSERT da segunda cota quebra no indice do
     * banco. Este teste deve FALHAR contra o schema atual e PASSAR depois da
     * migration V99 que recria uk_cota_mensal incluindo profissional_id.
     */
    @Test
    @DisplayName("BANCO REAL: duas cotas MENSAL, mesmo escopo/periodo, profissionais diferentes, sao aceitas")
    void duasCotasMensaisComProfissionaisDiferentesSaoAceitas() {
        Profissional drA = novoProfissional("Dr. A");
        Profissional drB = novoProfissional("Dr. B");

        CotaUnidadeCreateDTO dtoDrA = new CotaUnidadeCreateDTO(
                unidade.getId(), null, cardiologia.getId(), null,
                TipoPeriodoCota.MENSAL, periodo, null, 5,
                drA.getId(), null, false, null,
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(12, 0), List.of("SEG"));

        assertThatCode(() -> service.criar(dtoDrA)).doesNotThrowAnyException();

        CotaUnidadeCreateDTO dtoDrB = new CotaUnidadeCreateDTO(
                unidade.getId(), null, cardiologia.getId(), null,
                TipoPeriodoCota.MENSAL, periodo, null, 5,
                drB.getId(), null, false, null,
                java.time.LocalTime.of(13, 0), java.time.LocalTime.of(17, 0), List.of("TER"));

        assertThatCode(() -> service.criar(dtoDrB))
                .as("segunda cota MENSAL do mesmo escopo/periodo, com profissional diferente, deveria ser aceita")
                .doesNotThrowAnyException();
    }
}
