package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.validator.constraints.br.CPF;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.UsfEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.validation.UniqueCPF;
import jakarta.validation.constraints.NotBlank;

public record SolicitacaoCreateDTO(
    UsfEnum usfOrigem,
    Long unidadeId,
    String nomePaciente,
    @NotBlank
    @CPF
    @UniqueCPF 
    String cpfPaciente,
    String cns,
    String telefone,
    LocalDate datanascimento,
    String observacoes,
    LocalDate dataMalote,
    List<Long> cids,
    List<SolicitacaoEspecialidadeCreateDTO> especialidades
) { }
