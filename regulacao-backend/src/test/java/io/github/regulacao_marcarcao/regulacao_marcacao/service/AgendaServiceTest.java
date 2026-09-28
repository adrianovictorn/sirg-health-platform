package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
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

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaDistribuicaoInputDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaRemanejarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Agenda;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaDistribuicao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaOcorrencia;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusOcorrenciaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoOfertaAgendaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendaOcorrenciaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CboRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalVinculoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Validacoes da abertura de agenda (V89) — materializacao de ocorrencias,
 * R8 (subconjunto do grupo) e R9 (sobreposicao de horario). O consumo/estorno
 * de saldo em si ja e coberto por {@code CotaUnidadeValidacaoTest} e
 * {@code CotaUnidadeSimulacaoTest} — aqui o foco e o que e exclusivo da agenda.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaServiceTest {

    private static final Long EXECUTANTE_ID = 1L;
    private static final Long SOLICITANTE_ID = 2L;
    private static final Long PROFISSIONAL_ID = 10L;
    private static final Long ESPECIALIDADE_ID = 20L;
    private static final Long GRUPO_ID = 30L;
    private static final String CPF_CRIADOR = "11111111111";

    @Mock private AgendaRepository agendaRepository;
    @Mock private AgendaOcorrenciaRepository agendaOcorrenciaRepository;
    @Mock private UnidadeRepository unidadeRepository;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private ProfissionalVinculoRepository profissionalVinculoRepository;
    @Mock private CboRepository cboRepository;
    @Mock private LocalAgendamentoRepository localAgendamentoRepository;
    @Mock private GrupoRelatorioRepository grupoRelatorioRepository;
    @Mock private EspecialidadeRepository especialidadeRepository;
    @Mock private UserRepository userRepository;
    @Mock private CotaUnidadeRepository cotaUnidadeRepository;
    @Mock private CotaUnidadeService cotaUnidadeService;

    @InjectMocks private AgendaService service;

    private Unidade executante;
    private Unidade solicitante;
    private Profissional profissional;
    private Especialidade especialidade;

    @BeforeEach
    void setUp() {
        executante = new Unidade();
        executante.setId(EXECUTANTE_ID);
        executante.setNome("Policlinica");
        executante.setTipo(TipoUnidadeEnum.EXECUTANTE);

        solicitante = new Unidade();
        solicitante.setId(SOLICITANTE_ID);
        solicitante.setNome("USF Centro");
        solicitante.setTipo(TipoUnidadeEnum.SOLICITANTE);

        profissional = new Profissional();
        profissional.setId(PROFISSIONAL_ID);
        profissional.setNome("Dra. Ana");

        especialidade = new Especialidade();
        especialidade.setId(ESPECIALIDADE_ID);
        especialidade.setNome("Hemograma");

        User criador = new User();
        criador.setId(999L);

        when(unidadeRepository.findById(EXECUTANTE_ID)).thenReturn(Optional.of(executante));
        when(unidadeRepository.findById(SOLICITANTE_ID)).thenReturn(Optional.of(solicitante));
        when(profissionalRepository.findById(PROFISSIONAL_ID)).thenReturn(Optional.of(profissional));
        when(especialidadeRepository.findById(ESPECIALIDADE_ID)).thenReturn(Optional.of(especialidade));
        when(userRepository.findByCpf(CPF_CRIADOR)).thenReturn(Optional.of(criador));
        when(profissionalVinculoRepository.existsByProfissionalIdAndUnidadeIdAndAtivoTrue(
                PROFISSIONAL_ID, EXECUTANTE_ID)).thenReturn(true);
        when(agendaOcorrenciaRepository.existeSobreposicao(any(), any(), any(), any(), any())).thenReturn(false);
        when(agendaRepository.save(any(Agenda.class))).thenAnswer(inv -> {
            Agenda a = inv.getArgument(0);
            a.setId(500L);
            long ocorrenciaId = 1L;
            for (var o : a.getOcorrencias()) {
                if (o.getId() == null) {
                    o.setId(ocorrenciaId++);
                }
            }
            return a;
        });
    }

    private AgendaCreateDTO dtoIndividual(LocalDate inicio, LocalDate fim, List<String> dias) {
        return new AgendaCreateDTO(
                EXECUTANTE_ID, PROFISSIONAL_ID, null, null, null,
                TipoOfertaAgendaEnum.INDIVIDUAL, null, List.of(ESPECIALIDADE_ID),
                inicio, fim, dias, LocalTime.of(8, 0), LocalTime.of(12, 0),
                "Observacao", List.of(new AgendaDistribuicaoInputDTO(SOLICITANTE_ID, 5)));
    }

    @Test
    @DisplayName("Executante que nao executa e recusado")
    void executanteQueNaoExecutaERecusado() {
        executante.setTipo(TipoUnidadeEnum.SOLICITANTE);
        var dto = dtoIndividual(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), List.of("SEG"));

        assertThatThrownBy(() -> service.criar(dto, CPF_CRIADOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executante");
    }

    @Test
    @DisplayName("Profissional sem vinculo ativo com o executante e recusado")
    void profissionalSemVinculoERecusado() {
        when(profissionalVinculoRepository.existsByProfissionalIdAndUnidadeIdAndAtivoTrue(
                PROFISSIONAL_ID, EXECUTANTE_ID)).thenReturn(false);
        var dto = dtoIndividual(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), List.of("SEG"));

        assertThatThrownBy(() -> service.criar(dto, CPF_CRIADOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vinculo ativo");
    }

    @Test
    @DisplayName("Oferta individual com mais de uma especialidade e recusada")
    void ofertaIndividualComVariasEspecialidadesERecusada() {
        var dto = new AgendaCreateDTO(
                EXECUTANTE_ID, PROFISSIONAL_ID, null, null, null,
                TipoOfertaAgendaEnum.INDIVIDUAL, null, List.of(ESPECIALIDADE_ID, 21L),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), List.of("SEG"),
                LocalTime.of(8, 0), LocalTime.of(12, 0), null,
                List.of(new AgendaDistribuicaoInputDTO(SOLICITANTE_ID, 5)));

        assertThatThrownBy(() -> service.criar(dto, CPF_CRIADOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exatamente uma especialidade");
    }

    @Test
    @DisplayName("Oferta em grupo com especialidade fora do grupo e recusada (R8)")
    void especialidadeForaDoGrupoERecusada() {
        GrupoRelatorio grupo = new GrupoRelatorio();
        grupo.setId(GRUPO_ID);
        grupo.setNome("Laboratorio");
        when(grupoRelatorioRepository.findById(GRUPO_ID)).thenReturn(Optional.of(grupo));
        // especialidade sem grupoRelatorio setado, entao nao pertence a "Laboratorio"

        var dto = new AgendaCreateDTO(
                EXECUTANTE_ID, PROFISSIONAL_ID, null, null, null,
                TipoOfertaAgendaEnum.GRUPO, GRUPO_ID, List.of(ESPECIALIDADE_ID),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), List.of("SEG"),
                LocalTime.of(8, 0), LocalTime.of(12, 0), null,
                List.of(new AgendaDistribuicaoInputDTO(SOLICITANTE_ID, 5)));

        assertThatThrownBy(() -> service.criar(dto, CPF_CRIADOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao pertence ao grupo");
    }

    @Test
    @DisplayName("R9: profissional com horario sobreposto em outra agenda e recusado")
    void sobreposicaoDeHorarioERecusada() {
        when(agendaOcorrenciaRepository.existeSobreposicao(any(), any(), any(), any(), any())).thenReturn(true);
        var dto = dtoIndividual(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), List.of("SEG"));

        assertThatThrownBy(() -> service.criar(dto, CPF_CRIADOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("horario");

        verify(agendaRepository, never()).save(any());
        verify(cotaUnidadeService, never()).criarParaAgenda(any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    @DisplayName("Sem distribuicao de vagas e recusado")
    void semDistribuicaoERecusado() {
        var dto = new AgendaCreateDTO(
                EXECUTANTE_ID, PROFISSIONAL_ID, null, null, null,
                TipoOfertaAgendaEnum.INDIVIDUAL, null, List.of(ESPECIALIDADE_ID),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), List.of("SEG"),
                LocalTime.of(8, 0), LocalTime.of(12, 0), null, List.of());

        assertThatThrownBy(() -> service.criar(dto, CPF_CRIADOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unidade solicitante");
    }

    @Test
    @DisplayName("Materializa uma ocorrencia por dia da semana marcado dentro da vigencia")
    void materializaOcorrenciasPorDiaDaSemana() {
        // 2026-10-05 e 2026-10-12 sao segundas-feiras; a vigencia cobre as duas.
        var dto = dtoIndividual(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), List.of("SEG"));

        assertThatCode(() -> service.criar(dto, CPF_CRIADOR)).doesNotThrowAnyException();

        verify(cotaUnidadeService, times(2))
                .criarParaAgenda(any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    @DisplayName("Vigencia final anterior a inicial e recusada")
    void vigenciaInvalidaERecusada() {
        var dto = dtoIndividual(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 5), List.of("SEG"));

        assertThatThrownBy(() -> service.criar(dto, CPF_CRIADOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vigencia");
    }

    // ==================================================================
    // atualizar() — R2, R3
    // ==================================================================

    private Agenda agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum status) {
        Agenda agenda = new Agenda();
        agenda.setId(500L);
        agenda.setAtivo(true);
        agenda.setEstabelecimentoExecutante(executante);
        agenda.setProfissional(profissional);
        agenda.setTipoOferta(TipoOfertaAgendaEnum.INDIVIDUAL);

        AgendaDistribuicao distribuicao = new AgendaDistribuicao();
        distribuicao.setId(1L);
        distribuicao.setAgenda(agenda);
        distribuicao.setUnidadeSolicitante(solicitante);
        distribuicao.setVagasPorOcorrencia(5);
        agenda.getDistribuicoes().add(distribuicao);

        AgendaOcorrencia ocorrencia = new AgendaOcorrencia();
        ocorrencia.setId(10L);
        ocorrencia.setAgenda(agenda);
        ocorrencia.setData(LocalDate.of(2026, 10, 5));
        ocorrencia.setStatus(status);
        agenda.getOcorrencias().add(ocorrencia);

        when(agendaRepository.findById(500L)).thenReturn(Optional.of(agenda));
        when(agendaRepository.save(any(Agenda.class))).thenAnswer(inv -> inv.getArgument(0));
        return agenda;
    }

    @Test
    @DisplayName("Atualizar vagas de unidade que a agenda nao distribui e recusado")
    void atualizarUnidadeNaoDistribuidaERecusado() {
        agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum.ABERTA);
        var dto = new AgendaUpdateDTO(null, null, true, List.of(new AgendaDistribuicaoInputDTO(999L, 10)));

        assertThatThrownBy(() -> service.atualizar(500L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao distribui vagas");
    }

    @Test
    @DisplayName("Reduzir vagas abaixo do ja utilizado numa ocorrencia aberta e recusado (R3)")
    void reduzirVagasAbaixoDoUtilizadoERecusado() {
        agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum.ABERTA);
        CotaUnidade cota = new CotaUnidade();
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(3);
        when(cotaUnidadeRepository.findByAgendaOcorrenciaIdAndUnidadeId(10L, SOLICITANTE_ID))
                .thenReturn(Optional.of(cota));

        var dto = new AgendaUpdateDTO(null, null, true, List.of(new AgendaDistribuicaoInputDTO(SOLICITANTE_ID, 2)));

        assertThatThrownBy(() -> service.atualizar(500L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja utilizado");
    }

    @Test
    @DisplayName("Aumentar vagas de uma ocorrencia aberta propaga para a cota correspondente")
    void aumentarVagasPropagaParaCota() {
        agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum.ABERTA);
        CotaUnidade cota = new CotaUnidade();
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(3);
        when(cotaUnidadeRepository.findByAgendaOcorrenciaIdAndUnidadeId(10L, SOLICITANTE_ID))
                .thenReturn(Optional.of(cota));

        var dto = new AgendaUpdateDTO(null, null, true, List.of(new AgendaDistribuicaoInputDTO(SOLICITANTE_ID, 8)));

        assertThatCode(() -> service.atualizar(500L, dto)).doesNotThrowAnyException();
        assertThat(cota.getQuantidadeTotal()).isEqualTo(8);
    }

    @Test
    @DisplayName("Ocorrencia cancelada nao e afetada pela atualizacao de vagas (R2)")
    void ocorrenciaCanceladaNaoEAfetada() {
        agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum.CANCELADA);

        var dto = new AgendaUpdateDTO(null, null, true, List.of(new AgendaDistribuicaoInputDTO(SOLICITANTE_ID, 8)));

        assertThatCode(() -> service.atualizar(500L, dto)).doesNotThrowAnyException();
        verify(cotaUnidadeRepository, never()).findByAgendaOcorrenciaIdAndUnidadeId(any(), any());
    }

    // ==================================================================
    // cancelarOcorrencia() — R10
    // ==================================================================

    @Test
    @DisplayName("Cancelar ocorrencia fecha o saldo nao consumido e desativa as cotas geradas")
    void cancelarOcorrenciaFechaSaldoEDesativaCotas() {
        agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum.ABERTA);
        CotaUnidade cota = new CotaUnidade();
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(2);
        cota.setAtivo(true);
        when(cotaUnidadeRepository.findByAgendaOcorrenciaId(10L)).thenReturn(List.of(cota));

        service.cancelarOcorrencia(500L, 10L);

        assertThat(cota.getQuantidadeTotal()).isEqualTo(2);
        assertThat(cota.isAtivo()).isFalse();
    }

    @Test
    @DisplayName("Cancelar ocorrencia ja cancelada e recusado")
    void cancelarOcorrenciaJaCanceladaERecusado() {
        agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum.CANCELADA);

        assertThatThrownBy(() -> service.cancelarOcorrencia(500L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ja esta cancelada");
    }

    // ==================================================================
    // remanejar() — R1
    // ==================================================================

    @Test
    @DisplayName("Remanejar move vagas nao utilizadas entre unidades da mesma ocorrencia")
    void remanejarMoveVagasNaoUtilizadas() {
        agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum.ABERTA);
        Long outraUnidadeId = 3L;

        CotaUnidade origem = new CotaUnidade();
        origem.setQuantidadeTotal(5);
        origem.setQuantidadeUtilizada(1);
        CotaUnidade destino = new CotaUnidade();
        destino.setQuantidadeTotal(3);
        destino.setQuantidadeUtilizada(0);

        when(cotaUnidadeRepository.findByAgendaOcorrenciaIdAndUnidadeId(10L, SOLICITANTE_ID))
                .thenReturn(Optional.of(origem));
        when(cotaUnidadeRepository.findByAgendaOcorrenciaIdAndUnidadeId(10L, outraUnidadeId))
                .thenReturn(Optional.of(destino));

        var dto = new AgendaRemanejarDTO(10L, SOLICITANTE_ID, outraUnidadeId, 2);
        service.remanejar(500L, dto);

        assertThat(origem.getQuantidadeTotal()).isEqualTo(3);
        assertThat(destino.getQuantidadeTotal()).isEqualTo(5);
    }

    @Test
    @DisplayName("Remanejar mais vagas do que o saldo nao utilizado e recusado")
    void remanejarAcimaDoSaldoNaoUtilizadoERecusado() {
        agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum.ABERTA);
        Long outraUnidadeId = 3L;

        CotaUnidade origem = new CotaUnidade();
        origem.setQuantidadeTotal(5);
        origem.setQuantidadeUtilizada(4); // saldo nao usado = 1

        when(cotaUnidadeRepository.findByAgendaOcorrenciaIdAndUnidadeId(10L, SOLICITANTE_ID))
                .thenReturn(Optional.of(origem));
        when(cotaUnidadeRepository.findByAgendaOcorrenciaIdAndUnidadeId(10L, outraUnidadeId))
                .thenReturn(Optional.of(new CotaUnidade()));

        var dto = new AgendaRemanejarDTO(10L, SOLICITANTE_ID, outraUnidadeId, 2);

        assertThatThrownBy(() -> service.remanejar(500L, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao utilizada");
    }

    @Test
    @DisplayName("Remanejar com origem e destino iguais e recusado")
    void remanejarOrigemIgualDestinoERecusado() {
        agendaComUmaDistribuicaoEUmaOcorrencia(StatusOcorrenciaEnum.ABERTA);
        var dto = new AgendaRemanejarDTO(10L, SOLICITANTE_ID, SOLICITANTE_ID, 1);

        assertThatThrownBy(() -> service.remanejar(500L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diferentes");
    }
}
