package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.LocalDate;

/** Pedido de migracao: a nova data da cota por data, com os agendamentos dela. */
public record CotaMigracaoDataCreateDTO(LocalDate dataEspecifica) {
}
