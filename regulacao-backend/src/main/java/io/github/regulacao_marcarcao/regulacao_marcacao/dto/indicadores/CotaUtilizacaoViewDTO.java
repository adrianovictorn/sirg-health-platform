package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CotaUtilizacaoProjection;

/**
 * Utilizacao das cotas ativas do periodo.
 *
 * <p>Sempre separada por tipo (MENSAL e DATA): as duas incidem juntas sobre o
 * mesmo agendamento e somar ou tirar media entre elas contaria a mesma vaga
 * duas vezes.
 *
 * @param porTipo    uma linha por tipo de periodo
 * @param porTitular uma linha por tipo e titular (unidade ou grupo de unidades)
 */
public record CotaUtilizacaoViewDTO(
        LocalDate de,
        LocalDate ate,
        List<Linha> porTipo,
        List<Linha> porTitular) {

    /**
     * @param tipo            MENSAL ou DATA
     * @param unidadeId       nulo em {@code porTipo} e quando o titular e um grupo de unidades
     * @param titular         nome da unidade ou do grupo de unidades; nulo em {@code porTipo}
     * @param esgotadas       cotas sem vaga
     * @param ociosas         cotas de periodo ja encerrado sem nenhuma vaga usada
     * @param utilizacaoMedia media de utilizada/total das cotas, de 0 a 1; nulo sem cota com vagas
     */
    public record Linha(
            String tipo,
            Long unidadeId,
            String titular,
            long cotas,
            long esgotadas,
            long ociosas,
            Double utilizacaoMedia) {

        public static Linha from(CotaUtilizacaoProjection p) {
            return new Linha(p.getTipo(), p.getUnidadeId(), p.getTitular(), zeroSeNulo(p.getCotas()),
                    zeroSeNulo(p.getEsgotadas()), zeroSeNulo(p.getOciosas()), p.getUtilizacaoMedia());
        }

        private static long zeroSeNulo(Long valor) {
            return valor != null ? valor : 0L;
        }
    }
}
