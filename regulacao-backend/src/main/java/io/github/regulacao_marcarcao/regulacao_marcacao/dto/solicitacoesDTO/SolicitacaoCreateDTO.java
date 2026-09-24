package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.validator.constraints.br.CPF;

import io.github.regulacao_marcarcao.regulacao_marcacao.validation.UniqueCPF;
import jakarta.validation.constraints.NotBlank;

public record SolicitacaoCreateDTO(
    Long unidadeId,
    @NotBlank(message = "O nome do paciente é obrigatório.")
    String nomePaciente,
    @NotBlank(message = "O CPF do paciente é obrigatório.")
    @CPF
    @UniqueCPF
    String cpfPaciente,
    @NotBlank(message = "O CNS do paciente é obrigatório.")
    String cns,
    String telefone,
    @NotBlank(message = "O nome do pai é obrigatório.")
    String nomePai,
    @NotBlank(message = "O nome da mãe é obrigatório.")
    String nomeMae,
    @NotBlank(message = "O endereço é obrigatório.")
    String endereco,
    LocalDate datanascimento,
    String observacoes,
    LocalDate dataMalote,
    List<Long> cids,
    List<SolicitacaoEspecialidadeCreateDTO> especialidades
) { }
