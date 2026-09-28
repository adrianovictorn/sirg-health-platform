package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade;

/**
 * Cadastro de unidade.
 *
 * <p>Os campos a partir de {@code tipo} chegaram na V86, com o cadastro de
 * estabelecimento executante. Todos sao opcionais: a tela antiga, que envia
 * apenas nome/codigo/cnes/telefone/endereco, continua funcionando — {@code tipo}
 * nulo vira AMBOS, como as unidades que ja existiam.
 */
public record UnidadeCreateDTO(
        String nome,
        String codigo,
        String cnes,
        String telefone,
        String endereco,
        Long grupoRelatorioId,
        String tipo,
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String numero,
        String bairro,
        String cep,
        String email,
        /** true quando os dados vieram da busca por CNES; marca a data de sincronizacao. */
        Boolean importadoDoCnes) {
}
