package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
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
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.EspecialidadesEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.UsfEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CidRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoSpecification;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoListFiltersDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoPublicViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PendenciasPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.StatusCountProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UrgenciaEmergenciaPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UsfPendentesProjection;
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


    @Transactional
    public SolicitacaoViewDTO createSolicitacao(SolicitacaoCreateDTO dto) {
        Solicitacao solicitacao = new Solicitacao();
        solicitacao.setUsfOrigem(dto.usfOrigem());
        
        solicitacao.setNomePaciente(dto.nomePaciente());
        solicitacao.setCpfPaciente(dto.cpfPaciente());
        solicitacao.setCns(dto.cns());
        solicitacao.setTelefone(dto.telefone());
        solicitacao.setDataNascimento(dto.datanascimento());
        solicitacao.setObservacoes(dto.observacoes());
        solicitacao.setDataMalote(dto.dataMalote());

        var especialidades = dto.especialidades().stream()
            .map(e -> {
                var se = new SolicitacaoEspecialidade();
                se.setSolicitacao(solicitacao);
                se.setAgendamentoSolicitacao(null);
                // Preferência por ID explícito; fallback para código (enum legacy)
                if (e.especialidadeId() != null) {
                    var esp = especialidadeRepo.findById(e.especialidadeId())
                            .orElseThrow(() -> new IllegalArgumentException("Especialidade não encontrada: id=" + e.especialidadeId()));
                    se.setEspecialidadeSolicitada(esp);
                    se.setEspecialidadeCodigoLegacy(esp.getCodigo());
                } else {
                    var codigo = e.especialidadeSolicitada() != null ? e.especialidadeSolicitada().name() : null;
                    if (codigo != null) {
                        var esp = especialidadeRepo.findByCodigo(codigo)
                                .orElseThrow(() -> new IllegalArgumentException("Especialidade não cadastrada: " + codigo));
                        se.setEspecialidadeSolicitada(esp);
                        se.setEspecialidadeCodigoLegacy(codigo);
                    }
                }
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
    public SolicitacaoViewDTO updateSolicitacao(Long id, SolicitacaoUpdateDTO dto) {
        Solicitacao solicitacao = solicitacaoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada."));


            

        solicitacao.setNomePaciente(dto.nomePaciente());
        solicitacao.setCns(dto.cns());
        solicitacao.setTelefone(dto.telefone());
        solicitacao.setDataNascimento(dto.datanascimento());
        solicitacao.setObservacoes(dto.observacoes());
        solicitacao.setDataMalote(dto.dataMalote());
        solicitacao.setUsfOrigem(dto.usfOrigem());

        if (dto.cids() != null) {
        List<CID> cidsDoBanco = cidRepository.findAllById(dto.cids());
        solicitacao.setCids(cidsDoBanco); 
    }

        var updated = solicitacaoRepository.save(solicitacao);
        return SolicitacaoViewDTO.fromSolicitacao(updated);
    }

    @Transactional(readOnly = true)
    public SolicitacaoViewDTO getSolicitacaoById(Long id) {
        Solicitacao s = solicitacaoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada."));
        return SolicitacaoViewDTO.fromSolicitacao(s);
    }

    @Transactional(readOnly = true)
    public Page<SolicitacaoViewDTO> listSolicitacoes(SolicitacaoListFiltersDTO filters, Pageable pageable) {
        Specification<Solicitacao> spec = SolicitacaoSpecification.aplicarFiltros(filters);
        return solicitacaoRepository.findAll(spec, pageable)
                .map(SolicitacaoViewDTO::fromSolicitacao);
    }

    @Transactional(readOnly = true)
    public Page<PacienteResumoDTO> buscarPacientes(String termo, int page, int size) {
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

        solicitacaoRepository.findAll().stream()
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

        PacienteResumoDTO dto = new PacienteResumoDTO(
            selecionada.getId(),
            selecionada.getNomePaciente(),
            selecionada.getCpfPaciente(),
            selecionada.getUsfOrigem()
        );

        if (pagina > 0) {
            return new PageImpl<>(List.of(), pageable, 1);
        }

        return new PageImpl<>(List.of(dto), pageable, 1);
    }

    private boolean correspondeAoFiltro(Solicitacao solicitacao, String filtroNormalizado, String filtroNumerico) {
        if (solicitacao == null) {
            return false;
        }

        if (filtroNormalizado == null || filtroNormalizado.isBlank()) {
            return true;
        }

        String nomeNormalizado = normalizarTexto(solicitacao.getNomePaciente());
        if (!nomeNormalizado.isBlank() && nomeNormalizado.contains(filtroNormalizado)) {
            return true;
        }

        String cpfOriginal = solicitacao.getCpfPaciente();
        if (cpfOriginal != null) {
            String cpfNormalizado = normalizarTexto(cpfOriginal);
            if (cpfNormalizado.contains(filtroNormalizado)) {
                return true;
            }

            String cpfApenasNumeros = limparCpf(cpfOriginal);
            if (!filtroNumerico.isBlank() && cpfApenasNumeros.contains(filtroNumerico)) {
                return true;
            }
        }

        if (solicitacao.getUsfOrigem() != null) {
            String usfNormalizado = normalizarTexto(solicitacao.getUsfOrigem().name());
            if (usfNormalizado.contains(filtroNormalizado)) {
                return true;
            }
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
        return new PacienteResumoDTO(
            solicitacao.getId(),
            solicitacao.getNomePaciente(),
            solicitacao.getCpfPaciente(),
            solicitacao.getUsfOrigem()
        );
    }

    @Transactional(readOnly = true)
    public Page<SolicitacaoViewDTO> todasSolicitacoes(int page, int size){
        Pageable pagina = PageRequest.of(page, size, Sort.by("nomePaciente").ascending());
        return solicitacaoRepository.findAll(pagina).map(SolicitacaoViewDTO::fromSolicitacao);
    }

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoDashboard() {
        long totalSolicitacoes = solicitacaoRepository.count();

        List<StatusCountProjection> porStatus = solicitacaoRepository.contarPorStatus();
        long totalPendentes = extrairTotalStatus(porStatus, StatusDaMarcacao.AGUARDANDO);
        long totalAgendadas = extrairTotalStatus(porStatus, StatusDaMarcacao.AGENDADO);
        long totalConcluidas = extrairTotalStatus(porStatus, StatusDaMarcacao.REALIZADO);

        long totalUrgentes = solicitacaoRepository.contarPorStatusPrioridades(
            StatusDaMarcacao.AGUARDANDO,
            Arrays.asList(PrioridadeDaMarcacaoEnum.URGENTE, PrioridadeDaMarcacaoEnum.EMERGENCIA)
        );

        Map<UsfEnum, Long> pendentesPorUsf = new EnumMap<>(UsfEnum.class);
        for (UsfEnum usf : UsfEnum.values()) {
            pendentesPorUsf.put(usf, 0L);
        }

        List<UsfPendentesProjection> pendentes = solicitacaoRepository.contarPorUsfEStatus(StatusDaMarcacao.AGUARDANDO);
        for (UsfPendentesProjection proj : pendentes) {
            pendentesPorUsf.put(proj.getUsf(), proj.getTotal());
        }

        return new DashboardResumoDTO(
            totalSolicitacoes,
            totalPendentes,
            totalAgendadas,
            totalConcluidas,
            totalUrgentes,
            pendentesPorUsf
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
    public AgendamentoSolicitacaoSimpleViewDTO createAgendamento(
            Long solicitacaoId,
            AgendamentoSolicitacaoCreateDTO dto) {
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
    public SolicitacaoViewDTO adicionarEspecialidadeASolicitacao(Long solicitacaoId, EspecialidadeAdicionarDTO dto) {
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
            .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada."));

        SolicitacaoEspecialidade novaEspecialidade = new SolicitacaoEspecialidade();
        novaEspecialidade.setSolicitacao(solicitacao);
        novaEspecialidade.setAgendamentoSolicitacao(null);

        if (dto.especialidadeId() != null) {
            var esp = especialidadeRepo.findById(dto.especialidadeId())
                    .orElseThrow(() -> new IllegalArgumentException("Especialidade não encontrada: id=" + dto.especialidadeId()));
            novaEspecialidade.setEspecialidadeSolicitada(esp);
            novaEspecialidade.setEspecialidadeCodigoLegacy(esp.getCodigo());
        } else {
            String codigo = dto.especialidadeSolicitada() != null ? dto.especialidadeSolicitada().name() : null;
            if (codigo != null) {
                var esp = especialidadeRepo.findByCodigo(codigo)
                        .orElseThrow(() -> new IllegalArgumentException("Especialidade não cadastrada: " + codigo));
                novaEspecialidade.setEspecialidadeSolicitada(esp);
                novaEspecialidade.setEspecialidadeCodigoLegacy(codigo);
            }
        }
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
    public void removerEspecialidade(Long id) {
        // Verifica se a especialidade existe antes de tentar deletar
        if (!especialidadeRepository.existsById(id)) {
            throw new EntityNotFoundException("Especialidade de solicitação com ID " + id + " não encontrada.");
        }
        especialidadeRepository.deleteById(id);
    }

    @Transactional
    public List<SolicitacaoPublicViewDTO> buscarPacientePorCpf(String cpf){
        return solicitacaoRepository.findByCpfPacienteSemPonto(cpf).stream().map(SolicitacaoPublicViewDTO::fromSolicitacao).collect(Collectors.toList());
    }

    @Transactional
    public SolicitacaoAgendamentoViewDTO buscarSolicitacaoPorId(Long id){
        return SolicitacaoAgendamentoViewDTO.fromEntity(solicitacaoRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada !")));
    }

    @Transactional
    public Page<SolicitacaoSimpleViewDTO> buscarPorNomeOuCpf(int page, int size, String termo){
        Pageable pagina = PageRequest.of(page, size, Sort.by("nomePaciente").ascending());

        if (termo == null ||termo.isEmpty()) {
            return solicitacaoRepository.findAll(pagina).map(SolicitacaoSimpleViewDTO::fromSolicitacao);
        }

        return solicitacaoRepository.findByNomePacienteContainingIgnoreCaseOrCpfPacienteContainingIgnoreCase(pagina, termo, termo).map(SolicitacaoSimpleViewDTO::fromSolicitacao);
    } 

    public Page<PendenciasPacienteProjection> buscarPorUsf(int page, int size, UsfEnum usfEnum){
        Pageable pageable = PageRequest.of(page, size, Sort.by("nomePaciente").ascending());
        return solicitacaoRepository.findByUsfOrigem(pageable, usfEnum);
    }

    public Page<PendenciasPacienteProjection> buscarPorStatusAguardandoeUsf(int page, int size, String usfEnum, String termo,String status){
        Pageable pagina = PageRequest.of(page, size);

        return solicitacaoRepository.listarPacientesPendentes(usfEnum, status, termo, pagina);
    }

    public Page<UrgenciaEmergenciaPacienteProjection> buscarPorUrgenteeEmergencia(int page, int size, String termo){
        Pageable pagina = PageRequest.of(page, size);

        return solicitacaoRepository.listarPacientesUrgenteseEmergencias(pagina, termo);

    }

    public long totalPacientesCadastrados(){
        return solicitacaoRepository.count();
    }

}















