package io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila;

import java.time.LocalDate;
import java.util.List;

/**
 * Filtros da fila de espera, como chegam da tela. Todos opcionais: sem nenhum,
 * a fila traz os tres status de espera, dos mais antigos para os mais recentes.
 *
 * Status, prioridades, categoria e ordem chegam como texto e sao validados no
 * service — valor desconhecido vira 400 com mensagem, nao erro de conversao.
 *
 * {@code termo} e a busca livre por nome, CPF ou CNS do paciente. E o unico
 * filtro que os cards do dashboard nunca enviam.
 */
public record FilaEsperaFiltroDTO(
    Long especialidadeId,
    String categoria,
    List<String> status,
    List<String> prioridades,
    Long unidadeId,
    Integer esperaMinimaDias,
    LocalDate dataDe,
    LocalDate dataAte,
    String ordem,
    String termo
) {
    /** Filtro sem busca por texto. */
    public FilaEsperaFiltroDTO(Long especialidadeId, String categoria, List<String> status,
            List<String> prioridades, Long unidadeId, Integer esperaMinimaDias, LocalDate dataDe,
            LocalDate dataAte, String ordem) {
        this(especialidadeId, categoria, status, prioridades, unidadeId, esperaMinimaDias, dataDe, dataAte,
                ordem, null);
    }

    /** Filtro so por status e prioridade — e o que os cards do dashboard contam. */
    public static FilaEsperaFiltroDTO de(List<String> status, List<String> prioridades, Long unidadeId) {
        return new FilaEsperaFiltroDTO(null, null, status, prioridades, unidadeId, null, null, null, null);
    }
}
