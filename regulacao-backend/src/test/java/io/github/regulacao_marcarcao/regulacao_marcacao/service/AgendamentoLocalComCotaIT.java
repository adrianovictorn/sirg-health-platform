package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
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
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * Regressao: unidade (ADMIN_UNIDADE) com cota agendando para um local que tem
 * cidade cadastrada — o caso real da tela /agendar em producao.
 *
 * <p>{@code consumirVaga} usa {@code clearAutomatically = true} e desanexa tudo
 * que o servico ja tinha carregado, inclusive o proxy LAZY de
 * {@code LocalAgendamento.cidade}. Montar a resposta lendo esse proxy estourava
 * {@code LazyInitializationException} (500, que a tela via como 403 vazio,
 * "Acesso negado"). Os testes de {@link AgendamentoCotaFluxoIT} nao pegavam
 * porque sempre agendam sem local.
 *
 * <p>O {@code flush} + {@code clear} antes de agendar e essencial: sem ele o
 * local e a cidade seriam as instancias reais criadas no proprio teste, e nao
 * proxies lidos do banco como em producao.
 */
@SpringBootTest
@Transactional
class AgendamentoLocalComCotaIT {

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CidadeRepository cidadeRepository;
    @Autowired private LocalAgendamentoRepository localAgendamentoRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void unidadeComCotaAgendaEmLocalComCidade() {
        String sufixo = "_IT_" + System.nanoTime();
        LocalDate data = LocalDate.now();

        Unidade unidade = new Unidade();
        unidade.setNome("Unidade Local" + sufixo);
        unidade.setCodigo("UL" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        Especialidade usg = new Especialidade();
        usg.setCodigo("USG" + sufixo);
        usg.setNome("USG" + sufixo);
        usg.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        usg.setAtivo(true);
        usg.setVagas(0);
        usg = especialidadeRepository.saveAndFlush(usg);

        User operador = new User();
        operador.setCpf(String.format("%011d", System.nanoTime() % 100_000_000_000L));
        operador.setNome("Operador da Unidade");
        operador.setPassword("x");
        operador.setRole(Roles.ADMIN_UNIDADE);
        operador.setAtivo(true);
        operador.setUnidade(unidade);
        operador = userRepository.saveAndFlush(operador);

        // Mesmo cenario da tela: sem cota mensal, so a cota da data (0/1).
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setEspecialidade(usg);
        cota.setTipoPeriodo(TipoPeriodoCota.DATA);
        cota.setDataEspecifica(data);
        cota.setQuantidadeTotal(1);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cota = cotaRepository.saveAndFlush(cota);

        Cidade cidade = new Cidade();
        cidade.setNomeCidade("Cidade" + sufixo);
        cidade = cidadeRepository.saveAndFlush(cidade);

        LocalAgendamento local = new LocalAgendamento();
        local.setNomeLocal("Policlinica" + sufixo);
        local.setCidade(cidade);
        local = localAgendamentoRepository.saveAndFlush(local);

        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sufixo);
        s.setCpfPaciente(String.format("%011d", (System.nanoTime() + 7) % 100_000_000_000L));
        s.setCns("700000000000001");
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(usg);
        se.setEspecialidadeCodigoLegacy(usg.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> especs = new ArrayList<>();
        especs.add(se);
        s.setEspecialidades(especs);
        s = solicitacaoRepository.saveAndFlush(s);

        entityManager.flush();
        entityManager.clear();

        var dto = new MultiAgendamentoCreateDTO(
                List.of(usg.getCodigo()), data, null, local.getId(),
                TurnoEnum.MANHA, "regressao local com cidade", null, null, null);

        var resposta = agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, operador.getCpf());

        assertThat(resposta.localAgendado()).isEqualTo("Policlinica" + sufixo + " - Cidade" + sufixo);
        assertThat(cotaRepository.findById(cota.getId()).orElseThrow().getQuantidadeUtilizada()).isEqualTo(1);
    }
}
