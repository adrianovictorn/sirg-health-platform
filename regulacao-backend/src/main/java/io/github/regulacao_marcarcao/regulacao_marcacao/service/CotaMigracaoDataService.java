package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaMigracaoDataCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaMigracaoDataPreviaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaMigracaoDataViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaMigracaoData;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaConsumidoresRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaMigracaoDataRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.FechamentoIndicadoresDiaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ConsumidorCotaProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ItemMigracaoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CotaUnidadeService.TransferenciaItem;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.federacao.AgendamentoExternoRemarcadoEvent;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp.evento.AgendamentoRemarcadoEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Migracao dos agendamentos de uma cota quando a DATA dela muda.
 *
 * <p>Vale so para cota do tipo DATA e de origem manual. Move, numa unica
 * transacao e com tudo-ou-nada, a cota e os agendamentos que a consomem para a
 * data nova: remarcacao real ({@code data_agendada} muda). Se a data nova nao
 * comporta os agendamentos (cota mensal ou de grupo esgotada, capacidade global da
 * especialidade), a operacao inteira falha com 409 e nada e alterado.
 *
 * <p><b>Quem migra.</b> Cota com profissional: os itens que apontam para ela pela
 * FK. Cota sem profissional: os itens que incidem nela (ver
 * {@link CotaConsumidoresRepository}), em qualquer status — mudanca de status nao
 * estorna cota, entao cancelado e faltou tambem a ocupam. O agendamento vai
 * inteiro, com todos os exames.
 *
 * <p><b>Contador que viaja.</b> O contador da cota migrada vai com ela; as demais
 * cotas que incidem sobre os agendamentos (mensal do grupo, por exemplo) sao
 * acompanhadas por {@link CotaUnidadeService#transferirUtilizacao}. Se ja existe
 * cota com a mesma chave na data nova, as duas sao unificadas (somam-se total e
 * utilizado) e a de origem e excluida.
 *
 * <p>Fora de escopo: cota de agenda (continua somente leitura), cota mensal, teto
 * financeiro (nao e redebitado) e transporte sanitario (nao e ajustado).
 */
@Service
@RequiredArgsConstructor
public class CotaMigracaoDataService {

    private static final String STATUS_AGENDADO = "AGENDADO";

    private final CotaUnidadeRepository cotaRepository;
    private final CotaUnidadeService cotaUnidadeService;
    private final CotaConsumidoresRepository consumidoresRepository;
    private final SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    private final AgendamentoSolicitacaoRepository agendamentoRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final FechamentoIndicadoresDiaRepository fechamentoRepository;
    private final CotaMigracaoDataRepository migracaoRepository;
    private final UserRepository userRepository;
    private final InstanceContext instanceContext;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final EntityManager entityManager;

    /** Escalares da cota, copiados antes de qualquer bulk update (que desanexa as entidades). */
    private record Cota(Long id, Long unidadeId, Long grupoUnidadesId, Long especialidadeId,
            Long grupoEspecialidadesId, Long profissionalId, int total, int utilizada, LocalDate data) {

        static Cota de(CotaUnidade c) {
            return new Cota(c.getId(),
                    c.getUnidade() != null ? c.getUnidade().getId() : null,
                    c.getGrupoUnidades() != null ? c.getGrupoUnidades().getId() : null,
                    c.getEspecialidade() != null ? c.getEspecialidade().getId() : null,
                    c.getGrupoEspecialidades() != null ? c.getGrupoEspecialidades().getId() : null,
                    c.getProfissionalExecutante() != null ? c.getProfissionalExecutante().getId() : null,
                    c.getQuantidadeTotal(), c.getQuantidadeUtilizada(), c.getDataEspecifica());
        }
    }

    /** Cota de destino da unificacao, ja validada (existe, e manual e esta ativa). */
    private record Destino(Long id, int total, int utilizada) {
    }

    // ------------------------------------------------------------------
    // Previa
    // ------------------------------------------------------------------

    /**
     * O que a migracao faria, sem alterar nada. Faz as validacoes que recusam o
     * pedido (agenda, mensal, data, cota de destino) e devolve so contagens. A
     * capacidade global nao e conferida aqui — quem a confere e {@link #migrar}.
     * Cota sem agendamentos devolve {@code agendamentos = 0}, sem lancar.
     */
    @Transactional(readOnly = true)
    public CotaMigracaoDataPreviaDTO previa(Long cotaId, LocalDate dataEspecifica) {
        CotaUnidade cota = cotaRepository.findById(cotaId)
                .orElseThrow(() -> new EntityNotFoundException("Cota nao encontrada."));
        validarPedido(cota, dataEspecifica);
        Cota c = Cota.de(cota);

        List<ConsumidorCotaProjection> consumidores = cotaUnidadeService.consumidoresDe(cota);
        List<Long> agendamentoIds = consumidores.stream()
                .map(ConsumidorCotaProjection::getAgendamentoId).distinct().toList();
        if (agendamentoIds.isEmpty()) {
            return new CotaMigracaoDataPreviaDTO(0, 0, 0, 0, 0, c.utilizada(), false, false, null, null, 0);
        }

        List<ItemMigracaoProjection> itens = consumidoresRepository.itensDosAgendamentos(agendamentoIds);
        Destino destino = localizarDestino(c, dataEspecifica);

        int agendados = contar(itens, STATUS_AGENDADO);
        int realizados = contar(itens, "REALIZADO");
        int canceladosOuFaltosos = contar(itens, "CANCELADO") + contar(itens, "FALTOU");
        UUID localId = municipioLocalId();
        int federadas = (int) itens.stream()
                .filter(i -> externo(i, localId)).map(ItemMigracaoProjection::getSolicitacaoId).distinct().count();

        return new CotaMigracaoDataPreviaDTO(
                agendamentoIds.size(), itens.size(), agendados, realizados, canceladosOuFaltosos,
                c.utilizada(), consumidores.size() != c.utilizada(),
                destino != null, destino != null ? destino.total() : null, destino != null ? destino.utilizada() : null,
                federadas);
    }

    // ------------------------------------------------------------------
    // Migracao
    // ------------------------------------------------------------------

    @Transactional
    public CotaMigracaoDataViewDTO migrar(Long cotaId, CotaMigracaoDataCreateDTO dto, String callerCpf) {
        // 1-3. Leitura SEM lock: so para descobrir quais cotas precisam ser travadas. Os locks
        // sao adquiridos em ordem crescente de id (origem e destino), para que duas migracoes
        // cruzadas (A para B e B para A) nao se bloqueiem uma a outra.
        CotaUnidade lida = cotaRepository.findById(cotaId)
                .orElseThrow(() -> new EntityNotFoundException("Cota nao encontrada."));
        LocalDate novaData = dto != null ? dto.dataEspecifica() : null;
        validarPedido(lida, novaData);
        Cota lidaEscalares = Cota.de(lida);
        Destino destinoLido = localizarDestino(lidaEscalares, novaData);
        // Solta o que foi lido sem lock: as leituras abaixo precisam enxergar o estado travado.
        entityManager.clear();

        List<Long> ordem = new ArrayList<>(List.of(cotaId));
        if (destinoLido != null) {
            ordem.add(destinoLido.id());
        }
        ordem.sort(null);
        CotaUnidade cota = null;
        for (Long id : ordem) {
            CotaUnidade travada = cotaRepository.findByIdParaMigracao(id).orElseThrow(() ->
                    new IllegalStateException("A cota foi alterada por outro usuario. Recarregue e tente de novo."));
            if (id.equals(cotaId)) {
                cota = travada;
            }
        }

        // Revalida sob lock: origem e destino continuam como foram lidos?
        validarPedido(cota, novaData);
        Cota c = Cota.de(cota);
        if (!mesmaChave(lidaEscalares, c)) {
            throw new IllegalStateException("A cota foi alterada por outro usuario. Recarregue e tente de novo.");
        }
        Destino destino = localizarDestino(c, novaData);
        if ((destino == null) != (destinoLido == null)
                || (destino != null && !destino.id().equals(destinoLido.id()))) {
            throw new IllegalStateException("A cota foi alterada por outro usuario. Recarregue e tente de novo.");
        }

        // 4. Escalares, ANTES de qualquer bulk update.
        LocalDate dataAnterior = c.data();

        // 5. Agendamentos que a cota consome e todos os itens deles.
        List<Long> agendamentoIds = cotaUnidadeService.idsAgendamentosQueConsomem(cota);
        if (agendamentoIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Esta cota nao tem agendamentos a migrar; altere a data pela edicao normal.");
        }
        List<ItemMigracaoProjection> itens = consumidoresRepository.itensDosAgendamentos(agendamentoIds);

        // 6. Cota de destino com a mesma chave => unificacao (ja localizada e travada acima).

        // 7. Capacidade global da especialidade na data nova (mesma regra de AgendamentoService).
        validarCapacidadeGlobal(itens, novaData);

        // 8. Move a cota.
        if (destino == null) {
            cota.setDataEspecifica(novaData);
            try {
                cotaRepository.saveAndFlush(cota); // aqui a versao (@Version) e conferida
            } catch (DataIntegrityViolationException e) {
                throw new IllegalStateException("Ja existe cota cadastrada para esse titular, escopo e periodo.");
            }
        } else {
            cotaRepository.absorver(destino.id(), c.total(), c.utilizada());
            solicitacaoEspecialidadeRepository.repontarCota(c.id(), destino.id());
            cotaRepository.deleteById(c.id());
        }

        // 9. Demais cotas que incidem sobre os agendamentos acompanham a mudanca de data.
        Set<Long> viajantes = new HashSet<>();
        viajantes.add(c.id());
        if (destino != null) {
            viajantes.add(destino.id());
        }
        List<TransferenciaItem> transferencias = itens.stream()
                .map(i -> new TransferenciaItem(i.getUnidadeId(), i.getEspecialidadeId(),
                        i.getDataAgendada(), novaData, i.getCotaUnidadeId()))
                .toList();
        cotaUnidadeService.transferirUtilizacao(transferencias, viajantes);

        // 10. A remarcacao propriamente dita.
        agendamentoRepository.remarcarEmLote(agendamentoIds, novaData);

        // 11. Snapshot de indicadores das duas datas: recalculado na proxima consulta.
        fechamentoRepository.excluirPorDatas(Set.of(dataAnterior, novaData));

        // 12. Auditoria.
        Long resultanteId = destino != null ? destino.id() : c.id();
        CotaMigracaoData auditoria = new CotaMigracaoData();
        auditoria.setCotaOrigemId(c.id());
        auditoria.setCotaResultanteId(resultanteId);
        auditoria.setDataDe(dataAnterior);
        auditoria.setDataPara(novaData);
        auditoria.setUnificada(destino != null);
        auditoria.setAgendamentos(agendamentoIds.size());
        auditoria.setItens(itens.size());
        auditoria.setRealizadoPor(callerCpf != null ? userRepository.findByCpf(callerCpf).orElse(null) : null);
        auditoria.setRealizadoEm(clock.instant());
        migracaoRepository.save(auditoria);

        // 13. Eventos (consumidos so apos o commit). Por ultimo: nada depois disto pode falhar.
        publicarEventos(itens, dataAnterior, novaData);

        // 14.
        return new CotaMigracaoDataViewDTO(resultanteId, destino != null, agendamentoIds.size(), itens.size());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Recusas que valem para a previa e para a migracao (passos 2 e 3). */
    private void validarPedido(CotaUnidade cota, LocalDate novaData) {
        if (cota.getOrigem() == OrigemCotaEnum.AGENDA) {
            throw new IllegalStateException("Esta cota foi gerada por uma agenda — edite a agenda de origem.");
        }
        if (cota.getTipoPeriodo() == TipoPeriodoCota.MENSAL) {
            throw new IllegalStateException(
                    "Cota mensal com agendamentos nao pode mudar de mes; a migracao vale apenas para cota por data.");
        }
        if (novaData == null) {
            throw new IllegalArgumentException("Informe a nova data da cota.");
        }
        if (novaData.equals(cota.getDataEspecifica())) {
            throw new IllegalArgumentException("A nova data e igual a data atual da cota.");
        }
    }

    /**
     * Cota de destino com a mesma chave de {@code CotaUnidadeService#exigirCotaInexistente}
     * (tipo DATA, titular, escopo, profissional e a data nova), ou {@code null}.
     * De agenda ou inativa: 409.
     */
    private Destino localizarDestino(Cota c, LocalDate novaData) {
        List<CotaUnidade> doTitular = c.unidadeId() != null
                ? cotaRepository.findByUnidadeId(c.unidadeId())
                : cotaRepository.findByGrupoUnidadesId(c.grupoUnidadesId());

        CotaUnidade destino = doTitular.stream()
                .filter(o -> !o.getId().equals(c.id()))
                .filter(o -> o.getTipoPeriodo() == TipoPeriodoCota.DATA && novaData.equals(o.getDataEspecifica()))
                .filter(o -> Objects.equals(c.especialidadeId(),
                        o.getEspecialidade() != null ? o.getEspecialidade().getId() : null))
                .filter(o -> Objects.equals(c.grupoEspecialidadesId(),
                        o.getGrupoEspecialidades() != null ? o.getGrupoEspecialidades().getId() : null))
                .filter(o -> Objects.equals(c.profissionalId(),
                        o.getProfissionalExecutante() != null ? o.getProfissionalExecutante().getId() : null))
                .findFirst()
                .orElse(null);
        if (destino == null) {
            return null;
        }
        if (destino.getOrigem() == OrigemCotaEnum.AGENDA) {
            throw new IllegalStateException(
                    "Ja existe, na data de destino, uma cota gerada por agenda; ela nao pode ser unificada.");
        }
        if (!destino.isAtivo()) {
            throw new IllegalStateException(
                    "A cota de destino esta inativa; ative-a ou escolha outra data.");
        }
        return new Destino(destino.getId(), destino.getQuantidadeTotal(), destino.getQuantidadeUtilizada());
    }

    /** Mesma regra de {@code AgendamentoService#criarAgendamentoParaMultiplosExames}, contando todos os itens que chegam. */
    private void validarCapacidadeGlobal(List<ItemMigracaoProjection> itens, LocalDate novaData) {
        Map<String, Long> porCodigo = itens.stream()
                .filter(i -> i.getEspecialidadeCodigo() != null && !i.getEspecialidadeCodigo().isBlank())
                .collect(Collectors.groupingBy(i -> i.getEspecialidadeCodigo().trim().toUpperCase(),
                        LinkedHashMap::new, Collectors.counting()));

        for (Map.Entry<String, Long> entrada : porCodigo.entrySet()) {
            String codigo = entrada.getKey();
            var especialidades = especialidadeRepository.findByCodigoIn(List.of(codigo));
            if (especialidades.isEmpty()) {
                continue;
            }
            long capacidade = especialidades.stream().mapToLong(e -> e.getVagas() != null ? e.getVagas() : 0).sum();
            long jaAgendados = solicitacaoEspecialidadeRepository.countAgendadasPorDataECodigos(novaData, List.of(codigo));
            if (capacidade > 0 && (jaAgendados + entrada.getValue() > capacidade)) {
                throw new IllegalStateException("Capacidade excedida para " + codigo + " em " + novaData
                        + ". Vagas=" + capacidade + ", agendados=" + jaAgendados + ", solicitados=" + entrada.getValue());
            }
        }
    }

    private void publicarEventos(List<ItemMigracaoProjection> itens, LocalDate dataAnterior, LocalDate novaData) {
        Map<Long, List<ItemMigracaoProjection>> porAgendamento = itens.stream()
                .collect(Collectors.groupingBy(ItemMigracaoProjection::getAgendamentoId, LinkedHashMap::new, Collectors.toList()));

        // WhatsApp: so agendamento com ao menos um item AGENDADO (o resto nao tem o que avisar).
        for (List<ItemMigracaoProjection> doAgendamento : porAgendamento.values()) {
            if (doAgendamento.stream().noneMatch(i -> STATUS_AGENDADO.equals(i.getStatus()))) {
                continue;
            }
            ItemMigracaoProjection primeiro = doAgendamento.get(0);
            eventPublisher.publishEvent(new AgendamentoRemarcadoEvent(
                    primeiro.getAgendamentoId(), primeiro.getSolicitacaoId(), dataAnterior, novaData,
                    doAgendamento.stream().map(ItemMigracaoProjection::getSeId).toList()));
        }

        // Federacao: so paciente de outro municipio, e so o que o aviso de agendamento
        // externo ja expoe (CPF mascarado e nome).
        UUID localId = municipioLocalId();
        Set<Long> avisadas = new HashSet<>();
        for (ItemMigracaoProjection item : itens) {
            if (!externo(item, localId) || !avisadas.add(item.getSolicitacaoId())) {
                continue;
            }
            String cpf = item.getCpfPaciente();
            String cpfMascarado = cpf != null && cpf.length() >= 4 ? ("***.***.***-" + cpf.substring(cpf.length() - 2)) : "***";
            String destinoNome = (item.getOrigemMunicipioNome() != null
                    ? item.getOrigemMunicipioNome() : item.getOrigemMunicipioId().toString()).toUpperCase();
            eventPublisher.publishEvent(new AgendamentoExternoRemarcadoEvent(
                    item.getSolicitacaoId(), cpfMascarado, item.getNomePaciente(), destinoNome, dataAnterior, novaData));
        }
    }

    /** Titular, escopo, profissional e data iguais (total e utilizado podem mudar sem invalidar a leitura). */
    private static boolean mesmaChave(Cota a, Cota b) {
        return Objects.equals(a.unidadeId(), b.unidadeId())
                && Objects.equals(a.grupoUnidadesId(), b.grupoUnidadesId())
                && Objects.equals(a.especialidadeId(), b.especialidadeId())
                && Objects.equals(a.grupoEspecialidadesId(), b.grupoEspecialidadesId())
                && Objects.equals(a.profissionalId(), b.profissionalId())
                && Objects.equals(a.data(), b.data());
    }

    private static boolean externo(ItemMigracaoProjection item, UUID localId) {
        return localId != null && item.getOrigemMunicipioId() != null && !item.getOrigemMunicipioId().equals(localId);
    }

    /** Municipio desta instancia; {@code null} se nao resolvido (entao nao ha aviso federado). */
    private UUID municipioLocalId() {
        try {
            return instanceContext.getMunicipioLocal().getId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int contar(List<ItemMigracaoProjection> itens, String status) {
        return (int) itens.stream().filter(i -> status.equals(i.getStatus())).count();
    }
}
