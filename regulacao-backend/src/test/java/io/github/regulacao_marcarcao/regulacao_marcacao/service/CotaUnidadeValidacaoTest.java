package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Agenda;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaOcorrencia;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;

/**
 * Validacoes de CADASTRO de cota — as regras que recusam uma configuracao
 * incoerente antes de ela chegar ao banco.
 *
 * O comportamento de consumo fica em {@code CotaUnidadeSimulacaoTest} (sequencias
 * e concorrencia) e {@code CotaUnidadeIntegracaoIT} (SQL real).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CotaUnidadeValidacaoTest {

    private static final Long UNIDADE_ID = 1L;
    private static final Long GRUPO_UNIDADES_ID = 50L;
    private static final Long GRUPO_LABORATORIO_ID = 60L;
    private static final Long HEMOGRAMA_ID = 10L;
    private static final String PERIODO = "2026-05";

    @Mock private CotaUnidadeRepository cotaRepository;
    @Mock private UnidadeRepository unidadeRepository;
    @Mock private GrupoRelatorioRepository grupoRelatorioRepository;
    @Mock private EspecialidadeRepository especialidadeRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private LocalAgendamentoRepository localAgendamentoRepository;

    @InjectMocks private CotaUnidadeService service;

    private Unidade unidade;
    private GrupoRelatorio grupoLaboratorio;

    @BeforeEach
    void setUp() {
        unidade = new Unidade();
        unidade.setId(UNIDADE_ID);
        unidade.setNome("Unidade A");

        grupoLaboratorio = new GrupoRelatorio();
        grupoLaboratorio.setId(GRUPO_LABORATORIO_ID);
        grupoLaboratorio.setNome("Laboratorio");

        Especialidade hemograma = new Especialidade();
        hemograma.setId(HEMOGRAMA_ID);
        hemograma.setNome("Hemograma");

        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(unidade));
        when(especialidadeRepository.findById(HEMOGRAMA_ID)).thenReturn(Optional.of(hemograma));
        when(grupoRelatorioRepository.findById(GRUPO_LABORATORIO_ID)).thenReturn(Optional.of(grupoLaboratorio));
        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of());
        when(cotaRepository.save(any(CotaUnidade.class))).thenAnswer(inv -> {
            CotaUnidade c = inv.getArgument(0);
            c.setId(999L);
            return c;
        });
    }

    private CotaUnidadeCreateDTO dto(Long unidadeId, Long grupoUnidadesId,
            Long especialidadeId, Long grupoEspecialidadesId, String periodo, Integer qtd) {
        return new CotaUnidadeCreateDTO(unidadeId, grupoUnidadesId, especialidadeId,
                grupoEspecialidadesId, TipoPeriodoCota.MENSAL, periodo, null, qtd,
                null, null, false, null, null, null, null);
    }

    // ==================================================================
    // Titular — exatamente um
    // ==================================================================

    @Test
    @DisplayName("Titular ausente e recusado")
    void semTitularERecusado() {
        assertThatThrownBy(() -> service.criar(dto(null, null, HEMOGRAMA_ID, null, PERIODO, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exatamente um titular");
    }

    @Test
    @DisplayName("Dois titulares ao mesmo tempo sao recusados")
    void doisTitularesSaoRecusados() {
        assertThatThrownBy(() -> service.criar(
                dto(UNIDADE_ID, GRUPO_UNIDADES_ID, HEMOGRAMA_ID, null, PERIODO, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exatamente um titular");
    }

    // ==================================================================
    // Escopo — no maximo um
    // ==================================================================

    @Test
    @DisplayName("Especialidade e grupo de especialidades juntos sao recusados")
    void doisEscoposSaoRecusados() {
        assertThatThrownBy(() -> service.criar(
                dto(UNIDADE_ID, null, HEMOGRAMA_ID, GRUPO_LABORATORIO_ID, PERIODO, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apenas um escopo");
    }

    @Test
    @DisplayName("Escopo por especialidade e aceito")
    void escopoPorEspecialidadeEAceito() {
        when(especialidadeRepository.countByGrupoRelatorioId(GRUPO_LABORATORIO_ID)).thenReturn(172L);

        assertThatCode(() -> service.criar(dto(UNIDADE_ID, null, HEMOGRAMA_ID, null, PERIODO, 5)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Escopo por grupo de especialidades e aceito quando o grupo tem especialidades")
    void escopoPorGrupoEAceito() {
        when(especialidadeRepository.countByGrupoRelatorioId(GRUPO_LABORATORIO_ID)).thenReturn(172L);

        assertThatCode(() -> service.criar(dto(UNIDADE_ID, null, null, GRUPO_LABORATORIO_ID, PERIODO, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sem escopo nenhum = cota geral, aceita")
    void semEscopoECotaGeral() {
        assertThatCode(() -> service.criar(dto(UNIDADE_ID, null, null, null, PERIODO, 20)))
                .doesNotThrowAnyException();
    }

    /**
     * Cota num grupo vazio nao limitaria nada — seria uma configuracao silenciosamente
     * inofensiva, que o administrador so descobriria quando o limite nao fosse aplicado.
     */
    @Test
    @DisplayName("Cota em grupo de especialidades SEM especialidades e recusada")
    void grupoVazioERecusado() {
        when(especialidadeRepository.countByGrupoRelatorioId(GRUPO_LABORATORIO_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.criar(dto(UNIDADE_ID, null, null, GRUPO_LABORATORIO_ID, PERIODO, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao possui especialidades");
    }

    // ==================================================================
    // Periodo e quantidade
    // ==================================================================

    @Test
    @DisplayName("Periodo mensal fora do formato YYYY-MM e recusado")
    void periodoInvalidoERecusado() {
        assertThatThrownBy(() -> service.criar(dto(UNIDADE_ID, null, HEMOGRAMA_ID, null, "05/2026", 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM");
    }

    @Test
    @DisplayName("Cota por DATA exige data especifica")
    void cotaPorDataExigeData() {
        var semData = new CotaUnidadeCreateDTO(UNIDADE_ID, null, HEMOGRAMA_ID, null,
                TipoPeriodoCota.DATA, null, null, 5, null, null, false, null, null, null, null);

        assertThatThrownBy(() -> service.criar(semData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Data especifica");
    }

    @Test
    @DisplayName("Quantidade negativa e recusada")
    void quantidadeNegativaERecusada() {
        assertThatThrownBy(() -> service.criar(dto(UNIDADE_ID, null, HEMOGRAMA_ID, null, PERIODO, -1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior ou igual a zero");
    }

    // ==================================================================
    // Duplicidade
    // ==================================================================

    @Test
    @DisplayName("Cota duplicada (mesmo titular, escopo e periodo) e recusada")
    void duplicadaERecusada() {
        Especialidade hemograma = new Especialidade();
        hemograma.setId(HEMOGRAMA_ID);
        hemograma.setNome("Hemograma");

        CotaUnidade existente = new CotaUnidade();
        existente.setId(1L);
        existente.setUnidade(unidade);
        existente.setEspecialidade(hemograma);
        existente.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        existente.setPeriodo(PERIODO);
        existente.setQuantidadeTotal(5);
        existente.setQuantidadeUtilizada(0);
        existente.setAtivo(true);

        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.criar(dto(UNIDADE_ID, null, HEMOGRAMA_ID, null, PERIODO, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ja existe cota");
    }

    @Test
    @DisplayName("Cota de grupo nao colide com cota de especialidade do mesmo periodo")
    void grupoEEspecialidadeNaoColidem() {
        Especialidade hemograma = new Especialidade();
        hemograma.setId(HEMOGRAMA_ID);
        hemograma.setNome("Hemograma");

        CotaUnidade daEspecialidade = new CotaUnidade();
        daEspecialidade.setId(1L);
        daEspecialidade.setUnidade(unidade);
        daEspecialidade.setEspecialidade(hemograma);
        daEspecialidade.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        daEspecialidade.setPeriodo(PERIODO);
        daEspecialidade.setQuantidadeTotal(2);
        daEspecialidade.setQuantidadeUtilizada(0);
        daEspecialidade.setAtivo(true);

        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(daEspecialidade));
        when(especialidadeRepository.countByGrupoRelatorioId(GRUPO_LABORATORIO_ID)).thenReturn(172L);

        // Escopos diferentes: a cota do grupo pode conviver com a da especialidade
        assertThatCode(() -> service.criar(dto(UNIDADE_ID, null, null, GRUPO_LABORATORIO_ID, PERIODO, 10)))
                .doesNotThrowAnyException();
    }

    // ==================================================================
    // Atualizacao
    // ==================================================================

    @Test
    @DisplayName("Reduzir a cota abaixo do ja utilizado e recusado")
    void naoPermiteReduzirAbaixoDoUtilizado() {
        CotaUnidade cota = new CotaUnidade();
        cota.setId(100L);
        cota.setUnidade(unidade);
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(4);
        cota.setAtivo(true);
        when(cotaRepository.findById(100L)).thenReturn(Optional.of(cota));

        var dto = new CotaUnidadeUpdateDTO(UNIDADE_ID, null, null, null, TipoPeriodoCota.MENSAL, PERIODO, null, 2, true,
                null, null, false, null, null, null, null);
        assertThatThrownBy(() -> service.atualizar(100L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja utilizado");

        verify(cotaRepository, never()).save(any(CotaUnidade.class));
    }

    @Test
    @DisplayName("Cota gerada por agenda recusa edicao direta")
    void cotaDeAgendaRecusaEdicaoDireta() {
        CotaUnidade cota = new CotaUnidade();
        cota.setId(200L);
        cota.setUnidade(unidade);
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cota.setOrigem(io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum.AGENDA);
        when(cotaRepository.findById(200L)).thenReturn(Optional.of(cota));

        var dto = new CotaUnidadeUpdateDTO(UNIDADE_ID, null, null, null, TipoPeriodoCota.MENSAL, PERIODO, null, 10, true,
                null, null, false, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(200L, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agenda de origem");

        verify(cotaRepository, never()).save(any(CotaUnidade.class));
    }

    @Test
    @DisplayName("Estorno sem unidade ou sem data nao faz nada")
    void estornoSemParametrosNaoFazNada() {
        service.estornarUtilizacao(null, HEMOGRAMA_ID, LocalDate.now());
        service.estornarUtilizacao(UNIDADE_ID, HEMOGRAMA_ID, null);

        verify(cotaRepository, never()).devolverVaga(any());
    }

    @Test
    @DisplayName("Atualizacao troca titular e escopo com sucesso (substituicao total)")
    void atualizarTrocaTitularEEscopoComSucesso() {
        Especialidade hemograma = new Especialidade();
        hemograma.setId(HEMOGRAMA_ID);
        hemograma.setNome("Hemograma");

        CotaUnidade cota = new CotaUnidade();
        cota.setId(300L);
        cota.setUnidade(unidade);
        cota.setEspecialidade(hemograma);
        cota.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cota.setPeriodo(PERIODO);
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        when(cotaRepository.findById(300L)).thenReturn(Optional.of(cota));
        when(cotaRepository.findByGrupoUnidadesId(GRUPO_UNIDADES_ID)).thenReturn(List.of());

        GrupoRelatorio grupoUnidades = new GrupoRelatorio();
        grupoUnidades.setId(GRUPO_UNIDADES_ID);
        grupoUnidades.setNome("Unidades do polo");
        when(grupoRelatorioRepository.findById(GRUPO_UNIDADES_ID)).thenReturn(Optional.of(grupoUnidades));

        // Troca o titular de unidade para grupo de unidades, e o escopo de
        // "Hemograma" para geral — nada disso era editavel antes da V90.
        var dto = new CotaUnidadeUpdateDTO(
                null, GRUPO_UNIDADES_ID, null, null, TipoPeriodoCota.MENSAL, PERIODO, null, 10, true,
                null, null, false, null, null, null, null);

        CotaUnidadeViewDTO resultado = service.atualizar(300L, dto);

        assertThat(resultado.unidadeId()).isNull();
        assertThat(resultado.grupoUnidadesId()).isEqualTo(GRUPO_UNIDADES_ID);
        assertThat(resultado.especialidadeId()).isNull();
        assertThat(resultado.quantidadeTotal()).isEqualTo(10);
    }

    @Test
    @DisplayName("Atualizacao recusa titular colidindo com outra cota, exceto ela mesma")
    void atualizarNaoColideComSigoMesma() {
        Especialidade hemograma = new Especialidade();
        hemograma.setId(HEMOGRAMA_ID);
        hemograma.setNome("Hemograma");

        CotaUnidade cota = new CotaUnidade();
        cota.setId(300L);
        cota.setUnidade(unidade);
        cota.setEspecialidade(hemograma);
        cota.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cota.setPeriodo(PERIODO);
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        when(cotaRepository.findById(300L)).thenReturn(Optional.of(cota));
        // A propria cota (id 300) aparece na busca por titular — precisa ser excluida
        // da checagem de duplicidade, senao toda edicao "colidiria" consigo mesma.
        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(cota));

        var dto = new CotaUnidadeUpdateDTO(
                UNIDADE_ID, null, HEMOGRAMA_ID, null, TipoPeriodoCota.MENSAL, PERIODO, null, 8, true,
                null, null, false, null, null, null, null);

        assertThatCode(() -> service.atualizar(300L, dto)).doesNotThrowAnyException();
    }

    // ==================================================================
    // criarParaAgenda (V90) — nao duplica o motor de saldo, reusa a mesma
    // checagem de duplicidade de criar()/atualizar()
    // ==================================================================

    @Test
    @DisplayName("criarParaAgenda recusa colisao com cota ja existente para a mesma data/escopo")
    void criarParaAgendaRecusaColisaoComCotaExistente() {
        Especialidade hemograma = new Especialidade();
        hemograma.setId(HEMOGRAMA_ID);
        hemograma.setNome("Hemograma");

        LocalDate data = LocalDate.of(2026, 10, 5);

        CotaUnidade existente = new CotaUnidade();
        existente.setId(1L);
        existente.setUnidade(unidade);
        existente.setEspecialidade(hemograma);
        existente.setTipoPeriodo(TipoPeriodoCota.DATA);
        existente.setDataEspecifica(data);
        existente.setQuantidadeTotal(5);
        existente.setQuantidadeUtilizada(0);
        existente.setAtivo(true);
        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(existente));

        Agenda agenda = new Agenda();
        agenda.setId(500L);
        AgendaOcorrencia ocorrencia = new AgendaOcorrencia();
        ocorrencia.setId(10L);
        ocorrencia.setAgenda(agenda);
        ocorrencia.setData(data);

        // A colisao precisa virar erro de negocio (400), nunca o erro cru do
        // indice unico uk_cota_data (V84) — senao o operador leva um 500 opaco
        // quando a agenda materializa uma data que ja tinha cota manual.
        assertThatThrownBy(() -> service.criarParaAgenda(ocorrencia, unidade, hemograma, null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ja existe cota");

        verify(cotaRepository, never()).save(any(CotaUnidade.class));
    }

    @Test
    @DisplayName("criarParaAgenda cria cota com origem AGENDA e vinculo a ocorrencia")
    void criarParaAgendaCriaComOrigemAgenda() {
        LocalDate data = LocalDate.of(2026, 10, 5);
        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of());

        Agenda agenda = new Agenda();
        agenda.setId(500L);
        AgendaOcorrencia ocorrencia = new AgendaOcorrencia();
        ocorrencia.setId(10L);
        ocorrencia.setAgenda(agenda);
        ocorrencia.setData(data);

        CotaUnidade criada = service.criarParaAgenda(ocorrencia, unidade, null, grupoLaboratorio, 7);

        assertThat(criada.getOrigem())
                .isEqualTo(io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum.AGENDA);
        assertThat(criada.getAgendaOcorrencia()).isEqualTo(ocorrencia);
        assertThat(criada.getGrupoEspecialidades()).isEqualTo(grupoLaboratorio);
        assertThat(criada.getQuantidadeTotal()).isEqualTo(7);
        assertThat(criada.getTipoPeriodo()).isEqualTo(TipoPeriodoCota.DATA);
        assertThat(criada.getDataEspecifica()).isEqualTo(data);
    }

    // ==================================================================
    // Espelho de atendimento (V92) — profissional, horario e local
    // ==================================================================

    private CotaUnidadeCreateDTO dtoComEspelho(Long profissionalId, Long localAgendamentoId,
            boolean horarioDinamico, Integer tempoMedio, java.time.LocalTime horaInicial,
            java.time.LocalTime horaFinal, java.time.LocalDate dataEspecifica) {
        return new CotaUnidadeCreateDTO(UNIDADE_ID, null, HEMOGRAMA_ID, null,
                TipoPeriodoCota.DATA, null, dataEspecifica, 5,
                profissionalId, localAgendamentoId, horarioDinamico, tempoMedio, horaInicial, horaFinal, null);
    }

    private CotaUnidadeCreateDTO dtoMensalComEspelho(Long profissionalId, Long localAgendamentoId,
            boolean horarioDinamico, Integer tempoMedio, java.time.LocalTime horaInicial,
            java.time.LocalTime horaFinal, List<String> diasSemana) {
        return new CotaUnidadeCreateDTO(UNIDADE_ID, null, HEMOGRAMA_ID, null,
                TipoPeriodoCota.MENSAL, PERIODO, null, 5,
                profissionalId, localAgendamentoId, horarioDinamico, tempoMedio, horaInicial, horaFinal, diasSemana);
    }

    @Test
    @DisplayName("Cota sem profissional/local/horario continua aceita (cota geral)")
    void cotaSemEspelhoContinuaAceita() {
        assertThatCode(() -> service.criar(dto(UNIDADE_ID, null, HEMOGRAMA_ID, null, PERIODO, 5)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("V95: profissional em cota MENSAL sem dias da semana e recusado")
    void espelhoEmMensalSemDiasSemanaERecusado() {
        var dto = new CotaUnidadeCreateDTO(UNIDADE_ID, null, HEMOGRAMA_ID, null,
                TipoPeriodoCota.MENSAL, PERIODO, null, 5,
                7L, null, false, null, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(12, 0), null);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dias da semana");
    }

    @Test
    @DisplayName("V95: dias da semana numa cota DATA sao recusados (ja e um dia especifico)")
    void diasSemanaEmCotaDataERecusado() {
        var base = dtoComEspelho(null, null, false, null, null, null, LocalDate.of(2026, 6, 1));
        var dto = new CotaUnidadeCreateDTO(base.unidadeId(), base.grupoUnidadesId(), base.especialidadeId(),
                base.grupoEspecialidadesId(), base.tipoPeriodo(), base.periodo(), base.dataEspecifica(),
                base.quantidadeTotal(), base.profissionalId(), base.localAgendamentoId(), base.horarioDinamico(),
                base.tempoMedioAtendimentoMinutos(), base.horaInicial(), base.horaFinal(), List.of("SEG"));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MENSAL");
    }

    @Test
    @DisplayName("V95: profissional em cota MENSAL com dias da semana e aceito")
    void espelhoEmMensalComDiasSemanaEAceito() {
        Profissional dr = new Profissional();
        dr.setId(7L);
        dr.setNome("Dr. A");
        when(profissionalRepository.findById(7L)).thenReturn(Optional.of(dr));

        var dto = dtoMensalComEspelho(7L, null, false, null,
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(12, 0), List.of("SEG", "QUA"));

        CotaUnidadeViewDTO resultado = service.criar(dto);

        assertThat(resultado.profissionalId()).isEqualTo(7L);
        assertThat(resultado.diasSemana()).containsExactly("SEG", "QUA");
    }

    @Test
    @DisplayName("V95: dias da semana sozinhos (sem profissional) em cota MENSAL sao aceitos")
    void diasSemanaSemProfissionalEmMensalEAceito() {
        var dto = dtoMensalComEspelho(null, null, false, null, null, null, List.of("SAB", "DOM"));

        CotaUnidadeViewDTO resultado = service.criar(dto);

        assertThat(resultado.profissionalId()).isNull();
        assertThat(resultado.diasSemana()).containsExactly("SAB", "DOM");
    }

    @Test
    @DisplayName("V95: sigla de dia da semana invalida e recusada")
    void siglaDeDiaInvalidaERecusada() {
        var dto = dtoMensalComEspelho(null, null, false, null, null, null, List.of("FERIADO"));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalido");
    }

    @Test
    @DisplayName("Horario dinamico sem hora inicial/final e recusado")
    void horarioDinamicoSemHoraInicialFinalERecusado() {
        var dto = dtoComEspelho(7L, null, true, null, null, null, LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hora inicial e hora final");
    }

    @Test
    @DisplayName("Horario dinamico com hora inicial e final e aceito, sem exigir tempo medio")
    void horarioDinamicoComHoraInicialFinalEAceito() {
        Profissional dr = new Profissional();
        dr.setId(7L);
        dr.setNome("Dr. A");
        when(profissionalRepository.findById(7L)).thenReturn(Optional.of(dr));

        var dto = dtoComEspelho(7L, null, true, null,
                java.time.LocalTime.of(7, 0), java.time.LocalTime.of(11, 0), LocalDate.of(2026, 6, 1));

        CotaUnidadeViewDTO resultado = service.criar(dto);

        assertThat(resultado.horarioDinamico()).isTrue();
        assertThat(resultado.horaInicial()).isEqualTo(java.time.LocalTime.of(7, 0));
        assertThat(resultado.horaFinal()).isEqualTo(java.time.LocalTime.of(11, 0));
        assertThat(resultado.tempoMedioAtendimentoMinutos()).isNull();
    }

    @Test
    @DisplayName("Hora final antes ou igual a hora inicial e recusada")
    void horaFinalAntesDaInicialERecusada() {
        var dto = dtoComEspelho(null, null, false, null,
                java.time.LocalTime.of(12, 0), java.time.LocalTime.of(8, 0), LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    @DisplayName("Hora inicial sem hora final (ou vice-versa) e recusada")
    void horarioParcialERecusado() {
        var dto = dtoComEspelho(null, null, false, null,
                java.time.LocalTime.of(8, 0), null, LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hora inicial e hora final");
    }

    @Test
    @DisplayName("Cota com profissional, horario fixo e local e aceita")
    void cotaComProfissionalHorarioFixoELocalEAceita() {
        Profissional dr = new Profissional();
        dr.setId(7L);
        dr.setNome("Dr. A");
        when(profissionalRepository.findById(7L)).thenReturn(Optional.of(dr));

        LocalAgendamento local = new LocalAgendamento();
        local.setId(3L);
        local.setNomeLocal("UBS Central");
        when(localAgendamentoRepository.findById(3L)).thenReturn(Optional.of(local));

        var dto = dtoComEspelho(7L, 3L, false,
                null, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(12, 0), LocalDate.of(2026, 6, 1));

        CotaUnidadeViewDTO resultado = service.criar(dto);

        assertThat(resultado.profissionalId()).isEqualTo(7L);
        assertThat(resultado.localAgendamentoId()).isEqualTo(3L);
        assertThat(resultado.horaInicial()).isEqualTo(java.time.LocalTime.of(8, 0));
        assertThat(resultado.horaFinal()).isEqualTo(java.time.LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("Profissional inexistente e recusado com 404 de negocio")
    void profissionalInexistenteERecusado() {
        when(profissionalRepository.findById(999L)).thenReturn(Optional.empty());

        var dto = dtoComEspelho(999L, null, false,
                null, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(12, 0), LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ==================================================================
    // Bloqueio de agendamento sem cota (V93) — existeCotaAtivaAplicavel
    // ==================================================================

    @Test
    @DisplayName("existeCotaAtivaAplicavel: sem cota nenhuma cadastrada, devolve false")
    void existeCotaAtivaAplicavelSemCotaDevolveFalse() {
        Unidade u = new Unidade();
        u.setId(UNIDADE_ID);
        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(u));
        when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.existeCotaAtivaAplicavel(UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1)))
                .isFalse();
    }

    @Test
    @DisplayName("existeCotaAtivaAplicavel: com cota ativa cadastrada, devolve true")
    void existeCotaAtivaAplicavelComCotaDevolveTrue() {
        Unidade u = new Unidade();
        u.setId(UNIDADE_ID);
        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(u));

        CotaUnidade cota = new CotaUnidade();
        cota.setId(1L);
        cota.setUnidade(u);
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(5); // esgotada, mas ainda "existe"
        cota.setAtivo(true);
        when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cota));

        assertThat(service.existeCotaAtivaAplicavel(UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1)))
                .isTrue();
    }

    @Test
    @DisplayName("existeCotaAtivaAplicavel: sem unidade ou sem data, devolve false sem consultar o banco")
    void existeCotaAtivaAplicavelSemParametrosDevolveFalse() {
        assertThat(service.existeCotaAtivaAplicavel(null, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1))).isFalse();
        assertThat(service.existeCotaAtivaAplicavel(UNIDADE_ID, HEMOGRAMA_ID, null)).isFalse();
        verify(unidadeRepository, never()).findById(any());
    }

    // ==================================================================
    // resolverCotaParaAgendamento (V93) — rastreabilidade por profissional
    // ==================================================================

    @Test
    @DisplayName("resolverCotaParaAgendamento: sem cota com profissional, devolve null (nada a rastrear)")
    void resolverCotaSemProfissionalDevolveNull() {
        Unidade u = new Unidade();
        u.setId(UNIDADE_ID);
        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(u));

        CotaUnidade cotaGeral = new CotaUnidade();
        cotaGeral.setId(1L);
        cotaGeral.setUnidade(u);
        when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cotaGeral));

        assertThat(service.resolverCotaParaAgendamento(UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1), null))
                .isNull();
    }

    @Test
    @DisplayName("resolverCotaParaAgendamento: uma cota com profissional, resolve sozinho")
    void resolverCotaComUmProfissionalResolveSozinho() {
        Unidade u = new Unidade();
        u.setId(UNIDADE_ID);
        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(u));

        Profissional drA = new Profissional();
        drA.setId(7L);
        CotaUnidade cotaDrA = new CotaUnidade();
        cotaDrA.setId(1L);
        cotaDrA.setUnidade(u);
        cotaDrA.setProfissionalExecutante(drA);
        when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cotaDrA));

        CotaUnidade resolvida = service.resolverCotaParaAgendamento(
                UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1), null);

        assertThat(resolvida).isEqualTo(cotaDrA);
    }

    @Test
    @DisplayName("resolverCotaParaAgendamento: dois profissionais compativeis sem escolha e recusado")
    void resolverCotaComDoisProfissionaisSemEscolhaERecusado() {
        Unidade u = new Unidade();
        u.setId(UNIDADE_ID);
        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(u));

        Profissional drA = new Profissional();
        drA.setId(7L);
        Profissional drB = new Profissional();
        drB.setId(8L);

        CotaUnidade cotaDrA = new CotaUnidade();
        cotaDrA.setId(1L);
        cotaDrA.setUnidade(u);
        cotaDrA.setProfissionalExecutante(drA);

        CotaUnidade cotaDrB = new CotaUnidade();
        cotaDrB.setId(2L);
        cotaDrB.setUnidade(u);
        cotaDrB.setProfissionalExecutante(drB);

        when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cotaDrA, cotaDrB));

        assertThatThrownBy(() -> service.resolverCotaParaAgendamento(
                UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mais de uma cota");
    }

    @Test
    @DisplayName("resolverCotaParaAgendamento: dois profissionais, unidade escolhe a cota do Dr. B")
    void resolverCotaComDoisProfissionaisEEscolhaExplicita() {
        Unidade u = new Unidade();
        u.setId(UNIDADE_ID);
        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(u));

        Profissional drA = new Profissional();
        drA.setId(7L);
        Profissional drB = new Profissional();
        drB.setId(8L);

        CotaUnidade cotaDrA = new CotaUnidade();
        cotaDrA.setId(1L);
        cotaDrA.setUnidade(u);
        cotaDrA.setProfissionalExecutante(drA);

        CotaUnidade cotaDrB = new CotaUnidade();
        cotaDrB.setId(2L);
        cotaDrB.setUnidade(u);
        cotaDrB.setProfissionalExecutante(drB);

        when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cotaDrA, cotaDrB));

        CotaUnidade resolvida = service.resolverCotaParaAgendamento(
                UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1), 2L);

        assertThat(resolvida).isEqualTo(cotaDrB);
    }

    @Test
    @DisplayName("resolverCotaParaAgendamento: cota escolhida que nao e aplicavel e recusada")
    void resolverCotaComEscolhaInvalidaERecusado() {
        Unidade u = new Unidade();
        u.setId(UNIDADE_ID);
        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(u));
        when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolverCotaParaAgendamento(
                UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1), 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao e aplicavel");
    }

    // ==================================================================
    // V95: filtro por dia da semana em cotasAplicaveis (usado por
    // existeCotaAtivaAplicavel, listarCotasAplicaveis, incrementarUtilizacao)
    // ==================================================================

    @Test
    @DisplayName("V95: cota MENSAL com dias da semana so e aplicavel nos dias marcados")
    void cotaMensalComDiasSemanaSoAplicavelNosDiasMarcados() {
        Unidade u = new Unidade();
        u.setId(UNIDADE_ID);
        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(u));

        CotaUnidade cotaSegunda = new CotaUnidade();
        cotaSegunda.setId(1L);
        cotaSegunda.setUnidade(u);
        cotaSegunda.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cotaSegunda.setDiasSemana("SEG");
        when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cotaSegunda));

        // 2026-06-01 e uma segunda-feira; 2026-06-02 e uma terca-feira.
        assertThat(service.existeCotaAtivaAplicavel(UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1))).isTrue();
        assertThat(service.existeCotaAtivaAplicavel(UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 2))).isFalse();
    }

    @Test
    @DisplayName("V95: cota MENSAL sem dias da semana continua aplicavel todo dia (comportamento preservado)")
    void cotaMensalSemDiasSemanaContinuaAplicavelTodoDia() {
        Unidade u = new Unidade();
        u.setId(UNIDADE_ID);
        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(u));

        CotaUnidade cotaGeral = new CotaUnidade();
        cotaGeral.setId(1L);
        cotaGeral.setUnidade(u);
        cotaGeral.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cotaGeral.setDiasSemana(null);
        when(cotaRepository.buscarCotasAplicaveis(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cotaGeral));

        assertThat(service.existeCotaAtivaAplicavel(UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 1))).isTrue();
        assertThat(service.existeCotaAtivaAplicavel(UNIDADE_ID, HEMOGRAMA_ID, LocalDate.of(2026, 6, 2))).isTrue();
    }

    // ==================================================================
    // calcularHorarioSlot (V97) — divisao do periodo entre as vagas
    // ==================================================================

    private CotaUnidade cotaDinamica(int quantidadeTotal, java.time.LocalTime inicio, java.time.LocalTime fim) {
        CotaUnidade cota = new CotaUnidade();
        cota.setHorarioDinamico(true);
        cota.setHoraInicial(inicio);
        cota.setHoraFinal(fim);
        cota.setQuantidadeTotal(quantidadeTotal);
        return cota;
    }

    @Test
    @DisplayName("calcularHorarioSlot: 5 vagas em 07h-11h gera um horario por hora")
    void calcularHorarioSlotDivideOPeriodoIgualmente() {
        CotaUnidade cota = cotaDinamica(5, java.time.LocalTime.of(7, 0), java.time.LocalTime.of(11, 0));

        assertThat(service.calcularHorarioSlot(cota, 1)).isEqualTo(java.time.LocalTime.of(7, 0));
        assertThat(service.calcularHorarioSlot(cota, 2)).isEqualTo(java.time.LocalTime.of(8, 0));
        assertThat(service.calcularHorarioSlot(cota, 3)).isEqualTo(java.time.LocalTime.of(9, 0));
        assertThat(service.calcularHorarioSlot(cota, 4)).isEqualTo(java.time.LocalTime.of(10, 0));
        assertThat(service.calcularHorarioSlot(cota, 5)).isEqualTo(java.time.LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("calcularHorarioSlot: uma vaga so usa a hora inicial")
    void calcularHorarioSlotComUmaVagaUsaHoraInicial() {
        CotaUnidade cota = cotaDinamica(1, java.time.LocalTime.of(7, 0), java.time.LocalTime.of(11, 0));

        assertThat(service.calcularHorarioSlot(cota, 1)).isEqualTo(java.time.LocalTime.of(7, 0));
    }

    @Test
    @DisplayName("calcularHorarioSlot: divisao nao exata arredonda os minutos")
    void calcularHorarioSlotComDivisaoNaoExataArredonda() {
        // 3 vagas em 07h-08h: passo = 60/2 = 30min -> 07:00, 07:30, 08:00
        CotaUnidade cota = cotaDinamica(3, java.time.LocalTime.of(7, 0), java.time.LocalTime.of(8, 0));

        assertThat(service.calcularHorarioSlot(cota, 1)).isEqualTo(java.time.LocalTime.of(7, 0));
        assertThat(service.calcularHorarioSlot(cota, 2)).isEqualTo(java.time.LocalTime.of(7, 30));
        assertThat(service.calcularHorarioSlot(cota, 3)).isEqualTo(java.time.LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("calcularHorarioSlot: cota sem horario dinamico devolve null (nada a calcular)")
    void calcularHorarioSlotSemHorarioDinamicoDevolveNull() {
        CotaUnidade cota = new CotaUnidade();
        cota.setHorarioDinamico(false);
        cota.setHoraInicial(java.time.LocalTime.of(7, 0));
        cota.setHoraFinal(java.time.LocalTime.of(11, 0));
        cota.setQuantidadeTotal(5);

        assertThat(service.calcularHorarioSlot(cota, 1)).isNull();
    }
}
