package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoCancelamentoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoColetaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoFaltaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoPacienteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoResumoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoResumoViewDTO.Contagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoResumoViewDTO.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AtendimentoExecutanteRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoResumoProjection;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Tela de Atendimentos do operador da unidade executante (Usuario Padrao): os
 * quantitativos e a lista nominal do dia, do municipio inteiro, e o lancamento do
 * desfecho de cada atendimento. A leitura vale para USER e ADMIN; o desfecho, so
 * para o USER. Nada aqui toca em cota ou teto.
 */
@Service
@RequiredArgsConstructor
public class AtendimentoExecutanteService {

    static final ZoneId FUSO = ZoneId.of("America/Bahia");
    static final int TAMANHO_MAXIMO_PAGINA = 50;
    static final int TAMANHO_MAXIMO_TERMO = 100;
    private static final int TAMANHO_DO_CPF = 11;
    private static final int TAMANHO_DO_CNS = 15;
    static final int TAMANHO_MAXIMO_OBSERVACAO = 500;

    private final AtendimentoExecutanteRepository repository;
    private final SolicitacaoEspecialidadeRepository itemRepository;
    private final UserRepository userRepository;
    private final UnidadeRepository unidadeRepository;
    private final UnidadeAcessoService unidadeAcessoService;

    @Transactional(readOnly = true)
    public AtendimentoResumoViewDTO resumir(LocalDate data, String grupo, String callerCpf) {
        unidadeAcessoService.exigirOperadorDeAtendimentos(callerCpf);
        LocalDate dia = data != null ? data : LocalDate.now(FUSO);

        // Todas as unidades ativas aparecem, mesmo sem atendimento no dia.
        Map<Long, Unidade> unidades = new LinkedHashMap<>();
        unidadeRepository.findByAtivoTrue()
                .forEach(u -> unidades.put(u.getId(), new Unidade(u.getId(), u.getNome(), Contagem.ZERO)));

        Contagem totais = Contagem.ZERO;
        for (AtendimentoResumoProjection linha : repository.resumirPorUnidade(dia, grupoOuNulo(grupo))) {
            Contagem contagem = Contagem.from(linha);
            if (linha.getTotalGeral() != null && linha.getTotalGeral() == 1) {
                totais = contagem;
                continue;
            }
            Long id = linha.getUnidadeId();
            String nome = id == null ? "Sem unidade"
                    : (linha.getUnidadeNome() != null ? linha.getUnidadeNome() : "Unidade " + id);
            unidades.put(id, new Unidade(id, nome, contagem));
        }

        List<Unidade> ordenadas = new ArrayList<>(unidades.values());
        ordenadas.sort(Comparator.comparing((Unidade u) -> u.id() == null)
                .thenComparing(Unidade::nome, String.CASE_INSENSITIVE_ORDER));
        return new AtendimentoResumoViewDTO(dia, totais, ordenadas);
    }

    /**
     * Pagina de PACIENTES (fichas), cada um com o bloco inteiro de itens do dia na
     * agenda consultada. {@code status} escolhe os pacientes que tem algum item
     * naquela situacao; o bloco deles nao e recortado.
     */
    @Transactional(readOnly = true)
    public Page<AtendimentoPacienteViewDTO> listar(LocalDate data, String grupo, Long unidadeId, boolean semUnidade,
            String status, String termo, int page, int size, String callerCpf) {
        unidadeAcessoService.exigirOperadorDeAtendimentos(callerCpf);
        LocalDate dia = data != null ? data : LocalDate.now(FUSO);
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO_PAGINA);

        // Caracteres de controle viram espaco antes de decidir se ha busca: o
        // PostgreSQL recusa NUL em texto (seria um 500). CPF e CNS so sao comparados
        // quando o termo e o documento completo (11 ou 15 digitos, com ou sem
        // pontuacao): um trecho de numero listaria pacientes que o operador nao esta
        // atendendo.
        String busca = null;
        String buscaDigitos = null;
        if (termo != null) {
            String limpo = termo.replaceAll("\\p{Cntrl}", " ").strip();
            if (limpo.length() > TAMANHO_MAXIMO_TERMO) {
                limpo = limpo.substring(0, TAMANHO_MAXIMO_TERMO).strip();
            }
            if (!limpo.isEmpty()) {
                busca = limpo;
                if (limpo.matches("[0-9 ./-]+")) {
                    String digitos = limpo.replaceAll("[^0-9]", "");
                    boolean documentoCompleto = digitos.length() == TAMANHO_DO_CPF
                            || digitos.length() == TAMANHO_DO_CNS;
                    buscaDigitos = documentoCompleto ? digitos : null;
                }
            }
        }

        String grupoFiltro = grupoOuNulo(grupo);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanho);
        Page<AtendimentoPacienteProjection> pacientes = repository.listarPacientes(dia, grupoFiltro, unidadeId,
                semUnidade, statusValido(status), busca, buscaDigitos, pageable);
        // O IN da segunda consulta nunca pode ir vazio. Pagina alem da ultima vem vazia
        // mas com o total real: preserva-lo deixa a tela voltar para as paginas validas.
        if (pacientes.isEmpty()) {
            return pacientes.map(p -> AtendimentoPacienteViewDTO.from(p, List.of()));
        }

        List<Long> ids = pacientes.getContent().stream().map(AtendimentoPacienteProjection::getSolicitacaoId).toList();
        Map<Long, List<AtendimentoViewDTO>> itensPorPaciente = new HashMap<>();
        for (AtendimentoProjection item : repository.listarItensDosPacientes(ids, dia, grupoFiltro)) {
            itensPorPaciente.computeIfAbsent(item.getSolicitacaoId(), id -> new ArrayList<>())
                    .add(AtendimentoViewDTO.from(item));
        }
        return pacientes.map(p -> AtendimentoPacienteViewDTO.from(p,
                itensPorPaciente.getOrDefault(p.getSolicitacaoId(), List.of())));
    }

    // ------------------------------------------------------------------
    // Desfecho do atendimento (V109). So o operador da unidade executante (USER).
    //
    // Nenhuma acao toca cota ou teto — falta e cancelamento continuam ocupando a
    // vaga —, nem a observacao do agendamento, nem publica evento (o paciente nao
    // e avisado por WhatsApp). O item e carregado com trava de linha: a unidade
    // solicitante pode estar devolvendo o mesmo item a fila.
    // ------------------------------------------------------------------

    @Transactional
    public AtendimentoViewDTO confirmar(Long id, String callerCpf) {
        unidadeAcessoService.exigirExecutorDeAtendimentos(callerCpf);
        SolicitacaoEspecialidade item = carregarAgendado(id);
        registrar(item, StatusDaMarcacao.REALIZADO, null, callerCpf);
        return AtendimentoViewDTO.from(item);
    }

    @Transactional
    public AtendimentoViewDTO registrarFalta(Long id, AtendimentoFaltaDTO dto, String callerCpf) {
        unidadeAcessoService.exigirExecutorDeAtendimentos(callerCpf);
        String observacao = texto(dto != null ? dto.observacao() : null, "A observação da falta");
        SolicitacaoEspecialidade item = carregarAgendado(id);
        registrar(item, StatusDaMarcacao.FALTOU, observacao, callerCpf);
        return AtendimentoViewDTO.from(item);
    }

    @Transactional
    public AtendimentoViewDTO cancelar(Long id, AtendimentoCancelamentoDTO dto, String callerCpf) {
        unidadeAcessoService.exigirExecutorDeAtendimentos(callerCpf);
        String motivo = texto(dto != null ? dto.motivo() : null, "O motivo do cancelamento");
        if (motivo == null) {
            throw new IllegalArgumentException("Informe o motivo do cancelamento.");
        }
        SolicitacaoEspecialidade item = carregarAgendado(id);
        registrar(item, StatusDaMarcacao.CANCELADO, motivo, callerCpf);
        return AtendimentoViewDTO.from(item);
    }

    /** Nao muda o status. {@code dataColeta} nula limpa o campo. */
    @Transactional
    public AtendimentoViewDTO informarDataColeta(Long id, AtendimentoColetaDTO dto, String callerCpf) {
        unidadeAcessoService.exigirExecutorDeAtendimentos(callerCpf);
        // Sem corpo nao e "limpar": a data tambem e informada pela unidade solicitante
        // no cadastro, e apaga-la tem de ser um pedido explicito ({"dataColeta": null}).
        if (dto == null) {
            throw new IllegalArgumentException("Informe a data da coleta.");
        }
        SolicitacaoEspecialidade item = carregar(id);
        var especialidade = item.getEspecialidadeSolicitada();
        if (especialidade == null || especialidade.getCategoria() != ItemCategoria.EXAME_OU_PROCEDIMENTO) {
            throw new IllegalArgumentException("A data da coleta só se aplica a exame ou procedimento.");
        }
        if (item.getStatus() != StatusDaMarcacao.AGENDADO && item.getStatus() != StatusDaMarcacao.REALIZADO) {
            throw new IllegalStateException("Só é possível informar a coleta de um atendimento agendado ou confirmado.");
        }
        item.setDataColeta(dto.dataColeta());
        return AtendimentoViewDTO.from(item);
    }

    /** Volta para AGENDADO um desfecho lancado nesta tela. Nao mexe na data da coleta. */
    @Transactional
    public AtendimentoViewDTO desfazer(Long id, String callerCpf) {
        unidadeAcessoService.exigirExecutorDeAtendimentos(callerCpf);
        SolicitacaoEspecialidade item = carregar(id);
        StatusDaMarcacao status = item.getStatus();
        if (status != StatusDaMarcacao.REALIZADO && status != StatusDaMarcacao.FALTOU
                && status != StatusDaMarcacao.CANCELADO) {
            throw new IllegalStateException("Não há registro de atendimento para desfazer.");
        }
        // Sem a marca, o status veio de outro fluxo — pode ser um cancelamento real.
        if (item.getAtendimentoRegistradoEm() == null) {
            throw new IllegalStateException(
                    "Este registro não foi lançado na tela de Atendimentos e não pode ser desfeito aqui.");
        }
        if (item.getDevolvidoComo() != null) {
            throw new IllegalStateException("O paciente já foi devolvido à fila pela unidade.");
        }
        item.setStatus(StatusDaMarcacao.AGENDADO);
        item.setAtendimentoObservacao(null);
        item.setAtendimentoRegistradoPor(null);
        item.setAtendimentoRegistradoEm(null);
        return AtendimentoViewDTO.from(item);
    }

    private SolicitacaoEspecialidade carregar(Long id) {
        SolicitacaoEspecialidade item = itemRepository.findByIdParaAtualizar(id)
                .orElseThrow(() -> new EntityNotFoundException("Atendimento não encontrado."));
        if (item.getAgendamentoSolicitacao() == null) {
            throw new IllegalStateException("Este item não está agendado.");
        }
        return item;
    }

    /** Um desfecho nao troca direto por outro: desfaz e lanca de novo. */
    private SolicitacaoEspecialidade carregarAgendado(Long id) {
        SolicitacaoEspecialidade item = carregar(id);
        if (item.getStatus() != StatusDaMarcacao.AGENDADO) {
            throw new IllegalStateException(
                    "Este atendimento não está mais como agendado. Atualize a lista; para trocar um registro, desfaça-o antes.");
        }
        return item;
    }

    private void registrar(SolicitacaoEspecialidade item, StatusDaMarcacao status, String observacao, String callerCpf) {
        item.setStatus(status);
        item.setAtendimentoObservacao(observacao);
        item.setAtendimentoRegistradoPor(userRepository.findByCpf(callerCpf).orElse(null));
        item.setAtendimentoRegistradoEm(Instant.now());
    }

    /** Texto livre aparado; vazio vira nulo; acima do limite e 400, nao truncamento silencioso. */
    private static String texto(String valor, String rotulo) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.replaceAll("\\p{Cntrl}", " ").strip();
        if (limpo.isEmpty()) {
            return null;
        }
        if (limpo.length() > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new IllegalArgumentException(
                    rotulo + " deve ter no máximo " + TAMANHO_MAXIMO_OBSERVACAO + " caracteres.");
        }
        return limpo;
    }

    /** Codigo do Grupo de Relatorio; vazio e "todos os grupos". Codigo inexistente so devolve vazio. */
    private static String grupoOuNulo(String grupo) {
        if (grupo == null) {
            return null;
        }
        String limpo = grupo.replaceAll("\\p{Cntrl}", " ").strip();
        return limpo.isEmpty() ? null : limpo;
    }

    /** Valor desconhecido vira 400 com mensagem, nao lista vazia nem erro de conversao. */
    private static String statusValido(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return StatusDaMarcacao.valueOf(status.strip().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status de atendimento desconhecido: " + status.strip() + ".");
        }
    }
}
