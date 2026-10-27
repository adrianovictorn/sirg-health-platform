package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaListaItemDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaListaPaginaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeBlocoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Agenda;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaOcorrencia;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoOfertaAgendaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Listagem paginada de /admin/cotas contra o banco real. Cada teste isola os seus
 * dados filtrando pela unidade criada no setUp (a base pode ter cotas de verdade),
 * e a classe é {@code @Transactional}: tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class CotaListagemServiceIT {

    @Autowired private CotaListagemService listagem;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRepository;
    @Autowired private ProfissionalRepository profissionalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private jakarta.persistence.EntityManager em;

    private Unidade unidade;
    private Especialidade cardiologia;
    private String admin;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "_LST_" + System.nanoTime();

        unidade = new Unidade();
        unidade.setNome("Unidade" + sufixo);
        unidade.setCodigo("UL" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        cardiologia = new Especialidade();
        cardiologia.setCodigo("CARD" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(0);
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        User u = new User();
        u.setCpf(String.format("%011d", System.nanoTime() % 100_000_000_000L));
        u.setNome("Admin" + sufixo);
        u.setPassword("x");
        u.setRole(Roles.ADMIN);
        u.setAtivo(true);
        admin = userRepository.saveAndFlush(u).getCpf();
    }

    private CotaUnidade mensal(String periodo, boolean ativo, int total, int utilizada) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(cardiologia);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(periodo);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(utilizada);
        c.setAtivo(ativo);
        return cotaRepository.saveAndFlush(c);
    }

    private CotaUnidade porData(LocalDate data, boolean ativo) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(cardiologia);
        c.setTipoPeriodo(TipoPeriodoCota.DATA);
        c.setDataEspecifica(data);
        c.setQuantidadeTotal(10);
        c.setAtivo(ativo);
        return cotaRepository.saveAndFlush(c);
    }

    private CotaUnidadeFiltroDTO daUnidade() {
        return new CotaUnidadeFiltroDTO(unidade.getId(), null, null, null, null, null, null, null, null, null,
                null, null);
    }

    private CotaUnidadeFiltroDTO daUnidade(String status, String saldo, String q) {
        return new CotaUnidadeFiltroDTO(unidade.getId(), null, status, saldo, null, null, null, null, null, null,
                null, q);
    }

    /** Itens (cota manual ou agenda) de todos os blocos da página, na ordem de exibição. */
    private static List<CotaListaItemDTO> itens(CotaListaPaginaDTO p) {
        return p.pagina().conteudo().stream().flatMap(b -> b.itens().stream()).toList();
    }

    private static List<Long> ids(CotaListaPaginaDTO p) {
        return itens(p).stream().flatMap(i -> i.cotas().stream()).map(c -> c.id()).toList();
    }

    @Test
    @DisplayName("Dentro do titular: ativas antes das inativas; dentro de cada grupo, a data mais recente primeiro; sem data por ultimo")
    void ordemPadrao() {
        CotaUnidade antiga = mensal("2026-05", true, 10, 0);
        CotaUnidade recente = porData(LocalDate.of(2026, 7, 10), true);
        CotaUnidade inativaRecente = porData(LocalDate.of(2026, 12, 1), false);
        CotaUnidade semData = mensal(null, true, 10, 0);

        CotaListaPaginaDTO p = listagem.listar(daUnidade(), 0, 20, admin);

        assertThat(ids(p)).containsExactly(recente.getId(), antiga.getId(), semData.getId(),
                inativaRecente.getId());
        assertThat(itens(p).get(3).ativo()).isFalse();
    }

    @Test
    @DisplayName("Desempate pelo maior id quando a data e igual")
    void desempatePorId() {
        CotaUnidade primeira = mensal("2026-06", true, 10, 0);
        // Mesmo mes e mesma unidade: so a cota geral (sem especialidade) nao viola uk_cota_mensal.
        CotaUnidade segunda = new CotaUnidade();
        segunda.setUnidade(unidade);
        segunda.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        segunda.setPeriodo("2026-06");
        segunda.setQuantidadeTotal(10);
        segunda = cotaRepository.saveAndFlush(segunda);

        assertThat(ids(listagem.listar(daUnidade(), 0, 20, admin)))
                .containsExactly(segunda.getId(), primeira.getId());
    }

    @Test
    @DisplayName("Dentro do titular as inativas ficam no fim, mesmo as de data mais recente")
    void inativasNoFimDoTitular() {
        mensal("2026-01", true, 10, 0);
        mensal("2026-02", true, 10, 0);
        mensal("2026-03", true, 10, 0);
        mensal("2026-11", false, 10, 0);
        mensal("2026-12", false, 10, 0);

        CotaListaPaginaDTO p = listagem.listar(daUnidade(), 0, 20, admin);

        assertThat(itens(p)).extracting(CotaListaItemDTO::ativo).containsExactly(true, true, true, false, false);
        // A paginacao conta titulares: uma unidade, uma pagina, com as 5 cotas dentro.
        assertThat(p.pagina().totalElementos()).isEqualTo(1);
        assertThat(p.pagina().totalPaginas()).isEqualTo(1);
        assertThat(p.pagina().conteudo().get(0).totalCotas()).isEqualTo(5);
    }

    @Test
    @DisplayName("Filtro de status devolve so ativas ou so inativas; totais mostram filtrado e geral")
    void filtroDeStatusETotais() {
        mensal("2026-01", true, 10, 0);
        mensal("2026-02", true, 10, 0);
        mensal("2026-03", false, 10, 0);

        CotaListaPaginaDTO ativas = listagem.listar(daUnidade("ativa", null, null), 0, 20, admin);
        CotaListaPaginaDTO inativas = listagem.listar(daUnidade("inativa", null, null), 0, 20, admin);

        assertThat(ativas.totalCotas()).isEqualTo(2);
        assertThat(inativas.totalCotas()).isEqualTo(1);
        // O total geral nao depende do filtro: conta todas as cotas do banco.
        assertThat(ativas.totalCotasGeral()).isGreaterThanOrEqualTo(3).isEqualTo(inativas.totalCotasGeral());
    }

    @Test
    @DisplayName("Filtro de saldo separa cota esgotada de cota com saldo")
    void filtroDeSaldo() {
        CotaUnidade esgotada = mensal("2026-01", true, 5, 5);
        CotaUnidade comSaldo = mensal("2026-02", true, 5, 1);

        assertThat(ids(listagem.listar(daUnidade(null, "esgotada", null), 0, 20, admin)))
                .containsExactly(esgotada.getId());
        assertThat(ids(listagem.listar(daUnidade(null, "com-saldo", null), 0, 20, admin)))
                .containsExactly(comSaldo.getId());
    }

    @Test
    @DisplayName("Busca textual ignora caixa e exige todos os termos")
    void busca() {
        CotaUnidade c = mensal("2026-01", true, 5, 0);

        assertThat(ids(listagem.listar(daUnidade(null, null, "CARDIOLOGIA unidade"), 0, 20, admin)))
                .containsExactly(c.getId());
        assertThat(ids(listagem.listar(daUnidade(null, null, "cardiologia inexistente"), 0, 20, admin)))
                .isEmpty();
    }

    @Test
    @DisplayName("Cota de grupo de unidades e cota geral continuam visiveis (sem INNER JOIN)")
    void cotaDeGrupoECotaGeral() {
        GrupoRelatorio grupo = new GrupoRelatorio();
        grupo.setCodigo("GRP" + sufixo);
        grupo.setNome("Grupo" + sufixo);
        grupo.setAtivo(true);
        grupo = grupoRepository.saveAndFlush(grupo);
        unidade.setGrupoRelatorio(grupo);
        unidadeRepository.saveAndFlush(unidade);

        CotaUnidade doGrupo = new CotaUnidade();
        doGrupo.setGrupoUnidades(grupo);
        doGrupo.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        doGrupo.setPeriodo("2026-04");
        doGrupo.setQuantidadeTotal(20);
        doGrupo = cotaRepository.saveAndFlush(doGrupo);

        CotaUnidade geral = new CotaUnidade();
        geral.setUnidade(unidade);
        geral.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        geral.setPeriodo("2026-03");
        geral.setQuantidadeTotal(20);
        geral = cotaRepository.saveAndFlush(geral);

        CotaListaPaginaDTO porUnidade = listagem.listar(daUnidade(), 0, 20, admin);
        assertThat(ids(porUnidade)).contains(doGrupo.getId(), geral.getId());

        // A cota geral (sem especialidade) tambem incide sobre a especialidade filtrada.
        CotaUnidadeFiltroDTO porEspecialidade = new CotaUnidadeFiltroDTO(unidade.getId(), cardiologia.getId(), null,
                null, null, null, null, null, null, null, null, null);
        assertThat(ids(listagem.listar(porEspecialidade, 0, 20, admin))).contains(geral.getId());
    }

    private CotaUnidadeFiltroDTO daUnidadeCom(String origem, String tipo, String competencia, LocalDate de,
            LocalDate ate, Long profissionalId, Long executanteId) {
        return new CotaUnidadeFiltroDTO(unidade.getId(), null, null, null, origem, tipo, profissionalId,
                executanteId, competencia, de, ate, null);
    }

    @Test
    @DisplayName("Filtros de origem e tipo")
    void filtroDeOrigemETipo() {
        CotaUnidade mensal = mensal("2026-01", true, 5, 0);
        CotaUnidade data = porData(LocalDate.of(2026, 2, 10), true);

        assertThat(ids(listagem.listar(daUnidadeCom("manual", null, null, null, null, null, null), 0, 20, admin)))
                .containsExactlyInAnyOrder(mensal.getId(), data.getId());
        assertThat(ids(listagem.listar(daUnidadeCom("agenda", null, null, null, null, null, null), 0, 20, admin)))
                .isEmpty();
        assertThat(ids(listagem.listar(daUnidadeCom(null, "data", null, null, null, null, null), 0, 20, admin)))
                .containsExactly(data.getId());
        assertThat(ids(listagem.listar(daUnidadeCom(null, "mensal", null, null, null, null, null), 0, 20, admin)))
                .containsExactly(mensal.getId());
    }

    @Test
    @DisplayName("Competencia: mensal por mes, por data pelo mes da data; cota sem data continua visivel")
    void filtroDeCompetencia() {
        CotaUnidade mensalMarco = mensal("2026-03", true, 5, 0);
        CotaUnidade mensalMaio = mensal("2026-05", true, 5, 0);
        CotaUnidade dataMarco = porData(LocalDate.of(2026, 3, 20), true);
        CotaUnidade semData = mensal(null, true, 5, 0);

        assertThat(ids(listagem.listar(daUnidadeCom(null, null, "2026-03", null, null, null, null), 0, 20, admin)))
                .containsExactlyInAnyOrder(mensalMarco.getId(), dataMarco.getId(), semData.getId())
                .doesNotContain(mensalMaio.getId());
    }

    @Test
    @DisplayName("Intervalo de datas: data no intervalo, mes cruzando o intervalo, sem data visivel")
    void filtroDeIntervaloDeDatas() {
        CotaUnidade dentro = porData(LocalDate.of(2026, 3, 15), true);
        CotaUnidade fora = porData(LocalDate.of(2026, 4, 2), true);
        CotaUnidade mensalCruza = mensal("2026-03", true, 5, 0);
        CotaUnidade mensalFora = mensal("2026-06", true, 5, 0);
        CotaUnidade semData = mensal(null, true, 5, 0);

        List<Long> r = ids(listagem.listar(
                daUnidadeCom(null, null, null, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 31), null, null),
                0, 20, admin));

        assertThat(r).containsExactlyInAnyOrder(dentro.getId(), mensalCruza.getId(), semData.getId())
                .doesNotContain(fora.getId(), mensalFora.getId());
    }

    @Test
    @DisplayName("Profissional da cota manual casa; executante nunca casa com cota manual")
    void filtroDeProfissionalEExecutante() {
        Profissional prof = new Profissional();
        prof.setNome("Prof" + sufixo);
        prof.setAtivo(true);
        prof = profissionalRepository.saveAndFlush(prof);

        CotaUnidade comProf = porData(LocalDate.of(2026, 3, 15), true);
        comProf.setProfissionalExecutante(prof);
        cotaRepository.saveAndFlush(comProf);
        porData(LocalDate.of(2026, 3, 16), true);

        assertThat(ids(listagem.listar(daUnidadeCom(null, null, null, null, null, prof.getId(), null), 0, 20,
                admin))).containsExactly(comProf.getId());
        assertThat(ids(listagem.listar(daUnidadeCom(null, null, null, null, null, null, unidade.getId()), 0, 20,
                admin))).isEmpty();
    }

    @Test
    @DisplayName("Caracterizacao: com varias unidades e uma cota de grupo, nenhuma cota some entre as paginas")
    void nenhumaCotaSomeEntreAsPaginas() {
        GrupoRelatorio grupo = new GrupoRelatorio();
        grupo.setCodigo("GRP" + sufixo);
        grupo.setNome("Grupo " + sufixo);
        grupo.setAtivo(true);
        grupo = grupoRepository.saveAndFlush(grupo);

        Unidade outra = new Unidade();
        outra.setNome("Outra " + sufixo);
        outra.setCodigo("OU" + sufixo);
        outra.setAtivo(true);
        outra = unidadeRepository.saveAndFlush(outra);

        CotaUnidade a = mensal("2026-01", true, 10, 0);
        CotaUnidade b = porData(LocalDate.of(2026, 2, 10), false);
        CotaUnidade deOutra = new CotaUnidade();
        deOutra.setUnidade(outra);
        deOutra.setEspecialidade(cardiologia);
        deOutra.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        deOutra.setPeriodo("2026-03");
        deOutra.setQuantidadeTotal(7);
        deOutra = cotaRepository.saveAndFlush(deOutra);
        CotaUnidade doGrupo = new CotaUnidade();
        doGrupo.setGrupoUnidades(grupo);
        doGrupo.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        doGrupo.setPeriodo("2026-04");
        doGrupo.setQuantidadeTotal(20);
        doGrupo = cotaRepository.saveAndFlush(doGrupo);

        // A busca pelo sufixo pega as duas unidades (pelo nome) e a cota do grupo (pelo nome do grupo).
        CotaUnidadeFiltroDTO soDoTeste = new CotaUnidadeFiltroDTO(null, null, null, null, null, null, null, null,
                null, null, null, sufixo);
        List<Long> vistas = new java.util.ArrayList<>();
        CotaListaPaginaDTO ultima = null;
        for (int pagina = 0; pagina < 10; pagina++) {
            ultima = listagem.listar(soDoTeste, pagina, 1, admin);
            vistas.addAll(ids(ultima));
            if (ultima.pagina().conteudo().isEmpty()) {
                break;
            }
        }

        assertThat(vistas).containsExactlyInAnyOrder(a.getId(), b.getId(), deOutra.getId(), doGrupo.getId());
        assertThat(ultima.totalCotas()).isEqualTo(4);
    }

    // ---- Blocos por titular

    private Unidade outraUnidade(String nome) {
        Unidade u = new Unidade();
        u.setNome(nome + " " + sufixo);
        u.setCodigo("X" + nome.hashCode() + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private CotaUnidade cotaDe(Unidade u, String periodo, boolean ativo, int total, int utilizada) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(u);
        c.setEspecialidade(cardiologia);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(periodo);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(utilizada);
        c.setAtivo(ativo);
        return cotaRepository.saveAndFlush(c);
    }

    private CotaUnidadeFiltroDTO todasDoTeste() {
        return new CotaUnidadeFiltroDTO(null, null, null, null, null, null, null, null, null, null, null, sufixo);
    }

    @Test
    @DisplayName("Blocos em ordem alfabetica (ignora acento); o titular vem antes de 'ativas primeiro'")
    void blocosEmOrdemAlfabetica() {
        Unidade alamo = outraUnidade("Álamo");
        Unidade zeta = outraUnidade("Zeta");
        cotaDe(zeta, "2026-12", true, 5, 0);
        CotaUnidade inativaDoAlamo = cotaDe(alamo, "2026-01", false, 5, 0);
        mensal("2026-06", true, 5, 0);

        CotaListaPaginaDTO p = listagem.listar(todasDoTeste(), 0, 20, admin);

        assertThat(p.pagina().conteudo()).extracting(b -> b.nome())
                .containsExactly("Álamo " + sufixo, "Unidade" + sufixo, "Zeta " + sufixo);
        // A unica cota do Álamo e inativa, e o bloco continua na frente das unidades com cota ativa.
        assertThat(p.pagina().conteudo().get(0).itens().get(0).cotas().get(0).id()).isEqualTo(inativaDoAlamo.getId());
        assertThat(p.pagina().conteudo()).extracting(b -> b.tipo()).containsOnly("UNIDADE");
    }

    @Test
    @DisplayName("Paginacao por titular: cada pagina traz unidades completas, e nenhuma cota se perde")
    void paginacaoPorUnidade() {
        Unidade alamo = outraUnidade("Álamo");
        CotaUnidade a1 = cotaDe(alamo, "2026-01", true, 5, 0);
        CotaUnidade a2 = cotaDe(alamo, "2026-02", true, 5, 0);
        CotaUnidade a3 = cotaDe(alamo, "2026-03", false, 5, 0);
        CotaUnidade u1 = mensal("2026-06", true, 5, 0);

        CotaListaPaginaDTO p0 = listagem.listar(todasDoTeste(), 0, 1, admin);
        CotaListaPaginaDTO p1 = listagem.listar(todasDoTeste(), 1, 1, admin);

        assertThat(p0.pagina().totalElementos()).isEqualTo(2);
        assertThat(p0.pagina().totalPaginas()).isEqualTo(2);
        assertThat(ids(p0)).containsExactlyInAnyOrder(a1.getId(), a2.getId(), a3.getId());
        assertThat(ids(p1)).containsExactly(u1.getId());
        assertThat(p0.totalCotas()).isEqualTo(4);
    }

    @Test
    @DisplayName("Totais do bloco valem para o titular inteiro no filtro; 'sem filtro' ignora o filtro")
    void totaisDoBloco() {
        mensal("2026-01", true, 10, 4);
        mensal(null, false, 6, 1);

        CotaListaPaginaDTO semFiltro = listagem.listar(daUnidade(), 0, 20, admin);
        CotaUnidadeBlocoDTO bloco = semFiltro.pagina().conteudo().get(0);
        assertThat(bloco.totalCotas()).isEqualTo(2);
        assertThat(bloco.quantidadeTotal()).isEqualTo(16);
        assertThat(bloco.quantidadeUtilizada()).isEqualTo(5);
        assertThat(bloco.saldo()).isEqualTo(11);

        CotaListaPaginaDTO soAtivas = listagem.listar(daUnidade("ativa", null, null), 0, 20, admin);
        CotaUnidadeBlocoDTO blocoFiltrado = soAtivas.pagina().conteudo().get(0);
        assertThat(blocoFiltrado.totalCotas()).isEqualTo(1);
        assertThat(blocoFiltrado.saldo()).isEqualTo(6);
        assertThat(blocoFiltrado.totalCotasSemFiltro()).isEqualTo(2);
    }

    @Test
    @DisplayName("Filtro que nao e de unidade esconde as unidades sem cota que passou; o de unidade mostra so a dela")
    void filtrosEOsBlocos() {
        Unidade alamo = outraUnidade("Álamo");
        cotaDe(alamo, "2026-01", true, 5, 0);
        mensal("2026-02", false, 5, 0);

        CotaListaPaginaDTO soInativas = listagem.listar(
                new CotaUnidadeFiltroDTO(null, null, "inativa", null, null, null, null, null, null, null, null,
                        sufixo), 0, 20, admin);
        assertThat(soInativas.pagina().conteudo()).extracting(b -> b.nome()).containsExactly("Unidade" + sufixo);
        // O total geral e da base inteira (ignora o filtro): pelo menos as 2 unidades deste teste.
        assertThat(soInativas.totalUnidadesGeral()).isGreaterThanOrEqualTo(2);

        CotaListaPaginaDTO daAlamo = listagem.listar(
                new CotaUnidadeFiltroDTO(alamo.getId(), null, null, null, null, null, null, null, null, null, null,
                        null), 0, 20, admin);
        assertThat(daAlamo.pagina().conteudo()).extracting(b -> b.nome()).containsExactly("Álamo " + sufixo);
    }

    @Test
    @DisplayName("Cota de grupo de unidades e um bloco GRUPO no mesmo nivel das unidades; filtrar a unidade traz os dois")
    void blocoDoGrupo() {
        GrupoRelatorio grupo = new GrupoRelatorio();
        grupo.setCodigo("BGR" + sufixo);
        grupo.setNome("Bloco do grupo " + sufixo);
        grupo.setAtivo(true);
        grupo = grupoRepository.saveAndFlush(grupo);
        unidade.setGrupoRelatorio(grupo);
        unidadeRepository.saveAndFlush(unidade);

        CotaUnidade doGrupo = new CotaUnidade();
        doGrupo.setGrupoUnidades(grupo);
        doGrupo.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        doGrupo.setPeriodo("2026-04");
        doGrupo.setQuantidadeTotal(20);
        doGrupo = cotaRepository.saveAndFlush(doGrupo);
        CotaUnidade propria = mensal("2026-05", true, 5, 0);

        CotaListaPaginaDTO p = listagem.listar(daUnidade(), 0, 20, admin);

        assertThat(p.pagina().conteudo()).extracting(b -> b.tipo()).containsExactlyInAnyOrder("UNIDADE", "GRUPO");
        CotaUnidadeBlocoDTO blocoGrupo = p.pagina().conteudo().stream().filter(b -> "GRUPO".equals(b.tipo()))
                .findFirst().orElseThrow();
        assertThat(blocoGrupo.nome()).isEqualTo("Bloco do grupo " + sufixo);
        assertThat(blocoGrupo.id()).isEqualTo(grupo.getId());
        assertThat(ids(p)).containsExactlyInAnyOrder(doGrupo.getId(), propria.getId());
    }

    @Test
    @DisplayName("Agenda cobrindo duas unidades se desfaz: uma linha-resumo por unidade, cada uma com as suas cotas")
    void agendaSeDesfazPorUnidade() {
        User criador = new User();
        criador.setCpf(String.format("%011d", (System.nanoTime() + 7) % 100_000_000_000L));
        criador.setNome("Criador" + sufixo);
        criador.setPassword("x");
        criador.setRole(Roles.ADMIN);
        criador.setAtivo(true);
        criador = userRepository.saveAndFlush(criador);

        Unidade executante = outraUnidade("Executante");
        Profissional prof = new Profissional();
        prof.setNome("Prof agenda " + sufixo);
        prof.setAtivo(true);
        prof = profissionalRepository.saveAndFlush(prof);

        Agenda agenda = new Agenda();
        agenda.setEstabelecimentoExecutante(executante);
        agenda.setProfissional(prof);
        agenda.setTipoOferta(TipoOfertaAgendaEnum.INDIVIDUAL);
        agenda.setVigenciaInicio(LocalDate.of(2026, 5, 1));
        agenda.setVigenciaFim(LocalDate.of(2026, 5, 31));
        agenda.setDiasSemana("SEG");
        agenda.setHoraInicial(LocalTime.of(8, 0));
        agenda.setHoraFinal(LocalTime.of(12, 0));
        agenda.setCriadoPor(criador);
        em.persist(agenda);

        AgendaOcorrencia ocorrencia = new AgendaOcorrencia();
        ocorrencia.setAgenda(agenda);
        ocorrencia.setData(LocalDate.of(2026, 5, 4));
        ocorrencia.setHoraInicial(LocalTime.of(8, 0));
        ocorrencia.setHoraFinal(LocalTime.of(12, 0));
        em.persist(ocorrencia);
        em.flush();

        Unidade outra = outraUnidade("Outra");
        CotaUnidade daAgendaA = cotaDaAgenda(unidade, ocorrencia, 4);
        CotaUnidade daAgendaB = cotaDaAgenda(outra, ocorrencia, 6);

        CotaListaPaginaDTO p = listagem.listar(todasDoTeste(), 0, 20, admin);

        // Dois blocos (cada unidade), e em cada um UMA linha de agenda com so a cota daquela unidade.
        assertThat(p.pagina().conteudo()).hasSize(2);
        for (CotaUnidadeBlocoDTO bloco : p.pagina().conteudo()) {
            assertThat(bloco.itens()).hasSize(1);
            CotaListaItemDTO item = bloco.itens().get(0);
            assertThat(item.agendaId()).isEqualTo(agenda.getId());
            assertThat(item.cotas()).hasSize(1);
            assertThat(item.totalCotasDaAgenda()).isEqualTo(1);
        }
        assertThat(ids(p)).containsExactlyInAnyOrder(daAgendaA.getId(), daAgendaB.getId());
    }

    private CotaUnidade cotaDaAgenda(Unidade u, AgendaOcorrencia ocorrencia, int total) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(u);
        c.setOrigem(OrigemCotaEnum.AGENDA);
        c.setAgendaOcorrencia(ocorrencia);
        c.setTipoPeriodo(TipoPeriodoCota.DATA);
        c.setDataEspecifica(ocorrencia.getData());
        c.setQuantidadeTotal(total);
        return cotaRepository.saveAndFlush(c);
    }

    @Test
    @DisplayName("Pagina alem do fim devolve vazio; size acima do teto e limitado")
    void paginaForaDoIntervaloETeto() {
        mensal("2026-01", true, 5, 0);

        CotaListaPaginaDTO alemDoFim = listagem.listar(daUnidade(), 5, 20, admin);
        assertThat(alemDoFim.pagina().conteudo()).isEmpty();
        assertThat(alemDoFim.pagina().totalElementos()).isEqualTo(1);

        assertThat(listagem.listar(daUnidade(), 0, 100_000, admin).pagina().tamanho()).isEqualTo(200);
    }

    @Test
    @DisplayName("Parametros invalidos viram IllegalArgumentException (400)")
    void parametrosInvalidos() {
        assertThatThrownBy(() -> listagem.listar(daUnidade(), -1, 20, admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listagem.listar(daUnidade("talvez", null, null), 0, 20, admin))
                .isInstanceOf(IllegalArgumentException.class);
        CotaUnidadeFiltroDTO invertido = new CotaUnidadeFiltroDTO(null, null, null, null, null, null, null, null,
                null, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1), null);
        assertThatThrownBy(() -> listagem.listar(invertido, 0, 20, admin))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
