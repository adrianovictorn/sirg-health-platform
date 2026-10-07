package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoPainelLinhaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoPainelViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CustoPainelRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.UnidadeAcessoService.EscopoListagem;
import lombok.RequiredArgsConstructor;

/**
 * Painel de custos: estimado (fila), agendado e concluido, no total, por
 * unidade e por especialidade. Somente leitura e somente agregados.
 *
 * <p>Quem pode chamar e decidido no controller (ADMIN e GESTOR). O escopo por
 * unidade ainda passa por {@link UnidadeAcessoService#escopoDeListagem}, como
 * toda listagem do sistema — a regra de acesso por unidade vive num lugar so.
 */
@Service
@RequiredArgsConstructor
public class CustoPainelService {

    private final CustoPainelRepository painelRepository;
    private final UnidadeAcessoService unidadeAcessoService;

    @Transactional(readOnly = true)
    public CustoPainelViewDTO painel(Long unidadeId, Long grupoRelatorioId, String categoria,
            LocalDate dataDe, LocalDate dataAte, String callerCpf) {
        // Sem periodo informado, o mes corrente — o mesmo recorte do teto financeiro.
        YearMonth mesAtual = YearMonth.now();
        LocalDate de = dataDe != null ? dataDe : mesAtual.atDay(1);
        LocalDate ate = dataAte != null ? dataAte : mesAtual.atEndOfMonth();
        if (de.isAfter(ate)) {
            throw new IllegalArgumentException("A data inicial do período não pode ser posterior à data final.");
        }

        String categoriaValida = null;
        if (categoria != null && !categoria.isBlank()) {
            categoriaValida = categoria.trim().toUpperCase();
            try {
                ItemCategoria.valueOf(categoriaValida);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Tipo inválido: " + categoria + ". Use ESPECIALIDADE_MEDICA ou EXAME_OU_PROCEDIMENTO.");
            }
        }

        EscopoListagem escopo = unidadeAcessoService.escopoDeListagem(callerCpf, unidadeId);
        if (escopo.isSemAcesso()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Acesso restrito ao administrador e ao gestor.");
        }
        Long unidadeEscopo = escopo.unidadeId();

        CustoPainelLinhaViewDTO total = CustoPainelLinhaViewDTO.from(
                painelRepository.total(unidadeEscopo, grupoRelatorioId, categoriaValida, de, ate));

        List<CustoPainelLinhaViewDTO> porUnidade = painelRepository
                .porUnidade(unidadeEscopo, grupoRelatorioId, categoriaValida, de, ate).stream()
                .map(CustoPainelLinhaViewDTO::from)
                .filter(CustoPainelService::temMovimento)
                // "Sem unidade" (nome nulo) por ultimo.
                .sorted(Comparator.comparing(CustoPainelLinhaViewDTO::nome,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        List<CustoPainelLinhaViewDTO> porEspecialidade = painelRepository
                .porEspecialidade(unidadeEscopo, grupoRelatorioId, categoriaValida, de, ate).stream()
                .map(CustoPainelLinhaViewDTO::from)
                .filter(CustoPainelService::temMovimento)
                .sorted(Comparator
                        .comparing((CustoPainelLinhaViewDTO l) ->
                                l.estimado().add(l.agendado()).add(l.concluido()))
                        .reversed()
                        .thenComparing(CustoPainelLinhaViewDTO::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new CustoPainelViewDTO(de, ate, total, porUnidade, porEspecialidade);
    }

    /** Linha que so tem itens fora do periodo nao tem o que mostrar. */
    private static boolean temMovimento(CustoPainelLinhaViewDTO l) {
        return l.estimadoItens() + l.estimadoSemPreco()
                + l.agendadoItens() + l.agendadoSemValor()
                + l.concluidoItens() + l.concluidoSemValor() > 0;
    }
}
