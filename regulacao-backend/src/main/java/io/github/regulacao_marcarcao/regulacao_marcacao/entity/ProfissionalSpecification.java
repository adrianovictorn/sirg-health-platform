package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * Filtros de /admin/profissionais no servidor. O CPF nunca entra na busca: filtro
 * trafega na query string e vai parar em log de proxy e histórico do navegador.
 */
public final class ProfissionalSpecification {

    private ProfissionalSpecification() {
    }

    /**
     * @param nome  filtro histórico de {@code GET /api/profissionais/buscar}: só o nome
     * @param q     busca nova: nome, conselho, número de registro e especialidade de atuação
     * @param ativo {@code null} = todos
     */
    public static Specification<Profissional> comFiltros(String nome, String q, Boolean ativo, Long unidadeId) {
        return (root, query, cb) -> {
            // A listagem lê o nome da unidade: busca junto para não gerar uma consulta por linha.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("unidade", JoinType.LEFT);
            }

            List<Predicate> ps = new ArrayList<>();
            if (nome != null && !nome.isBlank()) {
                String padrao = "%" + SpecificationTexto.escaparLike(nome.trim().toLowerCase(Locale.ROOT)) + "%";
                ps.add(cb.like(cb.lower(root.get("nome")), padrao, '\\'));
            }
            if (q != null && !q.isBlank()) {
                for (String termo : q.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                    String padrao = "%" + SpecificationTexto.escaparLike(termo) + "%";
                    ps.add(cb.or(
                            cb.like(cb.lower(root.get("nome")), padrao, '\\'),
                            cb.like(cb.lower(root.get("conselho")), padrao, '\\'),
                            cb.like(cb.lower(root.get("numeroRegistro")), padrao, '\\'),
                            cb.like(cb.lower(root.get("especialidadeAtuacao")), padrao, '\\')));
                }
            }
            if (ativo != null) {
                ps.add(ativo ? cb.isTrue(root.get("ativo")) : cb.isFalse(root.get("ativo")));
            }
            if (unidadeId != null) {
                ps.add(cb.equal(root.get("unidade").get("id"), unidadeId));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
