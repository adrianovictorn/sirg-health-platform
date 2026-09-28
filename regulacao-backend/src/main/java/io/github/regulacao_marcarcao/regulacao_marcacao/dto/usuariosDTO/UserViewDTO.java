package io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO;

import java.util.List;

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
    String unidadeNome,
    /**
     * Todos os perfis que este usuario pode assumir. Contem sempre o principal
     * (`role`). Com mais de um, o frontend mostra o seletor de alternancia.
     */
    List<Roles> perfis
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
            user.getUnidade() != null ? user.getUnidade().getNome() : null,
            List.copyOf(user.getPerfisConcedidos())
        );
    }
}
