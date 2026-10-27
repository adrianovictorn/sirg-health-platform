package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/** Filtros de /admin/unidades no servidor. {@code f} já vem validado por {@code UnidadeService}. */
public final class UnidadeSpecification {

    private UnidadeSpecification() {
    }

    public static Specification<Unidade> comFiltros(UnidadeFiltroDTO f, TipoUnidadeEnum tipo) {
        return (root, query, cb) -> {
            // A listagem lê o nome do grupo: busca junto para não gerar uma consulta por linha.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("grupoRelatorio", JoinType.LEFT);
            }

            List<Predicate> ps = new ArrayList<>();
            if (f.q() != null) {
                for (String termo : f.q().toLowerCase(Locale.ROOT).split("\\s+")) {
                    String padrao = "%" + SpecificationTexto.escaparLike(termo) + "%";
                    ps.add(cb.or(
                            cb.like(cb.lower(root.get("nome")), padrao, '\\'),
                            cb.like(cb.lower(root.get("codigo")), padrao, '\\'),
                            cb.like(cb.lower(root.get("cnes")), padrao, '\\')));
                }
            }
            if ("ativa".equals(f.status())) {
                ps.add(cb.isTrue(root.get("ativo")));
            } else if ("inativa".equals(f.status())) {
                ps.add(cb.isFalse(root.get("ativo")));
            }
            if (tipo != null) {
                ps.add(cb.equal(root.get("tipo"), tipo));
            }
            if (f.grupoRelatorioId() != null) {
                ps.add(cb.equal(root.get("grupoRelatorio").get("id"), f.grupoRelatorioId()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
