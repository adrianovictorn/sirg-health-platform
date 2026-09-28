package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ocupacao do profissional no padrao CBO 2002 (V87).
 *
 * <p>A tabela nasce vazia: os registros entram pela importacao do CSV do CNES
 * (codigo e descricao vindos do DATASUS) ou por cadastro avulso do
 * administrador. Nao ha seed com codigos digitados a mao — um CBO errado rotula
 * o profissional com a ocupacao de outra pessoa e nada acusa o erro.
 */
@Entity
@Table(name = "cbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cbo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 6 digitos do CBO 2002. Unico — e a chave do upsert na importacao. */
    @Column(name = "codigo", nullable = false, length = 6, unique = true)
    private String codigo;

    @Column(name = "descricao", nullable = false, length = 255)
    private String descricao;

    /** Desativa em vez de excluir: o vinculo que aponta para o CBO segue legivel. */
    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}
