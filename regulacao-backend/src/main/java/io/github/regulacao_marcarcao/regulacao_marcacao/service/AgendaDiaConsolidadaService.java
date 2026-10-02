package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaConsolidadaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaConsolidadaViewDTO.CotaDia;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaConsolidadaViewDTO.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaConsolidadaViewDTO.Grupo;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaConsolidadaViewDTO.Indicadores;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaConsolidadaViewDTO.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia.AgendaDiaItemViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendaDiaConsolidadaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AgendaDiaAgregadoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AgendaDiaCotaProjection;
import lombok.RequiredArgsConstructor;

/**
 * Agenda do Dia consolidada para o ADMIN global: todas as unidades numa visao so,
 * organizada em unidade -> grupo -> especialidade. Somente leitura — nao toca em
 * cota, saldo, agendamento nem status.
 */
@Service
@RequiredArgsConstructor
public class AgendaDiaConsolidadaService {

    static final ZoneId FUSO = ZoneId.of("America/Bahia");
    static final int TAMANHO_MAXIMO_PAGINA = 50;
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AgendaDiaConsolidadaRepository repository;
    private final UnidadeRepository unidadeRepository;
    private final UnidadeAcessoService unidadeAcessoService;

    @Transactional(readOnly = true)
    public AgendaDiaConsolidadaViewDTO consolidar(LocalDate data, Long unidadeId, Long grupoId,
            Long especialidadeId, String callerCpf) {
        unidadeAcessoService.exigirAdminGlobal(callerCpf);
        LocalDate dia = data != null ? data : LocalDate.now(FUSO);

        List<AgendaDiaAgregadoProjection> linhas = repository.agregarDia(dia, unidadeId, grupoId, especialidadeId);
        List<AgendaDiaCotaProjection> cotas = repository.listarCotasDoDia(dia, dia.format(PERIODO));

        Indicadores totais = Indicadores.ZERO;
        Map<Long, Grupo> gruposGeral = new LinkedHashMap<>();
        Map<Long, UnidadeNo> unidades = new LinkedHashMap<>();

        // Todas as unidades ativas aparecem, mesmo sem agendamento no dia (cota/vagas).
        unidadeRepository.findByAtivoTrue().stream()
                .filter(u -> unidadeId == null || u.getId().equals(unidadeId))
                .forEach(u -> unidades.put(u.getId(), new UnidadeNo(u.getId(), u.getNome(),
                        u.getGrupoRelatorio() != null ? u.getGrupoRelatorio().getId() : null)));

        for (AgendaDiaAgregadoProjection l : linhas) {
            boolean uAgg = flag(l.getUnidadeAgregada());
            boolean gAgg = flag(l.getGrupoAgregado());
            boolean eAgg = flag(l.getEspecialidadeAgregada());
            Indicadores ind = Indicadores.from(l);

            if (uAgg && gAgg) {
                totais = ind;
            } else if (uAgg) {
                gruposGeral.put(l.getGrupoId(), new Grupo(l.getGrupoId(), l.getGrupoCodigo(),
                        nomeGrupo(l.getGrupoNome(), l.getGrupoId()), ind, List.of(), List.of()));
            } else {
                UnidadeNo un = unidades.computeIfAbsent(l.getUnidadeId(), id -> new UnidadeNo(id,
                        id == null ? "Sem unidade" : nomeOu(l.getUnidadeNome(), "Unidade " + id), null));
                if (gAgg) {
                    un.indicadores = ind;
                } else if (eAgg) {
                    un.grupo(l.getGrupoId(), l.getGrupoCodigo(), nomeGrupo(l.getGrupoNome(), l.getGrupoId())).indicadores = ind;
                } else {
                    GrupoNo g = un.grupo(l.getGrupoId(), l.getGrupoCodigo(), nomeGrupo(l.getGrupoNome(), l.getGrupoId()));
                    g.especialidade(l.getEspecialidadeId(), l.getEspecialidadeNome()).indicadores = ind;
                }
            }
        }

        aplicarCotas(dia, cotas, unidades.values(), unidadeId, grupoId, especialidadeId);

        List<Unidade> resultado = unidades.values().stream()
                .filter(u -> especialidadeId == null && grupoId == null || !u.grupos.isEmpty())
                .sorted(Comparator.comparing((UnidadeNo u) -> u.id == null)
                        .thenComparing(u -> u.nome, String.CASE_INSENSITIVE_ORDER))
                .map(UnidadeNo::toDto)
                .toList();

        List<Grupo> geral = gruposGeral.values().stream()
                .sorted(Comparator.comparing((Grupo g) -> g.id() == null)
                        .thenComparing(Grupo::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new AgendaDiaConsolidadaViewDTO(dia, totais, geral, resultado);
    }

    @Transactional(readOnly = true)
    public Page<AgendaDiaItemViewDTO> listarPacientes(LocalDate data, Long especialidadeId, Long unidadeId,
            boolean semUnidade, int page, int size, String callerCpf) {
        unidadeAcessoService.exigirAdminGlobal(callerCpf);
        if (especialidadeId == null) {
            throw new IllegalArgumentException("Informe a especialidade para listar os pacientes.");
        }
        LocalDate dia = data != null ? data : LocalDate.now(FUSO);
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO_PAGINA);
        return repository
                .listarItensDoDia(dia, especialidadeId, unidadeId, semUnidade, PageRequest.of(Math.max(page, 0), tamanho))
                .map(AgendaDiaItemViewDTO::from);
    }

    // ------------------------------------------------------------------
    // Cotas
    // ------------------------------------------------------------------

    private void aplicarCotas(LocalDate dia, List<AgendaDiaCotaProjection> cotas, Iterable<UnidadeNo> unidades,
            Long unidadeId, Long grupoId, Long especialidadeId) {
        String sigla = sigla(dia.getDayOfWeek());
        for (AgendaDiaCotaProjection c : cotas) {
            String tipo = tipoDaCota(c, sigla);
            if (tipo == null) {
                continue;
            }
            boolean compartilhada = c.getGrupoUnidadesId() != null;
            for (UnidadeNo un : unidades) {
                if (un.id == null || !titularIncide(c, un) || (unidadeId != null && !unidadeId.equals(un.id))) {
                    continue;
                }
                int total = nz(c.getQuantidadeTotal());
                int utilizada = nz(c.getQuantidadeUtilizada());
                if (c.getEspecialidadeId() != null) {
                    Long gid = c.getEspecialidadeGrupoId();
                    if ((grupoId != null && !grupoId.equals(gid))
                            || (especialidadeId != null && !especialidadeId.equals(c.getEspecialidadeId()))) {
                        continue;
                    }
                    EspecialidadeNo e = un.grupo(gid, c.getEspecialidadeGrupoCodigo(),
                            nomeGrupo(c.getEspecialidadeGrupoNome(), gid))
                            .especialidade(c.getEspecialidadeId(), c.getEspecialidadeNome());
                    e.cotas.add(new CotaDia(c.getId(), tipo, "ESPECIALIDADE", c.getEspecialidadeNome(), compartilhada,
                            total, utilizada, Math.max(total - utilizada, 0)));
                } else if (c.getGrupoEspecialidadesId() != null) {
                    if ((grupoId != null && !grupoId.equals(c.getGrupoEspecialidadesId())) || especialidadeId != null) {
                        continue;
                    }
                    GrupoNo g = un.grupo(c.getGrupoEspecialidadesId(), c.getGrupoEspecialidadesCodigo(),
                            nomeGrupo(c.getGrupoEspecialidadesNome(), c.getGrupoEspecialidadesId()));
                    g.cotas.add(new CotaDia(c.getId(), tipo, "GRUPO", g.nome, compartilhada,
                            total, utilizada, Math.max(total - utilizada, 0)));
                } else {
                    if (grupoId != null || especialidadeId != null) {
                        continue;
                    }
                    un.cotas.add(new CotaDia(c.getId(), tipo, "GERAL", "Todas as especialidades", compartilhada,
                            total, utilizada, Math.max(total - utilizada, 0)));
                }
            }
        }
    }

    private static boolean titularIncide(AgendaDiaCotaProjection c, UnidadeNo un) {
        if (c.getUnidadeId() != null) {
            return c.getUnidadeId().equals(un.id);
        }
        return c.getGrupoUnidadesId() != null && c.getGrupoUnidadesId().equals(un.grupoUnidadesId);
    }

    /** DIA (cota da data), DIA_SEMANA (mensal que atende neste dia), MES (mensal sem dia) ou null (nao incide). */
    private static String tipoDaCota(AgendaDiaCotaProjection c, String sigla) {
        if ("DATA".equals(c.getTipoPeriodo())) {
            return "DIA";
        }
        String dias = c.getDiasSemana();
        if (dias == null || dias.isBlank()) {
            return "MES";
        }
        boolean atende = Arrays.stream(dias.split(",")).map(String::trim).anyMatch(sigla::equals);
        return atende ? "DIA_SEMANA" : null;
    }

    private static String sigla(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "SEG";
            case TUESDAY -> "TER";
            case WEDNESDAY -> "QUA";
            case THURSDAY -> "QUI";
            case FRIDAY -> "SEX";
            case SATURDAY -> "SAB";
            case SUNDAY -> "DOM";
        };
    }

    // ------------------------------------------------------------------
    // Arvore mutavel usada so durante a montagem
    // ------------------------------------------------------------------

    private static boolean flag(Integer v) {
        return v != null && v == 1;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String nomeOu(String nome, String padrao) {
        return nome != null && !nome.isBlank() ? nome : padrao;
    }

    private static String nomeGrupo(String nome, Long id) {
        return id == null ? "Sem grupo" : nomeOu(nome, "Grupo " + id);
    }

    private static final class UnidadeNo {
        final Long id;
        final String nome;
        final Long grupoUnidadesId;
        Indicadores indicadores = Indicadores.ZERO;
        final List<CotaDia> cotas = new ArrayList<>();
        final Map<Long, GrupoNo> grupos = new LinkedHashMap<>();

        UnidadeNo(Long id, String nome, Long grupoUnidadesId) {
            this.id = id;
            this.nome = nome;
            this.grupoUnidadesId = grupoUnidadesId;
        }

        GrupoNo grupo(Long gid, String codigo, String nomeGrupo) {
            return grupos.computeIfAbsent(gid, k -> new GrupoNo(gid, codigo, nomeGrupo));
        }

        Unidade toDto() {
            List<Grupo> gs = grupos.values().stream()
                    .sorted(Comparator.comparing((GrupoNo g) -> g.id == null)
                            .thenComparing(g -> g.nome, String.CASE_INSENSITIVE_ORDER))
                    .map(GrupoNo::toDto)
                    .toList();
            return new Unidade(id, nome, indicadores, List.copyOf(cotas), gs);
        }
    }

    private static final class GrupoNo {
        final Long id;
        final String codigo;
        final String nome;
        Indicadores indicadores = Indicadores.ZERO;
        final List<CotaDia> cotas = new ArrayList<>();
        final Map<Long, EspecialidadeNo> especialidades = new LinkedHashMap<>();

        GrupoNo(Long id, String codigo, String nome) {
            this.id = id;
            this.codigo = codigo;
            this.nome = nome;
        }

        EspecialidadeNo especialidade(Long eid, String enome) {
            return especialidades.computeIfAbsent(eid, k -> new EspecialidadeNo(eid, enome));
        }

        Grupo toDto() {
            List<Especialidade> es = especialidades.values().stream()
                    .sorted(Comparator.comparing((EspecialidadeNo e) -> Objects.toString(e.nome, ""),
                            String.CASE_INSENSITIVE_ORDER))
                    .map(EspecialidadeNo::toDto)
                    .toList();
            return new Grupo(id, codigo, nome, indicadores, List.copyOf(cotas), es);
        }
    }

    private static final class EspecialidadeNo {
        final Long id;
        final String nome;
        Indicadores indicadores = Indicadores.ZERO;
        final List<CotaDia> cotas = new ArrayList<>();

        EspecialidadeNo(Long id, String nome) {
            this.id = id;
            this.nome = nome;
        }

        Especialidade toDto() {
            return new Especialidade(id, nome, indicadores, List.copyOf(cotas));
        }
    }
}
