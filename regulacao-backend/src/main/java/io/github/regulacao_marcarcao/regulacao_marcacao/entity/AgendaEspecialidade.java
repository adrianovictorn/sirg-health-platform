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
 * Um exame ofertado por uma {@link Agenda} (V89).
 *
 * <p>Agenda {@code INDIVIDUAL} tem exatamente uma linha. Agenda {@code GRUPO}
 * tem N linhas — o subconjunto de exames do grupo que a coordenacao marcou
 * (R8). A cota gerada continua escopada no {@code grupoEspecialidades} da
 * agenda (saldo unico), exatamente como a cota manual por grupo (V84); esta
 * tabela e informativa — descreve o que esta sendo ofertado, nao cria um novo
 * mecanismo de saldo por subconjunto.
 */
@Entity
@Table(name = "agenda_especialidade")
@Getter
@Setter
@NoArgsConstructor
public class AgendaEspecialidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agenda_id", nullable = false)
    private Agenda agenda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especialidade_id", nullable = false)
    private Especialidade especialidade;
}
