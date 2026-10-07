package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppAlvoTipo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppMensagemRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppCloudApiClient.Resposta;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppCloudApiClient.Situacao;
import jakarta.persistence.EntityNotFoundException;

/**
 * Regras que decidem se uma mensagem sai, para quem e quantas vezes — todas
 * reaplicadas no momento do envio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppMensagemServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-06T15:00:00Z");
    private static final LocalDate DATA = LocalDate.of(2026, 10, 12);
    private static final Long MENSAGEM = 50L;
    private static final Long AGENDAMENTO = 7L;
    private static final Long SOLICITACAO = 3L;

    @Mock private WhatsAppMensagemRepository mensagemRepository;
    @Mock private SolicitacaoRepository solicitacaoRepository;
    @Mock private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Mock private SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    @Mock private UserRepository userRepository;
    @Mock private WhatsAppConfigService configService;
    @Mock private InstanceContext instanceContext;

    private final List<WhatsAppMensagem> salvas = new ArrayList<>();
    private Solicitacao paciente;
    private WhatsAppMensagem mensagem;

    @BeforeEach
    void setUp() {
        when(mensagemRepository.save(any(WhatsAppMensagem.class))).thenAnswer(inv -> {
            WhatsAppMensagem m = inv.getArgument(0);
            if (!salvas.contains(m)) {
                salvas.add(m);
            }
            return m;
        });
        when(configService.envioLigado()).thenReturn(true);
        when(configService.podeEnviar()).thenReturn(true);
        when(mensagemRepository.descartarPendente(any(), any())).thenReturn(1);

        paciente = new Solicitacao();
        paciente.setId(SOLICITACAO);
        paciente.setNomePaciente("Maria Souza");
        paciente.setCpfPaciente("123.456.789-09");
        paciente.setTelefone("(75) 99999-0000");
        when(solicitacaoRepository.findById(SOLICITACAO)).thenReturn(Optional.of(paciente));
        when(solicitacaoRepository.findByCpfPacienteSemPonto("12345678909")).thenReturn(List.of(paciente));

        AgendamentoSolicitacao agendamento = new AgendamentoSolicitacao();
        agendamento.setId(AGENDAMENTO);
        agendamento.setSolicitacao(paciente);
        agendamento.setDataAgendada(DATA);
        agendamento.setTurno(TurnoEnum.TARDE);
        when(agendamentoRepository.findById(AGENDAMENTO)).thenReturn(Optional.of(agendamento));
        when(solicitacaoEspecialidadeRepository.findByAgendamentoSolicitacaoId(AGENDAMENTO))
                .thenReturn(List.of(item(StatusDaMarcacao.AGENDADO)));

        mensagem = mensagem(WhatsAppTipoMensagem.CONFIRMACAO);
        when(mensagemRepository.findById(MENSAGEM)).thenReturn(Optional.of(mensagem));
    }

    private SolicitacaoEspecialidade item(StatusDaMarcacao status) {
        Especialidade e = new Especialidade();
        e.setNome("Cardiologia");
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setEspecialidadeSolicitada(e);
        se.setStatus(status);
        return se;
    }

    private WhatsAppMensagem mensagem(WhatsAppTipoMensagem tipo) {
        WhatsAppMensagem m = new WhatsAppMensagem();
        m.setId(MENSAGEM);
        m.setAlvoTipo(WhatsAppAlvoTipo.CONSULTA_EXAME);
        m.setAlvoId(AGENDAMENTO);
        m.setSolicitacaoId(SOLICITACAO);
        m.setTipo(tipo);
        m.setOrigem(WhatsAppOrigem.AUTOMATICO);
        m.setResultado(WhatsAppResultado.ENVIANDO);
        m.setDataReferencia(DATA);
        m.setCriadoEm(AGORA);
        m.setEnviarApos(AGORA);
        return m;
    }

    private WhatsAppMensagemService service(WhatsAppEnvioProperties props) {
        return new WhatsAppMensagemService(mensagemRepository, solicitacaoRepository, agendamentoRepository,
                solicitacaoEspecialidadeRepository, userRepository, configService, new WhatsAppConteudoService(),
                props, instanceContext, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private WhatsAppMensagemService service() {
        return service(WhatsAppTestes.configurado());
    }

    // --- preparar ---------------------------------------------------------------

    @Test
    void confirmacaoProntaParaEnvio() {
        var preparo = service().preparar(MENSAGEM);

        assertThat(preparo.enviar()).isTrue();
        assertThat(preparo.numero()).isEqualTo("5575999990000");
        assertThat(preparo.telefoneFinal()).isEqualTo("0000");
        assertThat(preparo.template()).isEqualTo(WhatsAppTestes.TEMPLATE_CONFIRMACAO);
        assertThat(preparo.variaveis()).hasSize(8).startsWith("Maria", "Cardiologia");
    }

    @Test
    void lembreteERemarcacaoUsamOTemplateCerto() {
        mensagem.setTipo(WhatsAppTipoMensagem.LEMBRETE);
        assertThat(service().preparar(MENSAGEM).template()).isEqualTo(WhatsAppTestes.TEMPLATE_LEMBRETE);

        mensagem.setTipo(WhatsAppTipoMensagem.REMARCACAO);
        assertThat(service().preparar(MENSAGEM).template()).isEqualTo(WhatsAppTestes.TEMPLATE_CONFIRMACAO);
    }

    @Test
    void cancelamentoSaiMesmoComOAgendamentoJaApagado() {
        mensagem.setTipo(WhatsAppTipoMensagem.CANCELAMENTO);
        when(agendamentoRepository.findById(AGENDAMENTO)).thenReturn(Optional.empty());

        var preparo = service().preparar(MENSAGEM);

        assertThat(preparo.enviar()).isTrue();
        assertThat(preparo.template()).isEqualTo(WhatsAppTestes.TEMPLATE_CANCELAMENTO);
        assertThat(preparo.variaveis()).containsExactly("Maria", "consulta ou exame", "12/10/2026", "segunda-feira");
    }

    @Test
    void chaveDesligadaNaoEnvia() {
        when(configService.envioLigado()).thenReturn(false);

        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.ENVIO_DESLIGADO);
    }

    @Test
    void semCredenciaisNaoEnvia() {
        assertThat(service(WhatsAppTestes.naoConfigurado()).preparar(MENSAGEM).motivo())
                .isEqualTo(WhatsAppMotivoNaoEnvio.NAO_CONFIGURADO);
    }

    @Test
    void pacienteQueOptouPorSairNaoRecebe() {
        paciente.setWhatsappOptOut(true);

        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.OPT_OUT);
    }

    @Test
    void optOutEmOutraFichaDoMesmoCpfTambemVale() {
        Solicitacao outraFicha = new Solicitacao();
        outraFicha.setId(99L);
        outraFicha.setCpfPaciente("12345678909");
        outraFicha.setWhatsappOptOut(true);
        when(solicitacaoRepository.findByCpfPacienteSemPonto("12345678909"))
                .thenReturn(List.of(paciente, outraFicha));

        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.OPT_OUT);
    }

    @Test
    void pacienteSemCpfNaoConsultaOutrasFichas() {
        paciente.setCpfPaciente(null);

        assertThat(service().preparar(MENSAGEM).enviar()).isTrue();
        verify(solicitacaoRepository, never()).findByCpfPacienteSemPonto(any());
    }

    @Test
    void telefoneAusenteOuInvalidoNaoEnvia() {
        paciente.setTelefone(null);
        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.SEM_TELEFONE);

        paciente.setTelefone("3333-0000");
        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.TELEFONE_INVALIDO);
    }

    @Test
    void comListaDeTesteSoOsNumerosDaListaRecebem() {
        var foraDaLista = service(WhatsAppTestes.props("t", "1", 200, "5575911112222"));
        var naLista = service(WhatsAppTestes.props("t", "1", 200, "5575911112222, +55 (75) 99999-0000"));

        assertThat(foraDaLista.preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.FORA_DA_LISTA_DE_TESTE);
        assertThat(naLista.preparar(MENSAGEM).enviar()).isTrue();
    }

    @Test
    void limiteDiarioAtingidoNaoEnvia() {
        when(mensagemRepository.countByEnviadoEmGreaterThanEqual(any())).thenReturn(5L);

        assertThat(service(WhatsAppTestes.props("t", "1", 5, "")).preparar(MENSAGEM).motivo())
                .isEqualTo(WhatsAppMotivoNaoEnvio.LIMITE_DIARIO);
        assertThat(service(WhatsAppTestes.props("t", "1", 6, "")).preparar(MENSAGEM).enviar()).isTrue();
    }

    @Test
    void limiteDiarioContaODiaNoFusoLocal_naoEmUtc() {
        // 01:30 UTC do dia 7 ainda e 22:30 do dia 6 na Bahia.
        var service = new WhatsAppMensagemService(mensagemRepository, solicitacaoRepository, agendamentoRepository,
                solicitacaoEspecialidadeRepository, userRepository, configService, new WhatsAppConteudoService(),
                WhatsAppTestes.configurado(), instanceContext,
                Clock.fixed(Instant.parse("2026-10-07T01:30:00Z"), ZoneOffset.UTC));

        service.enviadasHoje();

        verify(mensagemRepository).countByEnviadoEmGreaterThanEqual(Instant.parse("2026-10-06T03:00:00Z"));
    }

    @Test
    void agendamentoExcluidoOuSemItemAgendadoNaoEnvia() {
        when(solicitacaoEspecialidadeRepository.findByAgendamentoSolicitacaoId(AGENDAMENTO))
                .thenReturn(List.of(item(StatusDaMarcacao.FALTOU), item(StatusDaMarcacao.CANCELADO)));
        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.AGENDAMENTO_REMOVIDO);

        when(agendamentoRepository.findById(AGENDAMENTO)).thenReturn(Optional.empty());
        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.AGENDAMENTO_REMOVIDO);

        when(solicitacaoRepository.findById(SOLICITACAO)).thenReturn(Optional.empty());
        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.AGENDAMENTO_REMOVIDO);
    }

    // --- enfileirar -------------------------------------------------------------

    @Test
    void agendamentoNovoEnfileiraConfirmacaoParaEnvioImediato() {
        service().enfileirarConfirmacao(AGENDAMENTO, SOLICITACAO, DATA, List.of(10L, 11L));

        assertThat(salvas).singleElement().satisfies(m -> {
            assertThat(m.getTipo()).isEqualTo(WhatsAppTipoMensagem.CONFIRMACAO);
            assertThat(m.getOrigem()).isEqualTo(WhatsAppOrigem.AUTOMATICO);
            assertThat(m.getResultado()).isEqualTo(WhatsAppResultado.PENDENTE);
            assertThat(m.getAlvoId()).isEqualTo(AGENDAMENTO);
            assertThat(m.getSolicitacaoId()).isEqualTo(SOLICITACAO);
            assertThat(m.getDataReferencia()).isEqualTo(DATA);
            assertThat(m.getItensRef()).isEqualTo("10,11");
            assertThat(m.getEnviarApos()).isEqualTo(AGORA);
            assertThat(m.getChaveIdempotencia()).isNull();
        });
    }

    @Test
    void exclusaoEnfileiraCancelamentoComAtraso() {
        service().enfileirarCancelamento(AGENDAMENTO, SOLICITACAO, DATA, List.of(10L));

        assertThat(salvas).singleElement().satisfies(m -> {
            assertThat(m.getTipo()).isEqualTo(WhatsAppTipoMensagem.CANCELAMENTO);
            assertThat(m.getResultado()).isEqualTo(WhatsAppResultado.PENDENTE);
            assertThat(m.getEnviarApos()).isEqualTo(AGORA.plus(Duration.ofMinutes(10)));
            assertThat(m.getItensRef()).isEqualTo("10");
        });
    }

    @Test
    void reagendarOMesmoItemDentroDaJanelaViraUmaUnicaMensagemDeRemarcacao() {
        WhatsAppMensagem cancelamentoPendente = mensagem(WhatsAppTipoMensagem.CANCELAMENTO);
        cancelamentoPendente.setResultado(WhatsAppResultado.PENDENTE);
        cancelamentoPendente.setItensRef("10,11");
        when(mensagemRepository.findBySolicitacaoIdAndTipoAndResultado(
                SOLICITACAO, WhatsAppTipoMensagem.CANCELAMENTO, WhatsAppResultado.PENDENTE))
                .thenReturn(List.of(cancelamentoPendente));

        service().enfileirarConfirmacao(8L, SOLICITACAO, DATA.plusDays(2), List.of(11L));

        verify(mensagemRepository).descartarPendente(MENSAGEM, WhatsAppMotivoNaoEnvio.SUBSTITUIDO_POR_REMARCACAO);
        assertThat(salvas).singleElement().satisfies(m -> {
            assertThat(m.getTipo()).isEqualTo(WhatsAppTipoMensagem.REMARCACAO);
            assertThat(m.getAlvoId()).isEqualTo(8L);
        });
    }

    @Test
    void seOCancelamentoJaFoiPegoParaEnvioONovoAgendamentoEConfirmacaoComum() {
        WhatsAppMensagem cancelamentoPendente = mensagem(WhatsAppTipoMensagem.CANCELAMENTO);
        cancelamentoPendente.setResultado(WhatsAppResultado.PENDENTE);
        cancelamentoPendente.setItensRef("10");
        when(mensagemRepository.findBySolicitacaoIdAndTipoAndResultado(
                SOLICITACAO, WhatsAppTipoMensagem.CANCELAMENTO, WhatsAppResultado.PENDENTE))
                .thenReturn(List.of(cancelamentoPendente));
        when(mensagemRepository.descartarPendente(any(), any())).thenReturn(0);

        service().enfileirarConfirmacao(8L, SOLICITACAO, DATA, List.of(10L));

        assertThat(salvas).singleElement()
                .satisfies(m -> assertThat(m.getTipo()).isEqualTo(WhatsAppTipoMensagem.CONFIRMACAO));
    }

    @Test
    void agendarOutroItemNaoApagaOCancelamentoPendente() {
        WhatsAppMensagem cancelamentoPendente = mensagem(WhatsAppTipoMensagem.CANCELAMENTO);
        cancelamentoPendente.setResultado(WhatsAppResultado.PENDENTE);
        cancelamentoPendente.setItensRef("10");
        when(mensagemRepository.findBySolicitacaoIdAndTipoAndResultado(
                SOLICITACAO, WhatsAppTipoMensagem.CANCELAMENTO, WhatsAppResultado.PENDENTE))
                .thenReturn(List.of(cancelamentoPendente));

        service().enfileirarConfirmacao(8L, SOLICITACAO, DATA, List.of(20L));

        verify(mensagemRepository, never()).descartarPendente(any(), any());
        assertThat(salvas).singleElement()
                .satisfies(m -> assertThat(m.getTipo()).isEqualTo(WhatsAppTipoMensagem.CONFIRMACAO));
    }

    @Test
    void agendamentoExcluidoAntesDeAConfirmacaoSairNaoGeraMensagemNenhuma() {
        WhatsAppMensagem confirmacaoNaFila = mensagem(WhatsAppTipoMensagem.CONFIRMACAO);
        confirmacaoNaFila.setResultado(WhatsAppResultado.PENDENTE);
        when(mensagemRepository.findByAlvoTipoAndAlvoIdAndResultado(
                WhatsAppAlvoTipo.CONSULTA_EXAME, AGENDAMENTO, WhatsAppResultado.PENDENTE))
                .thenReturn(List.of(confirmacaoNaFila));

        service().enfileirarCancelamento(AGENDAMENTO, SOLICITACAO, DATA, List.of(10L));

        verify(mensagemRepository).descartarPendente(MENSAGEM, WhatsAppMotivoNaoEnvio.AGENDAMENTO_REMOVIDO);
        assertThat(salvas).isEmpty();
    }

    @Test
    void seAConfirmacaoJaFoiPegaParaEnvioOCancelamentoEEnfileirado() {
        WhatsAppMensagem confirmacaoNaFila = mensagem(WhatsAppTipoMensagem.CONFIRMACAO);
        confirmacaoNaFila.setResultado(WhatsAppResultado.PENDENTE);
        when(mensagemRepository.findByAlvoTipoAndAlvoIdAndResultado(
                WhatsAppAlvoTipo.CONSULTA_EXAME, AGENDAMENTO, WhatsAppResultado.PENDENTE))
                .thenReturn(List.of(confirmacaoNaFila));
        when(mensagemRepository.descartarPendente(any(), any())).thenReturn(0);

        service().enfileirarCancelamento(AGENDAMENTO, SOLICITACAO, DATA, List.of(10L));

        assertThat(salvas).singleElement()
                .satisfies(m -> assertThat(m.getTipo()).isEqualTo(WhatsAppTipoMensagem.CANCELAMENTO));
    }

    @Test
    void atendimentoQueJaPassouNaoGeraMensagem() {
        // AGORA = 06/10/2026 12h na Bahia.
        mensagem.setDataReferencia(LocalDate.of(2026, 10, 5));
        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.DATA_PASSADA);

        mensagem.setTipo(WhatsAppTipoMensagem.CANCELAMENTO);
        assertThat(service().preparar(MENSAGEM).motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.DATA_PASSADA);

        mensagem.setDataReferencia(LocalDate.of(2026, 10, 6));
        assertThat(service().preparar(MENSAGEM).enviar()).isTrue();
    }

    // --- reenvio manual ---------------------------------------------------------

    @Test
    void reenvioManualEnfileiraSemChaveDeIdempotencia() {
        var criada = service().reenviar(AGENDAMENTO, WhatsAppTipoMensagem.LEMBRETE, "00000000000");

        assertThat(criada.getOrigem()).isEqualTo(WhatsAppOrigem.MANUAL);
        assertThat(criada.getTipo()).isEqualTo(WhatsAppTipoMensagem.LEMBRETE);
        assertThat(criada.getResultado()).isEqualTo(WhatsAppResultado.PENDENTE);
        assertThat(criada.getChaveIdempotencia()).isNull();
        assertThat(criada.getSolicitacaoId()).isEqualTo(SOLICITACAO);
        assertThat(criada.getDataReferencia()).isEqualTo(DATA);
    }

    @Test
    void reenvioManualRecusaComEnvioDesligadoTipoInvalidoOuAgendamentoInexistente() {
        assertThatThrownBy(() -> service().reenviar(AGENDAMENTO, WhatsAppTipoMensagem.CANCELAMENTO, "0"))
                .isInstanceOf(IllegalArgumentException.class);

        when(agendamentoRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().reenviar(123L, WhatsAppTipoMensagem.CONFIRMACAO, "0"))
                .isInstanceOf(EntityNotFoundException.class);

        when(configService.podeEnviar()).thenReturn(false);
        assertThatThrownBy(() -> service().reenviar(AGENDAMENTO, WhatsAppTipoMensagem.CONFIRMACAO, "0"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(salvas).isEmpty();
    }

    // --- registrar a resposta da Meta -------------------------------------------

    @Test
    void mensagemAceitaGuardaOIdESoOFinalDoTelefone() {
        service().registrarResposta(MENSAGEM, "0000", new Resposta(Situacao.ACEITA, "wamid.ABC", null));

        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.ENVIADO);
        assertThat(mensagem.getMetaMessageId()).isEqualTo("wamid.ABC");
        assertThat(mensagem.getEnviadoEm()).isEqualTo(AGORA);
        assertThat(mensagem.getTelefoneFinal()).isEqualTo("0000");
        assertThat(mensagem.getTentativas()).isEqualTo(1);
    }

    @Test
    void erroTemporarioVoltaParaAFilaAteATerceiraTentativa() {
        Resposta temporario = new Resposta(Situacao.ERRO_TEMPORARIO, null, "HTTP_503");

        service().registrarResposta(MENSAGEM, "0000", temporario);
        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.PENDENTE);
        assertThat(mensagem.getEnviarApos()).isEqualTo(AGORA.plus(Duration.ofMinutes(2)));

        service().registrarResposta(MENSAGEM, "0000", temporario);
        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.PENDENTE);
        assertThat(mensagem.getEnviarApos()).isEqualTo(AGORA.plus(Duration.ofMinutes(4)));

        service().registrarResposta(MENSAGEM, "0000", temporario);
        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.FALHOU);
        assertThat(mensagem.getErroCodigo()).isEqualTo("HTTP_503");
        assertThat(mensagem.getFalhouEm()).isEqualTo(AGORA);
    }

    @Test
    void erroDefinitivoOuIndeterminadoNuncaERepetido() {
        service().registrarResposta(MENSAGEM, "0000", new Resposta(Situacao.INDETERMINADO, null, "TIMEOUT"));

        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.FALHOU);
        assertThat(mensagem.getErroCodigo()).isEqualTo("TIMEOUT");

        mensagem.setResultado(WhatsAppResultado.ENVIANDO);
        service().registrarResposta(MENSAGEM, "0000", new Resposta(Situacao.ERRO_DEFINITIVO, null, "META_131026"));

        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.FALHOU);
        assertThat(mensagem.getErroCodigo()).isEqualTo("META_131026");
    }

    @Test
    void envioInterrompidoPorQuedaViraFalha_naoERepetido() {
        when(mensagemRepository.findByResultado(WhatsAppResultado.ENVIANDO)).thenReturn(List.of(mensagem));

        service().encerrarEnviosInterrompidos();

        assertThat(mensagem.getResultado()).isEqualTo(WhatsAppResultado.FALHOU);
        assertThat(mensagem.getErroCodigo()).isEqualTo(WhatsAppMensagemService.ERRO_INTERROMPIDO);
    }
}
