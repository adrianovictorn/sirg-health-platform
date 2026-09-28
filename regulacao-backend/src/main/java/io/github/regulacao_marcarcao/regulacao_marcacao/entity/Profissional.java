package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "profissional")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Profissional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    /**
     * CPF sem mascara (V88). Chave de deduplicacao da importacao do CNES: e por
     * ele que se decide entre criar profissional novo ou so acrescentar vinculo.
     *
     * <p>Nulo nos profissionais cadastrados antes da V88 — exigir o campo agora
     * travaria a edicao de todos eles. UNIQUE parcial (WHERE NOT NULL) no banco.
     */
    @Column(name = "cpf", length = 11, unique = true)
    private String cpf;

    @Column(name = "conselho", length = 20)
    private String conselho;

    @Column(name = "numero_registro", length = 50)
    private String numeroRegistro;

    @Column(name = "especialidade_atuacao", length = 200)
    private String especialidadeAtuacao;

    @Column(name = "telefone", length = 20)
    private String telefone;

    /**
     * Unidade unica do profissional.
     *
     * @deprecated desde a V88, substituido por {@link ProfissionalVinculo}, que
     *             permite N estabelecimentos com CBO proprio em cada um. A coluna
     *             continua populada e as telas atuais ainda leem dela; a V88
     *             copiou cada valor para um vinculo (cbo nulo, origem MANUAL).
     *             Remover so depois de migrar os pontos de leitura — apagar junto
     *             com a criacao do vinculo quebraria as telas em producao.
     */
    @Deprecated
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_id", nullable = true)
    private Unidade unidade;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
