package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "fechamento_indicadores_dia")
@Data
public class FechamentoIndicadoresDia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_id", nullable = false)
    private LocalAgendamento localAgendamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private GrupoRelatorio grupoRelatorio;

    @Column(name = "agendados_total", nullable = false)
    private int agendadosTotal;
    
    @Column(name = "atendidos_total", nullable = false)
    private int atendidosTotal;
    
    @Column(name = "faltosos_total", nullable = false)
    private int faltososTotal;

    @Column(name = "cancelados_total", nullable = false)
    private int canceladosTotal;

    @Column(name = "solicitacoes_cadastradas_total", nullable = false)
    private int solicitacoesCadastradasTotal;

    @Column(name = "fechado_em", nullable = false)
    private LocalDateTime fechadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fechado_por_user_id")
    private User fechadoPor;
}
