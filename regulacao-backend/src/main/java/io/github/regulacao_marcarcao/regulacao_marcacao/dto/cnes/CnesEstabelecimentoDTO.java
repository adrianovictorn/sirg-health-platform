package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Estabelecimento como o SIRG o enxerga, ja normalizado.
 *
 * <p>A resposta crua do DATASUS tem ~40 campos (leitos, turno de atendimento,
 * latitude, natureza juridica...). Aqui ficam apenas os que a tela de cadastro
 * usa — o resto e descartado de proposito, para que uma mudanca no formato do
 * DATASUS nao se espalhe pelo sistema.
 *
 * <p>O campo {@code jaCadastrado} evita o erro mais comum da tela: buscar um
 * CNES que ja existe no banco e so descobrir no momento de salvar.
 */
public record CnesEstabelecimentoDTO(
        String cnes,
        String razaoSocial,
        String nomeFantasia,
        String cnpj,
        String endereco,
        String numero,
        String bairro,
        String cep,
        String telefone,
        String email,
        Integer codigoMunicipio,
        boolean jaCadastrado,
        Long unidadeId) {

    /**
     * Le o JSON do DATASUS. Todo campo e opcional: estabelecimento pequeno
     * costuma vir sem CNPJ, sem e-mail e sem telefone, e isso nao e erro.
     */
    public static CnesEstabelecimentoDTO from(JsonNode no) {
        return new CnesEstabelecimentoDTO(
                texto(no, "codigo_cnes"),
                texto(no, "nome_razao_social"),
                texto(no, "nome_fantasia"),
                // O DATASUS usa dois campos para a mesma coisa e preenche um ou
                // outro conforme o vinculo do estabelecimento: consultorio privado
                // traz `numero_cnpj`, unidade publica traz `numero_cnpj_entidade`
                // (o CNPJ da prefeitura mantenedora). Ler so um devolvia nulo em
                // metade dos casos.
                somenteDigitos(primeiroPreenchido(
                        texto(no, "numero_cnpj"),
                        texto(no, "numero_cnpj_entidade"))),
                texto(no, "endereco_estabelecimento"),
                texto(no, "numero_estabelecimento"),
                texto(no, "bairro_estabelecimento"),
                somenteDigitos(texto(no, "codigo_cep_estabelecimento")),
                texto(no, "numero_telefone_estabelecimento"),
                texto(no, "endereco_email_estabelecimento"),
                no.path("codigo_municipio").isNumber() ? no.path("codigo_municipio").asInt() : null,
                false,
                null);
    }

    /** Mesma leitura, marcando que este CNES ja tem unidade no banco. */
    public CnesEstabelecimentoDTO comCadastroExistente(Long unidadeId) {
        return new CnesEstabelecimentoDTO(cnes, razaoSocial, nomeFantasia, cnpj, endereco,
                numero, bairro, cep, telefone, email, codigoMunicipio, true, unidadeId);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        String texto = valor.asText().trim();
        return texto.isEmpty() ? null : texto;
    }

    private static String primeiroPreenchido(String a, String b) {
        return a != null ? a : b;
    }

    private static String somenteDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.replaceAll("\\D", "");
        return limpo.isEmpty() ? null : limpo;
    }
}
