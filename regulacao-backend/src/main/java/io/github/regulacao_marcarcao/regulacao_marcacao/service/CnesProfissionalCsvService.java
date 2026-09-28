package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesProfissionalLinhaDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * Le o CSV de "Extracao de dados de profissional" do portal do CNES.
 *
 * <p>Por que arquivo e nao API: nao existe endpoint publico de profissionais no
 * DATASUS. Todas as variantes foram testadas em 25/09/2026 e respondem 404
 * (/cnes/profissionais, /cnes/vinculos, /cnes/equipes, /cnes/ocupacoes e o
 * sub-recurso /cnes/estabelecimentos/{cnes}/profissionais); os servicos internos
 * de cnes.datasus.gov.br responderam 503. O webservice SOAP exige credencial
 * solicitada formalmente pela SMS. Ver docs/especificacoes/Agenda e Oferta.md,
 * secao 5.2.
 *
 * <h2>Por que o leitor e tolerante</h2>
 * O arquivo e gerado por uma tela do DATASUS que muda de layout sem aviso, e o
 * operador pode reabri-lo no Excel antes de subir — o que troca o separador,
 * reescreve o encoding e come zeros a esquerda. Em vez de fixar um layout, o
 * leitor:
 *
 * <ul>
 *   <li>descobre o separador contando candidatos na linha de cabecalho;</li>
 *   <li>casa cada coluna por uma lista de apelidos, comparando o cabecalho
 *       normalizado (sem acento, sem pontuacao, maiusculo);</li>
 *   <li>decodifica em UTF-8 e cai para ISO-8859-1 quando os bytes nao formam
 *       UTF-8 valido (o CNES entrega Latin-1, o Excel as vezes reescreve);</li>
 *   <li>pula o preambulo, procurando o cabecalho nas primeiras linhas;</li>
 *   <li>repoe zeros a esquerda de CPF, CNES e CBO.</li>
 * </ul>
 *
 * <p>Nada aqui toca o banco: a classe so traduz bytes em linhas. Decidir o que
 * fazer com cada linha e da {@link ProfissionalImportacaoService}.
 */
@Component
@Slf4j
public class CnesProfissionalCsvService {

    /** Ate onde procurar o cabecalho antes de desistir (o portal gera preambulo). */
    private static final int LINHAS_PARA_ACHAR_CABECALHO = 25;

    /**
     * Quantos zeros a esquerda o leitor aceita repor.
     *
     * <p>O limite separa as duas causas de um codigo curto. Ate 3 digitos faltando
     * e o Excel tendo comido zeros a esquerda ao tratar o campo como numero — dano
     * comum e reversivel. Mais que isso e campo truncado ou lixo, e completar com
     * zeros ali FABRICA um codigo plausivel: o CPF "123" viraria "00000000123",
     * passaria na validacao de formato e criaria um profissional com documento
     * inventado. Abaixo do limite o valor segue cru, e a previa o recusa mostrando
     * o texto original.
     */
    private static final int MAX_ZEROS_REPOSTOS = 3;

    private static final char[] SEPARADORES = {';', ',', '\t', '|'};

    // Apelidos por coluna, ja normalizados (sem acento/pontuacao, maiusculo).
    // A ordem importa: o primeiro apelido encontrado no cabecalho ganha.
    private static final List<String> APELIDOS_CPF = List.of(
            "COCPF", "CPF", "CPFPROFISSIONAL", "NUCPF", "CPFDOPROFISSIONAL", "CPFPROF");

    private static final List<String> APELIDOS_NOME = List.of(
            "NOPROFISSIONAL", "NOMEPROFISSIONAL", "NOMEDOPROFISSIONAL", "NMPROFISSIONAL",
            "NOMECOMPLETO", "PROFISSIONAL", "NOME");

    private static final List<String> APELIDOS_CNES = List.of(
            "COCNES", "CNES", "CODIGOCNES", "CODCNES", "COUNIDADE", "CNESESTABELECIMENTO",
            "CNESUNIDADE");

    private static final List<String> APELIDOS_ESTABELECIMENTO = List.of(
            "NOFANTASIA", "NOMEFANTASIA", "NOESTABELECIMENTO", "NOMEESTABELECIMENTO",
            "ESTABELECIMENTO", "NORAZAOSOCIAL", "RAZAOSOCIAL", "UNIDADE");

    private static final List<String> APELIDOS_CBO_CODIGO = List.of(
            "COCBO", "COCBO2002", "CBO2002", "CODIGOCBO", "CODCBO", "CBO");

    private static final List<String> APELIDOS_CBO_DESCRICAO = List.of(
            "DSCBO", "DSCBO2002", "DESCRICAOCBO", "DESCBO", "NOCBO", "OCUPACAO",
            "DSATIVIDADEPROFISSIONAL", "CBODESCRICAO", "DESCRICAOOCUPACAO");

    /**
     * Traduz o arquivo em linhas normalizadas.
     *
     * @param conteudo bytes do arquivo enviado
     * @param avisos   lista onde o leitor acrescenta o que o operador precisa saber
     *                 (coluna ausente, linha malformada); nunca nula
     * @return linhas de dados, na ordem do arquivo
     * @throws IllegalArgumentException quando o arquivo esta vazio ou nao tem
     *         cabecalho reconhecivel — casos em que importar as cegas seria pior
     */
    public List<CnesProfissionalLinhaDTO> ler(byte[] conteudo, List<String> avisos) {
        if (conteudo == null || conteudo.length == 0) {
            throw new IllegalArgumentException("O arquivo enviado está vazio.");
        }

        List<String> linhas = quebrarLinhas(decodificar(conteudo));
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("O arquivo enviado não tem nenhuma linha de dados.");
        }

        Cabecalho cabecalho = acharCabecalho(linhas);
        avisarColunasAusentes(cabecalho, avisos);

        List<CnesProfissionalLinhaDTO> resultado = new ArrayList<>();
        // Chave (cpf|cnes|cbo) -> primeira linha onde apareceu. O arquivo do CNES
        // repete o profissional quando ele tem mais de um vinculo no mesmo
        // estabelecimento; importar duas vezes a mesma tripla bateria no indice
        // unico da V88 e derrubaria a transacao inteira por causa de uma linha.
        Map<String, Integer> jaVistas = new LinkedHashMap<>();
        Set<String> duplicatasAvisadas = new LinkedHashSet<>();

        for (int i = cabecalho.indiceLinha() + 1; i < linhas.size(); i++) {
            String bruta = linhas.get(i);
            if (bruta == null || bruta.isBlank()) {
                continue;
            }

            List<String> campos = separarCampos(bruta, cabecalho.separador());
            String cpf = digitosComZeros(valor(campos, cabecalho.colunaCpf()), 11);
            String nome = textoLimpo(valor(campos, cabecalho.colunaNome()));

            // Rodape e linha de totais do portal caem aqui: sem CPF e sem nome.
            if (cpf == null && (nome == null || nome.isBlank())) {
                continue;
            }

            String cnes = digitosComZeros(valor(campos, cabecalho.colunaCnes()), 7);
            String estabelecimento = textoLimpo(valor(campos, cabecalho.colunaEstabelecimento()));
            String cboCodigo = digitosComZeros(valor(campos, cabecalho.colunaCboCodigo()), 6);
            String cboDescricao = textoLimpo(valor(campos, cabecalho.colunaCboDescricao()));

            int numeroLinha = i + 1;
            String chave = (cpf == null ? "?" : cpf) + "|" + (cnes == null ? "?" : cnes)
                    + "|" + (cboCodigo == null ? "?" : cboCodigo);
            Integer primeira = jaVistas.putIfAbsent(chave, numeroLinha);
            if (primeira != null) {
                if (duplicatasAvisadas.add(chave)) {
                    avisos.add("Linha " + numeroLinha + " repete o vínculo da linha " + primeira
                            + " (mesmo CPF, estabelecimento e CBO) e foi desconsiderada.");
                }
                continue;
            }

            resultado.add(CnesProfissionalLinhaDTO.lida(
                    numeroLinha, cnes, estabelecimento, cpf, nome, cboCodigo, cboDescricao));
        }

        if (resultado.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nenhuma linha de profissional foi encontrada no arquivo. Confira se o CSV é o da"
                            + " Extração de dados de profissional do portal do CNES.");
        }

        log.info("CSV do CNES lido: {} linhas de dados, separador '{}', colunas mapeadas {}.",
                resultado.size(), cabecalho.separador(), cabecalho.resumoColunas());
        return resultado;
    }

    // ------------------------------------------------------------------
    // Cabecalho
    // ------------------------------------------------------------------

    /** Posicao de cada coluna que interessa, mais o separador descoberto. */
    private record Cabecalho(
            int indiceLinha,
            char separador,
            int colunaCpf,
            int colunaNome,
            int colunaCnes,
            int colunaEstabelecimento,
            int colunaCboCodigo,
            int colunaCboDescricao) {

        String resumoColunas() {
            return "cpf=" + colunaCpf + ", nome=" + colunaNome + ", cnes=" + colunaCnes
                    + ", estabelecimento=" + colunaEstabelecimento
                    + ", cboCodigo=" + colunaCboCodigo + ", cboDescricao=" + colunaCboDescricao;
        }
    }

    private Cabecalho acharCabecalho(List<String> linhas) {
        int limite = Math.min(linhas.size(), LINHAS_PARA_ACHAR_CABECALHO);
        List<String> cabecalhosVistos = new ArrayList<>();

        for (int i = 0; i < limite; i++) {
            String linha = linhas.get(i);
            if (linha == null || linha.isBlank()) {
                continue;
            }

            char separador = descobrirSeparador(linha);
            List<String> titulos = separarCampos(linha, separador).stream()
                    .map(this::normalizarTitulo)
                    .toList();

            int cpf = acharColuna(titulos, APELIDOS_CPF);
            int nome = acharColuna(titulos, APELIDOS_NOME);

            // CPF e nome sao o minimo: sem CPF nao ha como deduplicar profissional,
            // sem nome nao ha o que cadastrar. Achou os dois, e o cabecalho.
            if (cpf >= 0 && nome >= 0) {
                return new Cabecalho(i, separador, cpf, nome,
                        acharColuna(titulos, APELIDOS_CNES),
                        acharColuna(titulos, APELIDOS_ESTABELECIMENTO),
                        acharColuna(titulos, APELIDOS_CBO_CODIGO),
                        acharColuna(titulos, APELIDOS_CBO_DESCRICAO));
            }

            if (titulos.size() > 1) {
                cabecalhosVistos.add(String.join(", ", titulos));
            }
        }

        // A mensagem carrega o que FOI encontrado de proposito: e o unico jeito de
        // alguem descobrir o apelido novo que o DATASUS passou a usar sem ter o
        // arquivo em maos.
        throw new IllegalArgumentException(
                "Não encontrei as colunas de CPF e nome do profissional no arquivo."
                        + (cabecalhosVistos.isEmpty()
                                ? " O arquivo não parece ser um CSV."
                                : " Cabeçalhos lidos: " + String.join(" / ", cabecalhosVistos) + ".")
                        + " Use o CSV da Extração de dados de profissional do portal do CNES, sem editar as colunas.");
    }

    private void avisarColunasAusentes(Cabecalho cabecalho, List<String> avisos) {
        if (cabecalho.colunaCnes() < 0) {
            avisos.add("O arquivo não traz a coluna de CNES do estabelecimento:"
                    + " escolha o estabelecimento manualmente antes de confirmar.");
        }
        if (cabecalho.colunaCboCodigo() < 0 && cabecalho.colunaCboDescricao() < 0) {
            avisos.add("O arquivo não traz a ocupação (CBO): os vínculos serão criados sem CBO,"
                    + " e a ocupação pode ser preenchida depois na tela de profissionais.");
        }
    }

    private int acharColuna(List<String> titulosNormalizados, List<String> apelidos) {
        for (String apelido : apelidos) {
            int indice = titulosNormalizados.indexOf(apelido);
            if (indice >= 0) {
                return indice;
            }
        }
        // Segunda passada tolerante: o DATASUS prefixa e sufixa titulos
        // ("CO_CPF_PROFISSIONAL"), o que faria a igualdade exata falhar.
        for (String apelido : apelidos) {
            for (int i = 0; i < titulosNormalizados.size(); i++) {
                if (titulosNormalizados.get(i).contains(apelido)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private char descobrirSeparador(String linha) {
        char escolhido = ';';
        int melhor = 0;
        for (char candidato : SEPARADORES) {
            int quantidade = contar(linha, candidato);
            if (quantidade > melhor) {
                melhor = quantidade;
                escolhido = candidato;
            }
        }
        return escolhido;
    }

    private int contar(String texto, char alvo) {
        int total = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == alvo) {
                total++;
            }
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Leitura crua
    // ------------------------------------------------------------------

    /**
     * UTF-8 quando os bytes forem UTF-8 valido, ISO-8859-1 caso contrario.
     *
     * <p>O portal do CNES entrega Latin-1, mas o operador que abre e salva o
     * arquivo no Excel pode reescrever em UTF-8. Decodificar com o charset errado
     * nao falha: gera nome de profissional com caractere trocado, que entra no
     * banco assim e so aparece semanas depois num relatorio.
     */
    private String decodificar(byte[] conteudo) {
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(conteudo))
                    .toString();
            return removerBom(texto);
        } catch (CharacterCodingException e) {
            return removerBom(new String(conteudo, Charset.forName("ISO-8859-1")));
        }
    }

    private String removerBom(String texto) {
        return texto.startsWith("﻿") ? texto.substring(1) : texto;
    }

    private List<String> quebrarLinhas(String texto) {
        List<String> linhas = new ArrayList<>();
        for (String linha : texto.split("\\r\\n|\\r|\\n", -1)) {
            linhas.add(linha);
        }
        // Tira o vazio final que o split deixa quando o arquivo termina em quebra.
        while (!linhas.isEmpty() && linhas.get(linhas.size() - 1).isBlank()) {
            linhas.remove(linhas.size() - 1);
        }
        return linhas;
    }

    /**
     * Separa uma linha respeitando campo entre aspas.
     *
     * <p>Necessario porque nome de estabelecimento traz virgula ("POLICLINICA
     * MUNICIPAL DR. ANTONIO ALBUQUERQUE, ANEXO II") e descricao de CBO traz ponto
     * e virgula. Aspas duplicadas dentro do campo viram uma aspa, como no padrao.
     */
    private List<String> separarCampos(String linha, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean dentroDeAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (dentroDeAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    dentroDeAspas = !dentroDeAspas;
                }
            } else if (c == separador && !dentroDeAspas) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString());
        return campos;
    }

    private String valor(List<String> campos, int indice) {
        if (indice < 0 || indice >= campos.size()) {
            return null;
        }
        String valor = campos.get(indice);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    // ------------------------------------------------------------------
    // Normalizacao
    // ------------------------------------------------------------------

    private String normalizarTitulo(String titulo) {
        if (titulo == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(titulo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /**
     * Digitos do campo, com zeros a esquerda repostos ate {@code tamanho}.
     *
     * <p>CPF, CNES e CBO sao codigos que comecam com zero com frequencia. Quem
     * abre o CSV no Excel para conferir e salva de volta perde esses zeros — o
     * campo vira numero. Sem repor, o CPF 01234567890 chega como 1234567890 e
     * cria um profissional duplicado a cada importacao.
     *
     * <p>Valor curto demais para ser zero comido (ver {@link #MAX_ZEROS_REPOSTOS})
     * sai cru, e a previa recusa a linha mostrando o que estava no arquivo.
     *
     * <p>Valor mais longo que o esperado e devolvido como esta: pode ser CPF
     * concatenado com outro dado, e truncar ali inventaria um CPF valido de outra
     * pessoa. A situacao aparece na previa como linha invalida.
     */
    private String digitosComZeros(String valor, int tamanho) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return null;
        }
        int faltando = tamanho - digitos.length();
        if (faltando <= 0 || faltando > MAX_ZEROS_REPOSTOS) {
            return digitos;
        }
        return "0".repeat(faltando) + digitos;
    }

    private String textoLimpo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim().replaceAll("\\s+", " ");
        return limpo.isEmpty() ? null : limpo;
    }
}
