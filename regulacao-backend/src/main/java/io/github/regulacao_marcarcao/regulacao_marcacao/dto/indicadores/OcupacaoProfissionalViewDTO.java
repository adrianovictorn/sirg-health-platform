package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.OcupacaoCotaProjection;

/**
 * Ocupacao das cotas com profissional executante no periodo: vagas ofertadas
 * (o total das cotas) x agendadas (o que as cotas registram como utilizado).
 *
 * <p>O profissional e o da COTA. A troca de profissional feita pelo operador
 * num agendamento especifico nao muda de qual cota a vaga saiu.
 */
public record OcupacaoProfissionalViewDTO(
        LocalDate de,
        LocalDate ate,
        List<Linha> porProfissional,
        List<Linha> porHorario) {

    /**
     * @param id   id do profissional; nulo nas linhas de horario
     * @param nome nome do profissional, ou a faixa "HH:MM–HH:MM"
     */
    public record Linha(Long id, String nome, long cotas, long ofertadas, long agendadas) {

        public static Linha from(OcupacaoCotaProjection p) {
            return new Linha(p.getId(), p.getNome(), zeroSeNulo(p.getCotas()), zeroSeNulo(p.getOfertadas()),
                    zeroSeNulo(p.getAgendadas()));
        }

        private static long zeroSeNulo(Long valor) {
            return valor != null ? valor : 0L;
        }
    }
}
