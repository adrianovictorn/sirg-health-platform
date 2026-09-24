package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSolicitacaoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.dashboard.DashboardResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.paciente.PacienteResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.EspecialidadeAdicionarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.AgendamentoSolicitacaoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoAgendamentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CID;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.EspecialidadesEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CidRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.UnidadeAcessoService.UnidadeContexto;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoSpecification;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoListFiltersDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoPublicViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PacientesGelProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PendenciasPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.StatusCountProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UrgenciaEmergenciaPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UnidadePendentesProjection;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SolicitacaoService {

    private final SolicitacaoRepository solicitacaoRepository;
    private final AgendamentoSolicitacaoRepository agendamentoRepository;
    private final SolicitacaoEspecialidadeRepository especialidadeRepository;
    private final CidRepository cidRepository;
    private final EspecialidadeRepository especialidadeRepo;
    private final ProfissionalRepository profissionalRepository;
    private final UserRepository userRepository;
    private final UnidadeRepository unidadeRepository;
    private final UnidadeAcessoService unidadeAcessoService;

    // Delega ao UnidadeAcessoService, que concentra a regra de segregação por unidade
    // (inclusive o perfil ADMIN_UNIDADE, restrito à própria unidade de lotação).
    private UnidadeContexto getContextoUnidade(String cpf) {
        return unidadeAcessoService.contextoDe(cpf);
    }

    @Transactional
    public SolicitacaoViewDTO createSolicitacao(SolicitacaoCreateDTO dto, String callerCpf) {
        Solicitacao solicitacao = new Solicitacao();

        // Vincula a Unidade. Operador restrito a uma unidade sempre grava na própria
        // unidade, mesmo que envie outro unidadeId no corpo da requisição — caso
        // contrário bastaria trocar o campo para cadastrar em nome de outra unidade.
        Long unidadeAlvo = unidadeAcessoService.resolverUnidadeAlvo(callerCpf, dto.unidadeId());
        if (unidadeAlvo != null) {
            unidadeRepository.findById(unidadeAlvo).ifPresent(solicitacao::setUnidade);
        } else if (callerCpf != null) {
            userRepository.findByCpf(callerCpf).ifPresent(u -> {
                if (u.getUnidade() != null) solicitacao.setUnidade(u.getUnidade());
            });
        }

        solicitacao.setNomePaciente(dto.nomePaciente());
        solicitacao.setCpfPaciente(dto.cpfPaciente());
        solicitacao.setCns(dto.cns());
        solicitacao.setTelefone(dto.telefone());
        solicitacao.setNomePai(dto.nomePai());
        solicitacao.setNomeMae(dto.nomeMae());
        solicitacao.setEndereco(dto.endereco());
        solicitacao.setDataNascimento(dto.datanascimento());
        solicitacao.setObservacoes(dto.observacoes());
        solicitacao.setDataMalote(dto.dataMalote());

        var especialidades = dto.especialidades().stream()
            .map(e -> {
                var se = new SolicitacaoEspecialidade();
                se.setSolicitacao(solicitacao);
                se.setAgendamentoSolicitacao(null);
                if (e.especialidadeId() != null) {
                    var esp = especialidadeRepo.findById(e.especialidadeId())
                            .orElseThrow(() -> new IllegalArgumentException("Especialidade não encontrada: id=" + e.especialidadeId()));
                    se.setEspecialidadeSolicitada(esp);
                    se.setEspecialidadeCodigoLegacy(esp.getCodigo());
                } else {
                    var codigo = e.especialidadeSolicitada() != null ? e.especialidadeSolicitada().trim() : null;
                    if (codigo != null && !codigo.isBlank()) {
                        var esp = especialidadeRepo.findByCodigo(codigo)
                                .orElseThrow(() -> new IllegalArgumentException("Especialidade não cadastrada: " + codigo));
                        se.setEspecialidadeSolicitada(esp);
                        se.setEspecialidadeCodigoLegacy(esp.getCodigo());
                    }
                }
                if (e.profissionalId() != null) {
                    profissionalRepository.findById(e.profissionalId())
                            .ifPresent(se::setProfissionalSolicitante);
                }
                se.setDataColeta(e.dataColeta());
                se.setStatus(e.status());
                se.setPrioridade(e.prioridade());
                return se;
            })
            .collect(Collectors.toList());

        solicitacao.setEspecialidades(especialidades);

        if (dto.cids() != null && !dto.cids().isEmpty()) {
            List<CID> cidsDoBanco = cidRepository.findAllById(dto.cids());
            solicitacao.setCids(cidsDoBanco);
        }

        var saved = solicitacaoRepository.save(solicitacao);
        return SolicitacaoViewDTO.fromSolicitacao(saved);
    }

    @Transactional
    public SolicitacaoViewDTO updateSolicitacao(Long id, SolicitacaoUpdateDTO dto, String callerCpf) {
        Solicitacao solicitacao = solicitacaoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada."));

        // Impede editar solicitação de outra unidade via chamada direta à API.
        exigirAcessoASolicitacao(solicitacao, callerCpf);

        solicitacao.setNomePaciente(dto.nomePaciente());
        solicitacao.setCns(dto.cns());
        solicitacao.setTelefone(dto.telefone());
        solicitacao.setNomePai(dto.nomePai());
        solicitacao.setNomeMae(dto.nomeMae());
        solicitacao.setEndereco(dto.endereco());
        solicitacao.setDataNascimento(dto.datanascimento());
        solicitacao.setObservacoes(dto.observacoes());
        solicitacao.setDataMalote(dto.dataMalote());

        Long unidadeAlvo = unidadeAcessoService.resolverUnidadeAlvo(callerCpf, dto.unidadeId());
        if (unidadeAlvo != null) {
            unidadeRepository.findById(unidadeAlvo).ifPresent(solicitacao::setUnidade);
        }

        if (dto.cids() != null) {
            List<CID> cidsDoBanco = cidRepository.findAllById(dto.cids());
            solicitacao.setCids(cidsDoBanco);
        }

        var updated = solicitacaoRepository.save(solicitacao);
        return SolicitacaoViewDTO.fromSolicitacao(updated);
    }

    @Transactional(readOnly = true)
    public SolicitacaoViewDTO getSolicitacaoById(Long id, String callerCpf) {
        Solicitacao s = solicitacaoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada."));
        exigirAcessoASolicitacao(s, callerCpf);
        return SolicitacaoViewDTO.fromSolicitacao(s);
    }

    /**
     * Bloqueia o acesso a uma solicitação que pertence a outra unidade.
     * O filtro por Specification já protege as listagens; este método protege os
     * acessos por id, onde o registro é buscado diretamente pela chave.
     */
    private void exigirAcessoASolicitacao(Solicitacao solicitacao, String callerCpf) {
        UnidadeContexto ctx = getContextoUnidade(callerCpf);
        if (ctx.isGlobal()) {
            return;
        }

        Long unidadeDaSolicitacao = solicitacao.getUnidade() != null ? solicitacao.getUnidade().getId() : null;

        // Registro legado sem unidade vinculada: NAO bloqueia.
        //
        // As migracoes de backfill (V73/V76/V77) so conseguem vincular a unidade
        // quando `usf_origem` esta preenchido e casa com alguma unidade cadastrada.
        // O que sobra e uma solicitacao "orfa", que nao pertence a unidade nenhuma —
        // portanto nao e "dado de outra unidade". Bloquea-la seria uma regressao:
        // antes deste controle qualquer usuario abria esses registros, e nenhuma
        // migracao consegue atribui-los automaticamente.
        //
        // Isto nao abre brecha: um usuario restrito nunca cria solicitacao orfa,
        // porque `resolverUnidadeAlvo` forca a unidade de lotacao dele no cadastro.
        if (unidadeDaSolicitacao == null) {
            return;
        }

        if (!ctx.permite(unidadeDaSolicitacao)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Acesso negado: esta solicitação pertence a outra unidade.");
        }
    }

    @Transactional(readOnly = true)
    public Page<SolicitacaoViewDTO> listSolicitacoes(SolicitacaoListFiltersDTO filters, Pageable pageable, String cpf) {
        Specification<Solicitacao> spec = SolicitacaoSpecification.aplicarFiltros(filters);
        UnidadeContexto ctx = getContextoUnidade(cpf);
        if (!ctx.isGlobal()) {
            spec = spec.and(SolicitacaoSpecification.filtrarPorUnidade(ctx.id()));
        }
        return solicitacaoRepository.findAll(spec, pageable)
                .map(SolicitacaoViewDTO::fromSolicitacao);
    }

    @Transactional(readOnly = true)
    public Page<PacienteResumoDTO> buscarPacientes(String termo, int page, int size, String cpf) {
        int pagina = Math.max(page, 0);
        int limite = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(pagina, limite);

        String filtroBruto = termo != null ? termo.trim() : "";
        boolean possuiFiltro = !filtroBruto.isBlank();
        String filtroNormalizado = possuiFiltro ? filtroBruto.toLowerCase(Locale.ROOT) : "";
        String filtroNumerico = possuiFiltro ? limparCpf(filtroBruto) : "";

        Comparator<Solicitacao> ordenacaoPorNome = Comparator
            .comparing((Solicitacao s) -> normalizarTexto(s.getNomePaciente()))
            .thenComparing(s -> s.getId(), Comparator.nullsLast(Long::compareTo));

        Map<String, Solicitacao> solicitacoesPorCpf = new LinkedHashMap<>();

        UnidadeContexto ctx = getContextoUnidade(cpf);
        solicitacaoRepository.findAll().stream()
            .filter(s -> {
                if (ctx.isGlobal()) return true;
                return s.getUnidade() != null && ctx.id().equals(s.getUnidade().getId());
            })
            .sorted(ordenacaoPorNome)
            .filter(s -> !possuiFiltro || correspondeAoFiltro(s, filtroNormalizado, filtroNumerico))
            .forEach(s -> {
                String chaveCpf = limparCpf(s.getCpfPaciente());
                if (chaveCpf.isEmpty()) {
                    chaveCpf = "NOCPF-" + s.getId();
                }
                solicitacoesPorCpf.putIfAbsent(chaveCpf, s);
            });

        List<PacienteResumoDTO> resultados = solicitacoesPorCpf.values().stream()
            .map(this::toPacienteResumo)
            .toList();

        int inicio = Math.min(pagina * limite, resultados.size());
        int fim = Math.min(inicio + limite, resultados.size());

        List<PacienteResumoDTO> conteudo = resultados.subList(inicio, fim);
        return new PageImpl<>(conteudo, pageable, resultados.size());
    }

    @Transactional(readOnly = true)
    public Page<PacienteResumoDTO> buscarPacientesDoPaciente(String cpf, int page, int size) {
        int pagina = Math.max(page, 0);
        int limite = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(pagina, limite);

        if (cpf == null) {
            return Page.empty(pageable);
        }

        String cpfNumerico = limparCpf(cpf);
        if (cpfNumerico.isBlank()) {
            return Page.empty(pageable);
        }

        List<Solicitacao> solicitacoes = solicitacaoRepository.findByCpfPacienteSemPonto(cpfNumerico);
        if (solicitacoes.isEmpty()) {
            return Page.empty(pageable);
        }

        Solicitacao selecionada = solicitacoes.stream()
            .min(Comparator.comparing(Solicitacao::getId))
            .orElse(solicitacoes.get(0));

        PacienteResumoDTO dto = toPacienteResumo(selecionada);

        if (pagina > 0) {
            return new PageImpl<>(List.of(), pageable, 1);
        }

        return new PageImpl<>(List.of(dto), pageable, 1);
    }

    private boolean correspondeAoFiltro(Solicitacao solicitacao, String filtroNormalizado, String filtroNumerico) {
        if (solicitacao == null) return false;
        if (filtroNormalizado == null || filtroNormalizado.isBlank()) return true;

        String nomeNormalizado = normalizarTexto(solicitacao.getNomePaciente());
        if (!nomeNormalizado.isBlank() && nomeNormalizado.contains(filtroNormalizado)) return true;

        String cpfOriginal = solicitacao.getCpfPaciente();
        if (cpfOriginal != null) {
            if (normalizarTexto(cpfOriginal).contains(filtroNormalizado)) return true;
            String cpfApenasNumeros = limparCpf(cpfOriginal);
            if (!filtroNumerico.isBlank() && cpfApenasNumeros.contains(filtroNumerico)) return true;
        }

        if (solicitacao.getUnidade() != null && solicitacao.getUnidade().getNome() != null) {
            if (normalizarTexto(solicitacao.getUnidade().getNome()).contains(filtroNormalizado)) return true;
        }

        return false;
    }

    private String normalizarTexto(String valor) {
        return valor == null ? "" : valor.toLowerCase(Locale.ROOT);
    }

    private String limparCpf(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private PacienteResumoDTO toPacienteResumo(Solicitacao solicitacao) {
        Unidade unidade = solicitacao.getUnidade();
        return new PacienteResumoDTO(
            solicitacao.getId(),
            solicitacao.getNomePaciente(),
            solicitacao.getCpfPaciente(),
            unidade != null ? unidade.getId() : null,
            unidade != null ? unidade.getNome() : null
        );
    }

    @Transactional(readOnly = true)
    public Page<SolicitacaoViewDTO> todasSolicitacoes(int page, int size, String cpf) {
        Pageable pagina = PageRequest.of(page, size, Sort.by("nomePaciente").ascending());
        UnidadeContexto ctx = getContextoUnidade(cpf);
        if (!ctx.isGlobal()) {
            Specification<Solicitacao> spec = SolicitacaoSpecification.filtrarPorUnidade(ctx.id());
            return solicitacaoRepository.findAll(spec, pagina).map(SolicitacaoViewDTO::fromSolicitacao);
        }
        return solicitacaoRepository.findAll(pagina).map(SolicitacaoViewDTO::fromSolicitacao);
    }

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoDashboard(String cpf) {
        UnidadeContexto ctx = getContextoUnidade(cpf);

        long totalSolicitacoes;
        List<StatusCountProjection> porStatus;
        long totalUrgentes;

        if (!ctx.isGlobal()) {
            totalSolicitacoes = solicitacaoRepository.contarSolicitacoesParaUnidadeSemUsf(ctx.id());
            porStatus = solicitacaoRepository.contarPorStatusParaUnidadeSemUsf(ctx.id());
            totalUrgentes = solicitacaoRepository.contarPorStatusPrioridadesParaUnidadeSemUsf(
                StatusDaMarcacao.AGUARDANDO,
                Arrays.asList(PrioridadeDaMarcacaoEnum.URGENTE, PrioridadeDaMarcacaoEnum.EMERGENCIA),
                ctx.id());
        } else {
            totalSolicitacoes = solicitacaoRepository.count();
            porStatus = solicitacaoRepository.contarPorStatus();
            totalUrgentes = solicitacaoRepository.contarPorStatusPrioridades(
                StatusDaMarcacao.AGUARDANDO,
                Arrays.asList(PrioridadeDaMarcacaoEnum.URGENTE, PrioridadeDaMarcacaoEnum.EMERGENCIA));
        }

        long totalPendentes = extrairTotalStatus(porStatus, StatusDaMarcacao.AGUARDANDO);
        long totalAgendadas = extrairTotalStatus(porStatus, StatusDaMarcacao.AGENDADO);
        long totalConcluidas = extrairTotalStatus(porStatus, StatusDaMarcacao.REALIZADO);
        long totalGel = extrairTotalStatus(porStatus, StatusDaMarcacao.GEL);

        Map<Long, Long> pendentesPorUnidade = new HashMap<>();
        for (UnidadePendentesProjection proj : solicitacaoRepository.contarPorUnidadeEStatus(StatusDaMarcacao.AGUARDANDO)) {
            pendentesPorUnidade.put(proj.getUnidadeId(), proj.getTotal());
        }

        return new DashboardResumoDTO(
            totalSolicitacoes,
            totalPendentes,
            totalAgendadas,
            totalConcluidas,
            totalUrgentes,
            totalGel,
            pendentesPorUnidade
        );
    }

    private long extrairTotalStatus(List<StatusCountProjection> lista, StatusDaMarcacao status) {
        return lista.stream()
            .filter(item -> item.getStatus() == status)
            .mapToLong(StatusCountProjection::getTotal)
            .findFirst()
            .orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoSolicitacaoSimpleViewDTO> listAllAgendamentos() {
        return agendamentoRepository.findAll().stream()
            .map(AgendamentoSolicitacaoSimpleViewDTO::fromAgendamentoSolicitacao)
            .collect(Collectors.toList());
    }

    @Transactional
    public AgendamentoSolicitacaoSimpleViewDTO createAgendamento(Long solicitacaoId, AgendamentoSolicitacaoCreateDTO dto) {
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
            .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada."));

        AgendamentoSolicitacao ag = new AgendamentoSolicitacao();
        ag.setSolicitacao(solicitacao);
        ag.setLocalAgendado(dto.localAgendado());
        ag.setObservacoes(dto.observacoes());
        ag.setDataAgendada(dto.dataAgendada());
        ag = agendamentoRepository.save(ag);

        SolicitacaoEspecialidade se = solicitacao.getEspecialidades().stream()
            .filter(e -> {
                if (dto.especialidadeId() != null) {
                    return e.getEspecialidadeSolicitada() != null && dto.especialidadeId().equals(e.getEspecialidadeSolicitada().getId());
                }
                String codigo = dto.especialidadeSolicitada() != null ? dto.especialidadeSolicitada().name() : null;
                String atual = e.getEspecialidadeSolicitada() != null ? e.getEspecialidadeSolicitada().getCodigo() : e.getEspecialidadeCodigoLegacy();
                return codigo != null && codigo.equalsIgnoreCase(atual);
            })
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Especialidade não encontrada para agendamento."));

        se.setStatus(StatusDaMarcacao.AGENDADO);
        se.setAgendamentoSolicitacao(ag);

        solicitacaoRepository.save(solicitacao);
        return AgendamentoSolicitacaoSimpleViewDTO.fromAgendamentoSolicitacao(ag);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoSolicitacaoSimpleViewDTO> listarAgendamentosPorSolicitacaoId(Long solicitacaoId) {
        List<AgendamentoSolicitacao> agendamentos = agendamentoRepository.findBySolicitacaoId(solicitacaoId);
        return agendamentos.stream()
                .map(AgendamentoSolicitacaoSimpleViewDTO::fromAgendamentoSolicitacao)
                .collect(Collectors.toList());
    }

    @Transactional
    public SolicitacaoViewDTO adicionarEspecialidadeASolicitacao(Long solicitacaoId, EspecialidadeAdicionarDTO dto, String callerCpf) {
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
            .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada."));

        exigirAcessoASolicitacao(solicitacao, callerCpf);

        SolicitacaoEspecialidade novaEspecialidade = new SolicitacaoEspecialidade();
        novaEspecialidade.setSolicitacao(solicitacao);
        novaEspecialidade.setAgendamentoSolicitacao(null);

        if (dto.especialidadeId() != null) {
            var esp = especialidadeRepo.findById(dto.especialidadeId())
                    .orElseThrow(() -> new IllegalArgumentException("Especialidade não encontrada: id=" + dto.especialidadeId()));
            novaEspecialidade.setEspecialidadeSolicitada(esp);
            novaEspecialidade.setEspecialidadeCodigoLegacy(esp.getCodigo());
        } else {
            String codigo = dto.especialidadeSolicitada() != null ? dto.especialidadeSolicitada().trim() : null;
            if (codigo == null || codigo.isBlank()) {
                throw new IllegalArgumentException("Informe a especialidade (especialidadeId ou especialidadeSolicitada).");
            }
            var esp = especialidadeRepo.findByCodigo(codigo)
                    .orElseThrow(() -> new IllegalArgumentException("Especialidade não cadastrada: " + codigo));
            novaEspecialidade.setEspecialidadeSolicitada(esp);
            novaEspecialidade.setEspecialidadeCodigoLegacy(esp.getCodigo());
        }
        if (dto.profissionalId() != null) {
            profissionalRepository.findById(dto.profissionalId())
                    .ifPresent(novaEspecialidade::setProfissionalSolicitante);
        }
        novaEspecialidade.setDataColeta(dto.dataColeta());
        novaEspecialidade.setStatus(dto.status());
        novaEspecialidade.setPrioridade(dto.prioridade());

        solicitacao.getEspecialidades().add(novaEspecialidade);

        solicitacao = solicitacaoRepository.save(solicitacao);
        return SolicitacaoViewDTO.fromSolicitacao(solicitacao);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> listarTodasEspecialidadesComDetalhes() {
        return Arrays.stream(EspecialidadesEnum.values())
                .map(e -> {
                    Map<String, String> detalhes = new HashMap<>();
                    detalhes.put("name", e.name());
                    detalhes.put("descricao", e.getDescricao());
                    detalhes.put("categoria", e.getCategoria().getDisplayValue());
                    return detalhes;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void removerEspecialidade(Long id, String callerCpf) {
        SolicitacaoEspecialidade se = especialidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Especialidade de solicitação com ID " + id + " não encontrada."));
        if (se.getSolicitacao() != null) {
            exigirAcessoASolicitacao(se.getSolicitacao(), callerCpf);
        }
        especialidadeRepository.deleteById(id);
    }

    @Transactional
    public List<SolicitacaoPublicViewDTO> buscarPacientePorCpf(String cpf) {
        return solicitacaoRepository.findByCpfPacienteSemPonto(cpf).stream()
                .map(SolicitacaoPublicViewDTO::fromSolicitacao)
                .collect(Collectors.toList());
    }

    @Transactional
    public SolicitacaoAgendamentoViewDTO buscarSolicitacaoPorId(Long id) {
        return SolicitacaoAgendamentoViewDTO.fromEntity(
            solicitacaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada !"))
        );
    }

    @Transactional
    public Page<SolicitacaoSimpleViewDTO> buscarPorNomeOuCpf(int page, int size, String termo) {
        Pageable pagina = PageRequest.of(page, size, Sort.by("nomePaciente").ascending());

        if (termo == null || termo.isEmpty()) {
            return solicitacaoRepository.findAll(pagina).map(SolicitacaoSimpleViewDTO::fromSolicitacao);
        }

        return solicitacaoRepository.findByNomePacienteContainingIgnoreCaseOrCpfPacienteContainingIgnoreCase(pagina, termo, termo)
                .map(SolicitacaoSimpleViewDTO::fromSolicitacao);
    }

    public Page<PendenciasPacienteProjection> buscarPendentesPorUnidade(int page, int size, Long unidadeId, String termo) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nomePaciente").ascending());
        return solicitacaoRepository.listarPacientesPendentes(unidadeId, "AGUARDANDO", termo, pageable);
    }

    public Page<PacienteProjection> buscarPorStatusAguardando(int page, int size, String termo) {
        Pageable pagina = PageRequest.of(page, size);
        return solicitacaoRepository.buscarPorStatus(pagina, termo, StatusDaMarcacao.AGENDADO.name());
    }

    public Page<PacienteProjection> buscarPorStatusConcluido(int page, int size, String termo) {
        Pageable pagina = PageRequest.of(page, size);
        return solicitacaoRepository.buscarConcluidosAgrupados(termo, pagina);
    }

    public Page<UrgenciaEmergenciaPacienteProjection> buscarPorUrgenteeEmergencia(int page, int size, String termo) {
        Pageable pagina = PageRequest.of(page, size);
        return solicitacaoRepository.listarPacientesUrgenteseEmergencias(pagina, termo);
    }

    public long totalPacientesCadastrados() {
        return solicitacaoRepository.count();
    }

    public Page<PacientesGelProjection> listarPacientesGel(int page, int size, String termo) {
        int paginaAtual = Math.max(page, 0);
        int tamanhoPagina = Math.min(Math.max(size, 1), 50);
        Pageable pagina = PageRequest.of(paginaAtual, tamanhoPagina, Sort.by("nomePaciente").ascending());
        return especialidadeRepository.listarPacientesGel(termo, pagina);
    }
}
