package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaMigracaoDataCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaMigracaoDataPreviaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaMigracaoDataViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaMigracaoData;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Municipio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaConsumidoresRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaMigracaoDataRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.FechamentoIndicadoresDiaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ConsumidorCotaProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ItemMigracaoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.federacao.AgendamentoExternoRemarcadoEvent;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.evento.AgendamentoRemarcadoEvent;

/**
 * Orquestracao da migracao de data: ordem dos passos, recusas e efeitos
 * colaterais (auditoria, snapshot, eventos). O SQL real e o rollback
 * transacional ficam em {@code CotaMigracaoDataIT} e {@code CotaMigracaoDataRollbackIT}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CotaMigracaoDataServiceTest {

    private static final Long COTA_ID = 5L;
    private static final Long DESTINO_ID = 6L;
    private static final Long UNIDADE_ID = 1L;
    private static final LocalDate DE = LocalDate.of(2026, 5, 20);
    private static final LocalDate PARA = LocalDate.of(2026, 6, 3);
    private static final UUID LOCAL = UUID.randomUUID();
    private static final UUID OUTRO_MUNICIPIO = UUID.randomUUID();

    @Mock private CotaUnidadeRepository cotaRepository;
    @Mock private CotaUnidadeService cotaUnidadeService;
    @Mock private CotaConsumidoresRepository consumidoresRepository;
    @Mock private SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    @Mock private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Mock private EspecialidadeRepository especialidadeRepository;
    @Mock private FechamentoIndicadoresDiaRepository fechamentoRepository;
    @Mock private CotaMigracaoDataRepository migracaoRepository;
    @Mock private UserRepository userRepository;
    @Mock private InstanceContext instanceContext;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CotaMigracaoDataService service;
    private CotaUnidade cota;
    private Unidade unidade;

    @BeforeEach
    void setUp() {
        service = new CotaMigracaoDataService(cotaRepository, cotaUnidadeService, consumidoresRepository,
                solicitacaoEspecialidadeRepository, agendamentoRepository, especialidadeRepository,
                fechamentoRepository, migracaoRepository, userRepository, instanceContext, eventPublisher,
                Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC),
                mock(jakarta.persistence.EntityManager.class));

        unidade = new Unidade();
        unidade.setId(UNIDADE_ID);
        unidade.setNome("Unidade A");

        cota = novaCota(COTA_ID, DE);
        lenient().when(cotaRepository.findByIdParaMigracao(COTA_ID)).thenReturn(Optional.of(cota));
        lenient().when(cotaRepository.findById(COTA_ID)).thenReturn(Optional.of(cota));
        lenient().when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(cota));
        lenient().when(cotaUnidadeService.idsAgendamentosQueConsomem(cota)).thenReturn(List.of(100L, 101L));

        Municipio local = new Municipio();
        local.setId(LOCAL);
        local.setNome("Sao Felipe");
        lenient().when(instanceContext.getMunicipioLocal()).thenReturn(local);

        User usuario = new User();
        usuario.setId(9L);
        lenient().when(userRepository.findByCpf("111")).thenReturn(Optional.of(usuario));

        // Dois agendamentos: o 100 (paciente local, AGENDADO) e o 101 (paciente de outro
        // municipio, so CANCELADO).
        lenient().when(consumidoresRepository.itensDosAgendamentos(any())).thenReturn(List.of(
                item(1000L, 100L, 200L, "AGENDADO", LOCAL),
                item(1001L, 100L, 200L, "AGENDADO", LOCAL),
                item(1002L, 101L, 201L, "CANCELADO", OUTRO_MUNICIPIO)));
    }

    private CotaUnidade novaCota(Long id, LocalDate data) {
        CotaUnidade c = new CotaUnidade();
        c.setId(id);
        c.setUnidade(unidade);
        c.setTipoPeriodo(TipoPeriodoCota.DATA);
        c.setDataEspecifica(data);
        c.setQuantidadeTotal(10);
        c.setQuantidadeUtilizada(3);
        c.setAtivo(true);
        c.setOrigem(OrigemCotaEnum.MANUAL);
        return c;
    }

    private static ItemMigracaoProjection item(long seId, long agendamentoId, long solicitacaoId, String status,
            UUID municipio) {
        return new ItemMigracaoProjection() {
            public Long getSeId() { return seId; }
            public Long getAgendamentoId() { return agendamentoId; }
            public Long getSolicitacaoId() { return solicitacaoId; }
            public Long getUnidadeId() { return UNIDADE_ID; }
            public Long getEspecialidadeId() { return 10L; }
            public String getEspecialidadeCodigo() { return "HEMO"; }
            public Long getCotaUnidadeId() { return null; }
            public String getStatus() { return status; }
            public LocalDate getDataAgendada() { return DE; }
            public UUID getOrigemMunicipioId() { return municipio; }
            public String getOrigemMunicipioNome() { return municipio.equals(LOCAL) ? "Sao Felipe" : "Outra Cidade"; }
            public String getNomePaciente() { return "Paciente " + solicitacaoId; }
            public String getCpfPaciente() { return "123.456.789-09"; }
        };
    }

    private CotaMigracaoDataCreateDTO para(LocalDate data) {
        return new CotaMigracaoDataCreateDTO(data);
    }

    private void nadaFoiAlterado() {
        verify(cotaRepository, never()).saveAndFlush(any());
        verify(cotaRepository, never()).absorver(anyLong(), anyInt(), anyInt());
        verify(agendamentoRepository, never()).remarcarEmLote(any(), any());
        verify(migracaoRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ------------------------------------------------------------------
    // Recusas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Cota de origem AGENDA: 409")
    void origemAgendaERecusada() {
        cota.setOrigem(OrigemCotaEnum.AGENDA);

        assertThatThrownBy(() -> service.migrar(COTA_ID, para(PARA), "111"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agenda de origem");
        nadaFoiAlterado();
    }

    @Test
    @DisplayName("Cota mensal com agendamentos nao muda de mes: 409")
    void cotaMensalERecusada() {
        cota.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cota.setPeriodo("2026-05");
        cota.setDataEspecifica(null);

        assertThatThrownBy(() -> service.migrar(COTA_ID, para(PARA), "111"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota mensal");
        nadaFoiAlterado();
    }

    @Test
    @DisplayName("Data de destino igual a atual ou ausente: 400")
    void dataIgualOuAusenteE400() {
        assertThatThrownBy(() -> service.migrar(COTA_ID, para(DE), "111"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.migrar(COTA_ID, para(null), "111"))
                .isInstanceOf(IllegalArgumentException.class);
        nadaFoiAlterado();
    }

    @Test
    @DisplayName("Cota inexistente: 404")
    void cotaInexistente() {
        when(cotaRepository.findByIdParaMigracao(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.migrar(999L, para(PARA), "111"))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Cota sem agendamentos: 400, a tela segue com a edicao normal")
    void semAgendamentosE400() {
        when(cotaUnidadeService.idsAgendamentosQueConsomem(cota)).thenReturn(List.of());

        assertThatThrownBy(() -> service.migrar(COTA_ID, para(PARA), "111"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao tem agendamentos");
        nadaFoiAlterado();
    }

    @Test
    @DisplayName("Capacidade global da especialidade excedida na data nova: 409 e nada muda")
    void capacidadeGlobalExcedida() {
        Especialidade hemograma = new Especialidade();
        hemograma.setCodigo("HEMO");
        hemograma.setVagas(5);
        when(especialidadeRepository.findByCodigoIn(List.of("HEMO"))).thenReturn(List.of(hemograma));
        when(solicitacaoEspecialidadeRepository.countAgendadasPorDataECodigos(PARA, List.of("HEMO"))).thenReturn(4L);

        assertThatThrownBy(() -> service.migrar(COTA_ID, para(PARA), "111"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Capacidade excedida para HEMO")
                .hasMessageContaining("Vagas=5, agendados=4, solicitados=3");
        nadaFoiAlterado();
        verify(cotaUnidadeService, never()).transferirUtilizacao(any(), any());
    }

    @Test
    @DisplayName("Cota de destino inativa: 409")
    void destinoInativo() {
        CotaUnidade destino = novaCota(DESTINO_ID, PARA);
        destino.setAtivo(false);
        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(cota, destino));

        assertThatThrownBy(() -> service.migrar(COTA_ID, para(PARA), "111"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inativa");
        nadaFoiAlterado();
    }

    @Test
    @DisplayName("Cota de destino gerada por agenda: 409")
    void destinoDeAgenda() {
        CotaUnidade destino = novaCota(DESTINO_ID, PARA);
        destino.setOrigem(OrigemCotaEnum.AGENDA);
        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(cota, destino));

        assertThatThrownBy(() -> service.migrar(COTA_ID, para(PARA), "111"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agenda");
        nadaFoiAlterado();
    }

    // ------------------------------------------------------------------
    // Sucesso
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Sem cota no destino: move a cota, remarca, apaga snapshots, audita e publica eventos")
    void migracaoSimples() {
        CotaMigracaoDataViewDTO resultado = service.migrar(COTA_ID, para(PARA), "111");

        assertThat(resultado.unificada()).isFalse();
        assertThat(resultado.cotaResultanteId()).isEqualTo(COTA_ID);
        assertThat(resultado.agendamentosMigrados()).isEqualTo(2);
        assertThat(resultado.itensMigrados()).isEqualTo(3);

        assertThat(cota.getDataEspecifica()).isEqualTo(PARA);
        verify(cotaRepository).saveAndFlush(cota);
        verify(cotaRepository, never()).absorver(anyLong(), anyInt(), anyInt());
        verify(cotaRepository, never()).deleteById(anyLong());

        ArgumentCaptor<Set<Long>> viajantes = ArgumentCaptor.forClass(Set.class);
        verify(cotaUnidadeService).transferirUtilizacao(any(), viajantes.capture());
        assertThat(viajantes.getValue()).containsExactly(COTA_ID);

        verify(agendamentoRepository).remarcarEmLote(List.of(100L, 101L), PARA);

        ArgumentCaptor<Collection<LocalDate>> datas = ArgumentCaptor.forClass(Collection.class);
        verify(fechamentoRepository).excluirPorDatas(datas.capture());
        assertThat(datas.getValue()).containsExactlyInAnyOrder(DE, PARA);

        ArgumentCaptor<CotaMigracaoData> auditoria = ArgumentCaptor.forClass(CotaMigracaoData.class);
        verify(migracaoRepository).save(auditoria.capture());
        CotaMigracaoData linha = auditoria.getValue();
        assertThat(linha.getCotaOrigemId()).isEqualTo(COTA_ID);
        assertThat(linha.getCotaResultanteId()).isEqualTo(COTA_ID);
        assertThat(linha.getDataDe()).isEqualTo(DE);
        assertThat(linha.getDataPara()).isEqualTo(PARA);
        assertThat(linha.isUnificada()).isFalse();
        assertThat(linha.getAgendamentos()).isEqualTo(2);
        assertThat(linha.getItens()).isEqualTo(3);
        assertThat(linha.getRealizadoPor().getId()).isEqualTo(9L);
        assertThat(linha.getRealizadoEm()).isEqualTo(Instant.parse("2026-05-10T12:00:00Z"));
    }

    @Test
    @DisplayName("Evento de WhatsApp so para agendamento com item AGENDADO; federacao so para origem externa")
    void eventosSoParaQuemTemQueReceber() {
        service.migrar(COTA_ID, para(PARA), "111");

        ArgumentCaptor<Object> eventos = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventos.capture());

        List<AgendamentoRemarcadoEvent> whatsapp = eventos.getAllValues().stream()
                .filter(AgendamentoRemarcadoEvent.class::isInstance).map(AgendamentoRemarcadoEvent.class::cast).toList();
        assertThat(whatsapp).hasSize(1);
        assertThat(whatsapp.get(0).agendamentoId()).isEqualTo(100L);
        assertThat(whatsapp.get(0).dataAnterior()).isEqualTo(DE);
        assertThat(whatsapp.get(0).dataNova()).isEqualTo(PARA);
        assertThat(whatsapp.get(0).itens()).containsExactly(1000L, 1001L);

        List<AgendamentoExternoRemarcadoEvent> federacao = eventos.getAllValues().stream()
                .filter(AgendamentoExternoRemarcadoEvent.class::isInstance)
                .map(AgendamentoExternoRemarcadoEvent.class::cast).toList();
        assertThat(federacao).hasSize(1);
        assertThat(federacao.get(0).solicitacaoId()).isEqualTo(201L);
        assertThat(federacao.get(0).destinoNome()).isEqualTo("OUTRA CIDADE");
        assertThat(federacao.get(0).cpfMascarado()).isEqualTo("***.***.***-09");
    }

    @Test
    @DisplayName("Com cota no destino: soma, reaponta as FKs, exclui a origem e nao altera a data da origem")
    void uniao() {
        CotaUnidade destino = novaCota(DESTINO_ID, PARA);
        destino.setQuantidadeTotal(4);
        destino.setQuantidadeUtilizada(1);
        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(cota, destino));
        when(cotaRepository.findByIdParaMigracao(DESTINO_ID)).thenReturn(Optional.of(destino));

        CotaMigracaoDataViewDTO resultado = service.migrar(COTA_ID, para(PARA), "111");

        assertThat(resultado.unificada()).isTrue();
        assertThat(resultado.cotaResultanteId()).isEqualTo(DESTINO_ID);
        verify(cotaRepository).absorver(DESTINO_ID, 10, 3);
        verify(solicitacaoEspecialidadeRepository).repontarCota(COTA_ID, DESTINO_ID);
        verify(cotaRepository).deleteById(COTA_ID);
        verify(cotaRepository, never()).saveAndFlush(any());

        ArgumentCaptor<Set<Long>> viajantes = ArgumentCaptor.forClass(Set.class);
        verify(cotaUnidadeService).transferirUtilizacao(any(), viajantes.capture());
        assertThat(viajantes.getValue()).containsExactlyInAnyOrder(COTA_ID, DESTINO_ID);

        ArgumentCaptor<CotaMigracaoData> auditoria = ArgumentCaptor.forClass(CotaMigracaoData.class);
        verify(migracaoRepository).save(auditoria.capture());
        assertThat(auditoria.getValue().isUnificada()).isTrue();
        assertThat(auditoria.getValue().getCotaResultanteId()).isEqualTo(DESTINO_ID);
    }

    // ------------------------------------------------------------------
    // Previa
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Previa conta agendamentos e itens por status, sem alterar nada")
    void previa() {
        ConsumidorCotaProjection consumidor = mock(ConsumidorCotaProjection.class);
        when(consumidor.getAgendamentoId()).thenReturn(100L);
        when(cotaUnidadeService.consumidoresDe(cota)).thenReturn(List.of(consumidor, consumidor));

        CotaMigracaoDataPreviaDTO previa = service.previa(COTA_ID, PARA);

        assertThat(previa.itens()).isEqualTo(3);
        assertThat(previa.itensAgendados()).isEqualTo(2);
        assertThat(previa.itensCanceladosOuFaltosos()).isEqualTo(1);
        assertThat(previa.quantidadeUtilizada()).isEqualTo(3);
        // 2 consumidores incidentes contra 3 de quantidadeUtilizada
        assertThat(previa.divergente()).isTrue();
        assertThat(previa.existeCotaNoDestino()).isFalse();
        assertThat(previa.solicitacoesFederadas()).isEqualTo(1);
        nadaFoiAlterado();
    }

    @Test
    @DisplayName("Previa de cota sem agendamentos devolve zero, sem lancar")
    void previaSemAgendamentos() {
        when(cotaUnidadeService.consumidoresDe(cota)).thenReturn(List.of());

        CotaMigracaoDataPreviaDTO previa = service.previa(COTA_ID, PARA);

        assertThat(previa.agendamentos()).isZero();
        assertThat(previa.divergente()).isFalse();
    }

    @Test
    @DisplayName("Previa avisa a existencia da cota de destino e os totais dela")
    void previaComDestino() {
        ConsumidorCotaProjection consumidor = mock(ConsumidorCotaProjection.class);
        when(consumidor.getAgendamentoId()).thenReturn(100L);
        when(cotaUnidadeService.consumidoresDe(cota)).thenReturn(List.of(consumidor));
        CotaUnidade destino = novaCota(DESTINO_ID, PARA);
        destino.setQuantidadeTotal(4);
        destino.setQuantidadeUtilizada(1);
        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(cota, destino));

        CotaMigracaoDataPreviaDTO previa = service.previa(COTA_ID, PARA);

        assertThat(previa.existeCotaNoDestino()).isTrue();
        assertThat(previa.destinoQuantidadeTotal()).isEqualTo(4);
        assertThat(previa.destinoQuantidadeUtilizada()).isEqualTo(1);
    }
}
