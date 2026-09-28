package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoOfertaAgendaEnum;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Oferta de atendimento de um profissional num estabelecimento executante
 * (V89). Descreve ONDE, QUANDO, COM QUEM e EM QUE HORARIO o atendimento
 * acontece, e distribui vagas entre as unidades solicitantes.
 *
 * <p>A agenda nao e o motor de saldo — ela GERA {@link CotaUnidade} por
 * ocorrencia (ver {@link AgendaOcorrencia}), reaproveitando o consumo, o
 * estorno e o optimistic locking ja em producao. Ver docs/especificacoes/
 * Agenda e Oferta.md, secoes 3.2 e 4.4.
 */
@Entity
@Table(name = "agenda")
@Getter
@Setter
@NoArgsConstructor
public class Agenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Prestador que realiza o atendimento. Precisa ter tipo EXECUTANTE ou AMBOS. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estabelecimento_executante_id", nullable = false)
    private Unidade estabelecimentoExecutante;

    /** Responsavel pelo atendimento. Precisa ter vinculo ativo com o executante. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    /** Ocupacao do profissional nesta oferta. Opcional, sugerido pelo vinculo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cbo_id")
    private Cbo cbo;

    /** Local do atendimento, quando cadastrado como local formal (R7). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_agendamento_id")
    private LocalAgendamento localAgendamento;

    /** Texto livre que sobrescreve o endereco do executante (caso do laboratorio, R7). */
    @Column(name = "local_descricao", length = 255)
    private String localDescricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_oferta", nullable = false, length = 20)
    private TipoOfertaAgendaEnum tipoOferta;

    /** Preenchido apenas quando {@code tipoOferta = GRUPO}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_especialidades_id")
    private GrupoRelatorio grupoEspecialidades;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim", nullable = false)
    private LocalDate vigenciaFim;

    /** Dias da semana marcados, ex.: {@code "TER,QUI"}. Data unica repete o mesmo dia. */
    @Column(name = "dias_semana", nullable = false, length = 20)
    private String diasSemana;

    @Column(name = "hora_inicial", nullable = false)
    private LocalTime horaInicial;

    @Column(name = "hora_final", nullable = false)
    private LocalTime horaFinal;

    /**
     * Reservado para a fase de slots individuais — sempre nulo nesta entrega
     * (decisao 3.3 da especificacao).
     */
    @Column(name = "minutos_por_atendimento")
    private Integer minutosPorAtendimento;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private User criadoPor;

    @OneToMany(mappedBy = "agenda", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AgendaEspecialidade> especialidades = new ArrayList<>();

    @OneToMany(mappedBy = "agenda", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AgendaDistribuicao> distribuicoes = new ArrayList<>();

    @OneToMany(mappedBy = "agenda", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("data ASC")
    private List<AgendaOcorrencia> ocorrencias = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Version
    private Long version;
}
