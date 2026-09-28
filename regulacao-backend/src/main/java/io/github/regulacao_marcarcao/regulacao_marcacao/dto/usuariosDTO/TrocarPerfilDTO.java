package io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import jakarta.validation.constraints.NotNull;

/** Perfil que o usuario quer assumir na alternancia. */
public record TrocarPerfilDTO(
    @NotNull(message = "Informe o perfil que deseja assumir.")
    Roles perfil
) {
}
