package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "local_agendamento")
public class LocalAgendamento {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_local")
    private String nomeLocal;

    @Column(name = "endereco")
    private String endereco;

    @Column(name = "numero")
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cidade_id")
    private Cidade cidade;

    @Column(name = "enum_value", unique = true)
    private String enumValue;

    // ------------------------------------------------------------------
    // Dados cadastrais do estabelecimento vindos do CNES (V91).
    // Mesmo padrao de Unidade (V86): preenchidos pela busca, editaveis a
    // mao, sempre opcionais — cadastro manual continua valendo sem eles.
    // ------------------------------------------------------------------

    @Column(name = "cnes", length = 20)
    private String cnes;

    @Column(name = "cnpj", length = 14)
    private String cnpj;

    @Column(name = "razao_social", length = 255)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 255)
    private String nomeFantasia;

    @Column(name = "bairro", length = 150)
    private String bairro;

    @Column(name = "cep", length = 8)
    private String cep;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "email", length = 150)
    private String email;

    /** Quando os dados vieram da API do CNES. Nulo = cadastro 100% manual. */
    @Column(name = "sincronizado_cnes_em")
    private LocalDateTime sincronizadoCnesEm;

}
