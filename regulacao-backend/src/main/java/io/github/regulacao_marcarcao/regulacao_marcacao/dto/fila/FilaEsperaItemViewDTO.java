package io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila;

import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.FilaEsperaItemProjection;

/** Um pedido (especialidade ou exame) do paciente que bate com os filtros da fila. */
public record FilaEsperaItemViewDTO(
    Long id,
    String especialidadeNome,
    String categoria,
    String status,
    String prioridade,
    LocalDateTime dataCadastro,
    long diasEspera
) {
    public static FilaEsperaItemViewDTO from(FilaEsperaItemProjection p, long diasEspera) {
        return new FilaEsperaItemViewDTO(
            p.getId(),
            p.getEspecialidadeNome(),
            p.getCategoria(),
            p.getStatus(),
            p.getPrioridade(),
            p.getDataCadastro(),
            diasEspera);
    }
}
