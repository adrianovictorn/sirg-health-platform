package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;

@Repository
public interface CotaUnidadeRepository extends JpaRepository<CotaUnidade, Long> {

    List<CotaUnidade> findByUnidadeIdAndPeriodo(Long unidadeId, String periodo);

    List<CotaUnidade> findByUnidadeId(Long unidadeId);

    @Query("SELECT c FROM CotaUnidade c WHERE c.unidade.id = :unidadeId AND c.especialidade.id = :especialidadeId AND c.periodo = :periodo")
    Optional<CotaUnidade> findByUnidadeEspecialidadePeriodo(
            @Param("unidadeId") Long unidadeId,
            @Param("especialidadeId") Long especialidadeId,
            @Param("periodo") String periodo);

    @Query("SELECT c FROM CotaUnidade c WHERE c.unidade.id = :unidadeId AND c.especialidade IS NULL AND c.periodo = :periodo")
    Optional<CotaUnidade> findCotaGeralByUnidadePeriodo(
            @Param("unidadeId") Long unidadeId,
            @Param("periodo") String periodo);
}
