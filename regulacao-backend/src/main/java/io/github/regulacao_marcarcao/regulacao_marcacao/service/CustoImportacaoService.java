package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoConfirmarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoLinhaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoPreviaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoResultadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.SituacaoLinhaCustoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemValorEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.PlanilhaCustoLeitorService.LinhaLida;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Importa precos e codigos SUS de uma planilha, em duas etapas — mesmo desenho
 * de {@link ProfissionalImportacaoService}:
 *
 * <ol>
 *   <li>{@link #previa} le o arquivo, confronta cada linha com o cadastro e
 *       <b>nao grava nada</b>.</li>
 *   <li>{@link #confirmar} grava apenas os itens que voltaram marcados, e
 *       <b>reconfere tudo</b>.</li>
 * </ol>
 *
 * <p>Por que nao e uma migration: os dois municipios tem cadastros diferentes e
 * os nomes da planilha do cliente tem erros de digitacao. Um preco atribuido a
 * especialidade errada vira numero financeiro errado no painel, e nada acusa.
 * Por isso <b>nada e casado por aproximacao</b>: so por codigo SUS ja gravado
 * ou por nome normalizado identico e unico. O resto o operador escolhe.
 *
 * <p>A importacao nunca cria nem renomeia especialidade, e e idempotente:
 * subir a mesma planilha duas vezes nao altera nada na segunda.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustoImportacaoService {

    private static final String CRITERIO_CODIGO = "CODIGO_SUS";
    private static final String CRITERIO_NOME = "NOME";

    private final PlanilhaCustoLeitorService leitor;
    private final EspecialidadeRepository especialidadeRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------
    // Etapa 1 — previa
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CustoImportacaoPreviaDTO previa(byte[] conteudo, String nomeArquivo) {
        List<LinhaLida> lidas = leitor.ler(conteudo, nomeArquivo);

        // Uma consulta em vez de duas por linha: o catalogo tem poucas centenas de itens.
        List<Especialidade> catalogo = especialidadeRepository.findAll();
        Map<String, List<Especialidade>> porCodigoSus = new HashMap<>();
        Map<String, List<Especialidade>> porNome = new HashMap<>();
        for (Especialidade e : catalogo) {
            if (e.getCodigoSus() != null) {
                porCodigoSus.computeIfAbsent(e.getCodigoSus(), k -> new ArrayList<>()).add(e);
            }
            String chave = normalizarNome(e.getNome());
            if (!chave.isEmpty()) {
                porNome.computeIfAbsent(chave, k -> new ArrayList<>()).add(e);
            }
        }

        List<CustoImportacaoLinhaDTO> linhas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        Map<String, Integer> codigosVistos = new HashMap<>();

        for (LinhaLida lida : lidas) {
            if (lida.problema() != null) {
                linhas.add(semDestino(lida, SituacaoLinhaCustoEnum.INVALIDA, lida.problema()));
                continue;
            }

            Integer primeira = codigosVistos.putIfAbsent(lida.codigoSus(), lida.linha());
            if (primeira != null) {
                avisos.add("O código " + lida.codigoSus() + " aparece nas linhas " + primeira + " e "
                        + lida.linha() + " da planilha. Confira qual valor vale antes de confirmar.");
            }

            List<Especialidade> peloCodigo = porCodigoSus.getOrDefault(lida.codigoSus(), List.of());
            if (!peloCodigo.isEmpty()) {
                for (Especialidade e : peloCodigo) {
                    linhas.add(comDestino(lida, e, CRITERIO_CODIGO));
                }
                continue;
            }

            List<Especialidade> peloNome = porNome.getOrDefault(normalizarNome(lida.procedimento()), List.of());
            if (peloNome.size() == 1) {
                linhas.add(comDestino(lida, peloNome.get(0), CRITERIO_NOME));
            } else if (peloNome.size() > 1) {
                linhas.add(semDestino(lida, SituacaoLinhaCustoEnum.AMBIGUA,
                        peloNome.size() + " especialidades têm esse nome. Escolha a correta."));
            } else {
                linhas.add(semDestino(lida, SituacaoLinhaCustoEnum.SEM_CORRESPONDENCIA,
                        "Nenhuma especialidade com este código SUS ou com este nome. "
                                + "Escolha a especialidade, ou cadastre-a e importe de novo."));
            }
        }

        int prontas = 0;
        int iguais = 0;
        int diferentes = 0;
        int pendentes = 0;
        int invalidas = 0;
        for (CustoImportacaoLinhaDTO linha : linhas) {
            switch (linha.situacao()) {
                case NOVO_VALOR, NOVO_CODIGO -> prontas++;
                case VALOR_IGUAL -> iguais++;
                case VALOR_DIFERENTE -> diferentes++;
                case AMBIGUA, SEM_CORRESPONDENCIA -> pendentes++;
                case INVALIDA -> invalidas++;
            }
        }

        return new CustoImportacaoPreviaDTO(nomeArquivo, lidas.size(), prontas, iguais, diferentes, pendentes,
                invalidas, linhas, avisos);
    }

    private CustoImportacaoLinhaDTO semDestino(LinhaLida lida, SituacaoLinhaCustoEnum situacao, String detalhe) {
        return new CustoImportacaoLinhaDTO(lida.linha(),
                lida.codigoSus() != null ? lida.codigoSus() : lida.codigoSusBruto(),
                lida.procedimento(), lida.valorUnitario(),
                null, null, null, null, null, null, situacao, detalhe);
    }

    private CustoImportacaoLinhaDTO comDestino(LinhaLida lida, Especialidade e, String criterio) {
        boolean codigoIgual = Objects.equals(e.getCodigoSus(), lida.codigoSus());
        boolean valorIgual = CustoEspecialidadeService.mesmoValor(e.getValorUnitario(), lida.valorUnitario());
        boolean codigoConflita = e.getCodigoSus() != null && !codigoIgual;
        boolean valorConflita = e.getValorUnitario() != null && !valorIgual;

        SituacaoLinhaCustoEnum situacao;
        String detalhe;
        if (codigoConflita) {
            situacao = SituacaoLinhaCustoEnum.VALOR_DIFERENTE;
            detalhe = "A especialidade já tem outro código SUS (" + e.getCodigoSus() + "). Só grava se você confirmar.";
        } else if (valorConflita) {
            situacao = SituacaoLinhaCustoEnum.VALOR_DIFERENTE;
            detalhe = e.getValorOrigem() == OrigemValorEspecialidade.MANUAL
                    ? "O preço atual foi digitado à mão e é diferente. Só grava se você confirmar."
                    : "O preço atual é diferente. Só grava se você confirmar.";
        } else if (valorIgual && codigoIgual) {
            situacao = SituacaoLinhaCustoEnum.VALOR_IGUAL;
            detalhe = "Preço e código já gravados. Importar não altera nada.";
        } else if (valorIgual) {
            situacao = SituacaoLinhaCustoEnum.NOVO_CODIGO;
            detalhe = "O preço já é este; grava o código SUS.";
        } else {
            situacao = SituacaoLinhaCustoEnum.NOVO_VALOR;
            detalhe = CRITERIO_CODIGO.equals(criterio)
                    ? "Casou pelo código SUS."
                    : "Casou pelo nome. Confira se é o mesmo procedimento.";
        }

        return new CustoImportacaoLinhaDTO(lida.linha(), lida.codigoSus(), lida.procedimento(), lida.valorUnitario(),
                e.getId(), e.getNome(), criterio, e.getCodigoSus(), e.getValorUnitario(), e.getValorOrigem(),
                situacao, detalhe);
    }

    // ------------------------------------------------------------------
    // Etapa 2 — confirmacao
    // ------------------------------------------------------------------

    /**
     * Grava os itens marcados pelo operador.
     *
     * <p>Transacao unica. Item que nao da para gravar e <b>pulado com aviso</b>,
     * nunca derruba o lote. Preco ou codigo ja gravado e diferente so e
     * substituido com {@code sobrescrever = true} naquele item.
     */
    @Transactional
    public CustoImportacaoResultadoDTO confirmar(CustoImportacaoConfirmarDTO dto, String callerCpf) {
        User autor = callerCpf != null ? userRepository.findByCpf(callerCpf).orElse(null) : null;
        LocalDateTime agora = LocalDateTime.now();

        List<String> avisos = new ArrayList<>();
        Set<Long> jaTratadas = new HashSet<>();
        int gravadas = 0;
        int inalteradas = 0;
        int ignoradas = 0;

        for (CustoImportacaoConfirmarDTO.Item item : dto.itens()) {
            String rotulo = item.linha() != null ? "Linha " + item.linha() : "Item";

            if (item.especialidadeId() == null) {
                avisos.add(rotulo + " ignorada: nenhuma especialidade escolhida.");
                ignoradas++;
                continue;
            }

            String codigoSus;
            BigDecimal valor;
            try {
                codigoSus = CustoEspecialidadeService.normalizarCodigoSus(item.codigoSus());
                valor = CustoEspecialidadeService.normalizarValor(item.valorUnitario());
            } catch (IllegalArgumentException e) {
                avisos.add(rotulo + " ignorada: " + e.getMessage());
                ignoradas++;
                continue;
            }
            if (codigoSus == null || valor == null) {
                avisos.add(rotulo + " ignorada: sem código SUS ou sem valor unitário.");
                ignoradas++;
                continue;
            }

            Especialidade especialidade = especialidadeRepository.findById(item.especialidadeId()).orElse(null);
            if (especialidade == null) {
                avisos.add(rotulo + " ignorada: especialidade " + item.especialidadeId() + " não encontrada.");
                ignoradas++;
                continue;
            }

            // Duas linhas apontando para a mesma especialidade: vale a primeira. A
            // segunda gravaria por cima sem ninguem ter escolhido entre as duas.
            if (!jaTratadas.add(especialidade.getId())) {
                avisos.add(rotulo + " ignorada: \"" + especialidade.getNome()
                        + "\" já recebeu o valor de outra linha deste lote.");
                ignoradas++;
                continue;
            }

            boolean codigoIgual = Objects.equals(especialidade.getCodigoSus(), codigoSus);
            boolean valorIgual = CustoEspecialidadeService.mesmoValor(especialidade.getValorUnitario(), valor);
            if (codigoIgual && valorIgual) {
                inalteradas++;
                continue;
            }

            boolean sobrescreve = (especialidade.getCodigoSus() != null && !codigoIgual)
                    || (especialidade.getValorUnitario() != null && !valorIgual);
            if (sobrescreve && !item.sobrescrever()) {
                avisos.add(rotulo + " ignorada: \"" + especialidade.getNome()
                        + "\" já tem preço ou código SUS diferente e a substituição não foi confirmada.");
                ignoradas++;
                continue;
            }

            especialidade.setCodigoSus(codigoSus);
            if (!valorIgual) {
                especialidade.setValorUnitario(valor);
                especialidade.setValorOrigem(OrigemValorEspecialidade.IMPORTACAO);
                especialidade.setValorAtualizadoEm(agora);
                especialidade.setValorAtualizadoPor(autor);
            }
            especialidadeRepository.save(especialidade);
            gravadas++;
        }

        log.info("Importação de preços concluída: {} gravadas, {} inalteradas, {} ignoradas.",
                gravadas, inalteradas, ignoradas);
        return new CustoImportacaoResultadoDTO(gravadas, inalteradas, ignoradas, avisos);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /**
     * Nome sem acento, sem pontuacao, sem espaco e em maiusculas — para que
     * "Dosagem de Glicose" e "DOSAGEM DE GLICOSE" casem. So remove ruido de
     * formatacao: palavras diferentes continuam diferentes.
     */
    static String normalizarNome(String nome) {
        if (nome == null) {
            return "";
        }
        return Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "");
    }
}
