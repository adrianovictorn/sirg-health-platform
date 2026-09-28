package io.github.regulacao_marcarcao.regulacao_marcacao.dto.usuariosDTO;

import java.util.Set;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// MUDANÇA: Convertido de 'record' para 'class' com anotações Lombok
@Getter
@Setter
public class UserCreateDTO {

    @NotBlank(message = "CPF não pode ser vazio")
    private String cpf;

    @NotBlank(message = "Nome não pode ser vazio")
    private String nome;

    @NotBlank(message = "Crie uma senha forte!")
    @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres")
    private String password;

    @NotNull(message = "O perfil (role) é obrigatório")
    private Roles cargo;

    /**
     * Perfis adicionais liberados para alternancia. Nulo mantem so o `cargo`,
     * que e como o formulario antigo (um cargo so) continua funcionando.
     */
    private Set<Roles> perfis;

    private Long unidadeId;
}