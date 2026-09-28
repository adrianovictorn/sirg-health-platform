package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes;

/**
 * O que acontecera com cada linha do arquivo do CNES, decidido na PREVIA.
 *
 * <p>A tela mostra isso antes de gravar nada: o oficio pede selecao em lote, e
 * marcar as cegas as 400 linhas de um arquivo do DATASUS e o mesmo que nao
 * conferir.
 */
public enum SituacaoLinhaImportacaoEnum {

    /** CPF inexistente no SIRG: cria profissional e vinculo. */
    NOVO_PROFISSIONAL,

    /** Profissional ja existe; falta o vinculo com este estabelecimento/CBO. */
    NOVO_VINCULO,

    /** Profissional e vinculo ja existem. Reimportar nao muda nada. */
    JA_EXISTE,

    /** O CNES do arquivo nao corresponde a nenhuma unidade cadastrada no SIRG. */
    SEM_UNIDADE,

    /** Linha sem CPF ou sem nome — nao da para importar. */
    INVALIDA
}
