package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.LocalDeAgendamentoEnum;
import jakarta.persistence.FetchType;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agendamento_solicitacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendamentoSolicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
        @Enumerated(EnumType.STRING)
    @Column(name = "local_agendado")
    private LocalDeAgendamentoEnum localAgendado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_agendamento_id")
    private LocalAgendamento localAgendamento;
    
    @Column(name = "data_agendada", nullable = false)
    private LocalDate dataAgendada;
    
    /**
     * Data em que o paciente foi marcado antes da primeira migracao de data da
     * cota (V108). Nula = o agendamento nunca foi migrado. Nao vai a nenhum DTO.
     */
    @Column(name = "data_agendada_original")
    private LocalDate dataAgendadaOriginal;

    @Column(name = "observacoes", length = 500)
    private String observacoes;

    @OneToMany(mappedBy = "agendamentoSolicitacao", fetch = FetchType.LAZY)
    private List<SolicitacaoEspecialidade> especialidades;
    

    @ManyToOne()
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;

    @Column(name = "turno")
    @Enumerated(EnumType.STRING)
    private TurnoEnum turno;


    @CreationTimestamp
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    /** Operador que criou este agendamento (V93). Nulo em agendamentos anteriores. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private User criadoPor;

}


