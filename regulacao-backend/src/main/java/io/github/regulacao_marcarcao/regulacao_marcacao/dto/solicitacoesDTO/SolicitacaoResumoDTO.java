package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.UsfEnum;

public record SolicitacaoResumoDTO(
    Long id,
    String nomePaciente,
    String cpfPaciente,
    String cns,
    UsfEnum usfOrigem,
    List<String> especialidadesPendentes
) {
    public SolicitacaoResumoDTO(
        Long id,
        String nomePaciente,
        String cpfPaciente,
        String cns,
        UsfEnum usfOrigem,
        Collection<String> especialidadesPendentes
    ) {
        this(
            id,
            nomePaciente,
            cpfPaciente,
            cns,
            usfOrigem,
            especialidadesPendentes == null ? List.of() : List.copyOf(especialidadesPendentes)
        );
    }

    public SolicitacaoResumoDTO(
        Long id,
        String nomePaciente,
        String cpfPaciente,
        String cns,
        UsfEnum usfOrigem,
        Set<String> especialidadesPendentes
    ) {
        this(id, nomePaciente, cpfPaciente, cns, usfOrigem, (Collection<String>) especialidadesPendentes);
    }

    public SolicitacaoResumoDTO(
        Long id,
        String nomePaciente,
        String cpfPaciente,
        String cns,
        UsfEnum usfOrigem,
        Object especialidadesPendentes
    ) {
        this(id, nomePaciente, cpfPaciente, cns, usfOrigem, toList(especialidadesPendentes));
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
