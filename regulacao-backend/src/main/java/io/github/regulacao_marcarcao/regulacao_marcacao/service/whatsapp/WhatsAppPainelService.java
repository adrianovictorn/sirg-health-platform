package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp.WhatsAppConfigViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp.WhatsAppIndicadoresDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp.WhatsAppMensagemViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppConfig;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagemSpecification;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppConfigRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppMensagemRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.WhatsAppWebhookService;
import lombok.RequiredArgsConstructor;

/**
 * Leitura para o painel de admin do WhatsApp: estado, lista de operacao e volume.
 *
 * <p>So ADMIN chega aqui (restricao no controller), por isso nao ha escopo por
 * unidade — e a lista nao expoe nome nem telefone.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppPainelService {

    /** Periodo usado quando a tela nao informa datas. */
    static final int DIAS_PADRAO = 7;

    private final WhatsAppMensagemRepository mensagemRepository;
    private final WhatsAppConfigRepository configRepository;
    private final WhatsAppMensagemService mensagemService;
    private final WhatsAppWebhookService webhookService;
    private final WhatsAppEnvioProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public WhatsAppConfigViewDTO estado() {
        WhatsAppConfig config = configRepository.findById(WhatsAppConfig.ID_UNICO).orElse(null);
        return WhatsAppConfigViewDTO.from(
                config,
                props.configurado(),
                webhookService.habilitado(),
                props.limiteDiario(),
                props.configurado() ? mensagemService.enviadasHoje() : 0,
                !props.numerosTesteSoDigitos().isEmpty());
    }

    @Transactional(readOnly = true)
    public Page<WhatsAppMensagemViewDTO> listar(LocalDate de, LocalDate ate, WhatsAppTipoMensagem tipo,
                                                WhatsAppResultado resultado, Pageable pageable) {
        Periodo periodo = periodo(de, ate);
        return mensagemRepository.findAll(
                Specification.where(WhatsAppMensagemSpecification.criadaEntre(periodo.inicio(), periodo.fim()))
                        .and(WhatsAppMensagemSpecification.doTipo(tipo))
                        .and(WhatsAppMensagemSpecification.comResultado(resultado)),
                pageable).map(WhatsAppMensagemViewDTO::from);
    }

    @Transactional(readOnly = true)
    public WhatsAppIndicadoresDTO indicadores(LocalDate de, LocalDate ate) {
        Periodo periodo = periodo(de, ate);

        long total = 0, enviadas = 0, entregues = 0, lidas = 0, falhas = 0, pendentes = 0, naoEnviadas = 0;
        Map<String, Long> porMotivo = new TreeMap<>();
        for (Object[] linha : mensagemRepository.contarPorResultadoEMotivo(periodo.inicio(), periodo.fim())) {
            WhatsAppResultado resultado = (WhatsAppResultado) linha[0];
            WhatsAppMotivoNaoEnvio motivo = (WhatsAppMotivoNaoEnvio) linha[1];
            long quantidade = (Long) linha[2];
            total += quantidade;
            switch (resultado) {
                case PENDENTE, ENVIANDO -> pendentes += quantidade;
                case ENVIADO -> enviadas += quantidade;
                case ENTREGUE -> {
                    enviadas += quantidade;
                    entregues += quantidade;
                }
                case LIDO -> {
                    enviadas += quantidade;
                    entregues += quantidade;
                    lidas += quantidade;
                }
                case FALHOU -> falhas += quantidade;
                case NAO_ENVIADO -> {
                    naoEnviadas += quantidade;
                    porMotivo.merge(motivo == null ? "SEM_MOTIVO" : motivo.name(), quantidade, Long::sum);
                }
            }
        }

        long cobraveis = 0;
        Map<String, Long> porCategoria = new TreeMap<>();
        for (Object[] linha : mensagemRepository.contarCobraveisPorCategoria(periodo.inicio(), periodo.fim())) {
            long quantidade = (Long) linha[1];
            cobraveis += quantidade;
            porCategoria.merge(linha[0] == null ? "sem categoria" : (String) linha[0], quantidade, Long::sum);
        }

        return new WhatsAppIndicadoresDTO(periodo.de(), periodo.ate(), total, enviadas, entregues, lidas, falhas,
                pendentes, naoEnviadas, porMotivo, cobraveis, porCategoria,
                mensagemRepository.somarRecebidas(periodo.de(), periodo.ate()));
    }

    private record Periodo(LocalDate de, LocalDate ate, Instant inicio, Instant fim) {
    }

    /** Datas no fuso local, inclusivas nas duas pontas. Sem datas: os ultimos 7 dias. */
    private Periodo periodo(LocalDate de, LocalDate ate) {
        LocalDate hoje = LocalDate.now(clock.withZone(WhatsAppEnvioProperties.FUSO));
        LocalDate fim = ate != null ? ate : hoje;
        LocalDate inicio = de != null ? de : fim.minusDays(DIAS_PADRAO - 1L);
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial não pode ser depois da data final.");
        }
        return new Periodo(inicio, fim,
                inicio.atStartOfDay(WhatsAppEnvioProperties.FUSO).toInstant(),
                fim.plusDays(1).atStartOfDay(WhatsAppEnvioProperties.FUSO).toInstant());
    }
}
