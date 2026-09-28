package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;

@Repository
public interface UnidadeRepository extends JpaRepository<Unidade, Long> {

    Optional<Unidade> findByNome(String nome);

    Optional<Unidade> findByCnes(String cnes);

    List<Unidade> findByAtivoTrue();

    /**
     * Unidades ativas que podem executar atendimento — o combo de estabelecimento
     * executante na abertura de agenda. Passa-se {@code EXECUTANTE} e {@code AMBOS}.
     */
    List<Unidade> findByAtivoTrueAndTipoInOrderByNomeAsc(List<TipoUnidadeEnum> tipos);

    List<Unidade> findByGrupoRelatorioId(Long grupoRelatorioId);
}
