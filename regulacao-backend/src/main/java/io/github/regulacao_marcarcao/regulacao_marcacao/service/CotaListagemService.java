package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.text.Collator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.PaginaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaListaItemDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaListaPaginaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaOpcoesFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeBlocoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidadeSpecification;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/**
 * Listagem paginada de /admin/cotas.
 *
 * A unidade de paginação é o TITULAR (uma unidade, ou um grupo de unidades dono de uma cota
 * compartilhada): cada página traz N titulares COMPLETOS, cada um com todas as suas cotas que
 * passaram pelo filtro, e os totais do bloco valem para o titular inteiro. Dentro do bloco, a
 * cota manual é uma linha e as cotas de uma mesma agenda são UMA linha-resumo que soma só as
 * cotas daquela unidade — a agenda se desfaz por unidade.
 *
 * Ordem (sempre no servidor, sobre a lista completa): titulares em ordem alfabética
 * (unidades e grupos misturados; "sem titular" por último). Dentro de cada titular: ativas
 * antes de inativas; data de referência mais recente primeiro, sem data por último; desempate
 * pelo maior id. A data de referência é a de "Período / Data" da tela: {@code dataEspecifica}
 * (DATA) ou o dia 1 de {@code periodo} (MENSAL); na agenda vale a mais recente entre as cotas.
 * Agenda ativa = {@code agenda.ativo}, como a linha-resumo já mostrava.
 *
 * Como são centenas de cotas, a ordenação e a paginação acontecem em memória sobre
 * uma projeção leve (id, agenda, ativo, data) — sem função de banco dependente de
 * dialeto —, e só as cotas da página são carregadas por inteiro.
 */
@Service
@RequiredArgsConstructor
public class CotaListagemService {

    static final int TAMANHO_PADRAO = 20;
    static final int TAMANHO_MAXIMO = 200;

    private static final Pattern COMPETENCIA = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final Pattern PERIODO = Pattern.compile("^(\\d{4})-(\\d{1,2})");

    private final CotaUnidadeRepository cotaRepository;
    private final UnidadeRepository unidadeRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final UnidadeAcessoService unidadeAcessoService;
    private final EntityManager entityManager;

    /** Cota (já projetada) com a chave de data comparável; {@code agendaId == null} = cota manual. */
    private record Linha(Long id, Long agendaId, boolean ativo, String chave, String titularChave,
            String titularTipo, Long titularId, String titularNome, long total, long utilizada) {
    }

    /** Item de um bloco em construção: uma cota manual ou as cotas de uma agenda NESTE titular. */
    private static final class Item {
        final Long agendaId;
        boolean ativo;
        String chaveMaisRecente;
        long maiorId;
        final List<Long> cotaIds = new ArrayList<>();

        Item(Long agendaId) {
            this.agendaId = agendaId;
        }
    }

    /** Bloco em construção: todas as cotas filtradas de um titular. */
    private static final class Bloco {
        final String chave;
        final String tipo;
        final Long id;
        final String nome;
        long totalCotas;
        long total;
        long utilizada;
        final List<Item> itens = new ArrayList<>();

        Bloco(Linha l) {
            this.chave = l.titularChave();
            this.tipo = l.titularTipo();
            this.id = l.titularId();
            this.nome = l.titularNome();
        }
    }

    @Transactional(readOnly = true)
    public CotaListaPaginaDTO listar(CotaUnidadeFiltroDTO bruto, int pagina, int tamanho, String cpf) {
        // Hoje só ADMIN chega aqui (@PreAuthorize); a checagem fica como defesa para
        // um eventual relaxamento do endpoint não vazar cotas de outras unidades.
        unidadeAcessoService.exigirAdminGlobal(cpf);

        if (pagina < 0) {
            throw new IllegalArgumentException("A página não pode ser negativa.");
        }
        int tamanhoEfetivo = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO);
        CotaUnidadeFiltroDTO filtro = normalizar(bruto);

        Long grupoDaUnidade = filtro.unidadeId() == null ? null
                : unidadeRepository.findById(filtro.unidadeId())
                        .map(u -> u.getGrupoRelatorio() != null ? u.getGrupoRelatorio().getId() : null)
                        .orElse(null);
        Long grupoDaEspecialidade = filtro.especialidadeId() == null ? null
                : especialidadeRepository.findById(filtro.especialidadeId())
                        .map(e -> e.getGrupoRelatorio() != null ? e.getGrupoRelatorio().getId() : null)
                        .orElse(null);

        List<Linha> filtradas = projetar(
                CotaUnidadeSpecification.comFiltros(filtro, grupoDaUnidade, grupoDaEspecialidade));
        List<Linha> todas = filtradas;
        if (haFiltro(filtro)) {
            todas = projetar(null);
        }

        List<Bloco> blocos = montarBlocos(filtradas);

        // Sem filtro, o conjunto já é o geral: não repete a varredura.
        long totalUnidadesGeral = haFiltro(filtro) ? montarBlocos(todas).size() : blocos.size();
        Map<String, Long> cotasPorTitularSemFiltro = new HashMap<>();
        Map<String, Long> cotasPorAgendaNoTitular = new HashMap<>();
        for (Linha l : todas) {
            cotasPorTitularSemFiltro.merge(l.titularChave(), 1L, Long::sum);
            if (l.agendaId() != null) {
                cotasPorAgendaNoTitular.merge(l.titularChave() + "|" + l.agendaId(), 1L, Long::sum);
            }
        }

        int inicio = (int) Math.min((long) pagina * tamanhoEfetivo, blocos.size());
        int fim = Math.min(inicio + tamanhoEfetivo, blocos.size());
        List<Bloco> daPagina = blocos.subList(inicio, fim);

        List<Long> ids = daPagina.stream()
                .flatMap(b -> b.itens.stream())
                .flatMap(i -> i.cotaIds.stream())
                .toList();
        Map<Long, CotaUnidadeViewDTO> dtos = new HashMap<>();
        if (!ids.isEmpty()) {
            for (CotaUnidade c : cotaRepository.buscarComRelacionamentos(ids)) {
                dtos.put(c.getId(), CotaUnidadeViewDTO.from(c));
            }
        }

        List<CotaUnidadeBlocoDTO> conteudo = new ArrayList<>();
        for (Bloco bloco : daPagina) {
            List<CotaListaItemDTO> itens = new ArrayList<>();
            for (Item item : bloco.itens) {
                List<CotaUnidadeViewDTO> cotas = item.cotaIds.stream()
                        .map(dtos::get)
                        .filter(d -> d != null)
                        .sorted(Comparator.comparing(CotaListagemService::chaveOuVazia)
                                .thenComparing(CotaUnidadeViewDTO::id))
                        .toList();
                if (cotas.isEmpty()) {
                    // A cota foi excluída entre a projeção e a carga da página: não há o que exibir.
                    continue;
                }
                itens.add(new CotaListaItemDTO(
                        item.agendaId,
                        item.ativo,
                        item.agendaId == null ? 1
                                : cotasPorAgendaNoTitular.getOrDefault(bloco.chave + "|" + item.agendaId,
                                        (long) cotas.size()),
                        cotas));
            }
            if (itens.isEmpty()) {
                continue;
            }
            conteudo.add(new CotaUnidadeBlocoDTO(
                    bloco.chave,
                    bloco.tipo,
                    bloco.id,
                    bloco.nome,
                    bloco.totalCotas,
                    cotasPorTitularSemFiltro.getOrDefault(bloco.chave, bloco.totalCotas),
                    bloco.total,
                    bloco.utilizada,
                    bloco.total - bloco.utilizada,
                    itens));
        }

        return new CotaListaPaginaDTO(
                PaginaDTO.de(conteudo, pagina, tamanhoEfetivo, blocos.size()),
                filtradas.size(),
                totalUnidadesGeral,
                todas.size());
    }

    @Transactional(readOnly = true)
    public CotaOpcoesFiltroDTO opcoesFiltro(String cpf) {
        unidadeAcessoService.exigirAdminGlobal(cpf);
        return new CotaOpcoesFiltroDTO(
                paraOpcoes(cotaRepository.opcoesEspecialidades()),
                paraOpcoes(cotaRepository.opcoesProfissionais()),
                paraOpcoes(cotaRepository.opcoesExecutantes()));
    }

    // ------------------------------------------------------------------

    private static final String TITULAR_UNIDADE = "UNIDADE";
    private static final String TITULAR_GRUPO = "GRUPO";
    private static final String TITULAR_SEM = "SEM_TITULAR";

    private static final Collator ALFABETO = criarAlfabeto();

    /** Ordem alfabética que ignora caixa e acento ("Álvaro" fica junto de "A"), como em pt-BR. */
    private static Collator criarAlfabeto() {
        Collator c = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        c.setStrength(Collator.PRIMARY);
        return c;
    }

    /** Dentro do bloco: ativas primeiro; depois data mais recente (sem data por último); depois maior id. */
    private static final Comparator<Item> ORDEM = Comparator
            .comparing((Item i) -> !i.ativo)
            .thenComparing((Item i) -> i.chaveMaisRecente == null)
            .thenComparing(Comparator.comparing((Item i) -> i.chaveMaisRecente == null ? "" : i.chaveMaisRecente)
                    .reversed())
            .thenComparing(Comparator.comparingLong((Item i) -> i.maiorId).reversed());

    /** Entre blocos: nome A–Z; "sem titular" por último; desempate por tipo e id. */
    private static final Comparator<Bloco> ORDEM_DOS_BLOCOS = Comparator
            .comparing((Bloco b) -> TITULAR_SEM.equals(b.tipo))
            .thenComparing((Bloco b) -> b.nome, ALFABETO::compare)
            .thenComparing((Bloco b) -> b.tipo)
            .thenComparing((Bloco b) -> b.id == null ? 0L : b.id);

    /**
     * Agrupa as cotas por titular e, dentro dele, em itens (cota manual, ou as cotas de uma
     * agenda NESTE titular). Devolve os blocos já na ordem de exibição.
     */
    private List<Bloco> montarBlocos(List<Linha> linhas) {
        Map<String, Bloco> blocos = new LinkedHashMap<>();
        Map<String, Item> itens = new HashMap<>();
        for (Linha l : linhas) {
            Bloco bloco = blocos.computeIfAbsent(l.titularChave(), k -> new Bloco(l));
            bloco.totalCotas++;
            bloco.total += l.total();
            bloco.utilizada += l.utilizada();

            String chaveItem = l.titularChave() + "|" + (l.agendaId() != null ? "a" + l.agendaId() : "c" + l.id());
            Item item = itens.computeIfAbsent(chaveItem, k -> {
                Item novo = new Item(l.agendaId());
                novo.ativo = l.ativo();
                bloco.itens.add(novo);
                return novo;
            });
            item.cotaIds.add(l.id());
            item.maiorId = Math.max(item.maiorId, l.id());
            if (l.chave() != null
                    && (item.chaveMaisRecente == null || l.chave().compareTo(item.chaveMaisRecente) > 0)) {
                item.chaveMaisRecente = l.chave();
            }
        }
        List<Bloco> ordenados = new ArrayList<>(blocos.values());
        ordenados.forEach(b -> b.itens.sort(ORDEM));
        ordenados.sort(ORDEM_DOS_BLOCOS);
        return ordenados;
    }

    /** Projeção leve das cotas que passam pelo filtro ({@code null} = todas). */
    private List<Linha> projetar(Specification<CotaUnidade> filtro) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<CotaUnidade> root = query.from(CotaUnidade.class);
        Join<?, ?> agenda = root.join("agendaOcorrencia", JoinType.LEFT).join("agenda", JoinType.LEFT);
        Join<?, ?> unidade = root.join("unidade", JoinType.LEFT);
        Join<?, ?> grupoUnidades = root.join("grupoUnidades", JoinType.LEFT);

        query.multiselect(
                root.get("id").alias("id"),
                root.get("origem").alias("origem"),
                agenda.get("id").alias("agendaId"),
                root.get("ativo").alias("ativo"),
                agenda.get("ativo").alias("agendaAtiva"),
                root.get("tipoPeriodo").alias("tipoPeriodo"),
                root.get("periodo").alias("periodo"),
                root.get("dataEspecifica").alias("dataEspecifica"),
                unidade.get("id").alias("unidadeId"),
                unidade.get("nome").alias("unidadeNome"),
                grupoUnidades.get("id").alias("grupoId"),
                grupoUnidades.get("nome").alias("grupoNome"),
                root.get("quantidadeTotal").alias("quantidadeTotal"),
                root.get("quantidadeUtilizada").alias("quantidadeUtilizada"));
        if (filtro != null) {
            Predicate p = filtro.toPredicate(root, query, cb);
            if (p != null) {
                query.where(p);
            }
        }

        List<Linha> linhas = new ArrayList<>();
        for (Tuple t : entityManager.createQuery(query).getResultList()) {
            // Cota de origem AGENDA sem agenda cai em "manual" em vez de sumir.
            boolean deAgenda = t.get("origem", OrigemCotaEnum.class) == OrigemCotaEnum.AGENDA
                    && t.get("agendaId") != null;
            Long agendaId = deAgenda ? t.get("agendaId", Long.class) : null;
            Boolean agendaAtiva = t.get("agendaAtiva", Boolean.class);
            boolean ativo = deAgenda ? agendaAtiva == null || agendaAtiva : t.get("ativo", Boolean.class);
            Long unidadeId = t.get("unidadeId", Long.class);
            Long grupoId = t.get("grupoId", Long.class);
            // Toda cota tem unidade OU grupo (CHECK do banco); "sem titular" é só defesa para
            // dado inconsistente não sumir da tela.
            String titularChave = unidadeId != null ? "u" + unidadeId : grupoId != null ? "g" + grupoId : "sem";
            String titularTipo = unidadeId != null ? TITULAR_UNIDADE : grupoId != null ? TITULAR_GRUPO : TITULAR_SEM;
            Long titularId = unidadeId != null ? unidadeId : grupoId;
            String titularNome = unidadeId != null ? t.get("unidadeNome", String.class)
                    : grupoId != null ? t.get("grupoNome", String.class) : "Sem titular";
            Integer total = t.get("quantidadeTotal", Integer.class);
            Integer utilizada = t.get("quantidadeUtilizada", Integer.class);
            linhas.add(new Linha(
                    t.get("id", Long.class),
                    agendaId,
                    ativo,
                    chaveDe(t.get("tipoPeriodo", TipoPeriodoCota.class), t.get("periodo", String.class),
                            t.get("dataEspecifica", LocalDate.class)),
                    titularChave,
                    titularTipo,
                    titularId,
                    titularNome == null ? "" : titularNome,
                    total == null ? 0 : total,
                    utilizada == null ? 0 : utilizada));
        }
        return linhas;
    }

    /** "YYYY-MM-DD" comparável, ou {@code null} se a cota não tem data reconhecível. */
    static String chaveDe(TipoPeriodoCota tipo, String periodo, LocalDate dataEspecifica) {
        if (tipo == TipoPeriodoCota.DATA) {
            return dataEspecifica != null ? dataEspecifica.toString() : null;
        }
        if (periodo == null) {
            return null;
        }
        Matcher m = PERIODO.matcher(periodo);
        return m.find() ? "%s-%02d-01".formatted(m.group(1), Integer.parseInt(m.group(2))) : null;
    }

    /** Dentro do bloco de agenda, as cotas seguem em ordem cronológica (como no resumo). */
    private static String chaveOuVazia(CotaUnidadeViewDTO d) {
        String chave = chaveDe(d.tipoPeriodo(), d.periodo(), d.dataEspecifica());
        return chave == null ? "" : chave;
    }

    private static boolean haFiltro(CotaUnidadeFiltroDTO f) {
        return !f.equals(CotaUnidadeFiltroDTO.vazio());
    }

    private static CotaUnidadeFiltroDTO normalizar(CotaUnidadeFiltroDTO bruto) {
        if (bruto == null) {
            return CotaUnidadeFiltroDTO.vazio();
        }
        String status = valorDeLista(bruto.status(), "status", "ativa", "inativa");
        String saldo = valorDeLista(bruto.saldo(), "saldo", "com-saldo", "esgotada");
        String origem = valorDeLista(bruto.origem(), "origem", "manual", "agenda");
        String tipo = valorDeLista(bruto.tipo(), "tipo", "mensal", "data");
        String competencia = vazioParaNulo(bruto.competencia());
        if (competencia != null && !COMPETENCIA.matcher(competencia).matches()) {
            throw new IllegalArgumentException("Competência inválida: use o formato AAAA-MM.");
        }
        if (bruto.dataDe() != null && bruto.dataAte() != null && bruto.dataDe().isAfter(bruto.dataAte())) {
            throw new IllegalArgumentException("A data inicial é maior que a data final.");
        }
        String q = vazioParaNulo(bruto.q());
        return new CotaUnidadeFiltroDTO(bruto.unidadeId(), bruto.especialidadeId(), status, saldo, origem, tipo,
                bruto.profissionalId(), bruto.executanteId(), competencia, bruto.dataDe(), bruto.dataAte(), q);
    }

    private static String valorDeLista(String valor, String campo, String... permitidos) {
        String v = vazioParaNulo(valor);
        if (v == null) {
            return null;
        }
        for (String p : permitidos) {
            if (p.equals(v)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Valor inválido para " + campo + ": " + v);
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static List<CotaOpcoesFiltroDTO.Opcao> paraOpcoes(List<Object[]> linhas) {
        return linhas.stream()
                .map(l -> new CotaOpcoesFiltroDTO.Opcao((Long) l[0], (String) l[1]))
                .sorted(Comparator.comparing(CotaOpcoesFiltroDTO.Opcao::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
