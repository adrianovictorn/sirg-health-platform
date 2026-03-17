package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EspecialidadeRepository extends JpaRepository<Especialidade, Long> {
    Optional<Especialidade> findByCodigo(String codigo);
    Optional<Especialidade> findByNome(String nome);
    List<Especialidade> findByCodigoIn(Collection<String> codigos);
    List<Especialidade> findByNomeIn(Collection<String> nomes);
    List<Especialidade> findByCategoria(ItemCategoria categoria);
    List<Especialidade> findByGrupoRelatorioCodigoIgnoreCase(String codigo);
    List<Especialidade> findByCategoriaAndAtivoTrue(ItemCategoria categoria);
    Page<Especialidade> findAll(Pageable pageable);
    Page<Especialidade> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
 
    @Query(value = """
           SELECT 
                nome
            FROM especialidade
            ORDER BY nome ASC
            """, nativeQuery = true)
    Page<String> listarNomeDasEspecialidades(Pageable pageable);


    @Query(value = """
           SELECT 
                nome
            FROM especialidade
            WHERE nome ILIKE %:termo%
            ORDER BY nome ASC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM especialidade
            WHERE nome ILIKE %:termo%        
            """,
            nativeQuery = true)
    Page<String> listarNomeComFiltroDasEspecialidades(Pageable pageable, @Param("termo") String termo);
   
   
    @Query(value = """
            SELECT 
                nome
            FROM especialidade
            """, nativeQuery = true)
    List<String> listarTodosNomesDasEspecialidades();
}

