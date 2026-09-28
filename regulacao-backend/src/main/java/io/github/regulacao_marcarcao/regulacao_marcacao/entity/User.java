package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "usuarios")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cpf", length = 15, unique = true, nullable = false)
    private String cpf;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "senha", nullable = false)
    private String password;

    /**
     * Perfil PRINCIPAL: o que vale no login e o fallback para tokens emitidos
     * antes da v1.7 (que nao carregam perfil ativo). Continua sendo a coluna
     * `cargo`, intocada — o conjunto de perfis concedidos vive em {@link #perfis}.
     */
    @Column(name = "cargo", nullable = false)
    @Enumerated(EnumType.STRING)
    private Roles role;

    /**
     * Perfis CONCEDIDos a este usuario. Sempre contem o principal.
     *
     * A alternancia de perfil nao grava nada aqui: o perfil em uso viaja no JWT
     * e e conferido contra este conjunto a cada requisicao. Assim, tirar um
     * perfil de alguem tem efeito imediato, sem esperar o token expirar.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_perfis", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "perfil", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Roles> perfis = new LinkedHashSet<>();

    /**
     * Todos os perfis que este usuario pode assumir, com o principal garantido.
     *
     * Usuario anterior a V85 (ou criado sem perfis) cai no principal — nunca
     * fica sem nenhum, que o deixaria sem acesso a nada.
     */
    public Set<Roles> getPerfisConcedidos() {
        Set<Roles> todos = new LinkedHashSet<>();
        if (role != null) {
            todos.add(role);
        }
        if (perfis != null) {
            todos.addAll(perfis);
        }
        return todos;
    }

    /** True se o usuario pode assumir o perfil informado. */
    public boolean podeAssumir(Roles perfil) {
        return perfil != null && getPerfisConcedidos().contains(perfil);
    }

    @Column(name = "foto_perfil")
    private String fotoPerfil;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_id", nullable = true)
    private Unidade unidade;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return cpf;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return ativo;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
