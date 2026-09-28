package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO;

public record LocalAgendamentoCreateDTO(
    String nomeLocal,
    Long cidadeId,
    String endereco,
    String numero,
    // Campos do estabelecimento (V91)
    String cnes,
    String cnpj,
    String razaoSocial,
    String nomeFantasia,
    String bairro,
    String cep,
    String telefone,
    String email,
    /** true quando os dados vieram da busca por CNES; marca a data de sincronizacao. */
    Boolean importadoDoCnes
) {}
