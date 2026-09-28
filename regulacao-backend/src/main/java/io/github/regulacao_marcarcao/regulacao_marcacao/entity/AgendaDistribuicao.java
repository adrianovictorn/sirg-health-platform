package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quantidade de vagas por ocorrencia que uma {@link Agenda} distribui para uma
 * unidade solicitante (V89) — o coracao da "multipla escolha de unidades" do
 * oficio. Uma agenda tem N distribuicoes, uma por unidade solicitante.
 */
@Entity
@Table(name = "agenda_distribuicao")
@Getter
@Setter
@NoArgsConstructor
public class AgendaDistribuicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agenda_id", nullable = false)
    private Agenda agenda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_solicitante_id", nullable = false)
    private Unidade unidadeSolicitante;

    @jakarta.persistence.Column(name = "vagas_por_ocorrencia", nullable = false)
    private Integer vagasPorOcorrencia;
}
