package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioFiltroDTO;
import jakarta.persistence.criteria.Predicate;

/** Filtros de /cadastrar/grupo-relatorio no servidor. {@code f} já vem validado por {@code GrupoRelatorioService}. */
public final class GrupoRelatorioSpecification {

    private GrupoRelatorioSpecification() {
    }

    public static Specification<GrupoRelatorio> comFiltros(GrupoRelatorioFiltroDTO f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.q() != null) {
                for (String termo : f.q().toLowerCase(Locale.ROOT).split("\\s+")) {
                    String padrao = "%" + SpecificationTexto.escaparLike(termo) + "%";
                    ps.add(cb.or(
                            cb.like(cb.lower(root.get("nome")), padrao, '\\'),
                            cb.like(cb.lower(root.get("codigo")), padrao, '\\')));
                }
            }
            if ("ativo".equals(f.status())) {
                ps.add(cb.isTrue(root.get("ativo")));
            } else if ("inativo".equals(f.status())) {
                ps.add(cb.isFalse(root.get("ativo")));
            }
            if ("sim".equals(f.direcionadoHospital())) {
                ps.add(cb.isTrue(root.get("direcionadoHospital")));
            } else if ("nao".equals(f.direcionadoHospital())) {
                ps.add(cb.isFalse(root.get("direcionadoHospital")));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
