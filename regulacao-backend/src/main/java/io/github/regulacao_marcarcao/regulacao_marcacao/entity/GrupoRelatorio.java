package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "grupo_relatorio")
@Data
public class GrupoRelatorio {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "codigo", nullable = false, length = 155)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 155)
    private String nome;

    @Column(name = "ativo")
    private Boolean ativo;

    @Column(name = "direcionado_hospital", nullable = false)
    private boolean direcionadoHospital = false;

    @OneToMany(mappedBy = "grupoRelatorio",fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Especialidade> especialidades;
}
