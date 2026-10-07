package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AntecedenciaProjection;

/**
 * Antecedencia do agendamento: dias entre o dia em que o agendamento foi criado
 * e a data marcada, para os agendamentos com data marcada no periodo.
 *
 * <p>Nao e o tempo de espera do paciente (pedido ate o atendimento): mede com
 * quanta folga a marcacao e feita.
 *
 * @param total          agendamentos com data marcada no periodo
 * @param semDataCriacao sem registro de quando foram criados — fora de media, mediana e faixas
 * @param retroativos    criados depois da data marcada — tambem fora
 * @param mediaDias      nulo quando nao ha agendamento medivel
 */
public record AntecedenciaViewDTO(
        LocalDate de,
        LocalDate ate,
        long total,
        long semDataCriacao,
        long retroativos,
        Double mediaDias,
        Double medianaDias,
        long ate1,
        long de2a7,
        long de8a15,
        long de16a30,
        long mais30) {

    public static AntecedenciaViewDTO from(AntecedenciaProjection p, PeriodoIndicador periodo) {
        return new AntecedenciaViewDTO(
                periodo.de(),
                periodo.ate(),
                zeroSeNulo(p.getTotal()),
                zeroSeNulo(p.getSemDataCriacao()),
                zeroSeNulo(p.getRetroativos()),
                p.getMedia(),
                p.getMediana(),
                zeroSeNulo(p.getAte1()),
                zeroSeNulo(p.getDe2a7()),
                zeroSeNulo(p.getDe8a15()),
                zeroSeNulo(p.getDe16a30()),
                zeroSeNulo(p.getMais30()));
    }

    private static long zeroSeNulo(Long valor) {
        return valor != null ? valor : 0L;
    }
}
