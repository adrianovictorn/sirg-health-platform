package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EspecialidadeRepository extends JpaRepository<Especialidade, Long> {
    Optional<Especialidade> findByCodigo(String codigo);
    Optional<Especialidade> findByNome(String nome);
    List<Especialidade> findByCodigoIn(Collection<String> codigos);
    List<Especialidade> findByNomeIn(Collection<String> nomes);
    List<Especialidade> findByCategoria(ItemCategoria categoria);
    List<Especialidade> findByCategoriaAndAtivoTrue(ItemCategoria categoria);
}

