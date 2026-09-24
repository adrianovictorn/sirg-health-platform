package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;

/**
 * Edicao de Solicitacao.
 *
 * Diferente de {@link SolicitacaoCreateDTO}, aqui os dados cadastrais do paciente
 * (nomePai, nomeMae, endereco, cns) NAO sao obrigatorios — de proposito.
 *
 * As solicitacoes anteriores a V80 tem esses campos nulos. Exigi-los na edicao
 * faria com que qualquer alteracao num registro antigo (corrigir um telefone, por
 * exemplo) passasse a exigir o preenchimento dos tres campos novos, travando a
 * operacao diaria em producao. A obrigatoriedade vale no cadastro: todo registro
 * NOVO nasce completo e a base se saneia naturalmente.
 *
 * Quando os registros historicos estiverem completos, o @NotBlank pode ser
 * promovido para ca e, na sequencia, o NOT NULL aplicado no banco.
 */
public record SolicitacaoUpdateDTO(
    Long unidadeId,
    String nomePaciente,
    String observacoes,
    String cns,
    String telefone,
    String nomePai,
    String nomeMae,
    String endereco,
    LocalDate datanascimento,
    LocalDate dataMalote,
    List<Long> cids,
    List<AgendamentoSolicitacao> agendamentoSolicitacaos,
    List<SolicitacaoEspecialidade> solicitacoesEspecialidade
) { }
