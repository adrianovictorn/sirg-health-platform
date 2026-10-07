package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "solicitacao_especialidade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SolicitacaoEspecialidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;

    @ManyToOne
    @JoinColumn(name = "agendamento_id")
    private AgendamentoSolicitacao agendamentoSolicitacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especialidade_id")
    private Especialidade especialidadeSolicitada;

    // Campo legado (enum como string). Mantido temporariamente para migração e compatibilidade
    @Column(name = "especialidade_solicitada")
    private String especialidadeCodigoLegacy;

    // Profissional que originou a solicitação (opcional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id", nullable = true)
    private Profissional profissionalSolicitante;

    /**
     * Cota efetivamente usada para agendar esta especialidade (V93) —
     * rastreabilidade quando ha mais de uma cota compativel (ex.: dois
     * profissionais da mesma especialidade no mesmo dia/horario). Nula
     * quando a especialidade nao esta agendada ou foi agendada sem cota
     * (ADMIN sem cota liberada).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cota_unidade_id", nullable = true)
    private CotaUnidade cotaUnidade;

    /** Operador que adicionou esta especialidade a solicitacao (V93). Nulo em registros anteriores. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id", nullable = true)
    private User criadoPor;

    // Data da coleta do material (exames/procedimentos). Opcional (V81).
    @Column(name = "data_coleta")
    private LocalDate dataColeta;

    /**
     * Horario efetivo do paciente para este exame/consulta (V97) — calculado
     * automaticamente quando a cota usada tem horario dinamico, ou informado a
     * mao pelo operador quando nao tem. Nulo em registros anteriores e quando
     * o operador nao informa hora numa cota sem horario dinamico.
     */
    @Column(name = "hora_agendada")
    private LocalTime horaAgendada;

    /**
     * Profissional que efetivamente atende esta especialidade/paciente (V100),
     * informado pelo operador no momento do agendamento — sobrescreve, so para
     * este agendamento, o profissional espelhado de {@code cotaUnidade}
     * (CotaUnidade.profissionalExecutante). Distinto de
     * {@link #profissionalSolicitante}, que e quem PEDIU o exame. Nulo quando
     * o operador nao sobrescreve (usa o da cota) ou a cota tambem nao define.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_executante_id", nullable = true)
    private Profissional profissionalExecutante;

    /**
     * Preco da especialidade no momento do agendamento (V106). O painel de
     * custos soma este valor, nao o preco atual do catalogo, para que um
     * reajuste nao altere periodos passados. Nulo quando o item nao esta
     * agendado, quando a especialidade nao tinha preco ou em agendamentos
     * anteriores a V106. So sai pelos endpoints de /api/custos.
     */
    @Column(name = "valor_unitario_agendado", precision = 12, scale = 2)
    private BigDecimal valorUnitarioAgendado;

    /**
     * Teto financeiro que este item debitou ao ser agendado (V107). O estorno
     * devolve exatamente a ele, sem recalcular. Nulo quando nao havia teto para
     * a unidade/grupo/mes ou o item nao tinha preco.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teto_financeiro_id", nullable = true)
    private TetoFinanceiro tetoFinanceiro;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private StatusDaMarcacao status;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade")
    private PrioridadeDaMarcacaoEnum prioridade;

    @CreationTimestamp
    @Column(name = "data_cadastro")
    private LocalDateTime dataDeCadastro;
}
