package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Fila e registro das mensagens do WhatsApp.
 *
 * <p>Duas metades:
 * <ul>
 *   <li><b>Enfileirar</b> — grava uma linha PENDENTE. Chamado depois do commit
 *       do agendamento, por isso {@code REQUIRES_NEW}: sem transacao nova, a
 *       escrita feita apos o commit se perderia em silencio.</li>
 *   <li><b>Preparar e registrar</b> — usados por {@link WhatsAppEnvioJob}. O
 *       conteudo e remontado na hora do envio, relendo agendamento e paciente:
 *       a fila nunca guarda nome, telefone ou texto, e toda regra (chave,
 *       opt-out, limite, lista de teste) vale no momento em que a mensagem sai.</li>
 * </ul>
 *
 * <p><b>Remarcacao:</b> no sistema, remarcar e excluir o agendamento e criar
 * outro. O cancelamento fica pendente por alguns minutos; se o mesmo item for
 * reagendado nesse intervalo, o cancelamento e descartado e sai uma unica
 * mensagem, de remarcacao.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppMensagemService {

    /** Tentativas de envio antes de desistir (so para erro temporario). */
    static final int MAXIMO_DE_TENTATIVAS = 3;
    static final String ERRO_INTERROMPIDO = "INTERROMPIDO";

    private final WhatsAppMensagemRepository mensagemRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final AgendamentoSolicitacaoRepository agendamentoRepository;
    private final SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    private final UserRepository userRepository;
    private final WhatsAppConfigService configService;
    private final WhatsAppConteudoService conteudoService;
    private final WhatsAppEnvioProperties props;
    private final InstanceContext instanceContext;
    private final Clock clock;

    /** O que a tarefa de envio precisa para chamar a Meta — ou o motivo para nao chamar. */
    public record Preparo(WhatsAppMotivoNaoEnvio motivo, String numero, String telefoneFinal,
                          String template, List<String> variaveis) {

        static Preparo naoEnviar(WhatsAppMotivoNaoEnvio motivo) {
            return new Preparo(motivo, null, null, null, null);
        }

        public boolean enviar() {
            return motivo == null;
        }
    }

    // ------------------------------------------------------------------
    // Enfileirar
    // ------------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enfileirarConfirmacao(Long agendamentoId, Long solicitacaoId, LocalDate data, List<Long> itens) {
        // Cancelamento ainda pendente dos mesmos itens = o operador esta remarcando.
        Set<Long> novos = Set.copyOf(itens);
        boolean remarcacao = false;
        for (WhatsAppMensagem cancelamento : mensagemRepository.findBySolicitacaoIdAndTipoAndResultado(
                solicitacaoId, WhatsAppTipoMensagem.CANCELAMENTO, WhatsAppResultado.PENDENTE)) {
            // So conta como remarcacao se o cancelamento ainda nao tinha sido pego para envio.
            if (itensDe(cancelamento).stream().anyMatch(novos::contains)
                    && mensagemRepository.descartarPendente(cancelamento.getId(),
                            WhatsAppMotivoNaoEnvio.SUBSTITUIDO_POR_REMARCACAO) == 1) {
                remarcacao = true;
            }
        }
        WhatsAppMensagem mensagem = nova(agendamentoId, solicitacaoId, data,
                remarcacao ? WhatsAppTipoMensagem.REMARCACAO : WhatsAppTipoMensagem.CONFIRMACAO,
                WhatsAppOrigem.AUTOMATICO);
        mensagem.setItensRef(juntar(itens));
        mensagemRepository.save(mensagem);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enfileirarCancelamento(Long agendamentoId, Long solicitacaoId, LocalDate data, List<Long> itens) {
        // Agendamento criado e excluido antes de a confirmacao sair: o paciente
        // nunca soube dele, entao nao ha o que cancelar.
        List<WhatsAppMensagem> aindaNaFila = mensagemRepository.findByAlvoTipoAndAlvoIdAndResultado(
                WhatsAppAlvoTipo.CONSULTA_EXAME, agendamentoId, WhatsAppResultado.PENDENTE);
        boolean confirmacaoNaoSaiu = false;
        for (WhatsAppMensagem pendente : aindaNaFila) {
            boolean descartada = mensagemRepository.descartarPendente(pendente.getId(),
                    WhatsAppMotivoNaoEnvio.AGENDAMENTO_REMOVIDO) == 1;
            if (descartada && pendente.getTipo() == WhatsAppTipoMensagem.CONFIRMACAO) {
                confirmacaoNaoSaiu = true;
            }
        }
        if (confirmacaoNaoSaiu) {
            return;
        }
        WhatsAppMensagem mensagem = nova(agendamentoId, solicitacaoId, data,
                WhatsAppTipoMensagem.CANCELAMENTO, WhatsAppOrigem.AUTOMATICO);
        mensagem.setItensRef(juntar(itens));
        mensagem.setEnviarApos(clock.instant().plus(Duration.ofMinutes(props.cancelamentoAtrasoMinutos())));
        mensagemRepository.save(mensagem);
    }

    /**
     * Reenvio manual, pelo painel, da confirmacao ou do lembrete de um
     * agendamento. Passa pelas mesmas regras do envio automatico (opt-out,
     * limite, lista de teste) e nao tem chave de idempotencia: reenviar de novo
     * e decisao de quem clicou.
     */
    @Transactional
    public WhatsAppMensagem reenviar(Long agendamentoId, WhatsAppTipoMensagem tipo, String callerCpf) {
        if (tipo != WhatsAppTipoMensagem.CONFIRMACAO && tipo != WhatsAppTipoMensagem.LEMBRETE) {
            throw new IllegalArgumentException("Só é possível reenviar confirmação ou lembrete.");
        }
        if (!configService.podeEnviar()) {
            throw new IllegalStateException("O envio pelo WhatsApp está desligado ou não configurado.");
        }
        AgendamentoSolicitacao agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new EntityNotFoundException("Agendamento não encontrado — pode ter sido excluído."));
        WhatsAppMensagem mensagem = nova(agendamento.getId(), agendamento.getSolicitacao().getId(),
                agendamento.getDataAgendada(), tipo, WhatsAppOrigem.MANUAL);
        mensagem.setSolicitadoPorId(userRepository.findByCpf(callerCpf).map(u -> u.getId()).orElse(null));
        return mensagemRepository.save(mensagem);
    }

    private WhatsAppMensagem nova(Long agendamentoId, Long solicitacaoId, LocalDate data,
                                  WhatsAppTipoMensagem tipo, WhatsAppOrigem origem) {
        Instant agora = clock.instant();
        WhatsAppMensagem mensagem = new WhatsAppMensagem();
        mensagem.setAlvoTipo(WhatsAppAlvoTipo.CONSULTA_EXAME);
        mensagem.setAlvoId(agendamentoId);
        mensagem.setSolicitacaoId(solicitacaoId);
        mensagem.setDataReferencia(data);
        mensagem.setTipo(tipo);
        mensagem.setOrigem(origem);
        mensagem.setResultado(WhatsAppResultado.PENDENTE);
        mensagem.setCriadoEm(agora);
        mensagem.setEnviarApos(agora);
        return mensagem;
    }

    // ------------------------------------------------------------------
    // Despacho (usado por WhatsAppEnvioJob)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Long> proximasDaFila() {
        return mensagemRepository
                .findTop20ByResultadoAndEnviarAposLessThanEqualOrderByIdAsc(WhatsAppResultado.PENDENTE, clock.instant())
                .stream().map(WhatsAppMensagem::getId).toList();
    }

    /**
     * Linhas que ficaram em ENVIANDO porque a aplicacao caiu no meio de um
     * envio. Nao da para saber se sairam; repetir poderia duplicar a mensagem
     * no celular do paciente, entao viram falha.
     */
    @Transactional
    public void encerrarEnviosInterrompidos() {
        for (WhatsAppMensagem presa : mensagemRepository.findByResultado(WhatsAppResultado.ENVIANDO)) {
            falhar(presa, ERRO_INTERROMPIDO);
        }
    }

    /** {@code true} se esta execucao ficou com a mensagem. */
    @Transactional
    public boolean reivindicar(Long id) {
        return mensagemRepository.trocarResultado(id, WhatsAppResultado.PENDENTE, WhatsAppResultado.ENVIANDO) == 1;
    }

    /** Reaplica todas as regras e monta o conteudo, no momento do envio. */
    @Transactional(readOnly = true)
    public Preparo preparar(Long id) {
        WhatsAppMensagem mensagem = mensagemRepository.findById(id).orElseThrow();
        if (!props.configurado()) {
            return Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.NAO_CONFIGURADO);
        }
        if (!configService.envioLigado()) {
            return Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.ENVIO_DESLIGADO);
        }
        // Atendimento que ja passou: excluir ou lancar agendamento antigo (limpeza de
        // cadastro, registro retroativo) nao pode avisar o paciente de uma data vencida.
        if (mensagem.getDataReferencia().isBefore(LocalDate.now(clock.withZone(WhatsAppEnvioProperties.FUSO)))) {
            return Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.DATA_PASSADA);
        }
        Solicitacao solicitacao = mensagem.getSolicitacaoId() == null ? null
                : solicitacaoRepository.findById(mensagem.getSolicitacaoId()).orElse(null);
        if (solicitacao == null) {
            return Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.AGENDAMENTO_REMOVIDO);
        }
        if (solicitacao.getOrigemMunicipioId() != null
                && !solicitacao.getOrigemMunicipioId().equals(instanceContext.getMunicipioLocal().getId())) {
            return Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.PACIENTE_DE_OUTRO_MUNICIPIO);
        }
        if (optouPorSair(solicitacao)) {
            return Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.OPT_OUT);
        }
        var telefone = TelefoneWhatsAppNormalizador.normalizar(solicitacao.getTelefone());
        if (!telefone.valido()) {
            return Preparo.naoEnviar(telefone.motivo());
        }
        Set<String> numerosTeste = props.numerosTesteSoDigitos();
        if (!numerosTeste.isEmpty() && !numerosTeste.contains(telefone.numero())) {
            return Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.FORA_DA_LISTA_DE_TESTE);
        }
        if (enviadasHoje() >= props.limiteDiario()) {
            return Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.LIMITE_DIARIO);
        }

        if (mensagem.getTipo() == WhatsAppTipoMensagem.CANCELAMENTO) {
            return new Preparo(null, telefone.numero(), telefone.finalDoNumero(), props.template().cancelamento(),
                    conteudoService.cancelamento(solicitacao, mensagem.getDataReferencia()));
        }

        AgendamentoSolicitacao agendamento = agendamentoRepository.findById(mensagem.getAlvoId()).orElse(null);
        List<SolicitacaoEspecialidade> itens = agendamento == null ? List.of()
                : solicitacaoEspecialidadeRepository.findByAgendamentoSolicitacaoId(agendamento.getId()).stream()
                        .filter(se -> se.getStatus() == StatusDaMarcacao.AGENDADO)
                        .toList();
        if (itens.isEmpty()) {
            // Excluido, ou todos os itens deixaram de estar agendados (faltou, cancelado a mao).
            return Preparo.naoEnviar(WhatsAppMotivoNaoEnvio.AGENDAMENTO_REMOVIDO);
        }
        boolean lembrete = mensagem.getTipo() == WhatsAppTipoMensagem.LEMBRETE;
        return new Preparo(null, telefone.numero(), telefone.finalDoNumero(),
                lembrete ? props.template().lembrete() : props.template().confirmacao(),
                lembrete ? conteudoService.lembrete(solicitacao, agendamento, itens)
                        : conteudoService.confirmacao(solicitacao, agendamento, itens));
    }

    /**
     * O opt-out vale para o paciente, nao para a ficha: ha CPFs gravados com e
     * sem mascara em fichas diferentes, entao a comparacao e pelos digitos.
     */
    private boolean optouPorSair(Solicitacao solicitacao) {
        if (Boolean.TRUE.equals(solicitacao.getWhatsappOptOut())) {
            return true;
        }
        String cpf = solicitacao.getCpfPaciente() == null ? "" : solicitacao.getCpfPaciente().replaceAll("\\D", "");
        if (cpf.isEmpty()) {
            return false;
        }
        return solicitacaoRepository.findByCpfPacienteSemPonto(cpf).stream()
                .anyMatch(s -> Boolean.TRUE.equals(s.getWhatsappOptOut()));
    }

    /** Mensagens aceitas pela Meta hoje, no fuso local. */
    @Transactional(readOnly = true)
    public long enviadasHoje() {
        Instant inicioDoDia = LocalDate.now(clock.withZone(WhatsAppEnvioProperties.FUSO))
                .atStartOfDay(WhatsAppEnvioProperties.FUSO).toInstant();
        return mensagemRepository.countByEnviadoEmGreaterThanEqual(inicioDoDia);
    }

    @Transactional
    public void registrarNaoEnvio(Long id, WhatsAppMotivoNaoEnvio motivo) {
        mensagemRepository.findById(id).ifPresent(m -> naoEnviar(m, motivo));
    }

    @Transactional
    public void registrarFalha(Long id, String erroCodigo) {
        mensagemRepository.findById(id).ifPresent(m -> falhar(m, erroCodigo));
    }

    @Transactional
    public void registrarResposta(Long id, String telefoneFinal, Resposta resposta) {
        WhatsAppMensagem mensagem = mensagemRepository.findById(id).orElse(null);
        if (mensagem == null) {
            return;
        }
        Instant agora = clock.instant();
        mensagem.setTelefoneFinal(telefoneFinal);
        mensagem.setTentativas(mensagem.getTentativas() + 1);
        switch (resposta.situacao()) {
            case ACEITA -> {
                mensagem.setResultado(WhatsAppResultado.ENVIADO);
                mensagem.setMetaMessageId(resposta.messageId());
                mensagem.setEnviadoEm(agora);
                mensagem.setErroCodigo(null);
            }
            case ERRO_TEMPORARIO -> {
                mensagem.setErroCodigo(resposta.erroCodigo());
                if (mensagem.getTentativas() < MAXIMO_DE_TENTATIVAS) {
                    mensagem.setResultado(WhatsAppResultado.PENDENTE);
                    mensagem.setEnviarApos(agora.plus(Duration.ofMinutes(2L * mensagem.getTentativas())));
                } else {
                    mensagem.setResultado(WhatsAppResultado.FALHOU);
                    mensagem.setFalhouEm(agora);
                }
            }
            case ERRO_DEFINITIVO, INDETERMINADO -> {
                mensagem.setResultado(WhatsAppResultado.FALHOU);
                mensagem.setErroCodigo(resposta.erroCodigo());
                mensagem.setFalhouEm(agora);
            }
        }
        mensagemRepository.save(mensagem);
    }

    private void naoEnviar(WhatsAppMensagem mensagem, WhatsAppMotivoNaoEnvio motivo) {
        mensagem.setResultado(WhatsAppResultado.NAO_ENVIADO);
        mensagem.setMotivo(motivo);
        mensagemRepository.save(mensagem);
    }

    private void falhar(WhatsAppMensagem mensagem, String erroCodigo) {
        mensagem.setResultado(WhatsAppResultado.FALHOU);
        mensagem.setErroCodigo(erroCodigo);
        mensagem.setFalhouEm(clock.instant());
        mensagemRepository.save(mensagem);
    }

    private static String juntar(List<Long> itens) {
        return itens.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Long> itensDe(WhatsAppMensagem mensagem) {
        if (mensagem.getItensRef() == null || mensagem.getItensRef().isBlank()) {
            return List.of();
        }
        return Arrays.stream(mensagem.getItensRef().split(",")).map(Long::valueOf).toList();
    }
}
