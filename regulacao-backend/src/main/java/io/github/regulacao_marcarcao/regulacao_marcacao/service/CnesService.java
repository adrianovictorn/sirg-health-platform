package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesEstabelecimentoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.exceptions.ServicoExternoIndisponivelException;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

/**
 * Consulta estabelecimentos na API de dados abertos do DATASUS.
 *
 * <p>Verificado em 25/09/2026: a API e publica e nao exige autenticacao.
 * Existe endpoint de ESTABELECIMENTOS, mas <b>nao</b> de profissionais — todas
 * as variantes testadas (/cnes/profissionais, /cnes/vinculos, /cnes/equipes)
 * respondem 404. A importacao de profissionais entra por arquivo, na fase 2.
 *
 * <p>A chamada sai sempre do backend, nunca do browser: assim o CORS do DATASUS
 * nao entra na conta, a URL fica configuravel por instancia e os erros chegam
 * ao frontend no mesmo formato dos demais.
 */
@Service
@Slf4j
public class CnesService {

    private final RestClient cnesRestClient;
    private final UnidadeRepository unidadeRepository;
    private final String codigoMunicipioPadrao;

    public CnesService(RestClient cnesRestClient,
                       UnidadeRepository unidadeRepository,
                       @Value("${app.cnes.codigo-municipio:}") String codigoMunicipioPadrao) {
        this.cnesRestClient = cnesRestClient;
        this.unidadeRepository = unidadeRepository;
        this.codigoMunicipioPadrao = codigoMunicipioPadrao;
    }

    /**
     * Busca um estabelecimento pelo codigo CNES.
     *
     * @throws EntityNotFoundException quando o CNES nao existe na base nacional
     * @throws ServicoExternoIndisponivelException quando o DATASUS nao responde
     */
    public CnesEstabelecimentoDTO buscarPorCnes(String cnes) {
        String codigo = somenteDigitos(cnes);
        if (codigo == null) {
            throw new IllegalArgumentException("Informe um código CNES válido (apenas números).");
        }

        // 404 do DATASUS significa "esse CNES nao existe", nao "o DATASUS caiu" —
        // por isso o Optional vazio vira 404 aqui, e nao 503. Sem essa distincao o
        // operador via "serviço indisponível" ao digitar um CNES errado e ficava
        // tentando de novo em vez de conferir o numero.
        JsonNode resposta = chamar("/cnes/estabelecimentos/" + codigo,
                "Não foi possível consultar o CNES " + codigo + " no DATASUS.")
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nenhum estabelecimento encontrado para o CNES " + codigo + "."));

        if (resposta.isEmpty() || resposta.path("codigo_cnes").isMissingNode()) {
            throw new EntityNotFoundException("Nenhum estabelecimento encontrado para o CNES " + codigo + ".");
        }

        return marcarSeJaCadastrado(CnesEstabelecimentoDTO.from(resposta));
    }

    /**
     * Lista estabelecimentos de um municipio, para o autocomplete.
     *
     * <p>Atencao ao codigo: a API usa o IBGE de <b>6 digitos</b>, sem o digito
     * verificador. Sao Felipe e 292910 (e nao 2929107) e Conceicao do Almeida e
     * 290830. Passar os 7 digitos devolve lista vazia, sem erro — por isso a
     * normalizacao acontece aqui e nao na tela.
     */
    public List<CnesEstabelecimentoDTO> listarPorMunicipio(String codigoMunicipio, int limite, int offset) {
        String codigo = normalizarCodigoMunicipio(
                (codigoMunicipio == null || codigoMunicipio.isBlank()) ? codigoMunicipioPadrao : codigoMunicipio);

        if (codigo == null) {
            throw new IllegalArgumentException(
                    "Informe o código IBGE do município ou configure app.cnes.codigo-municipio.");
        }

        int limiteEfetivo = Math.clamp(limite, 1, 100);
        String uri = "/cnes/estabelecimentos?codigo_municipio=" + codigo
                + "&limit=" + limiteEfetivo
                + "&offset=" + Math.max(offset, 0);

        JsonNode resposta = chamar(uri, "Não foi possível listar os estabelecimentos do município no DATASUS.")
                .orElse(null);

        if (resposta == null) {
            // 404 nesta rota nao e "municipio sem estabelecimentos" (isso vem como
            // 200 com lista vazia) e sim rota mudada no DATASUS. Lista vazia evita
            // travar a tela, mas o aviso precisa ficar no log.
            log.warn("A rota de listagem do CNES respondeu 404 em {} — verificar se a API mudou.", uri);
            return List.of();
        }

        List<CnesEstabelecimentoDTO> estabelecimentos = new ArrayList<>();
        for (JsonNode no : resposta.path("estabelecimentos")) {
            estabelecimentos.add(marcarSeJaCadastrado(CnesEstabelecimentoDTO.from(no)));
        }
        return estabelecimentos;
    }

    /**
     * Chamada crua ao DATASUS.
     *
     * <p>Devolve {@code Optional.empty()} quando a API responde 404 — "nao ha esse
     * registro", que cada chamador interpreta a seu modo. Qualquer outra falha
     * (rede, timeout, 5xx) vira {@link ServicoExternoIndisponivelException}, ou
     * seja 503 na tela: instabilidade do DATASUS nao pode parecer defeito do SIRG
     * nem impedir o cadastro manual.
     */
    private Optional<JsonNode> chamar(String uri, String mensagemDeFalha) {
        try {
            return Optional.ofNullable(cnesRestClient.get().uri(uri).retrieve().body(JsonNode.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Falha ao consultar a API do CNES em {}: {}", uri, e.getMessage());
            throw new ServicoExternoIndisponivelException(
                    mensagemDeFalha + " O cadastro manual continua disponível.", e);
        }
    }

    /** Avisa a tela quando o CNES ja tem unidade, antes de o operador tentar salvar. */
    private CnesEstabelecimentoDTO marcarSeJaCadastrado(CnesEstabelecimentoDTO dto) {
        if (dto.cnes() == null) {
            return dto;
        }
        return unidadeRepository.findByCnes(dto.cnes())
                .map(u -> dto.comCadastroExistente(u.getId()))
                .orElse(dto);
    }

    /** 7 digitos (IBGE completo) -> 6 digitos (o que a API aceita). */
    private String normalizarCodigoMunicipio(String valor) {
        String codigo = somenteDigitos(valor);
        if (codigo == null) {
            return null;
        }
        return codigo.length() == 7 ? codigo.substring(0, 6) : codigo;
    }

    private String somenteDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.replaceAll("\\D", "");
        return limpo.isEmpty() ? null : limpo;
    }
}
