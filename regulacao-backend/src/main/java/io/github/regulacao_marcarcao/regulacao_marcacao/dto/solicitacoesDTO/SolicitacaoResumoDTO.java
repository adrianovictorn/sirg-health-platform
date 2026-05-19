package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public record SolicitacaoResumoDTO(
    Long id,
    String nomePaciente,
    String cpfPaciente,
    String cns,
    Long unidadeId,
    String unidadeNome,
    List<String> especialidadesPendentes
) {
    public SolicitacaoResumoDTO(
        Long id,
        String nomePaciente,
        String cpfPaciente,
        String cns,
        Long unidadeId,
        String unidadeNome,
        Collection<String> especialidadesPendentes
    ) {
        this(
            id,
            nomePaciente,
            cpfPaciente,
            cns,
            unidadeId,
            unidadeNome,
            especialidadesPendentes == null ? List.of() : List.copyOf(especialidadesPendentes)
        );
    }

    public SolicitacaoResumoDTO(
        Long id,
        String nomePaciente,
        String cpfPaciente,
        String cns,
        Long unidadeId,
        String unidadeNome,
        Set<String> especialidadesPendentes
    ) {
        this(id, nomePaciente, cpfPaciente, cns, unidadeId, unidadeNome, (Collection<String>) especialidadesPendentes);
    }

    public SolicitacaoResumoDTO(
        Long id,
        String nomePaciente,
        String cpfPaciente,
        String cns,
        Long unidadeId,
        String unidadeNome,
        Object especialidadesPendentes
    ) {
        this(id, nomePaciente, cpfPaciente, cns, unidadeId, unidadeNome, toList(especialidadesPendentes));
    }

    private static List<String> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }
}
