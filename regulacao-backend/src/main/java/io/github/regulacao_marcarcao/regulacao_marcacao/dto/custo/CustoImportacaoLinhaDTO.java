package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemValorEspecialidade;

/**
 * Uma linha da planilha de precos, ja confrontada com o cadastro.
 *
 * <p>Uma linha do arquivo pode virar mais de uma aqui: o codigo SUS nao e unico
 * no catalogo, e cada especialidade que o tem recebe a sua linha.
 *
 * @param linha             numero da linha no arquivo
 * @param codigoSus         codigo da planilha, normalizado para 10 digitos (nulo se invalido)
 * @param procedimento      nome do procedimento na planilha
 * @param valorUnitario     valor da planilha (nulo se invalido)
 * @param especialidadeId   especialidade do SIRG que casou; nulo quando o operador precisa escolher
 * @param especialidadeNome nome da especialidade que casou
 * @param criterio          como casou: "CODIGO_SUS" ou "NOME"
 * @param codigoSusAtual    codigo SUS ja gravado na especialidade
 * @param valorAtual        preco ja gravado na especialidade
 * @param valorOrigemAtual  origem do preco ja gravado (MANUAL merece atencao antes de sobrescrever)
 * @param situacao          o que a importacao fara com a linha
 * @param detalhe           explicacao curta da situacao, exibida na tela
 */
public record CustoImportacaoLinhaDTO(
        int linha,
        String codigoSus,
        String procedimento,
        BigDecimal valorUnitario,
        Long especialidadeId,
        String especialidadeNome,
        String criterio,
        String codigoSusAtual,
        BigDecimal valorAtual,
        OrigemValorEspecialidade valorOrigemAtual,
        SituacaoLinhaCustoEnum situacao,
        String detalhe) {
}
