package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "especialidade", uniqueConstraints = {
        @UniqueConstraint(name = "uk_especialidade_codigo", columnNames = {"codigo"}),
        @UniqueConstraint(name = "uk_especialidade_nome", columnNames = {"nome"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Especialidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Código estável (migração do enum name)
    @Column(nullable = false, length = 150)
    private String codigo;

    // Nome de exibição (migração do getDescricao)
    @Column(nullable = false, length = 255)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ItemCategoria categoria;

    @Column(nullable = false)
    private Boolean ativo = true;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_relatorio_id", nullable = true)
    private GrupoRelatorio grupoRelatorio;

    @Column(name = "vagas", nullable = false)
    private Integer vagas = 0;
}

