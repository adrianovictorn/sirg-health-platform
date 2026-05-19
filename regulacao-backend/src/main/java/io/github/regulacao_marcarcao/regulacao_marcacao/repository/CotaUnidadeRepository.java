package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;

@Repository
public interface CotaUnidadeRepository extends JpaRepository<CotaUnidade, Long> {

    List<CotaUnidade> findByUnidadeIdAndPeriodo(Long unidadeId, String periodo);

    List<CotaUnidade> findByUnidadeId(Long unidadeId);

    // MENSAL – com especialidade
    @Query("SELECT c FROM CotaUnidade c WHERE c.unidade.id = :unidadeId AND c.especialidade.id = :especialidadeId AND c.periodo = :periodo AND c.tipoPeriodo = io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota.MENSAL")
    Optional<CotaUnidade> findByUnidadeEspecialidadePeriodo(
            @Param("unidadeId") Long unidadeId,
            @Param("especialidadeId") Long especialidadeId,
            @Param("periodo") String periodo);

    // MENSAL – geral (sem especialidade)
    @Query("SELECT c FROM CotaUnidade c WHERE c.unidade.id = :unidadeId AND c.especialidade IS NULL AND c.periodo = :periodo AND c.tipoPeriodo = io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota.MENSAL")
    Optional<CotaUnidade> findCotaGeralByUnidadePeriodo(
            @Param("unidadeId") Long unidadeId,
            @Param("periodo") String periodo);

    // DATA – com especialidade
    @Query("SELECT c FROM CotaUnidade c WHERE c.unidade.id = :unidadeId AND c.especialidade.id = :especialidadeId AND c.dataEspecifica = :data AND c.tipoPeriodo = io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota.DATA")
    Optional<CotaUnidade> findByUnidadeEspecialidadeData(
            @Param("unidadeId") Long unidadeId,
            @Param("especialidadeId") Long especialidadeId,
            @Param("data") LocalDate data);

    // DATA – geral (sem especialidade)
    @Query("SELECT c FROM CotaUnidade c WHERE c.unidade.id = :unidadeId AND c.especialidade IS NULL AND c.dataEspecifica = :data AND c.tipoPeriodo = io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota.DATA")
    Optional<CotaUnidade> findCotaGeralByUnidadeData(
            @Param("unidadeId") Long unidadeId,
            @Param("data") LocalDate data);
}
