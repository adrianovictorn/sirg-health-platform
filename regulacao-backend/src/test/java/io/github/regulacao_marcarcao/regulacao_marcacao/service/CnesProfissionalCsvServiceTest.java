package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesProfissionalLinhaDTO;

/**
 * Leitura do CSV de extracao de profissionais do CNES.
 *
 * <p>Estes testes existem porque o arquivo NAO tem contrato: o portal do DATASUS
 * muda titulo de coluna sem aviso, entrega Latin-1, e o operador costuma abrir o
 * arquivo no Excel antes de subir — o que troca o separador e come zeros a
 * esquerda. Cada caso aqui e uma dessas deformacoes, e a consequencia de nao
 * tratar esta no nome do teste.
 *
 * <p>A decisao do que fazer com cada linha fica em
 * {@code ProfissionalImportacaoServiceTest}.
 */
class CnesProfissionalCsvServiceTest {

    private CnesProfissionalCsvService service;
    private List<String> avisos;

    @BeforeEach
    void setUp() {
        service = new CnesProfissionalCsvService();
        avisos = new ArrayList<>();
    }

    @Test
    @DisplayName("le o layout tipico do CNES: separador ponto e virgula e colunas CO_/NO_")
    void leLayoutTipico() {
        String csv = """
                CO_CNES;NO_FANTASIA;CO_CPF;NO_PROFISSIONAL;CO_CBO;DS_CBO
                2514648;POLICLINICA MUNICIPAL;12345678901;MARIA DE SOUZA;225125;MEDICO CLINICO
                2514648;POLICLINICA MUNICIPAL;98765432100;JOAO LIMA;223505;ENFERMEIRO
                """;

        List<CnesProfissionalLinhaDTO> linhas = service.ler(bytes(csv, StandardCharsets.UTF_8), avisos);

        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).cnes()).isEqualTo("2514648");
        assertThat(linhas.get(0).estabelecimento()).isEqualTo("POLICLINICA MUNICIPAL");
        assertThat(linhas.get(0).cpf()).isEqualTo("12345678901");
        assertThat(linhas.get(0).nome()).isEqualTo("MARIA DE SOUZA");
        assertThat(linhas.get(0).cboCodigo()).isEqualTo("225125");
        assertThat(linhas.get(0).cboDescricao()).isEqualTo("MEDICO CLINICO");
        // Numero da linha no ARQUIVO (cabecalho e a 1), para o operador achar o erro.
        assertThat(linhas.get(0).linha()).isEqualTo(2);
        assertThat(linhas.get(1).cpf()).isEqualTo("98765432100");
        assertThat(avisos).isEmpty();
    }

    @Test
    @DisplayName("repoe zeros a esquerda que o Excel come em CPF, CNES e CBO")
    void repoeZerosAEsquerda() {
        // Reabrir o CSV no Excel transforma os codigos em numero: 01234567890 vira
        // 1234567890. Sem repor, cada importacao criaria um profissional novo para a
        // mesma pessoa, porque a deduplicacao e por CPF.
        String csv = """
                CNES,NOME FANTASIA,CPF,NOME PROFISSIONAL,CBO,DESCRICAO CBO
                12345,UNIDADE X,1234567890,ANA PAULA,12345,AGENTE
                """;

        List<CnesProfissionalLinhaDTO> linhas = service.ler(bytes(csv, StandardCharsets.UTF_8), avisos);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).cpf()).isEqualTo("01234567890");
        assertThat(linhas.get(0).cnes()).isEqualTo("0012345");
        assertThat(linhas.get(0).cboCodigo()).isEqualTo("012345");
    }

    @Test
    @DisplayName("aceita virgula como separador e virgula dentro de campo entre aspas")
    void aceitaCampoEntreAspas() {
        String csv = """
                CO_CNES,NO_FANTASIA,CO_CPF,NO_PROFISSIONAL,CO_CBO,DS_CBO
                2514648,"POLICLINICA MUNICIPAL, ANEXO II",12345678901,"SOUZA, MARIA DE",225125,MEDICO
                """;

        List<CnesProfissionalLinhaDTO> linhas = service.ler(bytes(csv, StandardCharsets.UTF_8), avisos);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).estabelecimento()).isEqualTo("POLICLINICA MUNICIPAL, ANEXO II");
        assertThat(linhas.get(0).nome()).isEqualTo("SOUZA, MARIA DE");
        assertThat(linhas.get(0).cboCodigo()).isEqualTo("225125");
    }

    @Test
    @DisplayName("preserva acentuacao de arquivo em ISO-8859-1, que e o que o CNES entrega")
    void preservaAcentuacaoLatin1() {
        // Decodificar Latin-1 como UTF-8 nao falha: gera nome com caractere trocado,
        // que entra no banco assim e so aparece semanas depois num relatorio.
        String csv = """
                CO_CNES;NO_FANTASIA;CO_CPF;NO_PROFISSIONAL;CO_CBO;DS_CBO
                2514648;POLICLÍNICA;12345678901;JOÃO CONCEIÇÃO;225125;MÉDICO
                """;

        List<CnesProfissionalLinhaDTO> linhas =
                service.ler(bytes(csv, Charset.forName("ISO-8859-1")), avisos);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).nome()).isEqualTo("JOÃO CONCEIÇÃO");
        assertThat(linhas.get(0).estabelecimento()).isEqualTo("POLICLÍNICA");
        assertThat(linhas.get(0).cboDescricao()).isEqualTo("MÉDICO");
    }

    @Test
    @DisplayName("pula o preambulo do portal e acha o cabecalho mais abaixo")
    void pulaPreambulo() {
        String csv = """
                RELATORIO DE PROFISSIONAIS
                Extracao gerada em 25/09/2026

                CO_CNES;NO_FANTASIA;CO_CPF;NO_PROFISSIONAL;CO_CBO;DS_CBO
                2514648;POLICLINICA;12345678901;MARIA DE SOUZA;225125;MEDICO
                """;

        List<CnesProfissionalLinhaDTO> linhas = service.ler(bytes(csv, StandardCharsets.UTF_8), avisos);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).nome()).isEqualTo("MARIA DE SOUZA");
        assertThat(linhas.get(0).linha()).isEqualTo(5);
    }

    @Test
    @DisplayName("desconsidera a tripla repetida no proprio arquivo e avisa qual linha era")
    void desconsideraDuplicataInterna() {
        // O arquivo repete o profissional quando ele tem mais de um registro no mesmo
        // estabelecimento. Importar duas vezes a mesma tripla bateria no indice unico
        // da V88 e derrubaria a transacao por causa de uma linha.
        String csv = """
                CO_CNES;NO_FANTASIA;CO_CPF;NO_PROFISSIONAL;CO_CBO;DS_CBO
                2514648;POLICLINICA;12345678901;MARIA DE SOUZA;225125;MEDICO CLINICO
                2514648;POLICLINICA;12345678901;MARIA DE SOUZA;225125;MEDICO CLINICO
                2514648;POLICLINICA;12345678901;MARIA DE SOUZA;225250;GINECOLOGISTA
                """;

        List<CnesProfissionalLinhaDTO> linhas = service.ler(bytes(csv, StandardCharsets.UTF_8), avisos);

        // A terceira linha e outro CBO: e outro vinculo, nao duplicata.
        assertThat(linhas).hasSize(2);
        assertThat(linhas).extracting(CnesProfissionalLinhaDTO::cboCodigo)
                .containsExactly("225125", "225250");
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0)).contains("Linha 3").contains("linha 2");
    }

    @Test
    @DisplayName("avisa quando o arquivo nao traz CBO nem CNES, em vez de importar em silencio")
    void avisaColunasAusentes() {
        String csv = """
                CO_CPF;NO_PROFISSIONAL
                12345678901;MARIA DE SOUZA
                """;

        List<CnesProfissionalLinhaDTO> linhas = service.ler(bytes(csv, StandardCharsets.UTF_8), avisos);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).cnes()).isNull();
        assertThat(linhas.get(0).cboCodigo()).isNull();
        assertThat(avisos).hasSize(2);
        assertThat(avisos).anySatisfy(a -> assertThat(a).contains("CNES do estabelecimento"));
        assertThat(avisos).anySatisfy(a -> assertThat(a).contains("ocupação (CBO)"));
    }

    @Test
    @DisplayName("cabecalho irreconhecivel recusa o arquivo e mostra o que foi lido")
    void recusaCabecalhoIrreconhecivel() {
        // A mensagem carrega os titulos encontrados porque e o unico jeito de alguem
        // descobrir o apelido novo do DATASUS sem ter o arquivo em maos.
        String csv = """
                COLUNA_A;COLUNA_B;COLUNA_C
                1;2;3
                """;

        assertThatThrownBy(() -> service.ler(bytes(csv, StandardCharsets.UTF_8), avisos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPF e nome")
                .hasMessageContaining("COLUNAA");
    }

    @Test
    @DisplayName("arquivo vazio e arquivo so com cabecalho param com mensagem propria")
    void recusaArquivoSemDados() {
        assertThatThrownBy(() -> service.ler(new byte[0], avisos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazio");

        String soCabecalho = "CO_CPF;NO_PROFISSIONAL\n";
        assertThatThrownBy(() -> service.ler(bytes(soCabecalho, StandardCharsets.UTF_8), avisos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nenhuma linha de profissional");
    }

    @Test
    @DisplayName("mantem CPF de tamanho estranho em vez de truncar, para a previa recusar a linha")
    void mantemCpfDeTamanhoEstranho() {
        // Truncar aqui inventaria um CPF valido de outra pessoa. A linha segue para a
        // previa, que a marca como INVALIDA com o valor a vista.
        String csv = """
                CO_CPF;NO_PROFISSIONAL
                123456789012345;MARIA DE SOUZA
                """;

        List<CnesProfissionalLinhaDTO> linhas = service.ler(bytes(csv, StandardCharsets.UTF_8), avisos);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).cpf()).isEqualTo("123456789012345");
    }

    @Test
    @DisplayName("NAO completa com zeros o campo curto demais, para nao fabricar CPF")
    void naoFabricaCpfAPartirDeCampoTruncado() {
        // Repor zero resolve o dano do Excel; repor OITO zeros inventa um documento.
        // "123" viraria "00000000123", passaria na validacao de formato e criaria um
        // profissional com CPF que nao existe. Fica cru para a previa recusar a linha.
        String csv = """
                CO_CNES;NO_FANTASIA;CO_CPF;NO_PROFISSIONAL;CO_CBO;DS_CBO
                2514648;POLICLINICA;123;CPF QUEBRADO;225125;MEDICO
                2514648;POLICLINICA;123456789;NOVE DIGITOS;225125;MEDICO
                """;

        List<CnesProfissionalLinhaDTO> linhas = service.ler(bytes(csv, StandardCharsets.UTF_8), avisos);

        assertThat(linhas.get(0).cpf()).isEqualTo("123");
        // 9 dígitos: faltam 2, dentro do limite — é o dano típico do Excel.
        assertThat(linhas.get(1).cpf()).isEqualTo("00123456789");
    }

    @Test
    @DisplayName("ignora rodape e linhas em branco no meio do arquivo")
    void ignoraRodapeELinhasVazias() {
        String csv = """
                CO_CNES;NO_FANTASIA;CO_CPF;NO_PROFISSIONAL;CO_CBO;DS_CBO
                2514648;POLICLINICA;12345678901;MARIA DE SOUZA;225125;MEDICO

                2514648;POLICLINICA;98765432100;JOAO LIMA;223505;ENFERMEIRO
                ;;;;;
                """;

        List<CnesProfissionalLinhaDTO> linhas = service.ler(bytes(csv, StandardCharsets.UTF_8), avisos);

        assertThat(linhas).hasSize(2);
        assertThat(linhas).extracting(CnesProfissionalLinhaDTO::nome)
                .containsExactly("MARIA DE SOUZA", "JOAO LIMA");
    }

    private byte[] bytes(String conteudo, Charset charset) {
        return conteudo.getBytes(charset);
    }
}
