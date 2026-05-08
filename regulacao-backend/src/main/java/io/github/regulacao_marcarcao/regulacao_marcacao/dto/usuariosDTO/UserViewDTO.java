package io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;

public record UserViewDTO(
    Long id,
    String cpf,
    String nome,
    Roles role,
    String fotoUrl,
    boolean ativo
) {

    public static UserViewDTO from(User user) {
        return new UserViewDTO(
            user.getId(),
            user.getUsername(),
            user.getNome(),
            user.getRole(),
            user.getFotoPerfil(),
            user.isAtivo()
        );
    }
}
