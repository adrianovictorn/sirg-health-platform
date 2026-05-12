package io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;

public record UserViewDTO(
    Long id,
    String cpf,
    String nome,
    Roles role,
    String fotoUrl,
    boolean ativo,
    Long unidadeId,
    String unidadeNome
) {

    public static UserViewDTO from(User user) {
        return new UserViewDTO(
            user.getId(),
            user.getUsername(),
            user.getNome(),
            user.getRole(),
            user.getFotoPerfil(),
            user.isAtivo(),
            user.getUnidade() != null ? user.getUnidade().getId() : null,
            user.getUnidade() != null ? user.getUnidade().getNome() : null
        );
    }
}
