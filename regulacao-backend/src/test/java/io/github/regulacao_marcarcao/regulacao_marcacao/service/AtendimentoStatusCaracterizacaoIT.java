package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.ObservacaoRequestDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * CARACTERIZACAO do desfecho do atendimento como esta hoje, antes do modulo do
 * executante: o que "Confirmar" e "Faltou" gravam, o que fazem com a observacao do
 * agendamento e com a cota, e o que a exclusao do agendamento faz com itens que ja
 * tiveram desfecho.
 *
 * Estes testes descrevem o comportamento atual, nao o desejado — "Faltou" gravar
 * CANCELADO e sobrescrever a observacao de quem agendou e conhecido. Eles existem
 * para acusar qualquer mudanca nesse fluxo legado, que o modulo novo nao altera.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class AtendimentoStatusCaracterizacaoIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String OBSERVACAO_DE_QUEM_AGENDOU = "agendado no teste de caracterizacao";

    @Autowired private SolicitacaoEspecialidadeService especialidadeService;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    @Autowired private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private String sufixo;
    private Unidade unidade;
    private GrupoRelatorio grupo;
    private Especialidade hemograma;
    private Especialidade glicemia;
    private CotaUnidade cotaHemograma;
    private CotaUnidade cotaGlicemia;
    private User operadorDaUnidade;
    private LocalDate data;

    @BeforeEach
    void setUp() {
        sufixo = "_ATEND_CARAC_" + System.nanoTime();
        data = LocalDate.now();

        unidade = new Unidade();
        unidade.setNome("Unidade A" + sufixo);
        unidade.setCodigo("UA" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        grupo = new GrupoRelatorio();
        grupo.setCodigo("lab" + sufixo);
        grupo.setNome("Laboratorio" + sufixo);
        grupo.setAtivo(true);
        grupo.setDirecionadoHospital(true);
        grupo = grupoRelatorioRepository.saveAndFlush(grupo);

        hemograma = exame("HEMO");
        glicemia = exame("GLIC");
        cotaHemograma = cota(hemograma);
        cotaGlicemia = cota(glicemia);

        // Operador lotado na unidade: NAO e admin global, portanto consome cota.
        operadorDaUnidade = new User();
        operadorDaUnidade.setCpf(cpfUnico());
        operadorDaUnidade.setNome("Operador" + sufixo);
        operadorDaUnidade.setPassword("x");
        operadorDaUnidade.setRole(Roles.ADMIN_UNIDADE);
        operadorDaUnidade.setAtivo(true);
        operadorDaUnidade.setUnidade(unidade);
        operadorDaUnidade = userRepository.saveAndFlush(operadorDaUnidade);
    }

    @Test
    @DisplayName("Confirmar grava REALIZADO, mantem o agendamento e a observacao, e nao devolve a vaga")
    void confirmarGravaRealizado() {
        Long agendamentoId = agendar(hemograma);
        Long itemId = itensDo(agendamentoId).get(0).getId();

        especialidadeService.confirmarProcedimento(itemId);
        limpar();

        SolicitacaoEspecialidade item = solicitacaoEspecialidadeRepository.findById(itemId).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(StatusDaMarcacao.REALIZADO);
        assertThat(item.getAgendamentoSolicitacao().getId()).isEqualTo(agendamentoId);
        assertThat(item.getAgendamentoSolicitacao().getObservacoes()).isEqualTo(OBSERVACAO_DE_QUEM_AGENDOU);
        assertThat(utilizada(cotaHemograma)).isEqualTo(1);
    }

    @Test
    @DisplayName("Faltou grava CANCELADO (nao FALTOU), sobrescreve a observacao do agendamento e nao devolve a vaga")
    void faltouGravaCanceladoESobrescreveAObservacao() {
        Long agendamentoId = agendar(hemograma);
        Long itemId = itensDo(agendamentoId).get(0).getId();

        especialidadeService.faltouProcedimento(itemId, new ObservacaoRequestDTO("paciente nao compareceu"));
        limpar();

        SolicitacaoEspecialidade item = solicitacaoEspecialidadeRepository.findById(itemId).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(StatusDaMarcacao.CANCELADO);
        assertThat(item.getAgendamentoSolicitacao().getId()).isEqualTo(agendamentoId);
        assertThat(item.getAgendamentoSolicitacao().getObservacoes()).isEqualTo("paciente nao compareceu");
        assertThat(utilizada(cotaHemograma)).isEqualTo(1);
    }

    @Test
    @DisplayName("Faltou sem observacao (nula ou em branco) preserva a observacao do agendamento")
    void faltouSemObservacaoPreservaADoAgendamento() {
        Long primeiro = agendar(hemograma);
        Long segundo = agendar(hemograma);

        especialidadeService.faltouProcedimento(itensDo(primeiro).get(0).getId(), null);
        especialidadeService.faltouProcedimento(itensDo(segundo).get(0).getId(), new ObservacaoRequestDTO("   "));
        limpar();

        for (Long agendamentoId : List.of(primeiro, segundo)) {
            assertThat(itensDo(agendamentoId)).singleElement()
                    .extracting(SolicitacaoEspecialidade::getStatus).isEqualTo(StatusDaMarcacao.CANCELADO);
            assertThat(agendamentoRepository.findById(agendamentoId).orElseThrow().getObservacoes())
                    .isEqualTo(OBSERVACAO_DE_QUEM_AGENDOU);
        }
    }

    @Test
    @DisplayName("A agenda por grupo so lista AGENDADO: quem foi confirmado ou faltou sai da contagem")
    void itemComDesfechoSaiDaAgendaPorGrupo() {
        Long confirmado = agendar(hemograma);
        Long faltou = agendar(hemograma);
        agendar(hemograma);
        String cpf = operadorDaUnidade.getCpf();
        limpar();
        assertThat(especialidadeService.contarPacientesAgendadosPorDataEGrupo(grupo.getCodigo(), data, null, cpf))
                .isEqualTo(3);

        especialidadeService.confirmarProcedimento(itensDo(confirmado).get(0).getId());
        especialidadeService.faltouProcedimento(itensDo(faltou).get(0).getId(), null);
        limpar();

        assertThat(especialidadeService.contarPacientesAgendadosPorDataEGrupo(grupo.getCodigo(), data, null, cpf))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Excluir o agendamento devolve TODOS os itens a AGUARDANDO, inclusive os ja confirmados, e estorna a cota de cada um")
    void excluirAgendamentoComItensMistosDevolveTodosAFila() {
        Long agendamentoId = agendar(hemograma, glicemia);
        List<SolicitacaoEspecialidade> itens = itensDo(agendamentoId);
        Long itemHemograma = itemDe(itens, hemograma);
        Long itemGlicemia = itemDe(itens, glicemia);
        assertThat(utilizada(cotaHemograma)).isEqualTo(1);
        assertThat(utilizada(cotaGlicemia)).isEqualTo(1);

        especialidadeService.confirmarProcedimento(itemHemograma);
        especialidadeService.faltouProcedimento(itemGlicemia, null);
        limpar();

        agendamentoService.deleteAgendamento(agendamentoId, operadorDaUnidade.getCpf());
        limpar();

        assertThat(agendamentoRepository.findById(agendamentoId)).isEmpty();
        for (Long itemId : List.of(itemHemograma, itemGlicemia)) {
            SolicitacaoEspecialidade item = solicitacaoEspecialidadeRepository.findById(itemId).orElseThrow();
            assertThat(item.getStatus()).isEqualTo(StatusDaMarcacao.AGUARDANDO);
            assertThat(item.getAgendamentoSolicitacao()).isNull();
        }
        assertThat(utilizada(cotaHemograma)).isZero();
        assertThat(utilizada(cotaGlicemia)).isZero();
    }

    // ------------------------------------------------------------------

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private Especialidade exame(String prefixo) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        e.setGrupoRelatorio(grupo);
        return especialidadeRepository.saveAndFlush(e);
    }

    private CotaUnidade cota(Especialidade exame) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(exame);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(data.format(PERIODO_MENSAL));
        c.setQuantidadeTotal(10);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        return cotaRepository.saveAndFlush(c);
    }

    private int utilizada(CotaUnidade cota) {
        return cotaRepository.findById(cota.getId()).orElseThrow().getQuantidadeUtilizada();
    }

    /** Cria o pedido com os exames dados e agenda todos num unico agendamento, pelo fluxo real. */
    private Long agendar(Especialidade... exames) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);

        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        List<String> codigos = new ArrayList<>();
        for (Especialidade exame : exames) {
            SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
            se.setSolicitacao(s);
            se.setEspecialidadeSolicitada(exame);
            se.setEspecialidadeCodigoLegacy(exame.getCodigo());
            se.setStatus(StatusDaMarcacao.AGUARDANDO);
            se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
            itens.add(se);
            codigos.add(exame.getCodigo());
        }
        s.setEspecialidades(itens);
        s = solicitacaoRepository.saveAndFlush(s);

        return agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(codigos, data, null, null, TurnoEnum.MANHA,
                        OBSERVACAO_DE_QUEM_AGENDOU, null, null, null),
                operadorDaUnidade.getCpf()).id();
    }

    private List<SolicitacaoEspecialidade> itensDo(Long agendamentoId) {
        return solicitacaoEspecialidadeRepository.findByAgendamentoSolicitacaoId(agendamentoId);
    }

    private Long itemDe(List<SolicitacaoEspecialidade> itens, Especialidade exame) {
        return itens.stream()
                .filter(i -> exame.getId().equals(i.getEspecialidadeSolicitada().getId()))
                .findFirst().orElseThrow().getId();
    }
}
