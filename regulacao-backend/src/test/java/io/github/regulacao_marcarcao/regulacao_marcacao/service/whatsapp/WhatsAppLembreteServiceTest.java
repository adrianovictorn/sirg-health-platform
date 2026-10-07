package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppLoteLembrete;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppLoteLembreteRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppMensagemRepository;

/**
 * Quem entra no lote de lembretes. O relogio e fixo em 06/10/2026 as 22h30 da
 * Bahia — em UTC ja e dia 7, entao um {@code LocalDate.now()} sem fuso erraria
 * todas as datas abaixo em um dia.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppLembreteServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-07T01:30:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 6);
    private static final LocalDate ONTEM = HOJE.minusDays(1);
    private static final LocalDate EM_3_DIAS = HOJE.plusDays(3);
    private static final LocalDate EM_2_DIAS = HOJE.plusDays(2);

    @Mock private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Mock private WhatsAppMensagemRepository mensagemRepository;
    @Mock private WhatsAppLoteLembreteRepository loteRepository;
    @Mock private UserRepository userRepository;
    @Mock private WhatsAppConfigService configService;

    private WhatsAppLembreteService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppLembreteService(agendamentoRepository, mensagemRepository, loteRepository,
                userRepository, configService, Clock.fixed(AGORA, ZoneOffset.UTC));
        when(configService.podeEnviar()).thenReturn(true);
        when(loteRepository.existsByDataExecucao(ONTEM)).thenReturn(true);
        when(loteRepository.findByDataExecucao(any())).thenReturn(Optional.empty());
        when(loteRepository.save(any(WhatsAppLoteLembrete.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mensagemRepository.enfileirarLembrete(anyLong(), anyLong(), anyString(), any(), anyString(), any()))
                .thenReturn(1);
    }

    /** {@code data_criacao} como o Hibernate grava: hora local da JVM. */
    private static LocalDateTime criadoEm(LocalDate dia, int hora, int minuto) {
        return dia.atTime(hora, minuto).atZone(WhatsAppEnvioProperties.FUSO)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private AgendamentoSolicitacao agendamento(long id, LocalDate data, LocalDateTime criadoEm) {
        Solicitacao s = new Solicitacao();
        s.setId(id * 10);
        AgendamentoSolicitacao a = new AgendamentoSolicitacao();
        a.setId(id);
        a.setSolicitacao(s);
        a.setDataAgendada(data);
        a.setDataCriacao(criadoEm);
        return a;
    }

    private void naData(LocalDate data, AgendamentoSolicitacao... agendamentos) {
        when(agendamentoRepository.findComItemNoStatusPorData(data, StatusDaMarcacao.AGENDADO))
                .thenReturn(List.of(agendamentos));
    }

    @Test
    void avisaQuemTemAtendimentoDaquiATresDias() {
        naData(EM_3_DIAS, agendamento(1, EM_3_DIAS, criadoEm(HOJE.minusDays(10), 9, 0)));

        int enfileirados = service.executarLote(WhatsAppOrigem.AUTOMATICO, null);

        assertThat(enfileirados).isEqualTo(1);
        verify(mensagemRepository).enfileirarLembrete(1L, 10L, "AUTOMATICO", EM_3_DIAS,
                "LEMBRETE:CONSULTA_EXAME:1:2026-10-09", null);
    }

    @Test
    void comOLoteDeOntemFeitoNaoOlhaParaDaquiADoisDias() {
        naData(EM_2_DIAS, agendamento(2, EM_2_DIAS, criadoEm(HOJE.minusDays(10), 9, 0)));

        assertThat(service.executarLote(WhatsAppOrigem.AUTOMATICO, null)).isZero();
        verify(agendamentoRepository, never()).findComItemNoStatusPorData(eq(EM_2_DIAS), any());
    }

    @Test
    void seOLoteDeOntemNaoRodouRecuperaUmDia() {
        when(loteRepository.existsByDataExecucao(ONTEM)).thenReturn(false);
        naData(EM_3_DIAS, agendamento(1, EM_3_DIAS, null));
        naData(EM_2_DIAS, agendamento(2, EM_2_DIAS, null));

        assertThat(service.executarLote(WhatsAppOrigem.AUTOMATICO, null)).isEqualTo(2);
        verify(mensagemRepository).enfileirarLembrete(eq(2L), eq(20L), anyString(), eq(EM_2_DIAS),
                eq("LEMBRETE:CONSULTA_EXAME:2:2026-10-08"), any());
    }

    @Test
    void agendamentoCriadoDepoisDas8hDoDiaDoLoteNaoRecebeLembrete() {
        naData(EM_3_DIAS,
                agendamento(1, EM_3_DIAS, criadoEm(HOJE, 7, 59)),
                agendamento(2, EM_3_DIAS, criadoEm(HOJE, 8, 0)),
                agendamento(3, EM_3_DIAS, criadoEm(HOJE, 15, 0)));

        assertThat(service.executarLote(WhatsAppOrigem.MANUAL, null)).isEqualTo(1);
        verify(mensagemRepository).enfileirarLembrete(eq(1L), anyLong(), anyString(), any(), anyString(), any());
        verify(mensagemRepository, never())
                .enfileirarLembrete(eq(2L), anyLong(), anyString(), any(), anyString(), any());
        verify(mensagemRepository, never())
                .enfileirarLembrete(eq(3L), anyLong(), anyString(), any(), anyString(), any());
    }

    @Test
    void naRecuperacaoOCorteEAs8hDeOntem() {
        when(loteRepository.existsByDataExecucao(ONTEM)).thenReturn(false);
        naData(EM_2_DIAS,
                agendamento(1, EM_2_DIAS, criadoEm(ONTEM, 7, 0)),
                agendamento(2, EM_2_DIAS, criadoEm(ONTEM, 9, 0)));

        assertThat(service.executarLote(WhatsAppOrigem.AUTOMATICO, null)).isEqualTo(1);
        verify(mensagemRepository, never())
                .enfileirarLembrete(eq(2L), anyLong(), anyString(), any(), anyString(), any());
    }

    @Test
    void segundaExecucaoNoDiaNaoDuplica_oBancoRecusaAChaveRepetida() {
        naData(EM_3_DIAS, agendamento(1, EM_3_DIAS, null));
        when(mensagemRepository.enfileirarLembrete(anyLong(), anyLong(), anyString(), any(), anyString(), any()))
                .thenReturn(0);
        WhatsAppLoteLembrete loteDeHoje = new WhatsAppLoteLembrete();
        loteDeHoje.setDataExecucao(HOJE);
        loteDeHoje.setOrigem(WhatsAppOrigem.AUTOMATICO);
        loteDeHoje.setEnfileirados(4);
        when(loteRepository.findByDataExecucao(HOJE)).thenReturn(Optional.of(loteDeHoje));

        assertThat(service.executarLote(WhatsAppOrigem.MANUAL, null)).isZero();
        assertThat(loteDeHoje.getEnfileirados()).isEqualTo(4);
    }

    @Test
    void registraQueOLoteDoDiaRodou_noDiaLocal() {
        naData(EM_3_DIAS, agendamento(1, EM_3_DIAS, null));

        service.executarLote(WhatsAppOrigem.AUTOMATICO, null);

        ArgumentCaptor<WhatsAppLoteLembrete> lote = ArgumentCaptor.forClass(WhatsAppLoteLembrete.class);
        verify(loteRepository).save(lote.capture());
        assertThat(lote.getValue().getDataExecucao()).isEqualTo(HOJE);
        assertThat(lote.getValue().getEnfileirados()).isEqualTo(1);
        assertThat(lote.getValue().getOrigem()).isEqualTo(WhatsAppOrigem.AUTOMATICO);
        assertThat(lote.getValue().getExecutadoEm()).isEqualTo(AGORA);
    }

    @Test
    void comEnvioDesligadoOLoteAutomaticoNaoGravaNada() {
        when(configService.podeEnviar()).thenReturn(false);

        assertThat(service.executarLote(WhatsAppOrigem.AUTOMATICO, null)).isZero();
        verifyNoInteractions(agendamentoRepository, mensagemRepository, loteRepository);
    }

    @Test
    void comEnvioDesligadoODisparoManualERecusado() {
        when(configService.podeEnviar()).thenReturn(false);

        assertThatThrownBy(() -> service.executarLote(WhatsAppOrigem.MANUAL, "00000000000"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(agendamentoRepository, mensagemRepository, loteRepository);
    }
}
