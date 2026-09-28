package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaDistribuicaoInputDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaRemanejarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda.AgendaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Agenda;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaDistribuicao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaOcorrencia;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cbo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusOcorrenciaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoOfertaAgendaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendaOcorrenciaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CboRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalVinculoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Abertura e gestao de agenda (V89/V90) — ver docs/especificacoes/Agenda e
 * Oferta.md.
 *
 * <p>A agenda NAO e o motor de saldo. Ela materializa uma
 * {@link io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade}
 * por par (ocorrencia x unidade solicitante) atraves de
 * {@link CotaUnidadeService#criarParaAgenda}, reaproveitando consumo, estorno
 * e optimistic locking ja em producao (decisao 3.2 da especificacao).
 *
 * <p><b>Fora de escopo nesta entrega</b> (adiado por decisao explicita, ver
 * ADR 0001): {@code reservaRegulador} (R4, vagas sem unidade titular) e
 * adicionar/remover unidade solicitante depois que a agenda ja foi
 * materializada — para isso, cria-se uma agenda nova.
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    private static final Map<DayOfWeek, String> SIGLA_DIA = Map.of(
            DayOfWeek.MONDAY, "SEG",
            DayOfWeek.TUESDAY, "TER",
            DayOfWeek.WEDNESDAY, "QUA",
            DayOfWeek.THURSDAY, "QUI",
            DayOfWeek.FRIDAY, "SEX",
            DayOfWeek.SATURDAY, "SAB",
            DayOfWeek.SUNDAY, "DOM");

    private final AgendaRepository agendaRepository;
    private final AgendaOcorrenciaRepository agendaOcorrenciaRepository;
    private final UnidadeRepository unidadeRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ProfissionalVinculoRepository profissionalVinculoRepository;
    private final CboRepository cboRepository;
    private final LocalAgendamentoRepository localAgendamentoRepository;
    private final GrupoRelatorioRepository grupoRelatorioRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final UserRepository userRepository;
    private final CotaUnidadeRepository cotaUnidadeRepository;
    private final CotaUnidadeService cotaUnidadeService;

    // ------------------------------------------------------------------
    // Criacao
    // ------------------------------------------------------------------

    @Transactional
    public AgendaViewDTO criar(AgendaCreateDTO dto, String cpfCriador) {
        Unidade executante = unidadeRepository.findById(dto.estabelecimentoExecutanteId())
                .orElseThrow(() -> new EntityNotFoundException("Estabelecimento executante nao encontrado."));
        if (!executante.getTipo().executa()) {
            throw new IllegalArgumentException(
                    "A unidade '" + executante.getNome() + "' nao esta cadastrada como executante.");
        }

        Profissional profissional = profissionalRepository.findById(dto.profissionalId())
                .orElseThrow(() -> new EntityNotFoundException("Profissional nao encontrado."));
        if (!profissionalVinculoRepository.existsByProfissionalIdAndUnidadeIdAndAtivoTrue(
                profissional.getId(), executante.getId())) {
            throw new IllegalArgumentException(
                    "O profissional '" + profissional.getNome() + "' nao possui vinculo ativo com '"
                            + executante.getNome() + "'.");
        }

        Cbo cbo = null;
        if (dto.cboId() != null) {
            cbo = cboRepository.findById(dto.cboId())
                    .orElseThrow(() -> new EntityNotFoundException("CBO nao encontrado."));
        }

        LocalAgendamento local = null;
        if (dto.localAgendamentoId() != null) {
            local = localAgendamentoRepository.findById(dto.localAgendamentoId())
                    .orElseThrow(() -> new EntityNotFoundException("Local de agendamento nao encontrado."));
        }

        if (dto.tipoOferta() == null) {
            throw new IllegalArgumentException("Informe o tipo de oferta: INDIVIDUAL ou GRUPO.");
        }
        if (dto.vigenciaInicio() == null || dto.vigenciaFim() == null) {
            throw new IllegalArgumentException("Informe a vigencia inicial e final.");
        }
        if (dto.vigenciaFim().isBefore(dto.vigenciaInicio())) {
            throw new IllegalArgumentException("A vigencia final nao pode ser anterior a vigencia inicial.");
        }
        if (dto.horaInicial() == null || dto.horaFinal() == null || !dto.horaFinal().isAfter(dto.horaInicial())) {
            throw new IllegalArgumentException("O horario final deve ser posterior ao horario inicial.");
        }

        Set<DayOfWeek> diasSemana = validarDiasSemana(dto.diasSemana());

        if (dto.distribuicoes() == null || dto.distribuicoes().isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma unidade solicitante com vagas.");
        }

        Agenda agenda = new Agenda();
        agenda.setEstabelecimentoExecutante(executante);
        agenda.setProfissional(profissional);
        agenda.setCbo(cbo);
        agenda.setLocalAgendamento(local);
        agenda.setLocalDescricao(dto.localDescricao());
        agenda.setTipoOferta(dto.tipoOferta());
        agenda.setVigenciaInicio(dto.vigenciaInicio());
        agenda.setVigenciaFim(dto.vigenciaFim());
        agenda.setDiasSemana(String.join(",", dto.diasSemana()));
        agenda.setHoraInicial(dto.horaInicial());
        agenda.setHoraFinal(dto.horaFinal());
        agenda.setObservacao(dto.observacao());
        agenda.setAtivo(true);
        agenda.setCriadoPor(userRepository.findByCpf(cpfCriador)
                .orElseThrow(() -> new EntityNotFoundException("Usuario autenticado nao encontrado.")));

        GrupoRelatorio grupo = resolverEspecialidades(dto, agenda);

        for (AgendaDistribuicaoInputDTO d : validarDistribuicoes(dto.distribuicoes())) {
            Unidade solicitante = unidadeRepository.findById(d.unidadeSolicitanteId())
                    .orElseThrow(() -> new EntityNotFoundException("Unidade solicitante nao encontrada."));
            if (!solicitante.getTipo().solicita()) {
                throw new IllegalArgumentException(
                        "A unidade '" + solicitante.getNome() + "' nao esta cadastrada como solicitante.");
            }
            AgendaDistribuicao distribuicao = new AgendaDistribuicao();
            distribuicao.setAgenda(agenda);
            distribuicao.setUnidadeSolicitante(solicitante);
            distribuicao.setVagasPorOcorrencia(d.vagasPorOcorrencia());
            agenda.getDistribuicoes().add(distribuicao);
        }

        List<LocalDate> datas = materializarDatas(dto.vigenciaInicio(), dto.vigenciaFim(), diasSemana);
        if (datas.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nenhuma data cai nos dias da semana marcados dentro da vigencia informada.");
        }

        // R9: valida TODAS as datas antes de gravar qualquer coisa — uma agenda
        // parcialmente materializada e pior que a operacao recusada de uma vez.
        for (LocalDate data : datas) {
            if (agendaOcorrenciaRepository.existeSobreposicao(
                    profissional.getId(), data, dto.horaInicial(), dto.horaFinal(), null)) {
                throw new IllegalStateException(
                        "O profissional '" + profissional.getNome() + "' ja tem outra agenda ativa com horario "
                                + "sobreposto em " + data + ".");
            }
        }

        for (LocalDate data : datas) {
            AgendaOcorrencia ocorrencia = new AgendaOcorrencia();
            ocorrencia.setAgenda(agenda);
            ocorrencia.setData(data);
            ocorrencia.setHoraInicial(dto.horaInicial());
            ocorrencia.setHoraFinal(dto.horaFinal());
            ocorrencia.setStatus(StatusOcorrenciaEnum.ABERTA);
            agenda.getOcorrencias().add(ocorrencia);
        }

        agenda = agendaRepository.save(agenda);

        Especialidade especialidadeUnica = agenda.getTipoOferta() == TipoOfertaAgendaEnum.INDIVIDUAL
                ? agenda.getEspecialidades().get(0).getEspecialidade()
                : null;

        for (AgendaOcorrencia ocorrencia : agenda.getOcorrencias()) {
            for (AgendaDistribuicao distribuicao : agenda.getDistribuicoes()) {
                cotaUnidadeService.criarParaAgenda(
                        ocorrencia,
                        distribuicao.getUnidadeSolicitante(),
                        especialidadeUnica,
                        grupo,
                        distribuicao.getVagasPorOcorrencia());
            }
        }

        return AgendaViewDTO.from(agenda);
    }

    private GrupoRelatorio resolverEspecialidades(AgendaCreateDTO dto, Agenda agenda) {
        if (dto.especialidadeIds() == null || dto.especialidadeIds().isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma especialidade ofertada.");
        }

        if (dto.tipoOferta() == TipoOfertaAgendaEnum.INDIVIDUAL) {
            if (dto.especialidadeIds().size() != 1) {
                throw new IllegalArgumentException("Oferta individual aceita exatamente uma especialidade.");
            }
            if (dto.grupoEspecialidadesId() != null) {
                throw new IllegalArgumentException("Oferta individual nao usa grupo de especialidades.");
            }
            Especialidade especialidade = especialidadeRepository.findById(dto.especialidadeIds().get(0))
                    .orElseThrow(() -> new EntityNotFoundException("Especialidade nao encontrada."));
            adicionarEspecialidade(agenda, especialidade);
            return null;
        }

        // GRUPO
        if (dto.grupoEspecialidadesId() == null) {
            throw new IllegalArgumentException("Oferta em grupo exige o grupo de especialidades.");
        }
        GrupoRelatorio grupo = grupoRelatorioRepository.findById(dto.grupoEspecialidadesId())
                .orElseThrow(() -> new EntityNotFoundException("Grupo de especialidades nao encontrado."));
        agenda.setGrupoEspecialidades(grupo);

        Set<Long> especialidadeIds = new LinkedHashSet<>(dto.especialidadeIds());
        for (Long id : especialidadeIds) {
            Especialidade especialidade = especialidadeRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Especialidade nao encontrada."));
            boolean pertenceAoGrupo = especialidade.getGrupoRelatorio() != null
                    && especialidade.getGrupoRelatorio().getId().equals(grupo.getId());
            if (!pertenceAoGrupo) {
                throw new IllegalArgumentException(
                        "A especialidade '" + especialidade.getNome() + "' nao pertence ao grupo '"
                                + grupo.getNome() + "'.");
            }
            adicionarEspecialidade(agenda, especialidade);
        }
        return grupo;
    }

    private void adicionarEspecialidade(Agenda agenda, Especialidade especialidade) {
        AgendaEspecialidade ae = new AgendaEspecialidade();
        ae.setAgenda(agenda);
        ae.setEspecialidade(especialidade);
        agenda.getEspecialidades().add(ae);
    }

    private Set<DayOfWeek> validarDiasSemana(List<String> diasSemana) {
        if (diasSemana == null || diasSemana.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um dia da semana.");
        }
        Set<DayOfWeek> resultado = new LinkedHashSet<>();
        for (String sigla : diasSemana) {
            DayOfWeek dia = SIGLA_DIA.entrySet().stream()
                    .filter(e -> e.getValue().equalsIgnoreCase(sigla))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Dia da semana invalido: '" + sigla + "'. Use SEG,TER,QUA,QUI,SEX,SAB,DOM."));
            resultado.add(dia);
        }
        return resultado;
    }

    private List<AgendaDistribuicaoInputDTO> validarDistribuicoes(List<AgendaDistribuicaoInputDTO> distribuicoes) {
        Set<Long> unidadesJaVistas = new LinkedHashSet<>();
        for (AgendaDistribuicaoInputDTO d : distribuicoes) {
            if (d.unidadeSolicitanteId() == null) {
                throw new IllegalArgumentException("Toda distribuicao precisa de uma unidade solicitante.");
            }
            if (d.vagasPorOcorrencia() == null || d.vagasPorOcorrencia() <= 0) {
                throw new IllegalArgumentException(
                        "A quantidade de vagas por ocorrencia deve ser maior que zero.");
            }
            if (!unidadesJaVistas.add(d.unidadeSolicitanteId())) {
                throw new IllegalArgumentException("A mesma unidade solicitante foi informada mais de uma vez.");
            }
        }
        return distribuicoes;
    }

    private List<LocalDate> materializarDatas(LocalDate inicio, LocalDate fim, Set<DayOfWeek> diasSemana) {
        List<LocalDate> datas = new ArrayList<>();
        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            if (diasSemana.contains(data.getDayOfWeek())) {
                datas.add(data);
            }
        }
        return datas;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AgendaViewDTO> listar() {
        return agendaRepository.findAllByOrderByIdDesc().stream().map(AgendaViewDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public AgendaViewDTO buscarPorId(Long id) {
        return AgendaViewDTO.from(buscarEntidade(id));
    }

    private Agenda buscarEntidade(Long id) {
        return agendaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Agenda nao encontrada."));
    }

    // ------------------------------------------------------------------
    // Edicao (R2, R3)
    // ------------------------------------------------------------------

    @Transactional
    public AgendaViewDTO atualizar(Long id, AgendaUpdateDTO dto) {
        Agenda agenda = buscarEntidade(id);

        agenda.setLocalDescricao(dto.localDescricao());
        agenda.setObservacao(dto.observacao());
        agenda.setAtivo(dto.ativo());

        if (dto.distribuicoes() != null) {
            for (AgendaDistribuicaoInputDTO novaDistribuicao : dto.distribuicoes()) {
                if (novaDistribuicao.vagasPorOcorrencia() == null || novaDistribuicao.vagasPorOcorrencia() <= 0) {
                    throw new IllegalArgumentException(
                            "A quantidade de vagas por ocorrencia deve ser maior que zero.");
                }
                AgendaDistribuicao distribuicao = agenda.getDistribuicoes().stream()
                        .filter(d -> d.getUnidadeSolicitante().getId().equals(novaDistribuicao.unidadeSolicitanteId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Esta agenda nao distribui vagas para a unidade informada. "
                                        + "Adicionar/remover unidade exige criar uma nova agenda."));

                if (novaDistribuicao.vagasPorOcorrencia().equals(distribuicao.getVagasPorOcorrencia())) {
                    continue;
                }
                distribuicao.setVagasPorOcorrencia(novaDistribuicao.vagasPorOcorrencia());

                // R2: so as ocorrencias ainda ABERTAS (nao canceladas) refletem a
                // mudanca — a que ja aconteceu ou foi cancelada fica como estava.
                for (AgendaOcorrencia ocorrencia : agenda.getOcorrencias()) {
                    if (ocorrencia.getStatus() != StatusOcorrenciaEnum.ABERTA) {
                        continue;
                    }
                    CotaUnidade cota = cotaUnidadeRepository
                            .findByAgendaOcorrenciaIdAndUnidadeId(
                                    ocorrencia.getId(), distribuicao.getUnidadeSolicitante().getId())
                            .orElse(null);
                    if (cota == null) {
                        continue;
                    }
                    if (novaDistribuicao.vagasPorOcorrencia() < cota.getQuantidadeUtilizada()) {
                        throw new IllegalArgumentException(
                                "A nova quantidade e menor que o ja utilizado em " + ocorrencia.getData()
                                        + " para '" + distribuicao.getUnidadeSolicitante().getNome()
                                        + "'. Cancele agendamentos antes de reduzir.");
                    }
                    cota.setQuantidadeTotal(novaDistribuicao.vagasPorOcorrencia());
                }
            }
        }

        return AgendaViewDTO.from(agendaRepository.save(agenda));
    }

    /** Desativa a agenda sem tocar nos demais campos (R3) — nao cancela ocorrencias ja materializadas. */
    @Transactional
    public AgendaViewDTO desativar(Long id) {
        Agenda agenda = buscarEntidade(id);
        agenda.setAtivo(false);
        return AgendaViewDTO.from(agendaRepository.save(agenda));
    }

    // ------------------------------------------------------------------
    // Cancelamento de ocorrencia (R10)
    // ------------------------------------------------------------------

    @Transactional
    public AgendaViewDTO cancelarOcorrencia(Long agendaId, Long ocorrenciaId) {
        Agenda agenda = buscarEntidade(agendaId);
        AgendaOcorrencia ocorrencia = agenda.getOcorrencias().stream()
                .filter(o -> o.getId().equals(ocorrenciaId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Ocorrencia nao encontrada nesta agenda."));

        if (ocorrencia.getStatus() == StatusOcorrenciaEnum.CANCELADA) {
            throw new IllegalStateException("Esta ocorrencia ja esta cancelada.");
        }

        ocorrencia.setStatus(StatusOcorrenciaEnum.CANCELADA);

        // Fecha o saldo nao consumido e desativa — a vaga ja utilizada continua
        // registrada (nao se apaga atendimento real).
        for (CotaUnidade cota : cotaUnidadeRepository.findByAgendaOcorrenciaId(ocorrencia.getId())) {
            cota.setQuantidadeTotal(cota.getQuantidadeUtilizada());
            cota.setAtivo(false);
        }

        return AgendaViewDTO.from(agendaRepository.save(agenda));
    }

    // ------------------------------------------------------------------
    // Remanejamento manual (R1)
    // ------------------------------------------------------------------

    @Transactional
    public void remanejar(Long agendaId, AgendaRemanejarDTO dto) {
        Agenda agenda = buscarEntidade(agendaId);
        AgendaOcorrencia ocorrencia = agenda.getOcorrencias().stream()
                .filter(o -> o.getId().equals(dto.ocorrenciaId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Ocorrencia nao encontrada nesta agenda."));

        if (ocorrencia.getStatus() != StatusOcorrenciaEnum.ABERTA) {
            throw new IllegalStateException("Nao e possivel remanejar vagas de uma ocorrencia cancelada.");
        }
        if (dto.quantidade() == null || dto.quantidade() <= 0) {
            throw new IllegalArgumentException("Informe uma quantidade maior que zero para remanejar.");
        }
        if (dto.unidadeOrigemId() == null || dto.unidadeOrigemId().equals(dto.unidadeDestinoId())) {
            throw new IllegalArgumentException("Informe unidades de origem e destino diferentes.");
        }

        CotaUnidade origem = cotaUnidadeRepository
                .findByAgendaOcorrenciaIdAndUnidadeId(ocorrencia.getId(), dto.unidadeOrigemId())
                .orElseThrow(() -> new EntityNotFoundException("Cota de origem nao encontrada nesta ocorrencia."));
        CotaUnidade destino = cotaUnidadeRepository
                .findByAgendaOcorrenciaIdAndUnidadeId(ocorrencia.getId(), dto.unidadeDestinoId())
                .orElseThrow(() -> new EntityNotFoundException("Cota de destino nao encontrada nesta ocorrencia."));

        int saldoNaoUsado = origem.getQuantidadeTotal() - origem.getQuantidadeUtilizada();
        if (dto.quantidade() > saldoNaoUsado) {
            throw new IllegalStateException(
                    "A unidade de origem so tem " + saldoNaoUsado + " vaga(s) nao utilizada(s) nesta ocorrencia.");
        }

        origem.setQuantidadeTotal(origem.getQuantidadeTotal() - dto.quantidade());
        destino.setQuantidadeTotal(destino.getQuantidadeTotal() + dto.quantidade());

        cotaUnidadeRepository.save(origem);
        cotaUnidadeRepository.save(destino);
    }
}
