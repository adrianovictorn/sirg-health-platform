package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppConfig;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppConfigRepository;

/** A chave que liga o envio: nasce desligada e nao liga sem credenciais. */
class WhatsAppConfigServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-06T15:00:00Z");

    private final WhatsAppConfigRepository configRepository = mock(WhatsAppConfigRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private WhatsAppConfig linha;

    @BeforeEach
    void setUp() {
        linha = new WhatsAppConfig();
        linha.setId(WhatsAppConfig.ID_UNICO);
        when(configRepository.findById(WhatsAppConfig.ID_UNICO)).thenReturn(Optional.of(linha));
        when(configRepository.save(any(WhatsAppConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private WhatsAppConfigService service(WhatsAppEnvioProperties props) {
        return new WhatsAppConfigService(configRepository, userRepository, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void nasceDesligada() {
        assertThat(service(WhatsAppTestes.configurado()).envioLigado()).isFalse();
        assertThat(service(WhatsAppTestes.configurado()).podeEnviar()).isFalse();
    }

    @Test
    void semALinhaNoBancoContaComoDesligada() {
        when(configRepository.findById(WhatsAppConfig.ID_UNICO)).thenReturn(Optional.empty());

        assertThat(service(WhatsAppTestes.configurado()).envioLigado()).isFalse();
    }

    @Test
    void ligarRegistraQuemEQuando() {
        User admin = new User();
        admin.setId(9L);
        when(userRepository.findByCpf("11122233344")).thenReturn(Optional.of(admin));
        var service = service(WhatsAppTestes.configurado());

        service.alterarEnvio(true, "11122233344");

        assertThat(linha.isEnvioLigado()).isTrue();
        assertThat(linha.getAlteradoPor()).isSameAs(admin);
        assertThat(linha.getAlteradoEm()).isEqualTo(AGORA);
        assertThat(service.podeEnviar()).isTrue();
    }

    @Test
    void semCredenciaisNaoLiga() {
        var service = service(WhatsAppTestes.naoConfigurado());

        assertThatThrownBy(() -> service.alterarEnvio(true, "11122233344"))
                .isInstanceOf(IllegalStateException.class);
        verify(configRepository, never()).save(any());
        assertThat(linha.isEnvioLigado()).isFalse();
    }

    @Test
    void desligarSempreEPermitido() {
        linha.setEnvioLigado(true);

        service(WhatsAppTestes.naoConfigurado()).alterarEnvio(false, "11122233344");

        assertThat(linha.isEnvioLigado()).isFalse();
    }

    @Test
    void ligadaMasSemCredenciaisNaoPodeEnviar() {
        linha.setEnvioLigado(true);

        assertThat(service(WhatsAppTestes.naoConfigurado()).podeEnviar()).isFalse();
    }
}
