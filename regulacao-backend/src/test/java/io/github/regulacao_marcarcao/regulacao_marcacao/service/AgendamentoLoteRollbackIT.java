package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
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
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Caracterizacao do tudo-ou-nada do agendamento em lote: quando a cota do 2o
 * item esta esgotada, a vaga ja consumida pelo 1o item e desfeita e nenhum item
 * fica agendado.
 *
 * <b>Por que esta classe NAO e @Transactional:</b> mesmo motivo de
 * {@code CotaRollbackIT} — a transacao do teste absorveria a excecao e
 * mascararia o rollback. Como nada e desfeito automaticamente, o
 * {@code @AfterEach} remove tudo o que foi criado, na ordem inversa das
 * dependencias.
 */
@SpringBootTest
class AgendamentoLoteRollbackIT {

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;

    private final List<Long> solicitacoesCriadas = new ArrayList<>();
    private final List<Long> cotasCriadas = new ArrayList<>();
    private final List<Long> especialidadesCriadas = new ArrayList<>();
    private final List<Long> usuariosCriados = new ArrayList<>();
    private final List<Long> unidadesCriadas = new ArrayList<>();

    private String sfx;
    private LocalDate data;
    private Unidade unidade;

    @AfterEach
    void limpar() {
        solicitacoesCriadas.forEach(id -> solicitacaoRepository.deleteById(id));
        cotasCriadas.forEach(id -> cotaRepository.deleteById(id));
        especialidadesCriadas.forEach(id -> especialidadeRepository.deleteById(id));
        usuariosCriados.forEach(id -> userRepository.deleteById(id));
        unidadesCriadas.forEach(id -> unidadeRepository.deleteById(id));
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private Especialidade especialidade(String prefixo) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sfx);
        e.setNome(prefixo + sfx);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        e = especialidadeRepository.save(e);
        especialidadesCriadas.add(e.getId());
        return e;
    }

    private CotaUnidade cota(Especialidade especialidade, int total) {
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setEspecialidade(especialidade);
        cota.setTipoPeriodo(TipoPeriodoCota.DATA);
        cota.setDataEspecifica(data);
        cota.setQuantidadeTotal(total);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cota = cotaRepository.save(cota);
        cotasCriadas.add(cota.getId());
        return cota;
    }

    private SolicitacaoEspecialidade item(Solicitacao s, Especialidade especialidade) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(especialidade);
        se.setEspecialidadeCodigoLegacy(especialidade.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        return se;
    }

    @Test
    @DisplayName("BANCO REAL: cota do 2o item esgotada desfaz a vaga do 1o e nao agenda nenhum")
    void cotaDoSegundoItemEsgotadaDesfazOPrimeiro() {
        sfx = "_LOTE_RB_" + System.nanoTime();
        data = LocalDate.now();

        unidade = new Unidade();
        unidade.setNome("Unidade Lote Rollback" + sfx);
        unidade.setCodigo("ULR" + sfx);
        unidade.setAtivo(true);
        unidade = unidadeRepository.save(unidade);
        unidadesCriadas.add(unidade.getId());

        User operador = new User();
        operador.setCpf(cpfUnico());
        operador.setNome("Operador" + sfx);
        operador.setPassword("x");
        operador.setRole(Roles.ADMIN_UNIDADE);
        operador.setAtivo(true);
        operador.setUnidade(unidade);
        operador = userRepository.save(operador);
        usuariosCriados.add(operador.getId());

        Especialidade a = especialidade("A");
        Especialidade c = especialidade("C");
        // A tem vaga...
        CotaUnidade cotaA = cota(a, 1);
        // ...mas a cota de C ja nasce esgotada (0 vagas).
        CotaUnidade cotaC = cota(c, 0);

        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sfx);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        itens.add(item(s, a));
        itens.add(item(s, c));
        s.setEspecialidades(itens);
        s = solicitacaoRepository.save(s);
        solicitacoesCriadas.add(s.getId());

        final Long solicitacaoId = s.getId();
        final List<Long> itemIds = s.getEspecialidades().stream().map(SolicitacaoEspecialidade::getId).toList();
        final String cpf = operador.getCpf();
        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(a.getCodigo(), c.getCodigo()), data, null, null, TurnoEnum.MANHA, "rollback lote",
                null, null, null);

        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(solicitacaoId, dto, cpf))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada");

        // O ponto do teste: a vaga de A NAO pode ter ficado consumida.
        assertThat(cotaRepository.findById(cotaA.getId()).orElseThrow().getQuantidadeUtilizada())
                .as("consumo da cota do 1o item deveria ter sido desfeito pelo rollback")
                .isZero();
        assertThat(cotaRepository.findById(cotaC.getId()).orElseThrow().getQuantidadeUtilizada()).isZero();
        for (Long itemId : itemIds) {
            SolicitacaoEspecialidade item = solicitacaoEspecialidadeRepository.findById(itemId).orElseThrow();
            assertThat(item.getStatus()).as("o pedido deveria continuar na fila").isEqualTo(StatusDaMarcacao.AGUARDANDO);
            assertThat(item.getAgendamentoSolicitacao()).as("nenhum agendamento deveria ter sido gravado").isNull();
        }
    }
}
