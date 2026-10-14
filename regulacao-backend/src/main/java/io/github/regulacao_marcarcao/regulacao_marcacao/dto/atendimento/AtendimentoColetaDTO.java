package io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento;

import java.time.LocalDate;

/** Data da coleta de um exame/procedimento. {@code null} limpa o campo. */
public record AtendimentoColetaDTO(LocalDate dataColeta) {
}
