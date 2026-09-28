package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade;

/** Edicao de unidade. Mesmos campos do cadastro — ver {@link UnidadeCreateDTO}. */
public record UnidadeUpdateDTO(
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
        Boolean importadoDoCnes) {
}
