package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaOcorrencia;

@Repository
public interface AgendaOcorrenciaRepository extends JpaRepository<AgendaOcorrencia, Long> {

    List<AgendaOcorrencia> findByAgendaId(Long agendaId);

    /**
     * R9: o mesmo profissional nao pode ter duas agendas ativas com horario
     * sobreposto na mesma data.
     *
     * <p>Duas faixas [inicioA, fimA) e [inicioB, fimB) se sobrepoem quando
     * {@code inicioA < fimB AND inicioB < fimA}. {@code excluirAgendaId} deixa a
     * propria agenda de fora da checagem, para permitir edita-la sem colidir
     * consigo mesma.
     */
    @Query("""
            SELECT COUNT(o) > 0 FROM AgendaOcorrencia o
            WHERE o.agenda.profissional.id = :profissionalId
              AND o.agenda.ativo = true
              AND o.status = io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusOcorrenciaEnum.ABERTA
              AND o.data = :data
              AND o.horaInicial < :horaFinal
              AND :horaInicial < o.horaFinal
              AND (:excluirAgendaId IS NULL OR o.agenda.id <> :excluirAgendaId)
            """)
    boolean existeSobreposicao(
            @Param("profissionalId") Long profissionalId,
            @Param("data") LocalDate data,
            @Param("horaInicial") LocalTime horaInicial,
            @Param("horaFinal") LocalTime horaFinal,
            @Param("excluirAgendaId") Long excluirAgendaId);
}
