package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppCloudApiClient.Resposta;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.WhatsAppMensagemService.Preparo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tarefas agendadas do WhatsApp: despacho da fila e lote diario de lembretes.
 *
 * <p>E o unico ponto do sistema que chama a Meta. Fica fora de qualquer
 * transacao de negocio — o operador que agendou ja recebeu a resposta ha
 * segundos, e uma Meta lenta ou fora do ar so atrasa esta tarefa.
 *
 * <p>Cada mensagem passa por tres transacoes curtas (reivindicar, preparar,
 * registrar) com a chamada HTTP entre elas, fora de transacao: nao se segura
 * conexao de banco esperando a rede.
 *
 * <p>Instancia sem credenciais: as duas tarefas retornam na primeira linha, sem
 * tocar no banco.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppEnvioJob {

    private final WhatsAppMensagemService mensagemService;
    private final WhatsAppLembreteService lembreteService;
    private final WhatsAppCloudApiClient cloudApiClient;
    private final WhatsAppEnvioProperties props;

    @Scheduled(fixedDelayString = "${app.whatsapp.envio.intervalo-ms:30000}",
            initialDelayString = "${app.whatsapp.envio.intervalo-ms:30000}")
    public void despacharAgendado() {
        if (!props.agendadorLigado() || !props.configurado()) {
            return;
        }
        try {
            despachar();
        } catch (Throwable e) {
            log.error("WhatsApp: falha no despacho da fila ({}).", e.getClass().getSimpleName());
        }
    }

    /** As 8h de Brasilia. Bahia e UTC-3 sem horario de verao. */
    @Scheduled(cron = "${app.whatsapp.envio.lembrete-cron:0 0 8 * * *}", zone = "America/Bahia")
    public void lembretesAgendados() {
        if (!props.agendadorLigado() || !props.configurado()) {
            return;
        }
        try {
            lembreteService.executarLote(WhatsAppOrigem.AUTOMATICO, null);
        } catch (Throwable e) {
            log.error("WhatsApp: falha no lote de lembretes ({}).", e.getClass().getSimpleName());
        }
    }

    /** Uma rodada: ate 20 mensagens. */
    public void despachar() {
        // A tarefa tem uma thread so e roda em sequencia: no inicio de uma rodada
        // nenhum envio legitimo esta em andamento.
        mensagemService.encerrarEnviosInterrompidos();
        for (Long id : mensagemService.proximasDaFila()) {
            processar(id);
        }
    }

    void processar(Long id) {
        if (!mensagemService.reivindicar(id)) {
            return;
        }
        Preparo preparo;
        try {
            preparo = mensagemService.preparar(id);
        } catch (RuntimeException e) {
            log.error("WhatsApp: falha ao preparar a mensagem id={} ({}).", id, e.getClass().getSimpleName());
            mensagemService.registrarFalha(id, "ERRO_INTERNO");
            return;
        }
        if (!preparo.enviar()) {
            mensagemService.registrarNaoEnvio(id, preparo.motivo());
            return;
        }
        Resposta resposta = cloudApiClient.enviarTemplate(preparo.numero(), preparo.template(), preparo.variaveis());
        mensagemService.registrarResposta(id, preparo.telefoneFinal(), resposta);
        if (resposta.situacao() != WhatsAppCloudApiClient.Situacao.ACEITA) {
            log.warn("WhatsApp: mensagem id={} nao enviada ({} {}).", id, resposta.situacao(), resposta.erroCodigo());
        }
    }
}
