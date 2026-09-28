package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesImportacaoConfirmarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesImportacaoPreviaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesImportacaoResultadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesProfissionalLinhaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.SituacaoLinhaImportacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cbo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.ProfissionalVinculo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemVinculoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CboRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalVinculoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;

/**
 * Importacao de profissionais do CNES: o que cada linha do arquivo vira.
 *
 * <p>Cobre as duas etapas separadamente, porque elas respondem perguntas
 * diferentes: a PREVIA classifica sem gravar (e o que a tela mostra para o
 * operador conferir), a CONFIRMACAO grava reconferindo tudo. O caso central e a
 * REIMPORTACAO — a coordenacao sobe o arquivo inteiro de novo quando muda uma
 * linha, e isso nao pode duplicar profissional nem vinculo.
 *
 * <p>A leitura do arquivo em si esta em {@code CnesProfissionalCsvServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfissionalImportacaoServiceTest {

    private static final String CPF_MARIA = "12345678901";
    private static final String CPF_JOAO = "98765432100";
    private static final String CNES_POLICLINICA = "2514648";
    private static final String CBO_MEDICO = "225125";

    @Mock private CnesProfissionalCsvService csvService;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private ProfissionalVinculoRepository vinculoRepository;
    @Mock private UnidadeRepository unidadeRepository;
    @Mock private CboService cboService;
    @Mock private CboRepository cboRepository;

    @InjectMocks private ProfissionalImportacaoService service;

    private Unidade policlinica;
    private Cbo medicoClinico;

    @BeforeEach
    void setUp() {
        policlinica = new Unidade();
        policlinica.setId(7L);
        policlinica.setNome("Policlínica Municipal");
        policlinica.setCnes(CNES_POLICLINICA);

        medicoClinico = new Cbo();
        medicoClinico.setId(3L);
        medicoClinico.setCodigo(CBO_MEDICO);
        medicoClinico.setDescricao("Médico clínico");

        when(unidadeRepository.findAll()).thenReturn(List.of(policlinica));
        when(unidadeRepository.findById(7L)).thenReturn(Optional.of(policlinica));
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(chamada -> {
            Profissional p = chamada.getArgument(0);
            if (p.getId() == null) {
                p.setId(99L);
            }
            return p;
        });
        when(vinculoRepository.save(any(ProfissionalVinculo.class))).thenAnswer(c -> c.getArgument(0));
        when(cboService.buscarOuCriar(eq(CBO_MEDICO), any()))
                .thenReturn(new CboService.Upsert(medicoClinico, false));
        when(cboRepository.findByCodigo(CBO_MEDICO)).thenReturn(Optional.of(medicoClinico));
    }

    // ------------------------------------------------------------------
    // Previa
    // ------------------------------------------------------------------

    @Test
    @DisplayName("previa classifica CPF novo como NOVO_PROFISSIONAL e nao grava nada")
    void previaClassificaProfissionalNovo() {
        when(csvService.ler(any(), any())).thenAnswer(comLinhas(linha(2, CPF_MARIA, "MARIA DE SOUZA")));
        when(profissionalRepository.findByCpf(CPF_MARIA)).thenReturn(Optional.empty());

        CnesImportacaoPreviaDTO previa = service.previa(new byte[] {1}, "profissionais.csv");

        assertThat(previa.linhas()).hasSize(1);
        assertThat(previa.linhas().get(0).situacao())
                .isEqualTo(SituacaoLinhaImportacaoEnum.NOVO_PROFISSIONAL);
        assertThat(previa.linhas().get(0).unidadeId()).isEqualTo(7L);
        assertThat(previa.importaveis()).isEqualTo(1);
        assertThat(previa.arquivo()).isEqualTo("profissionais.csv");

        // A previa e leitura: nenhuma gravacao pode sair dela.
        verify(profissionalRepository, never()).save(any());
        verify(vinculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("previa separa vinculo que falta (NOVO_VINCULO) de vinculo que ja existe (JA_EXISTE)")
    void previaSeparaVinculoNovoDeExistente() {
        Profissional maria = profissional(11L, CPF_MARIA, "Maria de Souza");
        when(csvService.ler(any(), any())).thenAnswer(comLinhas(
                linha(2, CPF_MARIA, "MARIA DE SOUZA"),
                linha(3, CPF_JOAO, "JOAO LIMA")));
        when(profissionalRepository.findByCpf(CPF_MARIA)).thenReturn(Optional.of(maria));
        when(profissionalRepository.findByCpf(CPF_JOAO))
                .thenReturn(Optional.of(profissional(12L, CPF_JOAO, "João Lima")));

        // Maria ja tem o vinculo; Joao ainda nao.
        when(vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboId(11L, 7L, 3L))
                .thenReturn(Optional.of(new ProfissionalVinculo()));
        when(vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboId(12L, 7L, 3L))
                .thenReturn(Optional.empty());

        CnesImportacaoPreviaDTO previa = service.previa(new byte[] {1}, "arquivo.csv");

        assertThat(previa.linhas()).extracting(CnesProfissionalLinhaDTO::situacao)
                .containsExactly(SituacaoLinhaImportacaoEnum.JA_EXISTE,
                        SituacaoLinhaImportacaoEnum.NOVO_VINCULO);
        assertThat(previa.jaExistentes()).isEqualTo(1);
        assertThat(previa.importaveis()).isEqualTo(1);
    }

    @Test
    @DisplayName("previa marca SEM_UNIDADE quando o CNES do arquivo nao esta cadastrado, e diz o que fazer")
    void previaMarcaSemUnidade() {
        CnesProfissionalLinhaDTO deOutroCnes = CnesProfissionalLinhaDTO.lida(
                2, "9999999", "HOSPITAL DE OUTRA CIDADE", CPF_MARIA, "MARIA DE SOUZA", CBO_MEDICO, "MEDICO");
        when(csvService.ler(any(), any())).thenAnswer(comLinhas(deOutroCnes));

        CnesImportacaoPreviaDTO previa = service.previa(new byte[] {1}, "arquivo.csv");

        assertThat(previa.linhas().get(0).situacao()).isEqualTo(SituacaoLinhaImportacaoEnum.SEM_UNIDADE);
        assertThat(previa.ignoradas()).isEqualTo(1);
        // O aviso precisa apontar o caminho: cadastrar o estabelecimento pelo CNES.
        assertThat(previa.avisos()).anySatisfy(a -> assertThat(a)
                .contains("9999999")
                .contains("Cadastre o estabelecimento"));
    }

    @Test
    @DisplayName("previa marca INVALIDA a linha com CPF fora do formato, mostrando o valor lido")
    void previaMarcaLinhaInvalida() {
        when(csvService.ler(any(), any())).thenAnswer(comLinhas(
                linha(2, "123", "MARIA DE SOUZA"),
                CnesProfissionalLinhaDTO.lida(3, CNES_POLICLINICA, "POLICLINICA", CPF_JOAO, null, CBO_MEDICO, "MEDICO")));

        CnesImportacaoPreviaDTO previa = service.previa(new byte[] {1}, "arquivo.csv");

        assertThat(previa.linhas()).extracting(CnesProfissionalLinhaDTO::situacao)
                .containsExactly(SituacaoLinhaImportacaoEnum.INVALIDA,
                        SituacaoLinhaImportacaoEnum.INVALIDA);
        assertThat(previa.linhas().get(0).detalhe()).contains("3 dígitos").contains("123");
        assertThat(previa.linhas().get(1).detalhe()).contains("sem nome");
        assertThat(previa.ignoradas()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Confirmacao
    // ------------------------------------------------------------------

    @Test
    @DisplayName("confirmar cria profissional e vinculo, com origem IMPORTACAO_CNES")
    void confirmarCriaProfissionalEVinculo() {
        when(profissionalRepository.findByCpf(CPF_MARIA)).thenReturn(Optional.empty());
        when(vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboId(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        CnesImportacaoResultadoDTO resultado = service.confirmar(new CnesImportacaoConfirmarDTO(
                null, List.of(linha(2, CPF_MARIA, "MARIA DE SOUZA").comUnidade(7L, "Policlínica Municipal"))));

        assertThat(resultado.profissionaisCriados()).isEqualTo(1);
        assertThat(resultado.vinculosCriados()).isEqualTo(1);
        assertThat(resultado.ignoradas()).isZero();

        ArgumentCaptor<Profissional> profissionalSalvo = ArgumentCaptor.forClass(Profissional.class);
        verify(profissionalRepository).save(profissionalSalvo.capture());
        assertThat(profissionalSalvo.getValue().getCpf()).isEqualTo(CPF_MARIA);
        assertThat(profissionalSalvo.getValue().isAtivo()).isTrue();

        ArgumentCaptor<ProfissionalVinculo> vinculoSalvo = ArgumentCaptor.forClass(ProfissionalVinculo.class);
        verify(vinculoRepository).save(vinculoSalvo.capture());
        assertThat(vinculoSalvo.getValue().getOrigem()).isEqualTo(OrigemVinculoEnum.IMPORTACAO_CNES);
        assertThat(vinculoSalvo.getValue().getUnidade()).isEqualTo(policlinica);
        assertThat(vinculoSalvo.getValue().getCbo()).isEqualTo(medicoClinico);
        assertThat(vinculoSalvo.getValue().isAtivo()).isTrue();
    }

    @Test
    @DisplayName("reimportar o mesmo arquivo nao duplica nada")
    void reimportacaoEIdempotente() {
        // O caso real: a coordenacao corrige uma linha e sobe o arquivo inteiro.
        Profissional maria = profissional(11L, CPF_MARIA, "Maria de Souza");
        when(profissionalRepository.findByCpf(CPF_MARIA)).thenReturn(Optional.of(maria));
        when(vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboId(11L, 7L, 3L))
                .thenReturn(Optional.of(new ProfissionalVinculo()));

        CnesImportacaoResultadoDTO resultado = service.confirmar(new CnesImportacaoConfirmarDTO(
                null, List.of(linha(2, CPF_MARIA, "MARIA DE SOUZA").comUnidade(7L, "Policlínica Municipal"))));

        assertThat(resultado.profissionaisCriados()).isZero();
        assertThat(resultado.profissionaisReaproveitados()).isEqualTo(1);
        assertThat(resultado.vinculosCriados()).isZero();
        assertThat(resultado.vinculosJaExistentes()).isEqualTo(1);
        verify(profissionalRepository, never()).save(any());
        verify(vinculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("mesmo CPF em duas linhas cria um profissional e dois vinculos")
    void mesmoCpfEmDuasLinhasNaoDuplicaProfissional() {
        // Sem o cache do lote, a segunda linha nao acharia o profissional criado na
        // primeira (ele ainda nao esta visivel para uma nova consulta) e o insert
        // duplicado bateria no indice unico do CPF.
        Cbo ginecologista = new Cbo();
        ginecologista.setId(4L);
        ginecologista.setCodigo("225250");
        ginecologista.setDescricao("Médico ginecologista");

        when(profissionalRepository.findByCpf(CPF_MARIA)).thenReturn(Optional.empty());
        when(cboService.buscarOuCriar(eq("225250"), any()))
                .thenReturn(new CboService.Upsert(ginecologista, true));
        when(vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboId(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        CnesProfissionalLinhaDTO comoMedico = linha(2, CPF_MARIA, "MARIA DE SOUZA")
                .comUnidade(7L, "Policlínica Municipal");
        CnesProfissionalLinhaDTO comoGineco = CnesProfissionalLinhaDTO
                .lida(3, CNES_POLICLINICA, "POLICLINICA", CPF_MARIA, "MARIA DE SOUZA", "225250", "GINECOLOGISTA")
                .comUnidade(7L, "Policlínica Municipal");

        CnesImportacaoResultadoDTO resultado = service.confirmar(
                new CnesImportacaoConfirmarDTO(null, List.of(comoMedico, comoGineco)));

        assertThat(resultado.profissionaisCriados()).isEqualTo(1);
        assertThat(resultado.vinculosCriados()).isEqualTo(2);
        assertThat(resultado.cbosCriados()).isEqualTo(1);
        verify(profissionalRepository).save(any(Profissional.class));
    }

    @Test
    @DisplayName("linha ruim no meio do lote e pulada com aviso, sem derrubar as boas")
    void linhaRuimNaoDerrubaOLote() {
        // Num arquivo de 400 linhas, abortar tudo por um CPF malformado obrigaria a
        // coordenacao a editar o CSV para conseguir importar os outros 399.
        when(profissionalRepository.findByCpf(CPF_JOAO)).thenReturn(Optional.empty());
        when(vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboId(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        CnesImportacaoResultadoDTO resultado = service.confirmar(new CnesImportacaoConfirmarDTO(null, List.of(
                linha(2, "123", "CPF CURTO").comUnidade(7L, "Policlínica Municipal"),
                linha(3, CPF_JOAO, "JOAO LIMA").comUnidade(7L, "Policlínica Municipal"))));

        assertThat(resultado.ignoradas()).isEqualTo(1);
        assertThat(resultado.profissionaisCriados()).isEqualTo(1);
        assertThat(resultado.vinculosCriados()).isEqualTo(1);
        assertThat(resultado.avisos()).anySatisfy(a -> assertThat(a).contains("Linha 2"));
    }

    @Test
    @DisplayName("unidadeIdPadrao cobre a linha sem CNES reconhecido")
    void unidadePadraoCobreLinhaSemCnes() {
        // Vale quando o arquivo nao traz CNES e a coordenacao escolhe o
        // estabelecimento na tela.
        CnesProfissionalLinhaDTO semCnes = CnesProfissionalLinhaDTO.lida(
                2, null, null, CPF_MARIA, "MARIA DE SOUZA", CBO_MEDICO, "MEDICO");
        when(profissionalRepository.findByCpf(CPF_MARIA)).thenReturn(Optional.empty());
        when(vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboId(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        CnesImportacaoResultadoDTO resultado =
                service.confirmar(new CnesImportacaoConfirmarDTO(7L, List.of(semCnes)));

        assertThat(resultado.vinculosCriados()).isEqualTo(1);
        assertThat(resultado.ignoradas()).isZero();
    }

    @Test
    @DisplayName("linha sem CBO usa a consulta de vinculo com CBO nulo, que e outra query em SQL")
    void linhaSemCboUsaConsultaDeCboNulo() {
        // `cbo_id = NULL` nunca casa em SQL: sem a variante CboIsNull a importacao
        // criaria vinculo duplicado sem CBO em toda reimportacao.
        CnesProfissionalLinhaDTO semCbo = CnesProfissionalLinhaDTO.lida(
                2, CNES_POLICLINICA, "POLICLINICA", CPF_MARIA, "MARIA DE SOUZA", null, null);
        Profissional maria = profissional(11L, CPF_MARIA, "Maria de Souza");
        when(profissionalRepository.findByCpf(CPF_MARIA)).thenReturn(Optional.of(maria));
        when(vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboIsNull(11L, 7L))
                .thenReturn(Optional.of(new ProfissionalVinculo()));

        CnesImportacaoResultadoDTO resultado =
                service.confirmar(new CnesImportacaoConfirmarDTO(null, List.of(semCbo.comUnidade(7L, "Policlínica"))));

        assertThat(resultado.vinculosJaExistentes()).isEqualTo(1);
        assertThat(resultado.vinculosCriados()).isZero();
        verify(vinculoRepository).findByProfissionalIdAndUnidadeIdAndCboIsNull(11L, 7L);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private CnesProfissionalLinhaDTO linha(int numero, String cpf, String nome) {
        return CnesProfissionalLinhaDTO.lida(numero, CNES_POLICLINICA, "POLICLINICA MUNICIPAL",
                cpf, nome, CBO_MEDICO, "MEDICO CLINICO");
    }

    private Profissional profissional(Long id, String cpf, String nome) {
        Profissional p = new Profissional();
        p.setId(id);
        p.setCpf(cpf);
        p.setNome(nome);
        p.setAtivo(true);
        return p;
    }

    /** O leitor de CSV recebe a lista de avisos e a preenche; aqui ele so devolve linhas. */
    private org.mockito.stubbing.Answer<List<CnesProfissionalLinhaDTO>> comLinhas(
            CnesProfissionalLinhaDTO... linhas) {
        return chamada -> List.of(linhas);
    }
}
