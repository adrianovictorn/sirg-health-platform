package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesImportacaoConfirmarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesImportacaoPreviaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesImportacaoResultadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesProfissionalLinhaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.SituacaoLinhaImportacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cbo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.ProfissionalVinculo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemVinculoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CboRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalVinculoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Importa profissionais e vinculos a partir do CSV do CNES.
 *
 * <p>Duas etapas, deliberadamente separadas:
 *
 * <ol>
 *   <li>{@link #previa} le o arquivo, classifica cada linha e <b>nao grava
 *       nada</b>. E o "devolve o que foi lido para conferencia" do oficio: o
 *       arquivo do DATASUS traz o estabelecimento inteiro, e quase nunca e o
 *       estabelecimento inteiro que a coordenacao quer cadastrar.</li>
 *   <li>{@link #confirmar} grava apenas as linhas que voltaram marcadas, e
 *       <b>reconfere tudo</b>. A previa informa a tela, nao autoriza a gravacao —
 *       entre uma etapa e outra alguem pode ter cadastrado o mesmo CPF.</li>
 * </ol>
 *
 * <p>A importacao e IDEMPOTENTE: subir o mesmo arquivo duas vezes nao duplica
 * profissional (dedupe por CPF) nem vinculo (dedupe pela tripla profissional x
 * unidade x CBO). Isso importa porque a coordenacao reimporta o arquivo todo
 * quando muda uma linha.
 *
 * <p>Ver docs/especificacoes/Agenda e Oferta.md, secao 5.2.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfissionalImportacaoService {

    private final CnesProfissionalCsvService csvService;
    private final ProfissionalRepository profissionalRepository;
    private final ProfissionalVinculoRepository vinculoRepository;
    private final UnidadeRepository unidadeRepository;
    private final CboService cboService;
    private final CboRepository cboRepository;

    // ------------------------------------------------------------------
    // Etapa 1 — previa
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CnesImportacaoPreviaDTO previa(byte[] conteudo, String nomeArquivo) {
        List<String> avisos = new ArrayList<>();
        List<CnesProfissionalLinhaDTO> lidas = csvService.ler(conteudo, avisos);

        Map<String, Unidade> unidadesPorCnes = indexarUnidadesPorCnes();
        List<CnesProfissionalLinhaDTO> classificadas = new ArrayList<>(lidas.size());
        List<String> cnesSemUnidade = new ArrayList<>();

        int importaveis = 0;
        int jaExistentes = 0;
        int ignoradas = 0;

        for (CnesProfissionalLinhaDTO linha : lidas) {
            CnesProfissionalLinhaDTO classificada = classificar(linha, unidadesPorCnes);
            classificadas.add(classificada);

            switch (classificada.situacao()) {
                case NOVO_PROFISSIONAL, NOVO_VINCULO -> importaveis++;
                case JA_EXISTE -> jaExistentes++;
                default -> {
                    ignoradas++;
                    if (classificada.situacao() == SituacaoLinhaImportacaoEnum.SEM_UNIDADE
                            && classificada.cnes() != null
                            && !cnesSemUnidade.contains(classificada.cnes())) {
                        cnesSemUnidade.add(classificada.cnes());
                    }
                }
            }
        }

        if (!cnesSemUnidade.isEmpty()) {
            // Aviso acionavel: o caminho e cadastrar o estabelecimento pelo CNES
            // (fase 1) e subir o arquivo de novo, ou escolher a unidade na tela.
            avisos.add("Sem unidade cadastrada para o CNES " + String.join(", ", cnesSemUnidade)
                    + ". Cadastre o estabelecimento pelo código CNES ou escolha a unidade manualmente.");
        }

        return new CnesImportacaoPreviaDTO(
                nomeArquivo, lidas.size(), importaveis, jaExistentes, ignoradas, classificadas, avisos);
    }

    private CnesProfissionalLinhaDTO classificar(CnesProfissionalLinhaDTO linha,
                                                 Map<String, Unidade> unidadesPorCnes) {
        String problema = validar(linha);
        if (problema != null) {
            return linha.com(SituacaoLinhaImportacaoEnum.INVALIDA, problema);
        }

        Unidade unidade = unidadesPorCnes.get(chaveCnes(linha.cnes()));
        if (unidade == null) {
            return linha.com(SituacaoLinhaImportacaoEnum.SEM_UNIDADE,
                    linha.cnes() == null
                            ? "O arquivo não traz o CNES do estabelecimento."
                            : "Nenhuma unidade cadastrada com o CNES " + linha.cnes() + ".");
        }

        CnesProfissionalLinhaDTO comUnidade = linha.comUnidade(unidade.getId(), unidade.getNome());

        Optional<Profissional> existente = profissionalRepository.findByCpf(linha.cpf());
        if (existente.isEmpty()) {
            return comUnidade.com(SituacaoLinhaImportacaoEnum.NOVO_PROFISSIONAL,
                    "Novo profissional e novo vínculo com " + unidade.getNome() + ".");
        }

        Profissional profissional = existente.get();
        CnesProfissionalLinhaDTO comProfissional = comUnidade.comProfissional(profissional.getId());

        // O CBO e parte da identidade do vinculo: o mesmo CPF no mesmo
        // estabelecimento com outra ocupacao e outro vinculo, nao um duplicado.
        Long cboId = idDoCboExistente(linha.cboCodigo());
        boolean vinculoExiste = buscarVinculo(profissional.getId(), unidade.getId(), cboId).isPresent();

        if (vinculoExiste) {
            return comProfissional.com(SituacaoLinhaImportacaoEnum.JA_EXISTE,
                    "Vínculo já cadastrado. Reimportar não altera nada.");
        }
        return comProfissional.com(SituacaoLinhaImportacaoEnum.NOVO_VINCULO,
                "Profissional já cadastrado; será criado o vínculo com " + unidade.getNome() + ".");
    }

    // ------------------------------------------------------------------
    // Etapa 2 — confirmacao
    // ------------------------------------------------------------------

    /**
     * Grava as linhas marcadas pelo operador.
     *
     * <p>Transacao unica: ou entra o lote conferido, ou nao entra nada. Linha que
     * nao da para importar e <b>pulada com aviso</b>, nunca derruba o lote — num
     * arquivo de 400 linhas, abortar tudo por causa de um CPF malformado obrigaria
     * a coordenacao a editar o CSV para conseguir importar os outros 399.
     */
    @Transactional
    public CnesImportacaoResultadoDTO confirmar(CnesImportacaoConfirmarDTO dto) {
        Map<String, Unidade> unidadesPorCnes = indexarUnidadesPorCnes();
        Unidade unidadePadrao = unidadePadrao(dto.unidadeIdPadrao());

        List<String> avisos = new ArrayList<>();
        // Cache local do lote: o mesmo CPF aparece em varias linhas (um vinculo por
        // CBO), e sem isso a segunda linha nao acharia o profissional criado na
        // primeira — que ainda nao esta visivel para uma nova consulta.
        Map<String, Profissional> profissionaisDoLote = new HashMap<>();
        Map<String, Cbo> cbosDoLote = new HashMap<>();

        int profissionaisCriados = 0;
        int profissionaisReaproveitados = 0;
        int vinculosCriados = 0;
        int vinculosJaExistentes = 0;
        int cbosCriados = 0;
        int ignoradas = 0;

        for (CnesProfissionalLinhaDTO linha : dto.linhas()) {
            String problema = validar(linha);
            if (problema != null) {
                avisos.add("Linha " + linha.linha() + " ignorada: " + problema);
                ignoradas++;
                continue;
            }

            Unidade unidade = resolverUnidade(linha, unidadesPorCnes, unidadePadrao);
            if (unidade == null) {
                avisos.add("Linha " + linha.linha() + " ignorada: nenhuma unidade cadastrada para o CNES "
                        + linha.cnes() + ". Cadastre o estabelecimento ou escolha a unidade.");
                ignoradas++;
                continue;
            }

            // CBO primeiro: o vinculo precisa do id dele para a checagem de duplicata.
            Cbo cbo = null;
            if (linha.cboCodigo() != null) {
                cbo = cbosDoLote.get(linha.cboCodigo());
                if (cbo == null) {
                    CboService.Upsert upsert = cboService.buscarOuCriar(linha.cboCodigo(), linha.cboDescricao());
                    cbo = upsert.cbo();
                    if (upsert.criado()) {
                        cbosCriados++;
                    }
                    if (cbo != null) {
                        cbosDoLote.put(linha.cboCodigo(), cbo);
                    }
                }
            }

            Profissional profissional = profissionaisDoLote.get(linha.cpf());
            if (profissional == null) {
                Optional<Profissional> existente = profissionalRepository.findByCpf(linha.cpf());
                if (existente.isPresent()) {
                    profissional = existente.get();
                    profissionaisReaproveitados++;
                } else {
                    profissional = criarProfissional(linha, unidade);
                    profissionaisCriados++;
                }
                profissionaisDoLote.put(linha.cpf(), profissional);
            }

            Long cboId = cbo != null ? cbo.getId() : null;
            if (buscarVinculo(profissional.getId(), unidade.getId(), cboId).isPresent()) {
                vinculosJaExistentes++;
                continue;
            }

            ProfissionalVinculo vinculo = new ProfissionalVinculo();
            vinculo.setProfissional(profissional);
            vinculo.setUnidade(unidade);
            vinculo.setCbo(cbo);
            vinculo.setAtivo(true);
            vinculo.setOrigem(OrigemVinculoEnum.IMPORTACAO_CNES);
            vinculoRepository.save(vinculo);
            vinculosCriados++;
        }

        log.info("Importação CNES concluída: {} profissionais criados, {} vínculos criados, {} ignoradas.",
                profissionaisCriados, vinculosCriados, ignoradas);

        return new CnesImportacaoResultadoDTO(profissionaisCriados, profissionaisReaproveitados,
                vinculosCriados, vinculosJaExistentes, cbosCriados, ignoradas, avisos);
    }

    @SuppressWarnings("deprecation")
    private Profissional criarProfissional(CnesProfissionalLinhaDTO linha, Unidade unidade) {
        Profissional novo = new Profissional();
        novo.setNome(linha.nome());
        novo.setCpf(linha.cpf());
        novo.setAtivo(true);
        // Preenche tambem o campo deprecado `unidade`: as telas de profissionais
        // ainda leem dele, e deixar nulo faria o importado aparecer sem unidade.
        // Sai quando os pontos de leitura migrarem para ProfissionalVinculo (V88).
        novo.setUnidade(unidade);
        return profissionalRepository.save(novo);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /** Erro que impede a importacao da linha, ou nulo quando ela esta boa. */
    private String validar(CnesProfissionalLinhaDTO linha) {
        if (linha.nome() == null || linha.nome().isBlank()) {
            return "sem nome do profissional.";
        }
        if (linha.cpf() == null) {
            return "sem CPF, que é a chave usada para não duplicar o profissional.";
        }
        if (linha.cpf().length() != 11) {
            return "CPF com " + linha.cpf().length() + " dígitos (\"" + linha.cpf() + "\").";
        }
        return null;
    }

    private Unidade resolverUnidade(CnesProfissionalLinhaDTO linha,
                                    Map<String, Unidade> unidadesPorCnes,
                                    Unidade unidadePadrao) {
        // Precedencia: o que a tela mandou na linha, depois o CNES do arquivo,
        // depois o estabelecimento escolhido para o lote. A escolha explicita do
        // operador vem primeiro porque ele pode estar corrigindo o arquivo.
        if (linha.unidadeId() != null) {
            return unidadeRepository.findById(linha.unidadeId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Unidade " + linha.unidadeId() + " não encontrada."));
        }
        Unidade porCnes = unidadesPorCnes.get(chaveCnes(linha.cnes()));
        return porCnes != null ? porCnes : unidadePadrao;
    }

    private Unidade unidadePadrao(Long unidadeId) {
        if (unidadeId == null) {
            return null;
        }
        return unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new EntityNotFoundException("Unidade " + unidadeId + " não encontrada."));
    }

    /**
     * Unidades indexadas pelo CNES normalizado.
     *
     * <p>Uma consulta em vez de uma por linha: sao dezenas de unidades e centenas
     * de linhas. A normalizacao (digitos, sem zeros a esquerda) existe porque o
     * CNES do arquivo e o do cadastro podem diferir na formatacao — "2514648" e
     * "0002514648" sao o mesmo estabelecimento.
     */
    private Map<String, Unidade> indexarUnidadesPorCnes() {
        Map<String, Unidade> porCnes = new LinkedHashMap<>();
        for (Unidade unidade : unidadeRepository.findAll()) {
            String chave = chaveCnes(unidade.getCnes());
            if (chave != null) {
                porCnes.putIfAbsent(chave, unidade);
            }
        }
        return porCnes;
    }

    private String chaveCnes(String cnes) {
        if (cnes == null) {
            return null;
        }
        String digitos = cnes.replaceAll("\\D", "").replaceFirst("^0+", "");
        return digitos.isEmpty() ? null : digitos;
    }

    private Long idDoCboExistente(String codigo) {
        if (codigo == null) {
            return null;
        }
        return cboRepository.findByCodigo(codigo).map(Cbo::getId).orElse(null);
    }

    private Optional<ProfissionalVinculo> buscarVinculo(Long profissionalId, Long unidadeId, Long cboId) {
        // Consultas diferentes para CBO nulo e nao nulo: em SQL, `cbo_id = NULL`
        // nunca casa. Ver ProfissionalVinculoRepository.
        return cboId == null
                ? vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboIsNull(profissionalId, unidadeId)
                : vinculoRepository.findByProfissionalIdAndUnidadeIdAndCboId(profissionalId, unidadeId, cboId);
    }
}
