package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO;

import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.cidadeDTO.CidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;

public record LocalAgendamentoViewDTO(
    Long id,
    String nomeLocal,
    String endereco,
    String numero,
    CidadeViewDTO cidade,
    String enumValue,
    // Campos do estabelecimento (V91)
    String cnes,
    String cnpj,
    String razaoSocial,
    String nomeFantasia,
    String bairro,
    String cep,
    String telefone,
    String email,
    LocalDateTime sincronizadoCnesEm
) {
    public static LocalAgendamentoViewDTO fromEntity(LocalAgendamento localAgendamento) {
        return new LocalAgendamentoViewDTO(
            localAgendamento.getId(),
            localAgendamento.getNomeLocal(),
            localAgendamento.getEndereco(),
            localAgendamento.getNumero(),
            localAgendamento.getCidade() != null ? CidadeViewDTO.fromEntity(localAgendamento.getCidade()) : null,
            localAgendamento.getEnumValue(),
            localAgendamento.getCnes(),
            localAgendamento.getCnpj(),
            localAgendamento.getRazaoSocial(),
            localAgendamento.getNomeFantasia(),
            localAgendamento.getBairro(),
            localAgendamento.getCep(),
            localAgendamento.getTelefone(),
            localAgendamento.getEmail(),
            localAgendamento.getSincronizadoCnesEm()
        );
    }
}
