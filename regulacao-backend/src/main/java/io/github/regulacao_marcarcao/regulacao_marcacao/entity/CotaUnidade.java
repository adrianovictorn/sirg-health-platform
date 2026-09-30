package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cota_unidade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CotaUnidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------------------------------------------------------
    // TITULAR — quem detem a cota.
    // Exatamente um entre `unidade` e `grupoUnidades`
    // (CHECK ck_cota_titular_exclusivo).
    // ------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_id", nullable = true)
    private Unidade unidade;

    /** Grupo cujas UNIDADES compartilham a cota (pool). Vinculo em Unidade#grupoRelatorio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_unidades_id", nullable = true)
    private GrupoRelatorio grupoUnidades;

    // ------------------------------------------------------------------
    // ESCOPO — o que a cota limita.
    // No maximo um entre `especialidade` e `grupoEspecialidades`; ambos nulos
    // significa cota geral (CHECK ck_cota_escopo_exclusivo).
    // ------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especialidade_id", nullable = true)
    private Especialidade especialidade;

    /**
     * Grupo cujas ESPECIALIDADES a cota cobre, com saldo unico compartilhado
     * entre elas. Evita cadastrar uma cota por especialidade — o grupo
     * "Laboratorio", por exemplo, tem 172.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_especialidades_id", nullable = true)
    private GrupoRelatorio grupoEspecialidades;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_periodo", nullable = false, length = 10)
    private TipoPeriodoCota tipoPeriodo = TipoPeriodoCota.MENSAL;

    // Usado quando tipoPeriodo = MENSAL (formato YYYY-MM)
    @Column(name = "periodo", length = 7)
    private String periodo;

    // Usado quando tipoPeriodo = DATA
    @Column(name = "data_especifica")
    private LocalDate dataEspecifica;

    @Column(name = "quantidade_total", nullable = false)
    private Integer quantidadeTotal = 0;

    @Column(name = "quantidade_utilizada", nullable = false)
    private Integer quantidadeUtilizada = 0;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    // ------------------------------------------------------------------
    // ORIGEM — quem gerou esta cota (V90).
    // AGENDA nao e editavel na tela de cotas; edita-se a agenda de origem.
    // ------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agenda_ocorrencia_id", nullable = true)
    private AgendaOcorrencia agendaOcorrencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemCotaEnum origem = OrigemCotaEnum.MANUAL;

    // ------------------------------------------------------------------
    // ESPELHO DE ATENDIMENTO (V92) — profissional, horario e local que a
    // unidade deve reproduzir ao agendar. Todos opcionais: cota geral ou de
    // laboratorio continua sem eles, como antes desta versao. Quando
    // presentes, exigem tipoPeriodo = DATA (validado em CotaUnidadeService),
    // reaproveitando `dataEspecifica` em vez de uma coluna de data propria.
    // ------------------------------------------------------------------

    /** Quem atende — distinto de {@code SolicitacaoEspecialidade#profissionalSolicitante}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id", nullable = true)
    private Profissional profissionalExecutante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_agendamento_id", nullable = true)
    private LocalAgendamento localAgendamento;

    /**
     * true = horaInicial/horaFinal (ambos obrigatorios) sao divididos em
     * quantidadeTotal partes iguais, um horario calculado por vaga (ver
     * {@link io.github.regulacao_marcarcao.regulacao_marcacao.service.CotaUnidadeService#calcularHorarioSlot}).
     * false = horaInicial/horaFinal sao so um periodo livre, sem calculo.
     */
    @Column(name = "horario_dinamico", nullable = false)
    private boolean horarioDinamico = false;

    @Column(name = "tempo_medio_atendimento_minutos")
    private Integer tempoMedioAtendimentoMinutos;

    @Column(name = "hora_inicial")
    private LocalTime horaInicial;

    @Column(name = "hora_final")
    private LocalTime horaFinal;

    /**
     * Dias da semana em que o profissional atende (V95), para cota MENSAL —
     * substitui a data unica de DATA. Formato "SEG,QUA" (mesmo padrao de
     * {@link Agenda#getDiasSemana()}). Nulo = cota geral, sem restricao de dia.
     */
    @Column(name = "dias_semana", length = 20)
    private String diasSemana;

    @Version
    private Long version;
}
