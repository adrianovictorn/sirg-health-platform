package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    // Novo relacionamento para tabela de especialidades
    @ManyToOne
    @JoinColumn(name = "especialidade_id")
    private Especialidade especialidadeSolicitada;

    // Campo legado (enum como string). Mantido temporariamente para migração e compatibilidade
    @Column(name = "especialidade_solicitada")
    private String especialidadeCodigoLegacy;

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
