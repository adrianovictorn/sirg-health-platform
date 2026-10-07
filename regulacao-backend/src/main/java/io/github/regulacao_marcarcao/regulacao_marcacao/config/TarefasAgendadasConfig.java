package io.github.regulacao_marcarcao.regulacao_marcacao.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas agendadas ({@code @Scheduled}) — as primeiras do sistema sao
 * as do WhatsApp (despacho da fila e lote de lembretes).
 *
 * <p>O agendador padrao do Spring tem UMA thread: uma tarefa lenta atrasa as
 * demais. Por isso as tarefas trabalham em lotes pequenos e com timeout curto.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(WhatsAppEnvioProperties.class)
public class TarefasAgendadasConfig {

    /** Relogio injetavel: as regras de data do lembrete sao testadas com hora fixa. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
