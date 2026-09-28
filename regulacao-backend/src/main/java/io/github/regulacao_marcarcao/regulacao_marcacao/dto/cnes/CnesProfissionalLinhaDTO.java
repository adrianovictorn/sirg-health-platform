package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes;

/**
 * Uma linha do CSV de extracao de profissionais do CNES, ja normalizada.
 *
 * <p>E o mesmo objeto nas duas pontas: sai na previa com {@code situacao} e
 * {@code detalhe} preenchidos e volta na confirmacao com as linhas que o
 * operador marcou. A tela nao precisa remontar nada, e o backend reconfere tudo
 * na hora de gravar — a previa informa, nunca autoriza.
 *
 * @param linha           numero da linha no arquivo, para o operador achar o erro
 * @param cnes            CNES do estabelecimento como veio no arquivo
 * @param estabelecimento nome do estabelecimento no arquivo (so exibicao)
 * @param unidadeId       unidade do SIRG resolvida pelo CNES; nulo = SEM_UNIDADE
 * @param unidadeNome     nome da unidade do SIRG, quando resolvida
 * @param cpf             11 digitos, sem mascara
 * @param nome            nome do profissional
 * @param cboCodigo       6 digitos, quando o arquivo traz
 * @param cboDescricao    descricao da ocupacao no arquivo
 * @param profissionalId  id no SIRG quando o CPF ja existe
 * @param situacao        o que a importacao fara com esta linha
 * @param detalhe         explicacao curta da situacao, exibida na tela
 */
public record CnesProfissionalLinhaDTO(
        int linha,
        String cnes,
        String estabelecimento,
        Long unidadeId,
        String unidadeNome,
        String cpf,
        String nome,
        String cboCodigo,
        String cboDescricao,
        Long profissionalId,
        SituacaoLinhaImportacaoEnum situacao,
        String detalhe) {

    /** Linha recem-lida do arquivo, antes de o servico decidir a situacao. */
    public static CnesProfissionalLinhaDTO lida(int linha, String cnes, String estabelecimento,
                                                String cpf, String nome,
                                                String cboCodigo, String cboDescricao) {
        return new CnesProfissionalLinhaDTO(linha, cnes, estabelecimento, null, null,
                cpf, nome, cboCodigo, cboDescricao, null, null, null);
    }

    public CnesProfissionalLinhaDTO com(SituacaoLinhaImportacaoEnum novaSituacao, String novoDetalhe) {
        return new CnesProfissionalLinhaDTO(linha, cnes, estabelecimento, unidadeId, unidadeNome,
                cpf, nome, cboCodigo, cboDescricao, profissionalId, novaSituacao, novoDetalhe);
    }

    public CnesProfissionalLinhaDTO comUnidade(Long novaUnidadeId, String novoUnidadeNome) {
        return new CnesProfissionalLinhaDTO(linha, cnes, estabelecimento, novaUnidadeId, novoUnidadeNome,
                cpf, nome, cboCodigo, cboDescricao, profissionalId, situacao, detalhe);
    }

    public CnesProfissionalLinhaDTO comProfissional(Long novoProfissionalId) {
        return new CnesProfissionalLinhaDTO(linha, cnes, estabelecimento, unidadeId, unidadeNome,
                cpf, nome, cboCodigo, cboDescricao, novoProfissionalId, situacao, detalhe);
    }

    /** Linhas que a confirmacao aceita gravar. */
    public boolean importavel() {
        return situacao == SituacaoLinhaImportacaoEnum.NOVO_PROFISSIONAL
                || situacao == SituacaoLinhaImportacaoEnum.NOVO_VINCULO;
    }
}
