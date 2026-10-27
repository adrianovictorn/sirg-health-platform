package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * Filtros de /admin/cotas no servidor — as mesmas regras de {@code filtrarCotas}
 * (cotasFiltro.js), que deixou de filtrar no navegador.
 *
 * Todos os joins são LEFT: cota de grupo tem {@code unidade_id} nulo e cota geral
 * tem {@code especialidade_id} nulo; um INNER JOIN as descartaria em silêncio.
 */
public final class CotaUnidadeSpecification {

    private CotaUnidadeSpecification() {
    }

    /**
     * @param grupoDaUnidadeId       grupo da unidade filtrada (cotas do grupo também limitam a unidade)
     * @param grupoDaEspecialidadeId grupo da especialidade filtrada (cotas do grupo também incidem sobre ela)
     */
    public static Specification<CotaUnidade> comFiltros(CotaUnidadeFiltroDTO f, Long grupoDaUnidadeId,
            Long grupoDaEspecialidadeId) {
        return (root, query, cb) -> {
            Join<CotaUnidade, Unidade> unidade = root.join("unidade", JoinType.LEFT);
            Join<CotaUnidade, GrupoRelatorio> grupoUnidades = root.join("grupoUnidades", JoinType.LEFT);
            Join<CotaUnidade, Especialidade> especialidade = root.join("especialidade", JoinType.LEFT);
            Join<CotaUnidade, GrupoRelatorio> grupoEsp = root.join("grupoEspecialidades", JoinType.LEFT);
            Join<CotaUnidade, Profissional> profExec = root.join("profissionalExecutante", JoinType.LEFT);
            Join<CotaUnidade, LocalAgendamento> local = root.join("localAgendamento", JoinType.LEFT);
            Join<CotaUnidade, AgendaOcorrencia> ocorrencia = root.join("agendaOcorrencia", JoinType.LEFT);
            Join<AgendaOcorrencia, Agenda> agenda = ocorrencia.join("agenda", JoinType.LEFT);
            Join<Agenda, Profissional> profAgenda = agenda.join("profissional", JoinType.LEFT);
            Join<Agenda, Unidade> executante = agenda.join("estabelecimentoExecutante", JoinType.LEFT);

            // Cota de agenda: profissional e executante moram na agenda.
            Predicate deAgenda = cb.and(
                    cb.equal(root.get("origem"), OrigemCotaEnum.AGENDA),
                    cb.isNotNull(ocorrencia.get("id")));

            List<Predicate> ps = new ArrayList<>();

            if (f.unidadeId() != null) {
                List<Predicate> ou = new ArrayList<>();
                ou.add(cb.equal(unidade.get("id"), f.unidadeId()));
                if (grupoDaUnidadeId != null) {
                    ou.add(cb.equal(grupoUnidades.get("id"), grupoDaUnidadeId));
                }
                ps.add(cb.or(ou.toArray(new Predicate[0])));
            }

            if (f.especialidadeId() != null) {
                List<Predicate> ou = new ArrayList<>();
                ou.add(cb.equal(especialidade.get("id"), f.especialidadeId()));
                if (grupoDaEspecialidadeId != null) {
                    ou.add(cb.equal(grupoEsp.get("id"), grupoDaEspecialidadeId));
                }
                // Cota geral incide sobre qualquer especialidade.
                ou.add(cb.and(cb.isNull(especialidade.get("id")), cb.isNull(grupoEsp.get("id"))));
                ps.add(cb.or(ou.toArray(new Predicate[0])));
            }

            if ("ativa".equals(f.status())) {
                ps.add(cb.isTrue(root.get("ativo")));
            } else if ("inativa".equals(f.status())) {
                ps.add(cb.isFalse(root.get("ativo")));
            }

            if (f.saldo() != null) {
                Expression<Integer> saldo = cb.diff(root.<Integer>get("quantidadeTotal"),
                        root.<Integer>get("quantidadeUtilizada"));
                ps.add("esgotada".equals(f.saldo()) ? cb.le(saldo, 0) : cb.gt(saldo, 0));
            }

            if (f.origem() != null) {
                Predicate agendaOrigem = cb.equal(root.get("origem"), OrigemCotaEnum.AGENDA);
                ps.add("agenda".equals(f.origem()) ? agendaOrigem : cb.not(agendaOrigem));
            }

            Predicate porData = cb.equal(root.get("tipoPeriodo"), TipoPeriodoCota.DATA);
            if (f.tipo() != null) {
                ps.add("data".equals(f.tipo()) ? porData : cb.not(porData));
            }

            if (f.profissionalId() != null) {
                ps.add(cb.or(
                        cb.and(deAgenda, cb.equal(profAgenda.get("id"), f.profissionalId())),
                        cb.and(cb.not(deAgenda), cb.equal(profExec.get("id"), f.profissionalId()))));
            }

            if (f.executanteId() != null) {
                // Cota manual nunca casa: o executante só existe na agenda.
                ps.add(cb.and(deAgenda, cb.equal(executante.get("id"), f.executanteId())));
            }

            Predicate semData = cb.or(
                    cb.and(porData, cb.isNull(root.get("dataEspecifica"))),
                    cb.and(cb.not(porData), cb.isNull(root.get("periodo"))));

            if (f.competencia() != null) {
                YearMonth mes = YearMonth.parse(f.competencia());
                // Cota sem data reconhecida continua visível: não esconder dado legado.
                ps.add(cb.or(
                        semData,
                        cb.and(porData, cb.between(root.<LocalDate>get("dataEspecifica"),
                                mes.atDay(1), mes.atEndOfMonth())),
                        cb.and(cb.not(porData), cb.equal(root.get("periodo"), f.competencia()))));
            }

            if (f.dataDe() != null || f.dataAte() != null) {
                List<Predicate> dentroData = new ArrayList<>();
                List<Predicate> dentroMes = new ArrayList<>();
                if (f.dataDe() != null) {
                    dentroData.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("dataEspecifica"), f.dataDe()));
                    dentroMes.add(cb.greaterThanOrEqualTo(root.<String>get("periodo"),
                            YearMonth.from(f.dataDe()).toString()));
                }
                if (f.dataAte() != null) {
                    dentroData.add(cb.lessThanOrEqualTo(root.<LocalDate>get("dataEspecifica"), f.dataAte()));
                    dentroMes.add(cb.lessThanOrEqualTo(root.<String>get("periodo"),
                            YearMonth.from(f.dataAte()).toString()));
                }
                // Cota por data: a data cai no intervalo. Mensal: o mês cruza o intervalo.
                ps.add(cb.or(
                        semData,
                        cb.and(porData, cb.and(dentroData.toArray(new Predicate[0]))),
                        cb.and(cb.not(porData), cb.and(dentroMes.toArray(new Predicate[0])))));
            }

            if (f.q() != null && !f.q().isBlank()) {
                List<Expression<String>> campos = List.of(
                        unidade.<String>get("nome"), grupoUnidades.<String>get("nome"),
                        especialidade.<String>get("nome"), grupoEsp.<String>get("nome"),
                        profExec.<String>get("nome"), local.<String>get("nomeLocal"),
                        profAgenda.<String>get("nome"), executante.<String>get("nome"));
                // Todos os termos precisam aparecer em algum campo (caixa ignorada, acento não).
                for (String termo : f.q().trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                    String padrao = "%" + SpecificationTexto.escaparLike(termo) + "%";
                    List<Predicate> emAlgum = new ArrayList<>();
                    for (Expression<String> campo : campos) {
                        emAlgum.add(cb.like(cb.lower(campo), padrao, '\\'));
                    }
                    ps.add(cb.or(emAlgum.toArray(new Predicate[0])));
                }
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

}
