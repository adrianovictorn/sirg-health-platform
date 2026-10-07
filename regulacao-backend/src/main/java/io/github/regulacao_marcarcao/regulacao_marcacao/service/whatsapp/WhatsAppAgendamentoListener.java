package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.evento.AgendamentoCanceladoEvent;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.evento.AgendamentoCriadoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liga o agendamento ao WhatsApp sem que um dependa do outro.
 *
 * <p>Roda so DEPOIS do commit do agendamento: se a cota der 409 ou qualquer
 * coisa der rollback, este listener nem e chamado. E nada que aconteca aqui
 * volta para o operador — todo erro e engolido, porque o agendamento ja esta
 * gravado e nao pode falhar por causa de mensagem.
 *
 * <p>Aqui so se grava uma linha na fila (sem HTTP). Quem fala com a Meta e
 * {@link WhatsAppEnvioJob}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppAgendamentoListener {

    private final WhatsAppMensagemService mensagemService;
    private final WhatsAppEnvioProperties props;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoCriar(AgendamentoCriadoEvent evento) {
        if (!props.configurado()) {
            return;
        }
        try {
            mensagemService.enfileirarConfirmacao(
                    evento.agendamentoId(), evento.solicitacaoId(), evento.data(), evento.itens());
        } catch (Throwable e) {
            log.error("WhatsApp: falha ao enfileirar confirmacao do agendamento id={} ({}).",
                    evento.agendamentoId(), e.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoCancelar(AgendamentoCanceladoEvent evento) {
        if (!props.configurado()) {
            return;
        }
        try {
            mensagemService.enfileirarCancelamento(
                    evento.agendamentoId(), evento.solicitacaoId(), evento.data(), evento.itens());
        } catch (Throwable e) {
            log.error("WhatsApp: falha ao enfileirar cancelamento do agendamento id={} ({}).",
                    evento.agendamentoId(), e.getClass().getSimpleName());
        }
    }
}
