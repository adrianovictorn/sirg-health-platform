package io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoPacienteProjection;

/**
 * Bloco de um paciente (ficha) na tela de Atendimentos: quem e, e os itens dele no
 * dia e na agenda consultada.
 *
 * CPF e CNS saem COMPLETOS, como estao gravados — decisao registrada no ADR 0010:
 * o operador confere o documento do paciente no balcao. Por isso este DTO so sai
 * por {@code GET /api/atendimentos}, restrito a quem opera a tela (USER e ADMIN).
 */
public record AtendimentoPacienteViewDTO(
        Long solicitacaoId,
        String nomePaciente,
        String cpf,
        String cns,
        LocalDate dataNascimento,
        String unidadeNome,
        List<AtendimentoViewDTO> itens) {

    public static AtendimentoPacienteViewDTO from(AtendimentoPacienteProjection p, List<AtendimentoViewDTO> itens) {
        return new AtendimentoPacienteViewDTO(
                p.getSolicitacaoId(),
                p.getNomePaciente(),
                p.getCpf(),
                p.getCns(),
                p.getDataNascimento(),
                p.getUnidadeNome(),
                itens);
    }
}
