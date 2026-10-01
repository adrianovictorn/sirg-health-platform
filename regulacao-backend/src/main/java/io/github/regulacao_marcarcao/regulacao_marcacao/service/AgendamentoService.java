package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSendDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSolicitacaoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoViewDto;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.AgendamentoSolicitacaoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.LocalDeAgendamentoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private final SolicitacaoRepository solicitacaoRepository;
    private final AgendamentoSolicitacaoRepository agendamentoRepository;
    private final LocalAgendamentoRepository localAgendamentoRepository;
    private final SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final UserRepository userRepository;
    private final ProfissionalRepository profissionalRepository;
    private final CotaUnidadeService cotaUnidadeService;
    private final UnidadeAcessoService unidadeAcessoService;
    private final io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext instanceContext;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    /**
     * Retorna todas as solicitações com ao menos uma especialidade com status AGUARDANDO.
     */
    @Transactional(readOnly = true)
    public List<AgendamentoViewDto> listarSolicitacoesPendentes() {
        return solicitacaoRepository.findAll().stream()
            .filter(s -> s.getEspecialidades().stream()
                .anyMatch(e -> e.getStatus() == StatusDaMarcacao.AGUARDANDO || e.getStatus() == StatusDaMarcacao.RETORNO || e.getStatus() == StatusDaMarcacao.RETORNO_POLICLINICA))
            .map(AgendamentoViewDto::fromSolicitacao)
            .collect(Collectors.toList());
    }

    /**
     * RF01: para ADMIN_UNIDADE, restringe a busca a propria unidade de
     * lotacao (orfas sem unidade continuam visiveis). ADMIN e os demais
     * perfis nao sao afetados — recebem a lista completa, como antes.
     */
    @Transactional(readOnly = true)
    public Page<SolicitacaoResumoDTO> buscarPendentesParaAutoComplete(String termo, Pageable pageable, String callerCpf){
        var statusPendentes = List.of(StatusDaMarcacao.RETORNO, StatusDaMarcacao.RETORNO_POLICLINICA, StatusDaMarcacao.AGUARDANDO, StatusDaMarcacao.GEL);

        Long unidadeId = null;
        if (unidadeAcessoService.isAdminUnidade(callerCpf)) {
            try {
                unidadeId = unidadeAcessoService.contextoDe(callerCpf).id();
            } catch (AccessDeniedException e) {
                return Page.empty(pageable);
            }
        }

        return solicitacaoRepository.buscarPendentesPorTermo(statusPendentes, termo, unidadeId, pageable);
    }

    /**
     * Cria um agendamento para a solicitação e especialidade informadas, atualiza status e retorna DTO simples.
     */
    @Transactional
    public AgendamentoSolicitacaoSimpleViewDTO create(Long solicitacaoId, AgendamentoSolicitacaoCreateDTO dto) {
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
            .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada."));

        // Encontra especialidade pendente
        SolicitacaoEspecialidade especialidade = solicitacao.getEspecialidades().stream()
            .filter(e -> {
                if (dto.especialidadeId() != null) {
                    return e.getEspecialidadeSolicitada() != null && dto.especialidadeId().equals(e.getEspecialidadeSolicitada().getId())
                        && (e.getStatus() == StatusDaMarcacao.AGUARDANDO || e.getStatus() == StatusDaMarcacao.RETORNO || e.getStatus() == StatusDaMarcacao.RETORNO_POLICLINICA);
                }
                String codigo = dto.especialidadeSolicitada() != null ? dto.especialidadeSolicitada().name() : null;
                String atual = e.getEspecialidadeSolicitada() != null ? e.getEspecialidadeSolicitada().getCodigo() : e.getEspecialidadeCodigoLegacy();
                return (codigo != null && codigo.equalsIgnoreCase(atual))
                    && (e.getStatus() == StatusDaMarcacao.AGUARDANDO || e.getStatus() == StatusDaMarcacao.RETORNO || e.getStatus() == StatusDaMarcacao.RETORNO_POLICLINICA);
            })
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Especialidade não disponível para agendamento."));

        // Salva o agendamento
        AgendamentoSolicitacao ag = new AgendamentoSolicitacao();
        ag.setSolicitacao(solicitacao);
        ag.setLocalAgendado(dto.localAgendado());
        ag.setDataAgendada(dto.dataAgendada());
        ag.setObservacoes(dto.observacoes());
        ag.setTurno(dto.turno());
        ag = agendamentoRepository.save(ag);

        // Atualiza status da especialidade
        especialidade.setStatus(StatusDaMarcacao.AGENDADO);
        especialidade.setAgendamentoSolicitacao(ag);
        solicitacaoRepository.save(solicitacao);

        notificarAgendamentoExternoSeAplicavel(solicitacao, ag);
        return AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(ag);
    }

    /**
     * Lista todos os agendamentos realizados.
     */
    @Transactional(readOnly = true)
    public List<AgendamentoSolicitacaoSimpleViewDTO> listAll(String callerCpf) {
        var ctx = unidadeAcessoService.contextoDe(callerCpf);
        return agendamentoRepository.findAll().stream()
            .filter(ag -> {
                if (ctx.isGlobal()) return true;
                if (ag.getSolicitacao() == null) return true;
                // Agendamento de solicitacao legada sem unidade: continua visivel.
                if (ag.getSolicitacao().getUnidade() == null) return true;
                return ctx.permite(ag.getSolicitacao().getUnidade().getId());
            })
            .map(AgendamentoSolicitacaoSimpleViewDTO::fromAgendamentoSolicitacao)
            .collect(Collectors.toList());
    }

    /**
     * Busca agendamento por ID.
     */
    @Transactional(readOnly = true)
    public AgendamentoSolicitacaoSimpleViewDTO getById(Long id) {
        AgendamentoSolicitacao ag = agendamentoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Agendamento não encontrado."));
        return AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(ag);
    }

    private boolean isAdminGlobal(String callerCpf) {
        return unidadeAcessoService.isAcessoGlobal(callerCpf);
    }

    @Transactional
    public AgendamentoSolicitacaoSimpleViewDTO criarAgendamentoParaMultiplosExames(Long solicitacaoId, MultiAgendamentoCreateDTO dto, String callerCpf) {
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada com o ID: " + solicitacaoId));

        // Valida capacidade de cada especialidade selecionada para a data de agendamento
        var solicitadosPorCodigo = dto.examesSelecionados().stream()
                .map(String::trim)
                .filter(c -> !c.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));

        for (var entry : solicitadosPorCodigo.entrySet()) {
            String codigo = entry.getKey();
            long quantidadeSelecionada = entry.getValue();

            var especialidadesDB = especialidadeRepository.findByCodigoIn(List.of(codigo));
            if (especialidadesDB.isEmpty()) {
                continue;
            }

            long capacidade = especialidadesDB.stream().mapToLong(e -> e.getVagas() != null ? e.getVagas() : 0).sum();
            long jaAgendados = solicitacaoEspecialidadeRepository.countAgendadasPorDataECodigos(dto.dataAgendada(), List.of(codigo));

            if (capacidade > 0 && (jaAgendados + quantidadeSelecionada > capacidade)) {
                throw new IllegalStateException("Capacidade excedida para " + codigo + " em " + dto.dataAgendada() + ". Vagas=" + capacidade + ", agendados=" + jaAgendados + ", solicitados=" + quantidadeSelecionada);
            }
        }

        // 1. Cria a entidade de agendamento.
        AgendamentoSolicitacao novoAgendamento = new AgendamentoSolicitacao();
        novoAgendamento.setSolicitacao(solicitacao);
        novoAgendamento.setLocalAgendado(dto.localAgendado());
        novoAgendamento.setDataAgendada(dto.dataAgendada());
        novoAgendamento.setObservacoes(dto.observacoes());
        novoAgendamento.setTurno(dto.turno());
        
        // Resolve local e enum antes de salvar
        if (dto.localAgendamentoId() != null && dto.localAgendado() != null) {
            throw new IllegalArgumentException("Informe apenas 'localAgendamentoId' ou 'localAgendado'.");
        }

        if (dto.localAgendamentoId() != null) {
            var local = resolveLocal(dto.localAgendamentoId());
            novoAgendamento.setLocalAgendamento(local);
            novoAgendamento.setLocalAgendado(resolveEnumValue(local));
        } else if (dto.localAgendado() != null) {
            var local = localAgendamentoRepository.findByEnumValue(dto.localAgendado().name()).orElse(null);
            novoAgendamento.setLocalAgendamento(local);
            novoAgendamento.setLocalAgendado(dto.localAgendado());
        } else {
            novoAgendamento.setLocalAgendamento(null);
            novoAgendamento.setLocalAgendado(null);
        }

        // Autoria (V93): metadado, nunca impede o agendamento se o usuario nao for encontrado.
        novoAgendamento.setCriadoPor(userRepository.findByCpf(callerCpf).orElse(null));

        // Salva para obter um ID
        AgendamentoSolicitacao agendamentoSalvo = agendamentoRepository.save(novoAgendamento);

        // 2. Itera sobre os exames selecionados.
        boolean adminGlobal = isAdminGlobal(callerCpf);
        boolean adminUnidade = unidadeAcessoService.isAdminUnidade(callerCpf);
        Long unidadeId = solicitacao.getUnidade() != null ? solicitacao.getUnidade().getId() : null;

        for (String nomeExame : dto.examesSelecionados()) {
            SolicitacaoEspecialidade especialidadeParaAgendar = solicitacao.getEspecialidades().stream()
                    .filter(e -> {
                        // Compara o código (enum name) ou legado com a String recebida, ignorando maiúsculas/minúsculas.
                        String atual = e.getEspecialidadeSolicitada() != null ? e.getEspecialidadeSolicitada().getCodigo() : e.getEspecialidadeCodigoLegacy();
                        return atual != null && atual.equalsIgnoreCase(nomeExame)
                                && (
                                    e.getStatus() == StatusDaMarcacao.AGUARDANDO
                                    || e.getStatus() == StatusDaMarcacao.RETORNO
                                    || e.getStatus() == StatusDaMarcacao.RETORNO_POLICLINICA
                                    || e.getStatus() == StatusDaMarcacao.GEL);
                    })
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Exame pendente '" + nomeExame + "' não encontrado na solicitação."));

            // Valida cota da unidade (admin global não está sujeito a cotas)
            if (!adminGlobal && unidadeId != null) {
                Long especialidadeId = especialidadeParaAgendar.getEspecialidadeSolicitada() != null
                        ? especialidadeParaAgendar.getEspecialidadeSolicitada().getId()
                        : null;

                // ADMIN_UNIDADE so agenda especialidades com cota liberada para a unidade —
                // sem cota nenhuma cadastrada, o agendamento fica bloqueado (diferente do
                // ADMIN global e do GESTOR, que continuam sem restricao nenhuma).
                if (adminUnidade && !cotaUnidadeService.existeCotaAtivaAplicavel(unidadeId, especialidadeId, dto.dataAgendada())) {
                    throw new IllegalStateException(
                            "Nao ha cota liberada para esta especialidade nesta unidade — agendamento bloqueado.");
                }

                // Rastreabilidade (V93): resolve qual cota/profissional atendeu, quando
                // aplicavel. V100: resolvido ANTES de consumir — se houver ambiguidade
                // (2+ cotas com profissional, sem escolha), o erro e detectado sem
                // tocar em nenhum saldo, em vez de consumir-e-desfazer.
                Long cotaEscolhidaId = dto.cotasSelecionadas() != null
                        ? dto.cotasSelecionadas().get(nomeExame)
                        : null;
                CotaUnidade cotaAnterior = especialidadeParaAgendar.getCotaUnidade();
                java.time.LocalTime horaAnterior = especialidadeParaAgendar.getHoraAgendada();

                CotaUnidade cotaUsada = cotaUnidadeService.resolverCotaParaAgendamento(
                        unidadeId, especialidadeId, dto.dataAgendada(), cotaEscolhidaId);

                // V100: pool isolado por profissional — com cota resolvida, consome
                // SOMENTE ela; sem cota resolvida (nenhum profissional aplicavel),
                // consome só as cotas gerais (sem profissional).
                cotaUnidadeService.incrementarUtilizacao(unidadeId, especialidadeId, dto.dataAgendada(),
                        cotaUsada != null ? cotaUsada.getId() : null);

                especialidadeParaAgendar.setCotaUnidade(cotaUsada);
                especialidadeParaAgendar.setHoraAgendada(
                        resolverHoraAgendada(cotaUsada, cotaAnterior, horaAnterior, dto, nomeExame));

                // Profissional executante (V100): override do operador para este
                // agendamento — nunca grava nada em cotaUsada (o "espelho" da cota
                // permanece intocado).
                Long profissionalSelecionadoId = dto.profissionaisSelecionados() != null
                        ? dto.profissionaisSelecionados().get(nomeExame)
                        : null;
                if (profissionalSelecionadoId != null) {
                    Profissional profissional = profissionalRepository.findById(profissionalSelecionadoId)
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "Profissional nao encontrado: " + profissionalSelecionadoId));
                    especialidadeParaAgendar.setProfissionalExecutante(profissional);
                }
            }

            // 3. Atualiza o status e associa o agendamento.
            especialidadeParaAgendar.setStatus(StatusDaMarcacao.AGENDADO);
            especialidadeParaAgendar.setAgendamentoSolicitacao(agendamentoSalvo);
        }

        // 4. Salva a solicitação para persistir as alterações nas especialidades
        solicitacaoRepository.save(solicitacao);

        notificarAgendamentoExternoSeAplicavel(solicitacao, agendamentoSalvo);

        List<SolicitacaoEspecialidade> especialidadesAgendadas =
                solicitacaoEspecialidadeRepository.findByAgendamentoSolicitacaoId(agendamentoSalvo.getId());
        return AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(agendamentoSalvo, especialidadesAgendadas);
    }

    /**
     * Horario (V97) desta especialidade/paciente dentro do agendamento.
     *
     * <p>Cota dinamica: por padrao calcula o slot a partir de {@code
     * quantidadeUtilizada} apos o consumo desta vaga (ja incrementada por
     * {@code incrementarUtilizacao} na mesma transacao) — a posicao desta
     * vaga dentro da cota. A partir da V100, o operador pode SOBRESCREVER
     * esse calculo informando a hora manualmente ({@code
     * dto.horariosSelecionados}); a hora informada e validada contra o
     * periodo da cota antes de aceitar.
     *
     * <p>Remanejamento (excluir + recriar) na MESMA cota dinamica preserva o
     * horario original em vez de recalcular, para nao mudar o horario ja
     * entregue ao paciente no comprovante, quando o operador nao informa
     * override; ao trocar de cota, recalcula.
     *
     * <p>Cota nao dinamica: usa a hora informada manualmente pelo operador,
     * validada contra o periodo liberado pela cota ({@code
     * horaInicial}/{@code horaFinal}, quando definidos); sem informar,
     * preserva a hora anterior (remanejamento, sem revalidar — nao trava
     * retroativamente um horario ja gravado antes desta validacao existir) ou
     * fica nula (a tela usa o periodo da cota so como referencia).
     */
    private java.time.LocalTime resolverHoraAgendada(CotaUnidade cotaUsada, CotaUnidade cotaAnterior,
            java.time.LocalTime horaAnterior, MultiAgendamentoCreateDTO dto, String nomeExame) {
        java.time.LocalTime informada = dto.horariosSelecionados() != null
                ? dto.horariosSelecionados().get(nomeExame)
                : null;

        if (cotaUsada != null && cotaUsada.isHorarioDinamico()) {
            if (informada != null) {
                validarHoraDentroDoPeriodoDaCota(cotaUsada, informada, nomeExame);
                return informada;
            }
            if (horaAnterior != null && cotaAnterior != null && cotaAnterior.getId().equals(cotaUsada.getId())) {
                return horaAnterior;
            }
            return cotaUnidadeService.calcularHorarioSlot(cotaUsada, cotaUsada.getQuantidadeUtilizada());
        }

        if (informada != null) {
            validarHoraDentroDoPeriodoDaCota(cotaUsada, informada, nomeExame);
            return informada;
        }
        return horaAnterior;
    }

    /**
     * Recusa hora manual fora do periodo liberado pela cota (V99+) — sem isso
     * o operador podia marcar um horario fora do que a cota permite, mesmo
     * com o "espelho" mostrado na tela. Cota geral (sem horaInicial/horaFinal)
     * continua sem restricao, como antes.
     */
    private void validarHoraDentroDoPeriodoDaCota(CotaUnidade cotaUsada, java.time.LocalTime informada, String nomeExame) {
        if (cotaUsada == null || cotaUsada.getHoraInicial() == null || cotaUsada.getHoraFinal() == null) {
            return;
        }
        if (informada.isBefore(cotaUsada.getHoraInicial()) || informada.isAfter(cotaUsada.getHoraFinal())) {
            throw new IllegalArgumentException(
                    "Hora informada (" + informada + ") fora do periodo liberado pela cota ("
                            + cotaUsada.getHoraInicial() + "–" + cotaUsada.getHoraFinal() + ") para " + nomeExame + ".");
        }
    }

    private void preencherLocal(AgendamentoSolicitacao ag, Long localAgendamentoId, io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.LocalDeAgendamentoEnum localEnum) {
        if (localAgendamentoId == null && localEnum == null) {
            throw new EntityNotFoundException("Local de agendamento não informado.");
        }
        ag.setLocalAgendamento(resolveLocal(localAgendamentoId));
        ag.setLocalAgendado(localEnum);
    }

    private LocalAgendamento resolveLocal(Long localAgendamentoId) {
        if (localAgendamentoId == null) {
            return null;
        }
        return localAgendamentoRepository.findById(localAgendamentoId)
            .orElseThrow(() -> new EntityNotFoundException("Local de agendamento não encontrado."));
    }

    private LocalDeAgendamentoEnum resolveEnumValue(LocalAgendamento local) {
        if (local == null || local.getEnumValue() == null || local.getEnumValue().isBlank()) {
            return null;
        }
        try {
            return LocalDeAgendamentoEnum.valueOf(local.getEnumValue());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Transactional
    public List<AgendamentoSendDTO> buscarPorDataparaEnvio(int dias){

        LocalDate hoje = LocalDate.now();
        LocalDate dataAlvo = hoje.plusDays(dias);
        var agendamentos = agendamentoRepository.findByDataAgendada(dataAlvo);

        return agendamentos.stream().map(AgendamentoSendDTO::fromEntity).toList();
    }

    @Transactional
    public void deleteAgendamento(Long id, String callerCpf) {
        AgendamentoSolicitacao agendamento = agendamentoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Agendamento não encontrado."));

        exigirAcessoAoAgendamento(agendamento, callerCpf);

        // Devolve à cota as vagas que este agendamento havia consumido, ANTES de
        // desvincular as especialidades (depois disso não há mais como saber quais
        // especialidades pertenciam ao agendamento).
        estornarCotasDoAgendamento(agendamento);

        // Salva as alterações nas especialidades
        solicitacaoEspecialidadeRepository.desvincularAgendamento(id);

        // Agora, deleta o agendamento
        agendamentoRepository.delete(agendamento);
    }

    /**
     * Impede cancelar/excluir agendamento pertencente a outra unidade.
     *
     * Agendamento de solicitacao legada sem unidade vinculada nao e bloqueado —
     * mesma regra de {@code SolicitacaoService.exigirAcessoASolicitacao}: registro
     * orfao nao pertence a outra unidade, e bloquea-lo regrediria o comportamento
     * anterior sem ganho de seguranca.
     */
    private void exigirAcessoAoAgendamento(AgendamentoSolicitacao agendamento, String callerCpf) {
        var ctx = unidadeAcessoService.contextoDe(callerCpf);
        if (ctx.isGlobal()) {
            return;
        }
        Long unidadeId = agendamento.getSolicitacao() != null && agendamento.getSolicitacao().getUnidade() != null
                ? agendamento.getSolicitacao().getUnidade().getId()
                : null;
        if (unidadeId == null) {
            return;
        }
        if (!ctx.permite(unidadeId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Acesso negado: este agendamento pertence a outra unidade.");
        }
    }

    /**
     * Estorna a cota consumida por um agendamento que está sendo cancelado,
     * excluído ou remanejado.
     *
     * O estorno espelha exatamente o consumo feito em
     * {@link #criarAgendamentoParaMultiplosExames}: uma vaga por especialidade
     * agendada, na unidade da solicitação e na data em que o agendamento estava
     * marcado. Sem isto o saldo nunca volta e a unidade fica bloqueada por vagas
     * que não correspondem a atendimento nenhum.
     */
    private void estornarCotasDoAgendamento(AgendamentoSolicitacao agendamento) {
        Solicitacao solicitacao = agendamento.getSolicitacao();
        if (solicitacao == null || solicitacao.getUnidade() == null || agendamento.getDataAgendada() == null) {
            return;
        }
        Long unidadeId = solicitacao.getUnidade().getId();

        List<SolicitacaoEspecialidade> agendadas =
                solicitacaoEspecialidadeRepository.findByAgendamentoSolicitacaoId(agendamento.getId());

        for (SolicitacaoEspecialidade se : agendadas) {
            // Extrai especialidadeId e cotaUsadaId NESTA iteracao, antes de chamar
            // estornarUtilizacao: @Modifying(clearAutomatically = true) desanexa o
            // contexto de persistencia, e os proxies LAZY das demais entradas da
            // lista quebrariam se lidos depois (mesma armadilha ja documentada em
            // incrementarUtilizacao).
            Long especialidadeId = se.getEspecialidadeSolicitada() != null
                    ? se.getEspecialidadeSolicitada().getId()
                    : null;
            // V100: estorna exatamente a cota usada no consumo original (gravada em
            // SolicitacaoEspecialidade.cotaUnidade), preservando o isolamento por
            // profissional — nunca recalcula do zero, que devolveria vaga errada.
            Long cotaUsadaId = se.getCotaUnidade() != null ? se.getCotaUnidade().getId() : null;
            cotaUnidadeService.estornarUtilizacao(unidadeId, especialidadeId, agendamento.getDataAgendada(), cotaUsadaId);
        }
    }

    private void notificarAgendamentoExternoSeAplicavel(Solicitacao solicitacao, AgendamentoSolicitacao ag) {
        try {
            var local = instanceContext.getMunicipioLocal();
            // Só notifica se a solicitação tiver origem externa (campos preenchidos) e a origem for diferente do local
            if (solicitacao.getOrigemMunicipioId() != null && !solicitacao.getOrigemMunicipioId().equals(local.getId())) {
                String cpf = solicitacao.getCpfPaciente();
                String cpfMask = cpf != null && cpf.length() >= 4 ?
                        ("***.***.***-" + cpf.substring(cpf.length()-2)) : "***";

                var msg = new io.github.regulacao_marcarcao.regulacao_marcacao.dto.notificacao.AgendamentoExternoMensagemDTO(
                        solicitacao.getId(),
                        cpfMask,
                        solicitacao.getNomePaciente(),
                        local.getNome(),
                        ag.getDataAgendada()
                );

                String destinoNome = (solicitacao.getOrigemMunicipioNome() != null ? solicitacao.getOrigemMunicipioNome() : solicitacao.getOrigemMunicipioId().toString()).toUpperCase();
                String routingKey = String.format("agendamento-externo.%s", destinoNome);
                rabbitTemplate.convertAndSend(io.github.regulacao_marcarcao.regulacao_marcacao.config.RabbitMQConfig.EXCHANGE_NAME, routingKey, msg);
            } else {
                // Fallback: origem desconhecida → broadcast para assinantes de broadcast
                String routingKey = "agendamento-externo.BROADCAST";
                var msg = new io.github.regulacao_marcarcao.regulacao_marcacao.dto.notificacao.AgendamentoExternoMensagemDTO(
                        solicitacao.getId(),
                        "***",
                        solicitacao.getNomePaciente(),
                        local.getNome(),
                        ag.getDataAgendada()
                );
                rabbitTemplate.convertAndSend(io.github.regulacao_marcarcao.regulacao_marcacao.config.RabbitMQConfig.EXCHANGE_NAME, routingKey, msg);
            }
        } catch (Exception ignore) {}
    }

    public long totalAgendamentos(){
        return agendamentoRepository.count();
    }
}
