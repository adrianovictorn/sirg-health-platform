package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;

public record SolicitacaoUpdateDTO(
    Long unidadeId,
    String nomePaciente,
    String observacoes,
    String cns,
    String telefone,
    LocalDate datanascimento,
    LocalDate dataMalote,
    List<Long> cids,
    List<AgendamentoSolicitacao> agendamentoSolicitacaos,
    List<SolicitacaoEspecialidade> solicitacoesEspecialidade
) { }
