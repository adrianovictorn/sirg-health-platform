package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * As linhas que o operador marcou na conferencia.
 *
 * <p>Cada item ja traz a especialidade de destino — a que a previa casou ou a
 * que o operador escolheu a mao. O servidor reconfere tudo: a previa informa a
 * tela, nao autoriza a gravacao.
 */
public record CustoImportacaoConfirmarDTO(
        @NotEmpty(message = "Selecione ao menos uma linha para importar.")
        List<Item> itens) {

    /**
     * @param linha           numero da linha no arquivo, so para as mensagens
     * @param especialidadeId especialidade que recebe o preco
     * @param codigoSus       codigo SUS a gravar
     * @param valorUnitario   preco a gravar
     * @param sobrescrever    true = o operador confirmou substituir preco ou codigo ja gravado
     */
    public record Item(
            Integer linha,
            Long especialidadeId,
            String codigoSus,
            BigDecimal valorUnitario,
            boolean sobrescrever) {
    }
}
