package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppConfig;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Chave que liga/desliga o ENVIO pelo WhatsApp sem redeploy.
 *
 * <p>Fica em banco para sobreviver a reinicio. Vale a partir do proximo envio:
 * a tarefa que despacha a fila consulta a chave a cada mensagem. Nao afeta o
 * recebimento do webhook — recusar eventos faria a Meta reenviar por dias.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppConfigService {

    private final WhatsAppConfigRepository configRepository;
    private final UserRepository userRepository;
    private final WhatsAppEnvioProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean envioLigado() {
        return configRepository.findById(WhatsAppConfig.ID_UNICO)
                .map(WhatsAppConfig::isEnvioLigado)
                .orElse(false);
    }

    /** Configurado (credenciais na instancia) E ligado (chave). */
    @Transactional(readOnly = true)
    public boolean podeEnviar() {
        return props.configurado() && envioLigado();
    }

    @Transactional
    public WhatsAppConfig alterarEnvio(boolean ligado, String callerCpf) {
        if (ligado && !props.configurado()) {
            throw new IllegalStateException(
                    "O envio pelo WhatsApp não está configurado nesta instância (faltam as credenciais).");
        }
        WhatsAppConfig config = configRepository.findById(WhatsAppConfig.ID_UNICO).orElseGet(() -> {
            WhatsAppConfig nova = new WhatsAppConfig();
            nova.setId(WhatsAppConfig.ID_UNICO);
            return nova;
        });
        config.setEnvioLigado(ligado);
        config.setAlteradoPor(userRepository.findByCpf(callerCpf).orElse(null));
        config.setAlteradoEm(clock.instant());
        WhatsAppConfig salva = configRepository.save(config);
        log.info("Envio do WhatsApp {} pelo usuario id={}.", ligado ? "LIGADO" : "DESLIGADO",
                salva.getAlteradoPor() != null ? salva.getAlteradoPor().getId() : null);
        return salva;
    }
}
