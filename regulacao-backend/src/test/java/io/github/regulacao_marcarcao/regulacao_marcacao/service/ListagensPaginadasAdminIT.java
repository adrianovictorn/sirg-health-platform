package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.PaginaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioListaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;

/**
 * Listagens paginadas de unidades, grupos de relatório e profissionais contra o
 * banco real. Os dados levam um sufixo único e a busca textual ({@code q}) o usa
 * para isolar o teste do que já existe na base; a classe sofre rollback.
 */
@SpringBootTest
@Transactional
class ListagensPaginadasAdminIT {

    @Autowired private UnidadeService unidadeService;
    @Autowired private GrupoRelatorioService grupoService;
    @Autowired private ProfissionalService profissionalService;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRepository;
    @Autowired private ProfissionalRepository profissionalRepository;

    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "zzlst" + System.nanoTime();
    }

    // ---- Unidades

    private Unidade unidade(String nome, boolean ativo, TipoUnidadeEnum tipo) {
        Unidade u = new Unidade();
        u.setNome(nome + " " + sufixo);
        u.setCodigo("C" + nome + sufixo);
        u.setAtivo(ativo);
        u.setTipo(tipo);
        return unidadeRepository.saveAndFlush(u);
    }

    private UnidadeFiltroDTO filtroUnidade(String status, String tipo) {
        return new UnidadeFiltroDTO(sufixo, status, tipo, null);
    }

    @Test
    @DisplayName("Unidades: ordem por nome, total do filtro e paginação")
    void unidadesOrdemETotal() {
        unidade("Charlie", true, TipoUnidadeEnum.AMBOS);
        unidade("Alfa", true, TipoUnidadeEnum.AMBOS);
        unidade("Bravo", false, TipoUnidadeEnum.AMBOS);

        PaginaDTO<UnidadeViewDTO> p0 = unidadeService.listarPagina(filtroUnidade(null, null), 0, 2);
        PaginaDTO<UnidadeViewDTO> p1 = unidadeService.listarPagina(filtroUnidade(null, null), 1, 2);

        assertThat(p0.totalElementos()).isEqualTo(3);
        assertThat(p0.totalPaginas()).isEqualTo(2);
        assertThat(p0.conteudo()).extracting(UnidadeViewDTO::nome)
                .containsExactly("Alfa " + sufixo, "Bravo " + sufixo);
        assertThat(p1.conteudo()).extracting(UnidadeViewDTO::nome).containsExactly("Charlie " + sufixo);
    }

    @Test
    @DisplayName("Unidades: filtros de status e tipo; valor inválido vira IllegalArgumentException")
    void unidadesFiltros() {
        unidade("Ativa", true, TipoUnidadeEnum.SOLICITANTE);
        unidade("Inativa", false, TipoUnidadeEnum.EXECUTANTE);

        assertThat(unidadeService.listarPagina(filtroUnidade("ativa", null), 0, 20).conteudo())
                .extracting(UnidadeViewDTO::nome).containsExactly("Ativa " + sufixo);
        assertThat(unidadeService.listarPagina(filtroUnidade("inativa", null), 0, 20).conteudo())
                .extracting(UnidadeViewDTO::nome).containsExactly("Inativa " + sufixo);
        assertThat(unidadeService.listarPagina(filtroUnidade(null, "EXECUTANTE"), 0, 20).conteudo())
                .extracting(UnidadeViewDTO::nome).containsExactly("Inativa " + sufixo);

        assertThatThrownBy(() -> unidadeService.listarPagina(filtroUnidade("talvez", null), 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> unidadeService.listarPagina(filtroUnidade(null, "OUTRO"), 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> unidadeService.listarPagina(filtroUnidade(null, null), -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Unidades: busca não olha o telefone e o filtro de grupo traz só as do grupo")
    void unidadesBuscaEGrupo() {
        GrupoRelatorio grupo = grupo("Grupo", true, false);
        Unidade comGrupo = unidade("ComGrupo", true, TipoUnidadeEnum.AMBOS);
        comGrupo.setGrupoRelatorio(grupo);
        comGrupo.setTelefone("77999990000");
        unidadeRepository.saveAndFlush(comGrupo);
        unidade("SemGrupo", true, TipoUnidadeEnum.AMBOS);

        assertThat(unidadeService.listarPagina(new UnidadeFiltroDTO("77999990000", null, null, null), 0, 20)
                .totalElementos()).isZero();
        assertThat(unidadeService.listarPagina(new UnidadeFiltroDTO(sufixo, null, null, grupo.getId()), 0, 20)
                .conteudo()).extracting(UnidadeViewDTO::nome).containsExactly("ComGrupo " + sufixo);
    }

    // ---- Grupos de relatório

    private GrupoRelatorio grupo(String nome, boolean ativo, boolean hospital) {
        GrupoRelatorio g = new GrupoRelatorio();
        g.setNome(nome + " " + sufixo);
        g.setCodigo("G" + nome + sufixo);
        g.setAtivo(ativo);
        g.setDirecionadoHospital(hospital);
        return grupoRepository.saveAndFlush(g);
    }

    private GrupoRelatorioFiltroDTO filtroGrupo(String status, String hospital) {
        return new GrupoRelatorioFiltroDTO(sufixo, status, hospital);
    }

    @Test
    @DisplayName("Grupos: ordem por nome, filtros de status e hospital, total e validação")
    void grupos() {
        grupo("Beta", true, true);
        grupo("Alfa", false, false);
        grupo("Gama", true, false);

        PaginaDTO<GrupoRelatorioListaDTO> todos = grupoService.listarPagina(filtroGrupo(null, null), 0, 20);
        assertThat(todos.totalElementos()).isEqualTo(3);
        assertThat(todos.conteudo()).extracting(GrupoRelatorioListaDTO::nome)
                .containsExactly("Alfa " + sufixo, "Beta " + sufixo, "Gama " + sufixo);

        assertThat(grupoService.listarPagina(filtroGrupo("ativo", null), 0, 20).conteudo())
                .extracting(GrupoRelatorioListaDTO::nome).containsExactly("Beta " + sufixo, "Gama " + sufixo);
        assertThat(grupoService.listarPagina(filtroGrupo("inativo", null), 0, 20).conteudo())
                .extracting(GrupoRelatorioListaDTO::nome).containsExactly("Alfa " + sufixo);
        assertThat(grupoService.listarPagina(filtroGrupo(null, "sim"), 0, 20).conteudo())
                .extracting(GrupoRelatorioListaDTO::nome).containsExactly("Beta " + sufixo);
        assertThat(grupoService.listarPagina(filtroGrupo(null, "nao"), 0, 20).totalElementos()).isEqualTo(2);

        assertThatThrownBy(() -> grupoService.listarPagina(filtroGrupo("talvez", null), 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Profissionais

    private Profissional profissional(String nome, String conselho, boolean ativo, Unidade unidade) {
        Profissional p = new Profissional();
        p.setNome(nome + " " + sufixo);
        p.setConselho(conselho);
        p.setNumeroRegistro("123");
        p.setAtivo(ativo);
        p.setUnidade(unidade);
        return profissionalRepository.saveAndFlush(p);
    }

    @Test
    @DisplayName("Profissionais: sem filtros novos a busca por nome é a de sempre; q, ativo e unidade filtram")
    void profissionais() {
        Unidade u = unidade("Base", true, TipoUnidadeEnum.AMBOS);
        profissional("Beto", "CRM", true, u);
        profissional("Ana", "COREN", false, null);

        Page<ProfissionalViewDTO> pelaBuscaAntiga = profissionalService.buscar(0, 10, sufixo);
        assertThat(pelaBuscaAntiga.getTotalElements()).isEqualTo(2);
        assertThat(pelaBuscaAntiga.getContent()).extracting(ProfissionalViewDTO::nome)
                .containsExactly("Ana " + sufixo, "Beto " + sufixo);

        assertThat(profissionalService.buscar(0, 10, null, "coren " + sufixo, null, null).getContent())
                .extracting(ProfissionalViewDTO::nome).containsExactly("Ana " + sufixo);
        assertThat(profissionalService.buscar(0, 10, sufixo, null, true, null).getContent())
                .extracting(ProfissionalViewDTO::nome).containsExactly("Beto " + sufixo);
        assertThat(profissionalService.buscar(0, 10, sufixo, null, false, null).getTotalElements()).isEqualTo(1);
        assertThat(profissionalService.buscar(0, 10, sufixo, null, null, u.getId()).getContent())
                .extracting(ProfissionalViewDTO::nome).containsExactly("Beto " + sufixo);
    }
}
