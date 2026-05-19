package io.github.regulacao_marcarcao.regulacao_marcacao.dto.paciente;

public record PacienteResumoDTO(
    Long solicitacaoId,
    String nomePaciente,
    String cpfPaciente,
    Long unidadeId,
    String unidadeNome
) {}
