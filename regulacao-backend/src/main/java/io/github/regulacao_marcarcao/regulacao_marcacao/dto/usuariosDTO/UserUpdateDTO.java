package io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO;

import java.util.Set;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;

public record UserUpdateDTO(
    String nome,
    String cpf,
    String password,
    /** Perfil principal — o do login. */
    Roles role,
    /**
     * Perfis adicionais liberados para alternancia. Nulo mantem so o principal,
     * que e como o formulario antigo (um cargo so) continua funcionando.
     */
    Set<Roles> perfis,
    Long unidadeId
) {
}
