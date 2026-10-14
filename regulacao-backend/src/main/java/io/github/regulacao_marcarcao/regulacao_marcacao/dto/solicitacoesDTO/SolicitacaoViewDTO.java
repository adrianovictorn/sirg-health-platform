package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSolicitacaoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cid.CIDViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.SolicitacaoEspecialidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;

public record SolicitacaoViewDTO(
    Long id,
    String nomePaciente,
    String cpfPaciente,
    String cns,
    String telefone,
    Boolean whatsappOptOut,
    String nomePai,
    String nomeMae,
    String endereco,
    String datanascimento,
    String observacoes,
    Long unidadeId,
    String unidadeNome,
    String dataMalote,

    List<CIDViewDTO> cids,
    List<SolicitacaoEspecialidadeViewDTO> especialidades,
    List<AgendamentoSolicitacaoSimpleViewDTO> agendamentos
) {
    /**
     * Sem o registro do desfecho do atendimento (V109). Vale para as listagens e
     * para o que sai do municipio pela federacao (pacto): a observacao do executante
     * e texto livre sobre o paciente e so acompanha a ficha — ver {@link #paraFicha}.
     */
    public static SolicitacaoViewDTO fromSolicitacao(Solicitacao s) {
        return montar(s, SolicitacaoEspecialidadeViewDTO::fromSolicitacaoEspecialidade);
    }

    /** Ficha aberta por id, ja checada contra a unidade do operador: itens com o desfecho. */
    public static SolicitacaoViewDTO paraFicha(Solicitacao s) {
        return montar(s, SolicitacaoEspecialidadeViewDTO::paraFicha);
    }

    private static SolicitacaoViewDTO montar(Solicitacao s,
            Function<SolicitacaoEspecialidade, SolicitacaoEspecialidadeViewDTO> item) {
        List<SolicitacaoEspecialidadeViewDTO> especs = s.getEspecialidades() != null
            ? s.getEspecialidades().stream()
                  .map(item)
                  .collect(Collectors.toList())
            : List.of();

        List<AgendamentoSolicitacaoSimpleViewDTO> ags = s.getAgendamentos() != null
            ? s.getAgendamentos().stream()
                  .map(AgendamentoSolicitacaoSimpleViewDTO::fromAgendamentoSolicitacao)
                  .collect(Collectors.toList())
            : List.of();

        List<CIDViewDTO> cids = s.getCids() != null
            ? s.getCids().stream()
                  .map(CIDViewDTO::fromDTO).collect(Collectors.toList())
            : List.of();

        return new SolicitacaoViewDTO(
            s.getId(),
            s.getNomePaciente(),
            s.getCpfPaciente(),
            s.getCns(),
            s.getTelefone(),
            Boolean.TRUE.equals(s.getWhatsappOptOut()),
            s.getNomePai(),
            s.getNomeMae(),
            s.getEndereco(),
            s.getDataNascimento() != null ? s.getDataNascimento().toString() : null,
            s.getObservacoes(),
            s.getUnidade() != null ? s.getUnidade().getId() : null,
            s.getUnidade() != null ? s.getUnidade().getNome() : null,
            s.getDataMalote() != null ? s.getDataMalote().toString() : null,
            cids,
            especs,
            ags
        );
    }
}
