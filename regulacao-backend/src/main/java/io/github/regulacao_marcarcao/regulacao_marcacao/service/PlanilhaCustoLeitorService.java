package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/**
 * Le a planilha de precos (codigo SUS, procedimento, valor unitario) em
 * {@code .xlsx} ou {@code .csv}.
 *
 * <p>Tolerante pelo mesmo motivo de {@link CnesProfissionalCsvService}: a
 * planilha vem do cliente, passa pelo Excel e chega com o zero a esquerda do
 * codigo comido, "R$" em algumas celulas e nao em outras, e colunas a mais
 * (quantidade, valor total) que aqui sao ignoradas.
 *
 * <p>Nada aqui toca o banco nem decide o que fazer com a linha: a classe so
 * traduz bytes em linhas. Linha com problema NAO e descartada — volta com
 * {@code problema} preenchido, para a conferencia mostrar ao operador.
 */
@Component
public class PlanilhaCustoLeitorService {

    /** Ate onde procurar o cabecalho antes de desistir (titulo, linhas em branco). */
    private static final int LINHAS_PARA_ACHAR_CABECALHO = 25;

    private static final char[] SEPARADORES = {';', '\t', '|', ','};

    // Apelidos por coluna, ja normalizados (sem acento/pontuacao, maiusculo).
    // A ordem importa: o primeiro apelido encontrado no cabecalho ganha.
    private static final List<String> APELIDOS_CODIGO = List.of(
            "CODIGOSUS", "CODSUS", "CODIGOSIGTAP", "SIGTAP", "CODIGOPROCEDIMENTO", "CODIGO", "COD");

    private static final List<String> APELIDOS_PROCEDIMENTO = List.of(
            "PROCEDIMENTO", "DESCRICAO", "EXAME", "ESPECIALIDADE", "NOME");

    // "VALORTOTAL" fica fora de proposito: e quantidade x unitario, nao o preco.
    private static final List<String> APELIDOS_VALOR = List.of(
            "VALORUNIT", "VALORUNITARIO", "VLUNIT", "VLUNITARIO", "PRECOUNITARIO", "PRECO", "VALOR");

    /**
     * Uma linha de dados da planilha.
     *
     * @param linha          numero da linha no arquivo (1 = primeira), para o operador achar
     * @param codigoSusBruto codigo como veio escrito
     * @param codigoSus      codigo normalizado para 10 digitos; nulo se invalido ou ausente
     * @param procedimento   nome do procedimento como veio escrito
     * @param valorUnitario  valor com 2 casas; nulo se invalido ou ausente
     * @param problema       por que a linha nao pode ser importada, ou nulo se esta boa
     */
    public record LinhaLida(
            int linha,
            String codigoSusBruto,
            String codigoSus,
            String procedimento,
            BigDecimal valorUnitario,
            String problema) {
    }

    private record Cabecalho(int indiceLinha, int colunaCodigo, int colunaProcedimento, int colunaValor) {
    }

    /**
     * Traduz o arquivo em linhas.
     *
     * @throws IllegalArgumentException quando o arquivo esta vazio, nao e
     *         {@code .xlsx}/{@code .csv} ou nao tem cabecalho reconhecivel
     */
    public List<LinhaLida> ler(byte[] conteudo, String nomeArquivo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new IllegalArgumentException("O arquivo enviado está vazio.");
        }
        String nome = nomeArquivo != null ? nomeArquivo.toLowerCase() : "";
        List<List<Object>> tabela;
        if (nome.endsWith(".xlsx")) {
            tabela = lerXlsx(conteudo);
        } else if (nome.endsWith(".csv") || nome.endsWith(".txt")) {
            tabela = lerCsv(conteudo);
        } else {
            throw new IllegalArgumentException(
                    "Formato não aceito. Envie a planilha em .xlsx ou .csv (PDF precisa ser convertido antes).");
        }

        Cabecalho cabecalho = acharCabecalho(tabela);
        List<LinhaLida> linhas = new ArrayList<>();
        for (int i = cabecalho.indiceLinha() + 1; i < tabela.size(); i++) {
            List<Object> campos = tabela.get(i);
            String codigoBruto = texto(valor(campos, cabecalho.colunaCodigo()));
            String procedimento = texto(valor(campos, cabecalho.colunaProcedimento()));
            Object valorBruto = valor(campos, cabecalho.colunaValor());

            // Linha em branco, subtotal ou rodape: sem codigo e sem procedimento.
            if (codigoBruto == null && procedimento == null) {
                continue;
            }
            linhas.add(interpretar(i + 1, codigoBruto, procedimento, valorBruto));
        }

        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("A planilha não tem nenhuma linha de dados abaixo do cabeçalho.");
        }
        return linhas;
    }

    private LinhaLida interpretar(int numero, String codigoBruto, String procedimento, Object valorBruto) {
        String codigo = null;
        BigDecimal valor = null;
        String problema = null;

        if (codigoBruto == null) {
            problema = "sem código SUS.";
        } else {
            try {
                codigo = CustoEspecialidadeService.normalizarCodigoSus(codigoBruto);
            } catch (IllegalArgumentException e) {
                problema = e.getMessage();
            }
        }

        try {
            valor = interpretarValor(valorBruto);
            if (valor == null && problema == null) {
                problema = "sem valor unitário.";
            }
        } catch (IllegalArgumentException e) {
            if (problema == null) {
                problema = e.getMessage();
            }
        }

        return new LinhaLida(numero, codigoBruto, codigo, procedimento, valor, problema);
    }

    /**
     * Valor em reais, aceitando "R$ 1.234,56", "1234,56", "17,16" e numero puro.
     *
     * <p>Celula numerica do Excel chega como {@code Double} e e arredondada para
     * centavos (a representacao binaria de 2,01 nao e exata). Texto e lido ao pe
     * da letra: mais de duas casas e recusado, nao arredondado.
     */
    static BigDecimal interpretarValor(Object bruto) {
        if (bruto == null) {
            return null;
        }
        if (bruto instanceof Double numero) {
            return CustoEspecialidadeService.normalizarValor(
                    BigDecimal.valueOf(numero).setScale(2, RoundingMode.HALF_UP));
        }
        String texto = bruto.toString()
                .replace(' ', ' ')
                .replaceAll("(?i)R\\$", "")
                .replace(" ", "")
                .trim();
        if (texto.isEmpty()) {
            return null;
        }
        // Com virgula, e formato brasileiro: ponto e separador de milhar.
        String numerico = texto.contains(",") ? texto.replace(".", "").replace(',', '.') : texto;
        try {
            return CustoEspecialidadeService.normalizarValor(new BigDecimal(numerico));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor unitário ilegível: \"" + bruto + "\".");
        }
    }

    // ------------------------------------------------------------------
    // Cabecalho
    // ------------------------------------------------------------------

    private Cabecalho acharCabecalho(List<List<Object>> tabela) {
        int limite = Math.min(tabela.size(), LINHAS_PARA_ACHAR_CABECALHO);
        for (int i = 0; i < limite; i++) {
            List<String> normalizados = tabela.get(i).stream()
                    .map(c -> normalizarCabecalho(texto(c)))
                    .toList();
            int codigo = acharColuna(normalizados, APELIDOS_CODIGO);
            int valor = acharColuna(normalizados, APELIDOS_VALOR);
            if (codigo >= 0 && valor >= 0) {
                return new Cabecalho(i, codigo, acharColuna(normalizados, APELIDOS_PROCEDIMENTO), valor);
            }
        }
        throw new IllegalArgumentException(
                "Não encontrei o cabeçalho da planilha. Ela precisa ter as colunas \"Código SUS\" e \"Valor Unit\" "
                        + "(e, de preferência, \"Procedimento\").");
    }

    private static int acharColuna(List<String> cabecalhoNormalizado, List<String> apelidos) {
        for (String apelido : apelidos) {
            int indice = cabecalhoNormalizado.indexOf(apelido);
            if (indice >= 0) {
                return indice;
            }
        }
        return -1;
    }

    private static String normalizarCabecalho(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "");
    }

    // ------------------------------------------------------------------
    // XLSX
    // ------------------------------------------------------------------

    private List<List<Object>> lerXlsx(byte[] conteudo) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(conteudo))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("A planilha não tem nenhuma aba.");
            }
            Sheet aba = workbook.getSheetAt(0);
            DataFormatter formatador = new DataFormatter();
            List<List<Object>> tabela = new ArrayList<>();
            for (int r = 0; r <= aba.getLastRowNum(); r++) {
                Row linha = aba.getRow(r);
                List<Object> campos = new ArrayList<>();
                if (linha != null) {
                    for (int c = 0; c < linha.getLastCellNum(); c++) {
                        campos.add(valorDaCelula(linha.getCell(c), formatador));
                    }
                }
                // Linha vazia tambem entra: mantem o numero da linha igual ao do Excel.
                tabela.add(campos);
            }
            return tabela;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Não foi possível abrir a planilha. Confira se o arquivo é um .xlsx válido.");
        }
    }

    /** Numero continua numero (para o valor nao depender da formatacao da celula); o resto vira texto. */
    private static Object valorDaCelula(Cell celula, DataFormatter formatador) {
        if (celula == null) {
            return null;
        }
        CellType tipo = celula.getCellType() == CellType.FORMULA
                ? celula.getCachedFormulaResultType()
                : celula.getCellType();
        if (tipo == CellType.NUMERIC) {
            return celula.getNumericCellValue();
        }
        if (tipo == CellType.STRING) {
            return celula.getStringCellValue();
        }
        return formatador.formatCellValue(celula);
    }

    // ------------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------------

    private List<List<Object>> lerCsv(byte[] conteudo) {
        String texto = decodificar(conteudo);
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        String[] linhas = texto.split("\\r\\n|\\r|\\n", -1);
        char separador = descobrirSeparador(linhas);
        List<List<Object>> tabela = new ArrayList<>();
        for (String linha : linhas) {
            tabela.add(new ArrayList<>(separarCampos(linha, separador)));
        }
        return tabela;
    }

    /** UTF-8 quando os bytes formam UTF-8 valido; senao Latin-1 (o que o Excel grava no Windows). */
    private static String decodificar(byte[] conteudo) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(conteudo))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(conteudo, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * O separador e o candidato mais frequente na linha que parece o cabecalho.
     * Virgula e o ultimo criterio de desempate: em planilha brasileira ela
     * aparece dentro dos valores ("R$ 2,01").
     */
    private static char descobrirSeparador(String[] linhas) {
        int limite = Math.min(linhas.length, LINHAS_PARA_ACHAR_CABECALHO);
        for (int i = 0; i < limite; i++) {
            String normalizada = normalizarCabecalho(linhas[i]);
            if (!normalizada.contains("VALOR") && !normalizada.contains("PRECO")) {
                continue;
            }
            char melhor = SEPARADORES[0];
            long maior = 0;
            for (char candidato : SEPARADORES) {
                long quantidade = linhas[i].chars().filter(c -> c == candidato).count();
                if (quantidade > maior) {
                    maior = quantidade;
                    melhor = candidato;
                }
            }
            if (maior > 0) {
                return melhor;
            }
        }
        return ';';
    }

    /** Separa os campos respeitando aspas duplas (campo com separador ou aspas escapadas por ""). */
    private static List<String> separarCampos(String linha, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (entreAspas) {
                if (c == '"' && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else if (c == '"') {
                    entreAspas = false;
                } else {
                    atual.append(c);
                }
            } else if (c == '"') {
                entreAspas = true;
            } else if (c == separador) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString());
        return campos;
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private static Object valor(List<Object> campos, int coluna) {
        return coluna >= 0 && coluna < campos.size() ? campos.get(coluna) : null;
    }

    /** Texto limpo da celula, ou nulo se vazia. Codigo numerico do Excel (202010023.0) perde o ".0". */
    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto;
        if (valor instanceof Double numero) {
            texto = numero == Math.rint(numero) && !Double.isInfinite(numero)
                    ? BigDecimal.valueOf(numero).toBigInteger().toString()
                    : BigDecimal.valueOf(numero).toPlainString();
        } else {
            texto = valor.toString();
        }
        texto = texto.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        return texto.isEmpty() ? null : texto;
    }
}
