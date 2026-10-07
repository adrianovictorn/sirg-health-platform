package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoConfirmarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoConfirmarDTO.Item;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoLinhaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoPreviaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoResultadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.SituacaoLinhaCustoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemValorEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Importacao de precos: o que cada linha da planilha vira.
 *
 * <p>A PREVIA confronta com o cadastro sem gravar; a CONFIRMACAO grava
 * reconferindo tudo. Os casos centrais sao os que protegem o numero financeiro:
 * nada casa por aproximacao, nada e sobrescrito sem confirmacao, e reimportar a
 * mesma planilha nao altera nada.
 *
 * <p>A leitura do arquivo em si esta em {@code PlanilhaCustoLeitorServiceTest};
 * aqui o leitor e o real, alimentado por CSV em memoria.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustoImportacaoServiceTest {

    private static final String CABECALHO = "CODIGO SUS;PROCEDIMENTO;VALOR UNIT\n";

    @Mock private EspecialidadeRepository especialidadeRepository;
    @Mock private UserRepository userRepository;

    private CustoImportacaoService service;
    private final List<Especialidade> catalogo = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new CustoImportacaoService(new PlanilhaCustoLeitorService(), especialidadeRepository, userRepository);
        when(especialidadeRepository.findAll()).thenAnswer(inv -> new ArrayList<>(catalogo));
        when(especialidadeRepository.findById(any())).thenAnswer(inv ->
                catalogo.stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst());
        when(especialidadeRepository.save(any(Especialidade.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByCpf(any())).thenReturn(Optional.empty());
    }

    private Especialidade especialidade(long id, String nome) {
        Especialidade e = new Especialidade();
        e.setId(id);
        e.setCodigo("COD_" + id);
        e.setNome(nome);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        catalogo.add(e);
        return e;
    }

    private CustoImportacaoPreviaDTO previa(String linhas) {
        return service.previa((CABECALHO + linhas).getBytes(StandardCharsets.UTF_8), "precos.csv");
    }

    private CustoImportacaoResultadoDTO confirmar(Item... itens) {
        return service.confirmar(new CustoImportacaoConfirmarDTO(List.of(itens)), "111");
    }

    // ------------------------------------------------------------------
    // Previa
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PREVIA: nome identico (sem acento/caixa/pontuacao) casa; a previa nao grava nada")
    void previaCasaPorNomeNormalizadoSemGravar() {
        Especialidade hemograma = especialidade(1, "Hemograma Completo");

        CustoImportacaoPreviaDTO previa = previa("202020380;HEMOGRAMA  COMPLETO;R$ 4,11\n");

        CustoImportacaoLinhaDTO linha = previa.linhas().get(0);
        assertThat(linha.situacao()).isEqualTo(SituacaoLinhaCustoEnum.NOVO_VALOR);
        assertThat(linha.especialidadeId()).isEqualTo(1L);
        assertThat(linha.criterio()).isEqualTo("NOME");
        assertThat(linha.codigoSus()).isEqualTo("0202020380");
        assertThat(previa.prontas()).isEqualTo(1);

        assertThat(hemograma.getValorUnitario()).isNull();
        verify(especialidadeRepository, never()).save(any());
    }

    @Test
    @DisplayName("PREVIA: nome parecido NAO casa — palavra a mais ou a menos fica para o operador")
    void previaNaoCasaPorAproximacao() {
        especialidade(1, "Proteina C Reativa");
        especialidade(2, "Hemograma");

        CustoImportacaoPreviaDTO previa = previa("""
                202030202;DOSAGEM DE PROTEINA C REATIVA;R$ 2,83
                202020380;HEMOGRAMA COMPLETO;R$ 4,11
                """);

        assertThat(previa.linhas()).allSatisfy(linha -> {
            assertThat(linha.situacao()).isEqualTo(SituacaoLinhaCustoEnum.SEM_CORRESPONDENCIA);
            assertThat(linha.especialidadeId()).isNull();
        });
        assertThat(previa.pendentes()).isEqualTo(2);
    }

    @Test
    @DisplayName("PREVIA: palavras coladas ou separadas no lugar errado (planilha do cliente) casam")
    void previaCasaApesarDeEspacosForaDoLugar() {
        especialidade(1, "Dosagem de Colesterol HDL");
        especialidade(2, "Dosagem de Vitamina B12");

        CustoImportacaoPreviaDTO previa = previa("""
                202010279;DOSAGEM DE COLESTERO LHDL;R$ 3,51
                202010708;DOSAGEM DE VITAMINAB12;R$ 15,24
                """);

        assertThat(previa.linhas()).extracting(CustoImportacaoLinhaDTO::especialidadeId).containsExactly(1L, 2L);
        assertThat(previa.linhas()).allMatch(l -> l.situacao() == SituacaoLinhaCustoEnum.NOVO_VALOR);
    }

    @Test
    @DisplayName("PREVIA: codigo SUS ja gravado casa primeiro, e em TODAS as especialidades que o tem")
    void previaCasaPorCodigoSusEmTodasAsEspecialidades() {
        Especialidade glicose = especialidade(1, "Glicose");
        glicose.setCodigoSus("0202010473");
        Especialidade glicemia = especialidade(2, "Glicemia de Jejum");
        glicemia.setCodigoSus("0202010473");
        especialidade(3, "Dosagem de Glicose");

        CustoImportacaoPreviaDTO previa = previa("202010473;DOSAGEM DE GLICOSE;R$ 1,85\n");

        assertThat(previa.totalLinhas()).isEqualTo(1);
        assertThat(previa.linhas()).hasSize(2);
        assertThat(previa.linhas()).extracting(CustoImportacaoLinhaDTO::especialidadeId).containsExactly(1L, 2L);
        assertThat(previa.linhas()).allMatch(l -> "CODIGO_SUS".equals(l.criterio()));
    }

    @Test
    @DisplayName("PREVIA: duas especialidades com o mesmo nome normalizado ficam AMBIGUA")
    void previaNomeRepetidoFicaAmbigua() {
        especialidade(1, "VDRL");
        especialidade(2, "V.D.R.L.");

        CustoImportacaoPreviaDTO previa = previa("202031110;VDRL;R$ 2,83\n");

        assertThat(previa.linhas().get(0).situacao()).isEqualTo(SituacaoLinhaCustoEnum.AMBIGUA);
        assertThat(previa.linhas().get(0).especialidadeId()).isNull();
    }

    @Test
    @DisplayName("PREVIA: classifica igual, so codigo novo, valor diferente e invalida")
    void previaClassificaAsSituacoes() {
        Especialidade igual = especialidade(1, "Exame Igual");
        igual.setCodigoSus("0202010001");
        igual.setValorUnitario(new BigDecimal("1.85"));
        Especialidade soCodigo = especialidade(2, "Exame So Codigo");
        soCodigo.setValorUnitario(new BigDecimal("2.00"));
        Especialidade diferente = especialidade(3, "Exame Diferente");
        diferente.setValorUnitario(new BigDecimal("9.99"));
        diferente.setValorOrigem(OrigemValorEspecialidade.MANUAL);

        CustoImportacaoPreviaDTO previa = previa("""
                202010001;EXAME IGUAL;R$ 1,85
                202010002;EXAME SO CODIGO;R$ 2,00
                202010003;EXAME DIFERENTE;R$ 3,00
                123;CODIGO CURTO;R$ 1,00
                """);

        assertThat(previa.linhas()).extracting(CustoImportacaoLinhaDTO::situacao).containsExactly(
                SituacaoLinhaCustoEnum.VALOR_IGUAL,
                SituacaoLinhaCustoEnum.NOVO_CODIGO,
                SituacaoLinhaCustoEnum.VALOR_DIFERENTE,
                SituacaoLinhaCustoEnum.INVALIDA);
        assertThat(previa.linhas().get(2).detalhe()).contains("digitado à mão");
        assertThat(previa.iguais()).isEqualTo(1);
        assertThat(previa.prontas()).isEqualTo(1);
        assertThat(previa.diferentes()).isEqualTo(1);
        assertThat(previa.invalidas()).isEqualTo(1);
    }

    @Test
    @DisplayName("PREVIA: codigo repetido na planilha gera aviso")
    void previaAvisaCodigoRepetidoNaPlanilha() {
        especialidade(1, "Exame A");

        CustoImportacaoPreviaDTO previa = previa("""
                202010001;EXAME A;R$ 1,85
                202010001;EXAME A DE NOVO;R$ 2,85
                """);

        assertThat(previa.avisos()).anyMatch(a -> a.contains("0202010001") && a.contains("linhas 2 e 3"));
    }

    // ------------------------------------------------------------------
    // Confirmacao
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CONFIRMAR: grava valor, codigo de 10 digitos e origem IMPORTACAO")
    void confirmarGrava() {
        Especialidade hemograma = especialidade(1, "Hemograma Completo");

        CustoImportacaoResultadoDTO resultado =
                confirmar(new Item(2, 1L, "202020380", new BigDecimal("4.11"), false));

        assertThat(resultado.gravadas()).isEqualTo(1);
        assertThat(hemograma.getValorUnitario()).isEqualByComparingTo("4.11");
        assertThat(hemograma.getCodigoSus()).isEqualTo("0202020380");
        assertThat(hemograma.getValorOrigem()).isEqualTo(OrigemValorEspecialidade.IMPORTACAO);
        assertThat(hemograma.getValorAtualizadoEm()).isNotNull();
    }

    @Test
    @DisplayName("CONFIRMAR: reimportar a mesma planilha nao grava nada (idempotente)")
    void confirmarEIdempotente() {
        especialidade(1, "Hemograma Completo");
        Item item = new Item(2, 1L, "202020380", new BigDecimal("4.11"), false);
        confirmar(item);

        CustoImportacaoResultadoDTO segunda = confirmar(item);

        assertThat(segunda.gravadas()).isZero();
        assertThat(segunda.inalteradas()).isEqualTo(1);
        verify(especialidadeRepository).save(any());
    }

    @Test
    @DisplayName("CONFIRMAR: preco ja gravado e diferente NAO e sobrescrito sem confirmacao explicita")
    void confirmarNaoSobrescreveSemConfirmacao() {
        Especialidade exame = especialidade(1, "Exame");
        exame.setValorUnitario(new BigDecimal("9.99"));
        exame.setValorOrigem(OrigemValorEspecialidade.MANUAL);

        CustoImportacaoResultadoDTO resultado =
                confirmar(new Item(2, 1L, "0202010001", new BigDecimal("3.00"), false));

        assertThat(resultado.gravadas()).isZero();
        assertThat(resultado.ignoradas()).isEqualTo(1);
        assertThat(resultado.avisos()).anyMatch(a -> a.contains("Linha 2") && a.contains("não foi confirmada"));
        assertThat(exame.getValorUnitario()).isEqualByComparingTo("9.99");
        assertThat(exame.getCodigoSus()).isNull();
        verify(especialidadeRepository, never()).save(any());
    }

    @Test
    @DisplayName("CONFIRMAR: com sobrescrever = true o preco digitado a mao e substituido")
    void confirmarSobrescreveQuandoConfirmado() {
        Especialidade exame = especialidade(1, "Exame");
        exame.setValorUnitario(new BigDecimal("9.99"));
        exame.setValorOrigem(OrigemValorEspecialidade.MANUAL);

        CustoImportacaoResultadoDTO resultado =
                confirmar(new Item(2, 1L, "0202010001", new BigDecimal("3.00"), true));

        assertThat(resultado.gravadas()).isEqualTo(1);
        assertThat(exame.getValorUnitario()).isEqualByComparingTo("3.00");
        assertThat(exame.getValorOrigem()).isEqualTo(OrigemValorEspecialidade.IMPORTACAO);
    }

    @Test
    @DisplayName("CONFIRMAR: outro codigo SUS ja gravado tambem exige confirmacao")
    void confirmarNaoTrocaCodigoSemConfirmacao() {
        Especialidade exame = especialidade(1, "Exame");
        exame.setCodigoSus("0202019999");

        CustoImportacaoResultadoDTO resultado =
                confirmar(new Item(2, 1L, "0202010001", new BigDecimal("3.00"), false));

        assertThat(resultado.ignoradas()).isEqualTo(1);
        assertThat(exame.getCodigoSus()).isEqualTo("0202019999");
        assertThat(exame.getValorUnitario()).isNull();
    }

    @Test
    @DisplayName("CONFIRMAR: item invalido ou sem destino e pulado com aviso, sem derrubar o lote")
    void confirmarPulaItensRuinsSemDerrubarOLote() {
        Especialidade boa = especialidade(1, "Exame Bom");

        CustoImportacaoResultadoDTO resultado = confirmar(
                new Item(2, null, "0202010001", new BigDecimal("1.00"), false),
                new Item(3, 1L, "123", new BigDecimal("1.00"), false),
                new Item(4, 1L, "0202010001", new BigDecimal("-1.00"), false),
                new Item(5, 1L, "0202010001", null, false),
                new Item(6, 999L, "0202010001", new BigDecimal("1.00"), false),
                new Item(7, 1L, "0202010001", new BigDecimal("1.00"), false));

        assertThat(resultado.gravadas()).isEqualTo(1);
        assertThat(resultado.ignoradas()).isEqualTo(5);
        assertThat(resultado.avisos()).hasSize(5);
        assertThat(boa.getValorUnitario()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("CONFIRMAR: duas linhas para a mesma especialidade — vale a primeira, a segunda e avisada")
    void confirmarMesmaEspecialidadeDuasVezes() {
        Especialidade exame = especialidade(1, "Exame");

        CustoImportacaoResultadoDTO resultado = confirmar(
                new Item(2, 1L, "0202010001", new BigDecimal("1.00"), false),
                new Item(3, 1L, "0202010001", new BigDecimal("2.00"), true));

        assertThat(resultado.gravadas()).isEqualTo(1);
        assertThat(resultado.ignoradas()).isEqualTo(1);
        assertThat(resultado.avisos()).anyMatch(a -> a.contains("Linha 3") && a.contains("outra linha"));
        assertThat(exame.getValorUnitario()).isEqualByComparingTo("1.00");
    }
}
