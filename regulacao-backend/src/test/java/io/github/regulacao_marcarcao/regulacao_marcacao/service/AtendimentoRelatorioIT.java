package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoCancelamentoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoColetaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoFaltaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * FLUXO REAL: Relatorio de Atendimento em Excel (Confirmado, Faltou, Cancelado e
 * Data da Coleta), gerado por quem opera a tela de Atendimentos.
 *
 * A base pode ter dados reais: o periodo do teste fica tres anos a frente, onde nao
 * ha agenda nenhuma, e e isso que permite afirmar o conteudo exato da planilha.
 * <b>Nao suja a base:</b> classe {@code @Transactional}.
 */
@SpringBootTest
@Transactional
class AtendimentoRelatorioIT {

    private static final int PRIMEIRA_LINHA_DE_DADOS = 7;

    @Autowired private ExcelService excelService;
    @Autowired private AtendimentoExecutanteService atendimentoService;
    @Autowired private SolicitacaoEspecialidadeService legadoService;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository itemRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private String sufixo;
    private Unidade unidadeA;
    private Especialidade hemograma;
    private User admin;
    private User executante;
    private LocalDate dia;

    @BeforeEach
    void setUp() {
        sufixo = "_REL_ATEND_IT_" + System.nanoTime();
        dia = LocalDate.now(AtendimentoExecutanteService.FUSO).plusYears(3);

        unidadeA = new Unidade();
        unidadeA.setNome("Unidade A" + sufixo);
        unidadeA.setCodigo("UA" + sufixo);
        unidadeA.setAtivo(true);
        unidadeA = unidadeRepository.saveAndFlush(unidadeA);

        hemograma = new Especialidade();
        hemograma.setCodigo("HEMO" + sufixo);
        hemograma.setNome("Hemograma" + sufixo);
        hemograma.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        hemograma.setAtivo(true);
        hemograma.setVagas(0);
        hemograma = especialidadeRepository.saveAndFlush(hemograma);

        admin = usuario(Roles.ADMIN);
        executante = usuario(Roles.USER);
    }

    @Test
    @DisplayName("Planilha traz uma linha por desfecho, com CPF/CNS completos, rotulos e a data da coleta; agendado fica fora")
    void conteudoDaPlanilha() throws IOException {
        String cpf = executante.getCpf();
        SolicitacaoEspecialidade confirmado = agendar(unidadeA, "Ana", dia);
        atendimentoService.informarDataColeta(confirmado.getId(), new AtendimentoColetaDTO(dia), cpf);
        atendimentoService.confirmar(confirmado.getId(), cpf);
        SolicitacaoEspecialidade faltou = agendar(unidadeA, "Bruno", dia);
        atendimentoService.registrarFalta(faltou.getId(), new AtendimentoFaltaDTO("=nao veio"), cpf);
        SolicitacaoEspecialidade cancelado = agendar(null, "Carla", dia.plusDays(1));
        atendimentoService.cancelar(cancelado.getId(), new AtendimentoCancelamentoDTO("aparelho quebrado"), cpf);
        // Falta antiga pelo fluxo legado: CANCELADO, sem observacao nem autoria.
        SolicitacaoEspecialidade legado = agendar(unidadeA, "Davi", dia.plusDays(1));
        legadoService.faltouProcedimento(legado.getId(), null);
        agendar(unidadeA, "Elisa", dia); // segue AGENDADO: nao e desfecho
        agendar(unidadeA, "Fora do periodo", dia.plusDays(5));
        String cpfDaAna = confirmado.getSolicitacao().getCpfPaciente();
        String cnsDaAna = confirmado.getSolicitacao().getCns();
        limpar();

        // Visao municipal (ADMIN); o USER sem executante configurada nao ve nada (ADR 0013).
        List<List<String>> linhas = lerLinhas(dia, dia.plusDays(1), admin);

        assertThat(linhas).hasSize(4);
        assertThat(linhas).extracting(l -> l.get(1))
                .containsExactly("Ana" + sufixo, "Bruno" + sufixo, "Davi" + sufixo, "Carla" + sufixo);

        List<String> ana = linhas.get(0);
        assertThat(ana.get(3)).isEqualTo(cpfDaAna).hasSize(11);
        assertThat(ana.get(4)).isEqualTo(cnsDaAna).hasSize(15);
        assertThat(ana.get(5)).isEqualTo(unidadeA.getNome());
        assertThat(ana.get(6)).isEqualTo(hemograma.getNome());
        assertThat(ana.get(7)).isEqualTo("Confirmado");
        assertThat(ana.get(8)).isNotBlank();
        assertThat(ana.get(10)).isEqualTo(executante.getNome());
        assertThat(ana.get(11)).isNotBlank();

        assertThat(linhas.get(1).get(7)).isEqualTo("Faltou");
        // Texto livre do operador entra como texto, nunca como formula.
        assertThat(linhas.get(1).get(9)).isEqualTo("=nao veio");

        List<String> davi = linhas.get(2);
        assertThat(davi.get(7)).isEqualTo("Cancelado");
        assertThat(davi.get(9)).isEmpty();
        assertThat(davi.get(10)).isEmpty();
        assertThat(davi.get(11)).isEmpty();

        List<String> carla = linhas.get(3);
        assertThat(carla.get(5)).isEqualTo("Sem unidade");
        assertThat(carla.get(7)).isEqualTo("Cancelado");
        assertThat(carla.get(9)).isEqualTo("aparelho quebrado");

        // USER sem executante gera planilha valida, so com o aviso; periodo sem nada tambem.
        assertThat(lerLinhas(dia, dia.plusDays(1), executante)).singleElement()
                .satisfies(l -> assertThat(l.get(0)).startsWith("Nenhum atendimento"));
        assertThat(lerLinhas(dia.plusDays(20), dia.plusDays(21), admin)).singleElement()
                .satisfies(l -> assertThat(l.get(0)).startsWith("Nenhum atendimento"));
    }

    @Test
    @DisplayName("Periodo: obrigatorio, em ordem e de no maximo 31 dias — senao 400")
    void periodoInvalido() throws IOException {
        String cpf = executante.getCpf();

        assertThatThrownBy(() -> excelService.gerarPlanilhaAtendimentos(null, dia, null, cpf))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> excelService.gerarPlanilhaAtendimentos(dia, null, null, cpf))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> excelService.gerarPlanilhaAtendimentos(dia, dia.minusDays(1), null, cpf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("anterior");
        assertThatThrownBy(() -> excelService.gerarPlanilhaAtendimentos(dia, dia.plusDays(31), null, cpf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("31");

        // 31 dias, contando o primeiro e o ultimo, ainda cabe.
        assertThat(excelService.gerarPlanilhaAtendimentos(dia, dia.plusDays(30), null, cpf).available()).isPositive();
    }

    @Test
    @DisplayName("So quem opera atendimentos gera o relatorio: os demais perfis levam acesso negado")
    void outrosPerfisSaoNegados() {
        for (Roles role : List.of(Roles.GESTOR, Roles.ADMIN_UNIDADE, Roles.RECEPCAO, Roles.ENFERMEIRO,
                Roles.MEDICO, Roles.COORD_TRANSPORTE, Roles.PACIENTE)) {
            String cpf = usuario(role).getCpf();
            assertThatThrownBy(() -> excelService.gerarPlanilhaAtendimentos(dia, dia, null, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> excelService.gerarPlanilhaAtendimentos(dia, dia, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    /** Linhas de dados da planilha, cada celula como texto. Falha se alguma celula nao for texto. */
    private List<List<String>> lerLinhas(LocalDate inicio, LocalDate fim, User quem) throws IOException {
        List<List<String>> linhas = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                excelService.gerarPlanilhaAtendimentos(inicio, fim, null, quem.getCpf()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(PRIMEIRA_LINHA_DE_DADOS - 1).getCell(0).getStringCellValue()).isEqualTo("DATA");
            for (int r = PRIMEIRA_LINHA_DE_DADOS; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> linha = new ArrayList<>();
                for (Cell cell : row) {
                    assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
                    linha.add(cell.getStringCellValue());
                }
                linhas.add(linha);
            }
        }
        return linhas;
    }

    private User usuario(Roles role) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        if (role == Roles.ADMIN_UNIDADE || role == Roles.RECEPCAO) {
            u.setUnidade(unidadeA);
        }
        return userRepository.saveAndFlush(u);
    }

    /** Pede o exame e agenda para a data dada pelo fluxo real. Devolve o item agendado. */
    private SolicitacaoEspecialidade agendar(Unidade daUnidade, String nome, LocalDate data) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(daUnidade);

        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(hemograma);
        se.setEspecialidadeCodigoLegacy(hemograma.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        itens.add(se);
        s.setEspecialidades(itens);
        s = solicitacaoRepository.saveAndFlush(s);

        Long agendamentoId = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(List.of(hemograma.getCodigo()), data, null, null, TurnoEnum.MANHA,
                        "agendado no teste", null, null, null),
                admin.getCpf()).id();
        em.flush();
        return itemRepository.findByAgendamentoSolicitacaoId(agendamentoId).get(0);
    }
}
