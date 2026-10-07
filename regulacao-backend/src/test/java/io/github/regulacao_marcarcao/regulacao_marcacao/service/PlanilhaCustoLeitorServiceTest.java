package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.regulacao_marcarcao.regulacao_marcacao.service.PlanilhaCustoLeitorService.LinhaLida;

/**
 * Leitura da planilha de precos. Os casos vem da planilha real do cliente
 * ("EXAMES LABORATORIAIS"): codigo com 9 digitos, "R$" em umas linhas e nao em
 * outras, ponto de milhar, e colunas de quantidade e valor total que nao sao preco.
 */
class PlanilhaCustoLeitorServiceTest {

    private final PlanilhaCustoLeitorService leitor = new PlanilhaCustoLeitorService();

    private List<LinhaLida> lerCsv(String conteudo) {
        return leitor.ler(conteudo.getBytes(StandardCharsets.UTF_8), "precos.csv");
    }

    @Test
    @DisplayName("CSV no layout do cliente: le codigo, procedimento e VALOR UNIT, ignorando QUANT e VALOR TOTAL")
    void csvNoLayoutDoCliente() {
        List<LinhaLida> linhas = lerCsv("""
                CÓDIGO SUS;PROCEDIMENTO;VALOR UNIT;QUANT;VALOR TOTAL
                202020380;HEMOGRAMA COMPLETO;R$ 4,11;5000;R$ 20.550,00
                202030954;PESQUISA DE ANTICORPOS IGM CONTRA O VIRUS HERPES SIMPLES;17,16;8;R$ 137,28
                """);

        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).linha()).isEqualTo(2);
        assertThat(linhas.get(0).codigoSus()).isEqualTo("0202020380");
        assertThat(linhas.get(0).procedimento()).isEqualTo("HEMOGRAMA COMPLETO");
        assertThat(linhas.get(0).valorUnitario()).isEqualByComparingTo("4.11");
        assertThat(linhas.get(0).problema()).isNull();
        // Sem "R$" na celula: mesmo valor.
        assertThat(linhas.get(1).valorUnitario()).isEqualByComparingTo("17.16");
    }

    @Test
    @DisplayName("Cabecalho e achado depois de titulo e linhas em branco; rodape sem codigo e ignorado")
    void cabecalhoDepoisDePreambulo() {
        List<LinhaLida> linhas = lerCsv("""
                TABELA DE EXAMES LABORATORIAIS;;

                Código SUS;Procedimento;Valor Unit
                0202010473;DOSAGEM DE GLICOSE;R$ 1,85
                ;;
                """);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).linha()).isEqualTo(4);
        assertThat(linhas.get(0).codigoSus()).isEqualTo("0202010473");
    }

    @Test
    @DisplayName("Valor com ponto de milhar e virgula decimal e lido no formato brasileiro")
    void valorComMilhar() {
        List<LinhaLida> linhas = lerCsv("""
                CODIGO;PROCEDIMENTO;VALOR
                0202010473;EXAME CARO;R$ 1.234,56
                """);

        assertThat(linhas.get(0).valorUnitario()).isEqualByComparingTo("1234.56");
    }

    @Test
    @DisplayName("Linha com codigo ou valor ilegivel volta com o problema, sem derrubar as outras")
    void linhaComProblemaNaoDerrubaAsOutras() {
        List<LinhaLida> linhas = lerCsv("""
                CODIGO SUS;PROCEDIMENTO;VALOR UNIT
                123;CODIGO CURTO;R$ 1,00
                0202010473;SEM VALOR;
                0202010474;VALOR TORTO;abc
                0202010475;TRES CASAS;1,234
                ;SEM CODIGO;R$ 2,00
                0202010476;BOA;R$ 2,00
                """);

        assertThat(linhas).hasSize(6);
        assertThat(linhas.get(0).problema()).contains("Código SUS inválido");
        assertThat(linhas.get(0).codigoSus()).isNull();
        assertThat(linhas.get(1).problema()).contains("sem valor");
        assertThat(linhas.get(2).problema()).contains("ilegível");
        assertThat(linhas.get(3).problema()).contains("duas casas");
        assertThat(linhas.get(4).problema()).contains("sem código");
        assertThat(linhas.get(5).problema()).isNull();
    }

    @Test
    @DisplayName("CSV em Latin-1 (como o Excel grava no Windows) e lido com acento")
    void csvEmLatin1() {
        byte[] bytes = "CÓDIGO SUS;PROCEDIMENTO;VALOR UNIT\n0202050114;PROTEINÚRIA;R$ 2,04\n"
                .getBytes(StandardCharsets.ISO_8859_1);

        List<LinhaLida> linhas = leitor.ler(bytes, "precos.csv");

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).procedimento()).isEqualTo("PROTEINÚRIA");
    }

    @Test
    @DisplayName("XLSX: codigo e valor em celula numerica (zero comido, 4.11 binario) sao lidos certo")
    void xlsxComCelulasNumericas() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Sheet aba = workbook.createSheet("Exames");
            Row cabecalho = aba.createRow(0);
            cabecalho.createCell(0).setCellValue("CÓDIGO SUS");
            cabecalho.createCell(1).setCellValue("PROCEDIMENTO");
            cabecalho.createCell(2).setCellValue("VALOR UNIT");
            cabecalho.createCell(3).setCellValue("VALOR TOTAL");
            Row numerica = aba.createRow(1);
            numerica.createCell(0).setCellValue(202020380d);
            numerica.createCell(1).setCellValue("HEMOGRAMA COMPLETO");
            numerica.createCell(2).setCellValue(4.11d);
            numerica.createCell(3).setCellValue(20550d);
            Row texto = aba.createRow(2);
            texto.createCell(0).setCellValue("0202010473");
            texto.createCell(1).setCellValue("DOSAGEM DE GLICOSE");
            texto.createCell(2).setCellValue("R$ 1,85");
            workbook.write(saida);
            bytes = saida.toByteArray();
        }

        List<LinhaLida> linhas = leitor.ler(bytes, "Exames.XLSX");

        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).codigoSus()).isEqualTo("0202020380");
        assertThat(linhas.get(0).valorUnitario()).isEqualByComparingTo("4.11");
        assertThat(linhas.get(1).codigoSus()).isEqualTo("0202010473");
        assertThat(linhas.get(1).valorUnitario()).isEqualByComparingTo("1.85");
    }

    @Test
    @DisplayName("Arquivo vazio, de outro formato ou sem cabecalho e recusado com mensagem")
    void arquivoInvalido() {
        assertThatThrownBy(() -> leitor.ler(new byte[0], "precos.csv"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vazio");
        assertThatThrownBy(() -> leitor.ler("x".getBytes(StandardCharsets.UTF_8), "EXAMES LABORATORIAIS.pdf"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".xlsx ou .csv");
        assertThatThrownBy(() -> lerCsv("nome;idade\nMaria;30\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cabeçalho");
        assertThatThrownBy(() -> lerCsv("CODIGO SUS;PROCEDIMENTO;VALOR UNIT\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nenhuma linha");
        assertThatThrownBy(() -> leitor.ler("nao e um xlsx".getBytes(StandardCharsets.UTF_8), "precos.xlsx"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".xlsx válido");
    }
}
