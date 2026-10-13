package io.github.regulacao_marcarcao.regulacao_marcacao.service.federacao;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.config.RabbitMQConfig;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.notificacao.AgendamentoRemarcadoMensagemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisa o municipio de origem do paciente que o agendamento foi remarcado.
 *
 * <p>Roda so DEPOIS do commit da migracao: se algo der rollback, nada sai. Nao
 * escreve no banco, entao dispensa transacao propria. Todo erro e engolido — a
 * migracao ja esta gravada e nao pode falhar por causa da fila. Diferente do aviso
 * de agendamento externo, nao ha fallback BROADCAST: sem municipio de origem
 * conhecido o evento nem e publicado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FederacaoRemarcacaoListener {

    private final RabbitTemplate rabbitTemplate;
    private final InstanceContext instanceContext;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRemarcar(AgendamentoExternoRemarcadoEvent evento) {
        try {
            var mensagem = new AgendamentoRemarcadoMensagemDTO(
                    evento.solicitacaoId(),
                    evento.cpfMascarado(),
                    evento.nomePaciente(),
                    instanceContext.getMunicipioLocal().getNome(),
                    evento.dataAnterior(),
                    evento.dataNova());
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                    "agendamento-remarcado." + evento.destinoNome(), mensagem);
        } catch (Throwable e) {
            log.error("Federacao: falha ao avisar a remarcacao da solicitacao id={} ({}).",
                    evento.solicitacaoId(), e.getClass().getSimpleName());
        }
    }
}
