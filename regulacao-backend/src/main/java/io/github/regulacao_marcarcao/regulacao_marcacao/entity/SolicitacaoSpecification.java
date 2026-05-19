
package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import org.springframework.data.jpa.domain.Specification;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoListFiltersDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.EspecialidadesEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.List;

public class SolicitacaoSpecification {

    public static Specification<Solicitacao> filtrarPorNomePaciente(String nomePaciente) {
        return (root, query, criteriaBuilder) -> (nomePaciente == null || nomePaciente.isBlank())
                ? null
                : criteriaBuilder.like(criteriaBuilder.lower(root.get("nomePaciente")),
                        "%" + nomePaciente.toLowerCase() + "%");
    }

    public static Specification<Solicitacao> filtrarPorEspecialidade(List<EspecialidadesEnum> especialidades) {
        return (root, query, cb) -> {
            if (especialidades == null || especialidades.isEmpty()) {
                return null;
            }
            List<String> codigos = especialidades.stream().map(Enum::name).toList();
            Join<Object, Object> especialidadesJoin = root.join("especialidades");
            return especialidadesJoin.get("especialidadeSolicitada").get("codigo").in(codigos);
        };
    }

    public static Specification<Solicitacao> filtrarPorStatus(StatusDaMarcacao status) {
        return (root, query, criteriaBuilder) -> (status == null) ? null
                : criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Solicitacao> filtrarPorPrioridade(List<PrioridadeDaMarcacaoEnum> prioridades) {
        return (root, query, criteriaBuilder) -> (prioridades == null || prioridades.isEmpty())
                ? null
                : root.get("prioridade").in(prioridades);
    }

    public static Specification<Solicitacao> filtrarPorDataMalote(LocalDate dataInicio, LocalDate dataFim) {
        return (root, query, criteriaBuilder) -> {
            if (dataInicio == null && dataFim == null) {
                return null;
            }
            if (dataInicio != null && dataFim != null) {
                return criteriaBuilder.between(root.get("dataMalote"), dataInicio, dataFim);
            }
            if (dataInicio != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get("dataMalote"), dataInicio);
            }
            return criteriaBuilder.lessThanOrEqualTo(root.get("dataMalote"), dataFim);
        };
    }

    public static Specification<Solicitacao> filtrarPorUnidade(Long unidadeId) {
        return (root, query, cb) -> {
            if (unidadeId == null) return null;
            return cb.equal(root.get("unidade").get("id"), unidadeId);
        };
    }

    /**
     * Filtra por unidade com fallback para registros legados sem unidade_id.
     * Preserva compatibilidade com dados anteriores à migração V73.
     */
    public static Specification<Solicitacao> filtrarPorUnidadeComFallback(Long unidadeId, String codigoLegado) {
        return (root, query, cb) -> {
            if (unidadeId == null) return null;
            Predicate porUnidade = cb.equal(root.get("unidade").get("id"), unidadeId);
            if (codigoLegado != null && !codigoLegado.isBlank()) {
                Predicate legado = cb.and(
                    cb.isNull(root.get("unidade")),
                    cb.equal(root.get("usfOrigem").as(String.class), codigoLegado)
                );
                return cb.or(porUnidade, legado);
            }
            return porUnidade;
        };
    }

    public static Specification<Solicitacao> aplicarFiltros(SolicitacaoListFiltersDTO filtros) {
        return Specification.where(filtrarPorNomePaciente(filtros.nomePaciente()))
                .and(filtrarPorUnidade(filtros.unidadeId()))
                .and(filtrarPorEspecialidade(filtros.especialidadeSolicitada()))
                .and(filtrarPorStatus(filtros.status()))
                .and(filtrarPorPrioridade(filtros.prioridade()))
                .and(filtrarPorDataMalote(filtros.datainicio(), filtros.dataFim()));
    }
}
