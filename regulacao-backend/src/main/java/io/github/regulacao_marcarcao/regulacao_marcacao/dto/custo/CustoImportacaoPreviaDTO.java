package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.util.List;

/**
 * O que a planilha contem, confrontado com o cadastro, para conferencia.
 *
 * <p>Nada foi gravado quando este objeto e devolvido.
 *
 * @param arquivo      nome do arquivo enviado
 * @param totalLinhas  linhas de dados lidas da planilha
 * @param prontas      linhas que gravam sem sobrescrever nada (NOVO_VALOR, NOVO_CODIGO)
 * @param iguais       linhas que nao mudariam nada
 * @param diferentes   linhas que sobrescreveriam preco ou codigo ja gravado
 * @param pendentes    linhas que dependem de escolha do operador (AMBIGUA, SEM_CORRESPONDENCIA)
 * @param invalidas    linhas ilegiveis
 * @param linhas       as linhas, na ordem do arquivo
 * @param avisos       o que o operador precisa saber antes de confirmar
 */
public record CustoImportacaoPreviaDTO(
        String arquivo,
        int totalLinhas,
        int prontas,
        int iguais,
        int diferentes,
        int pendentes,
        int invalidas,
        List<CustoImportacaoLinhaDTO> linhas,
        List<String> avisos) {
}
