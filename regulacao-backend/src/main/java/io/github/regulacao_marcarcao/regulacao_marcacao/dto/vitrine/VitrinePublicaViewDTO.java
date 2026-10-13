package io.github.regulacao_marcarcao.regulacao_marcacao.dto.vitrine;

import java.time.Instant;
import java.util.List;

/**
 * Resumo publico do municipio, lido pela landing do SIRG. A lista de campos e
 * fechada: acrescentar um campo aqui e publica-lo na internet.
 *
 * <p>Em {@link Totais}, {@code null} significa suprimido (contagem abaixo do
 * limiar), nao zero.
 */
public record VitrinePublicaViewDTO(
        String municipio,
        Totais totais,
        Catalogo catalogo,
        Sistema sistema,
        Instant geradoEm) {

    public record Totais(
            Long solicitacoes,
            Long agendamentos,
            Long pacientesAtendidos,
            Long viagensTransporte) {
    }

    public record Catalogo(
            long unidades,
            long especialidades,
            List<String> nomesEspecialidades) {
    }

    /** {@code deployEm} e o instante do build do backend; nulo fora de um build Maven. */
    public record Sistema(String versao, Instant deployEm) {
    }
}
