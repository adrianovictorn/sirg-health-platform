package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;

/** Usuario que pode ser vinculado a uma executante (perfil USER concedido). Sem CPF. */
public record OperadorExecutanteViewDTO(Long id, String nome, String unidadeLotacao) {

    public static OperadorExecutanteViewDTO from(User user) {
        return new OperadorExecutanteViewDTO(user.getId(), user.getNome(),
                user.getUnidade() != null ? user.getUnidade().getNome() : null);
    }
}
