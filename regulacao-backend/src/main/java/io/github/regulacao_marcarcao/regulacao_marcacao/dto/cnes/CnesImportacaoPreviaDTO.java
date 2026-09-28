package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes;

import java.util.List;

/**
 * O que o arquivo contem, para conferencia antes de gravar.
 *
 * <p>Nada foi persistido quando este objeto e devolvido — e o "devolve o que foi
 * lido para conferencia" do oficio. Os filtros da tela (estabelecimento, CBO,
 * CPF, nome) atuam sobre {@code linhas}.
 *
 * @param arquivo      nome do arquivo enviado
 * @param totalLinhas  linhas de dados lidas (sem o cabecalho)
 * @param importaveis  quantas criariam profissional ou vinculo
 * @param jaExistentes quantas nao mudariam nada
 * @param ignoradas    quantas nao podem ser importadas (invalida ou sem unidade)
 * @param linhas       todas as linhas, na ordem do arquivo
 * @param avisos       o que o operador precisa saber (coluna faltando, CNES sem
 *                     unidade, duplicata dentro do proprio arquivo)
 */
public record CnesImportacaoPreviaDTO(
        String arquivo,
        int totalLinhas,
        int importaveis,
        int jaExistentes,
        int ignoradas,
        List<CnesProfissionalLinhaDTO> linhas,
        List<String> avisos) {
}
