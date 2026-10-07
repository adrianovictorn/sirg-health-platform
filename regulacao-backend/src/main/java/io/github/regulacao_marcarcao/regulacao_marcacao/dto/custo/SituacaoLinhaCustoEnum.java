package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

/** O que a importacao de precos fara com uma linha da planilha. */
public enum SituacaoLinhaCustoEnum {
    /** A especialidade nao tem preco: a linha grava valor e codigo SUS. */
    NOVO_VALOR,
    /** O preco ja e o da planilha; falta so gravar o codigo SUS. */
    NOVO_CODIGO,
    /** Preco e codigo ja sao os da planilha. Reimportar nao altera nada. */
    VALOR_IGUAL,
    /** A especialidade ja tem outro preco ou outro codigo: so grava com confirmacao explicita. */
    VALOR_DIFERENTE,
    /** Mais de uma especialidade tem esse nome normalizado: o operador escolhe qual. */
    AMBIGUA,
    /** Nenhuma especialidade casou por codigo nem por nome: o operador escolhe, ou cadastra e reimporta. */
    SEM_CORRESPONDENCIA,
    /** Codigo ou valor ilegivel na planilha. */
    INVALIDA
}
