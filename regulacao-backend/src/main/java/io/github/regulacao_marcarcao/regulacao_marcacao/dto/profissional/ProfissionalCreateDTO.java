package io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional;

public record ProfissionalCreateDTO(
        String nome,
        String conselho,
        String numeroRegistro,
        String especialidadeAtuacao,
        String telefone,
        Long unidadeId) {
}
