package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila.FilaEsperaFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila.FilaEsperaItemViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila.FilaEsperaPacienteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.FilaEsperaItemProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.FilaEsperaPacienteProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UnidadePendentesProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.UnidadeAcessoService.EscopoListagem;
import lombok.RequiredArgsConstructor;

/**
 * Fila de espera: pacientes com pedidos aguardando marcacao, uma linha por
 * paciente, filtravel por especialidade, tipo, status, prioridade e tempo de
 * espera.
 *
 * <p>Somente leitura — nao agenda, nao consome cota, nao muda status.
 *
 * <p>O tempo de espera e contado de {@code solicitacao_especialidade.data_cadastro}.
 * Duas limitacoes conhecidas, avisadas na tela: pedidos anteriores a V62 tem a
 * data da migracao (espera subestimada), e pedido em RETORNO conta desde o pedido
 * original, porque nao ha registro de quando o status mudou.
 *
 * <p>A contagem ({@link #contar}) e a mesma query da listagem; os cards do
 * dashboard a usam para o numero do card bater com o total da lista que ele abre.
 */
@Service
@RequiredArgsConstructor
public class FilaEsperaService {

    /** GEL tem card e lista proprios e fica fora da fila. */
    public static final List<String> STATUS_DA_FILA = List.of(
            StatusDaMarcacao.AGUARDANDO.name(),
            StatusDaMarcacao.RETORNO.name(),
            StatusDaMarcacao.RETORNO_POLICLINICA.name());

    private static final Set<String> PRIORIDADES_VALIDAS = Set.of(
            PrioridadeDaMarcacaoEnum.NORMAL.name(),
            PrioridadeDaMarcacaoEnum.URGENTE.name(),
            PrioridadeDaMarcacaoEnum.EMERGENCIA.name());

    private static final String ORDEM_ANTIGOS = "ANTIGOS";
    private static final String ORDEM_RECENTES = "RECENTES";
    private static final int TAMANHO_MAXIMO_PAGINA = 50;
    private static final int TAMANHO_MAXIMO_TERMO = 100;

    // Lista de IN nunca pode ir vazia para a query nativa; com filtrarPrioridade
    // = false o predicado e ignorado e este valor nao casa com nada.
    private static final List<String> SEM_FILTRO_DE_PRIORIDADE = List.of("");

    private final SolicitacaoEspecialidadeRepository especialidadeRepository;
    private final UnidadeAcessoService unidadeAcessoService;

    /** Filtro ja validado, no formato que as queries nativas recebem. */
    private record Parametros(
            List<String> status,
            Long especialidadeId,
            String categoria,
            boolean filtrarPrioridade,
            List<String> prioridades,
            LocalDate dataDe,
            LocalDate dataAte,
            LocalDateTime cadastradoAte,
            String termo,
            String termoDigitos,
            boolean maisRecentes) {
    }

    @Transactional(readOnly = true)
    public Page<FilaEsperaPacienteViewDTO> listar(FilaEsperaFiltroDTO filtro, int page, int size, String callerCpf) {
        LocalDateTime agora = LocalDateTime.now();
        Parametros p = validar(filtro, agora);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANHO_MAXIMO_PAGINA));

        EscopoListagem escopo = unidadeAcessoService.escopoDeListagem(callerCpf, filtro.unidadeId());
        if (escopo.isSemAcesso()) {
            return Page.empty(pageable);
        }
        Long unidadeId = escopo.unidadeId();

        Page<FilaEsperaPacienteProjection> pagina = especialidadeRepository.listarFilaDeEspera(
                p.status(), p.especialidadeId(), p.categoria(), p.filtrarPrioridade(), p.prioridades(),
                unidadeId, p.dataDe(), p.dataAte(), p.cadastradoAte(), p.termo(), p.termoDigitos(), p.maisRecentes(), pageable);
        if (pagina.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> ids = pagina.getContent().stream().map(FilaEsperaPacienteProjection::getSolicitacaoId).toList();
        Map<Long, List<FilaEsperaItemViewDTO>> itensPorSolicitacao = new HashMap<>();
        for (FilaEsperaItemProjection item : especialidadeRepository.listarItensDaFila(
                ids, p.status(), p.especialidadeId(), p.categoria(), p.filtrarPrioridade(), p.prioridades(),
                unidadeId, p.dataDe(), p.dataAte(), p.cadastradoAte(), p.termo(), p.termoDigitos())) {
            itensPorSolicitacao
                    .computeIfAbsent(item.getSolicitacaoId(), id -> new ArrayList<>())
                    .add(FilaEsperaItemViewDTO.from(item, diasDesde(item.getDataCadastro(), agora)));
        }

        List<FilaEsperaPacienteViewDTO> linhas = pagina.getContent().stream()
                .map(paciente -> FilaEsperaPacienteViewDTO.from(
                        paciente,
                        diasDesde(paciente.getEntradaMaisAntiga(), agora),
                        itensPorSolicitacao.getOrDefault(paciente.getSolicitacaoId(), List.of())))
                .toList();
        return new PageImpl<>(linhas, pageable, pagina.getTotalElements());
    }

    /** Total de pacientes que a listagem devolveria para o mesmo filtro e chamador. */
    @Transactional(readOnly = true)
    public long contar(FilaEsperaFiltroDTO filtro, String callerCpf) {
        Parametros p = validar(filtro, LocalDateTime.now());
        EscopoListagem escopo = unidadeAcessoService.escopoDeListagem(callerCpf, filtro.unidadeId());
        if (escopo.isSemAcesso()) {
            return 0;
        }
        return especialidadeRepository.contarPacientesNaFila(
                p.status(), p.especialidadeId(), p.categoria(), p.filtrarPrioridade(), p.prioridades(),
                escopo.unidadeId(), p.dataDe(), p.dataAte(), p.cadastradoAte(), p.termo(), p.termoDigitos());
    }

    /**
     * Pacientes na fila por unidade, para o mesmo filtro e chamador. Solicitacoes
     * sem unidade vinculada nao entram no mapa (so no total global).
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> contarPorUnidade(FilaEsperaFiltroDTO filtro, String callerCpf) {
        Parametros p = validar(filtro, LocalDateTime.now());
        EscopoListagem escopo = unidadeAcessoService.escopoDeListagem(callerCpf, filtro.unidadeId());
        Map<Long, Long> porUnidade = new HashMap<>();
        if (escopo.isSemAcesso()) {
            return porUnidade;
        }
        for (UnidadePendentesProjection linha : especialidadeRepository.contarPacientesNaFilaPorUnidade(
                p.status(), p.especialidadeId(), p.categoria(), p.filtrarPrioridade(), p.prioridades(),
                escopo.unidadeId(), p.dataDe(), p.dataAte(), p.cadastradoAte(), p.termo(), p.termoDigitos())) {
            porUnidade.put(linha.getUnidadeId(), linha.getTotal());
        }
        return porUnidade;
    }

    private static long diasDesde(LocalDateTime data, LocalDateTime agora) {
        return data == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(data, agora));
    }

    private static Parametros validar(FilaEsperaFiltroDTO filtro, LocalDateTime agora) {
        List<String> status = normalizar(filtro.status());
        if (status.isEmpty()) {
            status = STATUS_DA_FILA;
        } else {
            for (String s : status) {
                if (!STATUS_DA_FILA.contains(s)) {
                    throw new IllegalArgumentException(
                            "Status inválido para a fila de espera: " + s
                                    + ". Use AGUARDANDO, RETORNO ou RETORNO_POLICLINICA.");
                }
            }
        }

        List<String> prioridades = normalizar(filtro.prioridades());
        for (String prioridade : prioridades) {
            if (!PRIORIDADES_VALIDAS.contains(prioridade)) {
                throw new IllegalArgumentException(
                        "Prioridade inválida: " + prioridade + ". Use NORMAL, URGENTE ou EMERGENCIA.");
            }
        }

        String categoria = null;
        if (filtro.categoria() != null && !filtro.categoria().isBlank()) {
            categoria = filtro.categoria().trim().toUpperCase();
            try {
                ItemCategoria.valueOf(categoria);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Tipo de solicitação inválido: " + filtro.categoria()
                                + ". Use ESPECIALIDADE_MEDICA ou EXAME_OU_PROCEDIMENTO.");
            }
        }

        if (filtro.dataDe() != null && filtro.dataAte() != null && filtro.dataDe().isAfter(filtro.dataAte())) {
            throw new IllegalArgumentException("A data inicial do período não pode ser posterior à data final.");
        }

        LocalDateTime cadastradoAte = null;
        if (filtro.esperaMinimaDias() != null) {
            if (filtro.esperaMinimaDias() < 0) {
                throw new IllegalArgumentException("O tempo mínimo de espera não pode ser negativo.");
            }
            if (filtro.esperaMinimaDias() > 0) {
                cadastradoAte = agora.minusDays(filtro.esperaMinimaDias());
            }
        }

        boolean maisRecentes = false;
        if (filtro.ordem() != null && !filtro.ordem().isBlank()) {
            String ordem = filtro.ordem().trim().toUpperCase();
            if (ORDEM_RECENTES.equals(ordem)) {
                maisRecentes = true;
            } else if (!ORDEM_ANTIGOS.equals(ordem)) {
                throw new IllegalArgumentException("Ordenação inválida: " + filtro.ordem() + ". Use ANTIGOS ou RECENTES.");
            }
        }

        // Busca livre: vazio ou so espacos e "sem busca" (nulo), nunca texto vazio —
        // o predicado da query depende disso para ficar neutro. CPF e CNS so sao
        // comparados quando o termo e feito apenas de digitos e pontuacao: assim
        // "Maria 2" procura esse texto no nome, e nao todo CPF que contenha 2.
        //
        // Caracteres de controle viram espaco ANTES de decidir se ha busca: o
        // PostgreSQL recusa NUL em texto (seria um 500), e um termo so de controles
        // passaria por isBlank() e viraria texto vazio depois do trim().
        String termo = null;
        String termoDigitos = null;
        if (filtro.termo() != null) {
            String limpo = filtro.termo().replaceAll("\\p{Cntrl}", " ").strip();
            if (limpo.length() > TAMANHO_MAXIMO_TERMO) {
                limpo = limpo.substring(0, TAMANHO_MAXIMO_TERMO).strip();
            }
            if (!limpo.isEmpty()) {
                termo = limpo;
                if (termo.matches("[0-9 ./-]+")) {
                    String digitos = termo.replaceAll("[^0-9]", "");
                    termoDigitos = digitos.isEmpty() ? null : digitos;
                }
            }
        }

        boolean filtrarPrioridade = !prioridades.isEmpty();
        return new Parametros(
                status,
                filtro.especialidadeId(),
                categoria,
                filtrarPrioridade,
                filtrarPrioridade ? prioridades : SEM_FILTRO_DE_PRIORIDADE,
                filtro.dataDe(),
                filtro.dataAte(),
                cadastradoAte,
                termo,
                termoDigitos,
                maisRecentes);
    }

    /** Aceita tanto parametros repetidos quanto valores separados por virgula. */
    private static List<String> normalizar(List<String> valores) {
        List<String> normalizados = new ArrayList<>();
        if (valores == null) {
            return normalizados;
        }
        for (String valor : valores) {
            if (valor == null) {
                continue;
            }
            for (String parte : valor.split(",")) {
                String limpo = parte.trim().toUpperCase();
                if (!limpo.isEmpty() && !normalizados.contains(limpo)) {
                    normalizados.add(limpo);
                }
            }
        }
        return normalizados;
    }
}
