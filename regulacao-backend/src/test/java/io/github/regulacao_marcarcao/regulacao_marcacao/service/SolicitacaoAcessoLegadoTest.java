package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CidRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.UnidadeAcessoService.UnidadeContexto;

/**
 * Compatibilidade do controle de acesso por unidade com os REGISTROS ANTIGOS.
 *
 * O controle por unidade foi introduzido depois de a base ja existir. As
 * migracoes de backfill (V73/V76/V77) so conseguem vincular
 * {@code solicitacao.unidade_id} quando {@code usf_origem} esta preenchido e casa
 * com alguma unidade cadastrada — o que sobra e uma solicitacao "orfa".
 *
 * Estes testes fixam a regra que impede esse legado de quebrar: registro sem
 * unidade nao e tratado como "dado de outra unidade".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolicitacaoAcessoLegadoTest {

    private static final String CPF_RESTRITO = "22222222222";
    private static final Long MINHA_UNIDADE = 7L;
    private static final Long OUTRA_UNIDADE = 8L;

    @Mock private SolicitacaoRepository solicitacaoRepository;
    @Mock private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Mock private SolicitacaoEspecialidadeRepository especialidadeRepository;
    @Mock private CidRepository cidRepository;
    @Mock private EspecialidadeRepository especialidadeRepo;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private UserRepository userRepository;
    @Mock private UnidadeRepository unidadeRepository;
    @Mock private UnidadeAcessoService unidadeAcessoService;

    @InjectMocks private SolicitacaoService service;

    private Solicitacao solicitacaoDaUnidade(Long unidadeId) {
        Solicitacao s = new Solicitacao();
        s.setId(1L);
        s.setNomePaciente("Paciente Legado");
        s.setEspecialidades(List.of());
        if (unidadeId != null) {
            Unidade u = new Unidade();
            u.setId(unidadeId);
            u.setNome("Unidade " + unidadeId);
            s.setUnidade(u);
        }
        return s;
    }

    private void dadoUsuarioRestritoA(Long unidadeId) {
        lenient().when(unidadeAcessoService.contextoDe(CPF_RESTRITO))
                .thenReturn(new UnidadeContexto(unidadeId));
    }

    private void dadoUsuarioGlobal(String cpf) {
        lenient().when(unidadeAcessoService.contextoDe(cpf))
                .thenReturn(UnidadeContexto.GLOBAL);
    }

    // ==================================================================
    // Registros legados sem unidade vinculada
    // ==================================================================

    @Test
    @DisplayName("LEGADO: solicitacao sem unidade continua acessivel por usuario restrito")
    void solicitacaoOrfaNaoEBloqueada() {
        dadoUsuarioRestritoA(MINHA_UNIDADE);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacaoDaUnidade(null)));

        assertThatCode(() -> service.getSolicitacaoById(1L, CPF_RESTRITO))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LEGADO: solicitacao sem unidade continua editavel por usuario restrito")
    void solicitacaoOrfaContinuaEditavel() {
        dadoUsuarioRestritoA(MINHA_UNIDADE);
        Solicitacao orfa = solicitacaoDaUnidade(null);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(orfa));
        when(solicitacaoRepository.save(orfa)).thenReturn(orfa);
        when(unidadeAcessoService.resolverUnidadeAlvo(CPF_RESTRITO, null)).thenReturn(MINHA_UNIDADE);
        when(unidadeRepository.findById(MINHA_UNIDADE)).thenReturn(Optional.empty());

        var dto = new io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoUpdateDTO(
                null, "Paciente Legado", null, null, null, null, null, null, null, null, null, null, null);

        assertThatCode(() -> service.updateSolicitacao(1L, dto, CPF_RESTRITO))
                .doesNotThrowAnyException();
    }

    /**
     * Editar um registro antigo sem preencher os campos novos precisa funcionar:
     * e o caso de corrigir apenas o telefone de um paciente cadastrado em 2024.
     */
    @Test
    @DisplayName("LEGADO: edicao sem os campos novos (nomePai/nomeMae/endereco) e aceita")
    void edicaoSemOsCamposNovosEAceita() {
        dadoUsuarioGlobal("admin");
        Solicitacao antiga = solicitacaoDaUnidade(MINHA_UNIDADE);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(antiga));
        when(solicitacaoRepository.save(antiga)).thenReturn(antiga);
        when(unidadeAcessoService.resolverUnidadeAlvo("admin", null)).thenReturn(null);

        var dto = new io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoUpdateDTO(
                null, "Paciente Legado", "obs", "700000000000000", "(75)90000-0000",
                null, null, null, // nomePai, nomeMae, endereco ausentes
                null, null, null, null, null);

        assertThatCode(() -> service.updateSolicitacao(1L, dto, "admin"))
                .doesNotThrowAnyException();
    }

    // ==================================================================
    // A segregacao continua valendo onde ha unidade
    // ==================================================================

    @Test
    @DisplayName("Solicitacao de OUTRA unidade permanece bloqueada")
    void solicitacaoDeOutraUnidadeEBloqueada() {
        dadoUsuarioRestritoA(MINHA_UNIDADE);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacaoDaUnidade(OUTRA_UNIDADE)));

        assertThatThrownBy(() -> service.getSolicitacaoById(1L, CPF_RESTRITO))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("outra unidade");
    }

    @Test
    @DisplayName("Solicitacao da PROPRIA unidade e acessivel")
    void solicitacaoDaPropriaUnidadeEAcessivel() {
        dadoUsuarioRestritoA(MINHA_UNIDADE);
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacaoDaUnidade(MINHA_UNIDADE)));

        assertThatCode(() -> service.getSolicitacaoById(1L, CPF_RESTRITO))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADMIN global acessa qualquer unidade, inclusive registros orfaos")
    void adminGlobalAcessaTudo() {
        dadoUsuarioGlobal("admin");

        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacaoDaUnidade(OUTRA_UNIDADE)));
        assertThatCode(() -> service.getSolicitacaoById(1L, "admin")).doesNotThrowAnyException();

        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacaoDaUnidade(null)));
        assertThatCode(() -> service.getSolicitacaoById(1L, "admin")).doesNotThrowAnyException();
    }
}
