package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade;

public record UnidadeCreateDTO(
        String nome,
        String codigo,
        String cnes,
        String telefone,
        String endereco) {
}
