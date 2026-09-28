package io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums;

/**
 * Papel da unidade no fluxo de regulacao.
 *
 * <p>SOLICITANTE e quem abre solicitacao e recebe cota (as USF). EXECUTANTE e o
 * estabelecimento que realiza o atendimento — o prestador com CNES proprio, que
 * a agenda referencia. AMBOS e o caso comum no interior, onde a mesma unidade
 * solicita e executa.
 *
 * <p>Toda unidade existente virou AMBOS na V86: restringir depois e seguro,
 * restringir de cara quebraria os combos em producao.
 */
public enum TipoUnidadeEnum {
    SOLICITANTE,
    EXECUTANTE,
    AMBOS;

    public boolean executa() {
        return this == EXECUTANTE || this == AMBOS;
    }

    public boolean solicita() {
        return this == SOLICITANTE || this == AMBOS;
    }
}
