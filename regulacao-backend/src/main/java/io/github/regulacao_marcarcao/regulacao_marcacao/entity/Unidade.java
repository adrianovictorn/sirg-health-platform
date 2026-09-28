package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;
import jakarta.persistence.Column;
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
@Table(name = "unidade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Unidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "codigo", length = 50)
    private String codigo;

    @Column(name = "cnes", length = 20, unique = true)
    private String cnes;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "endereco", length = 300)
    private String endereco;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    // Papel da unidade: quem solicita, quem executa, ou os dois (V86).
    // A agenda so aceita como executante uma unidade com tipo EXECUTANTE ou AMBOS.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoUnidadeEnum tipo = TipoUnidadeEnum.AMBOS;

    // ------------------------------------------------------------------
    // Dados cadastrais do estabelecimento (V86).
    // Preenchidos automaticamente a partir do CNES quando a API do DATASUS
    // responde; editaveis a mao sempre. Ver CnesService.
    // ------------------------------------------------------------------

    @Column(name = "cnpj", length = 14)
    private String cnpj;

    @Column(name = "razao_social", length = 255)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 255)
    private String nomeFantasia;

    @Column(name = "numero", length = 20)
    private String numero;

    @Column(name = "bairro", length = 150)
    private String bairro;

    @Column(name = "cep", length = 8)
    private String cep;

    @Column(name = "email", length = 150)
    private String email;

    /** Quando os dados vieram da API do CNES. Nulo = cadastro 100% manual. */
    @Column(name = "sincronizado_cnes_em")
    private LocalDateTime sincronizadoCnesEm;

    // Grupo ao qual a unidade pertence (opcional). Habilita cotas coletivas (V82).
    // Reaproveita GrupoRelatorio: assim como uma Especialidade pertence a um grupo,
    // uma Unidade pertence a um grupo.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_relatorio_id", nullable = true)
    private GrupoRelatorio grupoRelatorio;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}
