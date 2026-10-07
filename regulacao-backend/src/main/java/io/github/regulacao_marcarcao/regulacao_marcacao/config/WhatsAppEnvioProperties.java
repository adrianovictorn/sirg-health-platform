package io.github.regulacao_marcarcao.regulacao_marcacao.config;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracao do ENVIO de mensagens pela WhatsApp Business Cloud API.
 *
 * <p>Independente do webhook ({@code app.whatsapp.verify-token/app-secret}): uma
 * instancia pode receber eventos sem enviar. O envio so esta "configurado" com
 * {@code access-token} E {@code phone-number-id}; sem os dois nada e enfileirado
 * e a instancia opera exatamente como antes.
 *
 * <p>{@code numerosTeste}: enquanto preenchida, so esses numeros recebem — trava
 * para a primeira ativacao e para ambiente com copia de dados reais.
 */
@ConfigurationProperties("app.whatsapp.envio")
public record WhatsAppEnvioProperties(
        String accessToken,
        String phoneNumberId,
        @DefaultValue("https://graph.facebook.com") String baseUrl,
        @DefaultValue("v23.0") String apiVersion,
        @DefaultValue("3000") int timeoutConexaoMs,
        @DefaultValue("10000") int timeoutLeituraMs,
        @DefaultValue("200") int limiteDiario,
        String numerosTeste,
        @DefaultValue("10") int cancelamentoAtrasoMinutos,
        @DefaultValue("true") boolean agendadorLigado,
        @DefaultValue("pt_BR") String idioma,
        @DefaultValue Template template) {

    /**
     * Fuso das regras de data (lembrete, limite diario). O container roda em
     * UTC; {@code LocalDate.now()} puro erra o dia entre 21h e 0h de Brasilia.
     */
    public static final ZoneId FUSO = ZoneId.of("America/Bahia");

    /** Nomes dos templates aprovados no WhatsApp Manager. */
    public record Template(
            @DefaultValue("sirg_confirmacao_agendamento") String confirmacao,
            @DefaultValue("sirg_cancelamento_agendamento") String cancelamento,
            @DefaultValue("sirg_lembrete_agendamento") String lembrete) {
    }

    public boolean configurado() {
        return accessToken != null && !accessToken.isBlank()
                && phoneNumberId != null && !phoneNumberId.isBlank();
    }

    /** Numeros de teste so com digitos. Vazio = sem restricao. */
    public Set<String> numerosTesteSoDigitos() {
        if (numerosTeste == null || numerosTeste.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(numerosTeste.split(","))
                .map(n -> n.replaceAll("\\D", ""))
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
