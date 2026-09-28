package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda;

import java.time.LocalDate;
import java.time.LocalTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaOcorrencia;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusOcorrenciaEnum;

public record AgendaOcorrenciaViewDTO(
        Long id,
        LocalDate data,
        LocalTime horaInicial,
        LocalTime horaFinal,
        StatusOcorrenciaEnum status) {

    public static AgendaOcorrenciaViewDTO from(AgendaOcorrencia o) {
        return new AgendaOcorrenciaViewDTO(
                o.getId(), o.getData(), o.getHoraInicial(), o.getHoraFinal(), o.getStatus());
    }
}
