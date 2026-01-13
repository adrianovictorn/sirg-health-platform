package io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade;

public record EspecialidadeUpdateDTO(
    String codigo,
    String nome, 
    String categoria, 
    Long grupoRelatorioId,
    Boolean ativo
) {
    
}
