package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.vitrine.VitrinePublicaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.vitrine.VitrinePublicaViewDTO.Catalogo;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.vitrine.VitrinePublicaViewDTO.Sistema;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.vitrine.VitrinePublicaViewDTO.Totais;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.VitrinePublicaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.VitrineTotaisProjection;
import lombok.extern.slf4j.Slf4j;

/**
 * Resumo publico do municipio para a landing do SIRG.
 *
 * <p>A rota e anonima, entao o visitante nunca dispara consulta: o resumo e
 * calculado no maximo uma vez por hora e servido da memoria. O cache e sob
 * demanda, e nao {@code @Scheduled}, porque o agendador tem uma unica thread,
 * que e do envio de WhatsApp.
 */
@Service
@Slf4j
public class VitrinePublicaService {

    static final Duration VALIDADE = Duration.ofHours(1);
    /** Depois de uma falha, quanto esperar para tentar o banco de novo. */
    static final Duration ESPERA_APOS_FALHA = Duration.ofMinutes(1);

    private final VitrinePublicaRepository repository;
    private final InstanceContext instanceContext;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final Clock clock;
    private final String versao;
    private final long limiarSupressao;

    private final AtomicReference<Snapshot> cache = new AtomicReference<>();
    /** Sem resumo nenhum ainda e com o banco falhando: ate quando nem tentar. */
    private volatile Instant semTentarAte = Instant.MIN;

    private record Snapshot(VitrinePublicaViewDTO dados, Instant validoAte) {
    }

    // BuildProperties so existe quando o jar passa pelo build-info do Maven; pela
    // IDE e nos testes o bean nao existe, por isso ObjectProvider.
    public VitrinePublicaService(VitrinePublicaRepository repository,
                                 InstanceContext instanceContext,
                                 ObjectProvider<BuildProperties> buildProperties,
                                 Clock clock,
                                 @Value("${app.versao}") String versao,
                                 @Value("${app.vitrine.limiar-supressao}") long limiarSupressao) {
        this.repository = repository;
        this.instanceContext = instanceContext;
        this.buildProperties = buildProperties;
        this.clock = clock;
        this.versao = versao;
        this.limiarSupressao = limiarSupressao;
    }

    public VitrinePublicaViewDTO resumo() {
        Snapshot atual = cache.get();
        if (valido(atual)) {
            return atual.dados();
        }
        return recalcular();
    }

    private boolean valido(Snapshot snapshot) {
        return snapshot != null && clock.instant().isBefore(snapshot.validoAte());
    }

    private synchronized VitrinePublicaViewDTO recalcular() {
        Snapshot atual = cache.get();
        if (valido(atual)) {
            return atual.dados();
        }
        Instant agora = clock.instant();
        if (atual == null && agora.isBefore(semTentarAte)) {
            throw indisponivel();
        }
        try {
            VitrinePublicaViewDTO novo = calcular(agora);
            cache.set(new Snapshot(novo, agora.plus(VALIDADE)));
            return novo;
        } catch (RuntimeException e) {
            if (atual == null) {
                // Rota anonima: sem a espera, cada visita tentaria o banco de novo.
                log.error("Vitrine publica: falha ao montar o resumo e nao ha resumo anterior", e);
                semTentarAte = agora.plus(ESPERA_APOS_FALHA);
                throw indisponivel();
            }
            log.warn("Vitrine publica: falha ao recalcular, servindo o resumo de {}", atual.dados().geradoEm(), e);
            cache.set(new Snapshot(atual.dados(), agora.plus(ESPERA_APOS_FALHA)));
            return atual.dados();
        }
    }

    /** Vira 409 com mensagem no GlobalExceptionHandler, em vez de um 500 com o erro do banco. */
    private static IllegalStateException indisponivel() {
        return new IllegalStateException("Resumo temporariamente indisponível. Tente novamente em instantes.");
    }

    private VitrinePublicaViewDTO calcular(Instant agora) {
        VitrineTotaisProjection t = repository.totais();
        List<String> nomes = List.copyOf(repository.nomesDasEspecialidadesPublicas());
        BuildProperties build = buildProperties.getIfAvailable();

        return new VitrinePublicaViewDTO(
                instanceContext.getNomeIdentificador(),
                new Totais(
                        suprimir(t.getSolicitacoes()),
                        suprimir(t.getAgendamentos()),
                        suprimir(t.getPacientesAtendidos()),
                        suprimir(t.getViagensTransporte())),
                new Catalogo(zeroSeNulo(t.getUnidades()), zeroSeNulo(t.getEspecialidades()), nomes),
                new Sistema(versao, build == null ? null : build.getTime()),
                agora);
    }

    /** Contagem de atendimento abaixo do limiar nao e publicada. */
    private Long suprimir(Long contagem) {
        return contagem == null || contagem < limiarSupressao ? null : contagem;
    }

    private static long zeroSeNulo(Long valor) {
        return valor == null ? 0 : valor;
    }
}
