package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.FaixasEsperaProjection;

/**
 * Quantos estao em cada faixa de espera: o total, uma unidade, uma
 * especialidade ou uma prioridade.
 *
 * @param id   id da unidade ou da especialidade; nulo no total, em "Sem unidade" e nas prioridades
 * @param nome nome da unidade ou da especialidade, ou a prioridade; nulo no total,
 *             em "Sem unidade" e em pedido sem prioridade
 */
public record FaixasEsperaViewDTO(
        Long id,
        String nome,
        long ate30,
        long de31a60,
        long de61a90,
        long mais90,
        long total) {

    public static FaixasEsperaViewDTO from(FaixasEsperaProjection p) {
        return de(p.getId(), p.getNome(), zeroSeNulo(p.getAte30()), zeroSeNulo(p.getDe31a60()),
                zeroSeNulo(p.getDe61a90()), zeroSeNulo(p.getMais90()));
    }

    /** Soma das linhas — so faz sentido quando cada paciente/pedido esta em uma unica linha. */
    public static FaixasEsperaViewDTO somar(List<FaixasEsperaViewDTO> linhas) {
        return de(null, null,
                linhas.stream().mapToLong(FaixasEsperaViewDTO::ate30).sum(),
                linhas.stream().mapToLong(FaixasEsperaViewDTO::de31a60).sum(),
                linhas.stream().mapToLong(FaixasEsperaViewDTO::de61a90).sum(),
                linhas.stream().mapToLong(FaixasEsperaViewDTO::mais90).sum());
    }

    private static FaixasEsperaViewDTO de(Long id, String nome, long ate30, long de31a60, long de61a90, long mais90) {
        return new FaixasEsperaViewDTO(id, nome, ate30, de31a60, de61a90, mais90,
                ate30 + de31a60 + de61a90 + mais90);
    }

    private static long zeroSeNulo(Long valor) {
        return valor != null ? valor : 0L;
    }
}
