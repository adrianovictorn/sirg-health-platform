package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppLoteLembrete;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppLoteLembreteRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppMensagemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lote diario de lembretes: avisa quem tem atendimento daqui a 3 dias corridos.
 *
 * <p>Regras:
 * <ul>
 *   <li>Roda as 8h. No dia H entram os agendamentos de H+3 que ja existiam as
 *       8h de H — quem foi agendado depois acabou de receber a confirmacao.</li>
 *   <li>Se o lote de H-1 nao rodou (servidor fora do ar, envio desligado),
 *       entram tambem os de H+2 que ja existiam as 8h de H-1: recuperacao de um
 *       dia, nunca mais. E o que impede rajada ao ligar o envio.</li>
 *   <li>No maximo um lembrete por agendamento e data, garantido por indice unico
 *       — rodar duas vezes no dia enfileira zero na segunda.</li>
 *   <li>Com o envio desligado ou nao configurado o lote nao grava nada, nem o
 *       registro de que rodou.</li>
 * </ul>
 *
 * <p>Aqui so se enfileira. Agendamento cancelado ou sem item agendado e
 * barrado de novo na hora do envio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppLembreteService {

    static final int DIAS_DE_ANTECEDENCIA = 3;
    static final LocalTime HORA_DO_LOTE = LocalTime.of(8, 0);

    private final AgendamentoSolicitacaoRepository agendamentoRepository;
    private final WhatsAppMensagemRepository mensagemRepository;
    private final WhatsAppLoteLembreteRepository loteRepository;
    private final UserRepository userRepository;
    private final WhatsAppConfigService configService;
    private final Clock clock;

    /**
     * @param callerCpf quem pediu, no disparo manual; nulo no automatico
     * @return quantos lembretes novos foram enfileirados
     * @throws IllegalStateException no disparo manual com o envio desligado
     */
    @Transactional
    public int executarLote(WhatsAppOrigem origem, String callerCpf) {
        if (!configService.podeEnviar()) {
            if (origem == WhatsAppOrigem.MANUAL) {
                throw new IllegalStateException("O envio pelo WhatsApp está desligado ou não configurado.");
            }
            return 0;
        }
        LocalDate hoje = LocalDate.now(clock.withZone(WhatsAppEnvioProperties.FUSO));
        Long solicitadoPorId = callerCpf == null ? null
                : userRepository.findByCpf(callerCpf).map(u -> u.getId()).orElse(null);

        int enfileirados = enfileirar(hoje.plusDays(DIAS_DE_ANTECEDENCIA), hoje, origem, solicitadoPorId);
        if (!loteRepository.existsByDataExecucao(hoje.minusDays(1))) {
            enfileirados += enfileirar(hoje.plusDays(DIAS_DE_ANTECEDENCIA - 1), hoje.minusDays(1),
                    origem, solicitadoPorId);
        }

        WhatsAppLoteLembrete lote = loteRepository.findByDataExecucao(hoje).orElseGet(() -> {
            WhatsAppLoteLembrete novo = new WhatsAppLoteLembrete();
            novo.setDataExecucao(hoje);
            novo.setOrigem(origem);
            return novo;
        });
        lote.setExecutadoEm(clock.instant());
        lote.setEnfileirados(lote.getEnfileirados() + enfileirados);
        loteRepository.save(lote);

        log.info("WhatsApp: lote de lembretes ({}) enfileirou {} mensagem(ns).", origem, enfileirados);
        return enfileirados;
    }

    /**
     * @param dataDoAtendimento agendamentos desta data
     * @param diaDoCorte        so os que ja existiam as 8h deste dia
     */
    private int enfileirar(LocalDate dataDoAtendimento, LocalDate diaDoCorte, WhatsAppOrigem origem,
                           Long solicitadoPorId) {
        // data_criacao e gravada no fuso da JVM (UTC no container); o corte e 8h no fuso local.
        LocalDateTime corte = diaDoCorte.atTime(HORA_DO_LOTE).atZone(WhatsAppEnvioProperties.FUSO)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        int enfileirados = 0;
        for (AgendamentoSolicitacao agendamento : agendamentoRepository
                .findComItemNoStatusPorData(dataDoAtendimento, StatusDaMarcacao.AGENDADO)) {
            // Sem data de criacao = agendamento antigo: ja existia.
            if (agendamento.getDataCriacao() != null && !agendamento.getDataCriacao().isBefore(corte)) {
                continue;
            }
            enfileirados += mensagemRepository.enfileirarLembrete(
                    agendamento.getId(),
                    agendamento.getSolicitacao().getId(),
                    origem.name(),
                    dataDoAtendimento,
                    chave(agendamento.getId(), dataDoAtendimento),
                    solicitadoPorId);
        }
        return enfileirados;
    }

    static String chave(Long agendamentoId, LocalDate data) {
        return "LEMBRETE:CONSULTA_EXAME:" + agendamentoId + ":" + data;
    }
}
