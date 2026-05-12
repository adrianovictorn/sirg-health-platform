package io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;

public record UserUpdateDTO(
    String nome,
    String cpf,
    String password,
    Roles role,
    Long unidadeId
) {
}