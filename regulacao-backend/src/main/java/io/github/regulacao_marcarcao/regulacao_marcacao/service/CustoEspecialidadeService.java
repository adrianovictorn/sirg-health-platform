package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.EspecialidadeCustoUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.EspecialidadeCustoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemValorEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Preco e codigo SUS das especialidades (V105).
 *
 * <p>Unico ponto que le e grava esses campos pela tela. Quem pode chamar e
 * decidido no controller ({@code @PreAuthorize}); aqui ficam as regras de
 * formato, compartilhadas com a importacao de planilha.
 */
@Service
@RequiredArgsConstructor
public class CustoEspecialidadeService {

    private static final int TAMANHO_MAXIMO_PAGINA = 200;
    private static final int DIGITOS_CODIGO_SUS = 10;
    /** NUMERIC(12,2): ate 10 digitos inteiros. */
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999.99");

    private final EspecialidadeRepository especialidadeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<EspecialidadeCustoViewDTO> listar(String nome, Long grupoRelatorioId, boolean somenteSemPreco,
            int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), TAMANHO_MAXIMO_PAGINA),
                Sort.by("nome").ascending());

        Specification<Especialidade> filtro = (root, query, cb) -> cb.conjunction();
        if (nome != null && !nome.isBlank()) {
            String termo = "%" + nome.trim().toLowerCase() + "%";
            filtro = filtro.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nome")), termo),
                    cb.like(root.get("codigoSus"), termo)));
        }
        if (grupoRelatorioId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("grupoRelatorio").get("id"), grupoRelatorioId));
        }
        if (somenteSemPreco) {
            filtro = filtro.and((root, query, cb) -> cb.isNull(root.get("valorUnitario")));
        }

        return especialidadeRepository.findAll(filtro, pageable).map(EspecialidadeCustoViewDTO::from);
    }

    @Transactional
    public EspecialidadeCustoViewDTO atualizar(Long id, EspecialidadeCustoUpdateDTO dto, String callerCpf) {
        Especialidade especialidade = especialidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Especialidade não encontrada: " + id));

        String codigoSus = normalizarCodigoSus(dto.codigoSus());
        BigDecimal valor = normalizarValor(dto.valorUnitario());

        boolean valorMudou = !mesmoValor(especialidade.getValorUnitario(), valor);
        especialidade.setCodigoSus(codigoSus);
        if (valorMudou) {
            especialidade.setValorUnitario(valor);
            especialidade.setValorOrigem(valor != null ? OrigemValorEspecialidade.MANUAL : null);
            especialidade.setValorAtualizadoEm(LocalDateTime.now());
            // Autoria e metadado: nunca impede a gravacao se o usuario nao for encontrado.
            especialidade.setValorAtualizadoPor(
                    callerCpf != null ? userRepository.findByCpf(callerCpf).orElse(null) : null);
        }
        return EspecialidadeCustoViewDTO.from(especialidadeRepository.save(especialidade));
    }

    // ------------------------------------------------------------------
    // Regras de formato — usadas tambem por CustoImportacaoService
    // ------------------------------------------------------------------

    /**
     * Codigo SIGTAP com 10 digitos, ou nulo quando nao informado.
     *
     * <p>Aceita 9 digitos e repoe o zero a esquerda: todo codigo SIGTAP comeca
     * com zero e planilhas costumam perde-lo ao tratar a coluna como numero.
     * Qualquer outro tamanho e recusado — completar com mais zeros fabricaria um
     * codigo plausivel e errado.
     *
     * @throws IllegalArgumentException quando o formato nao e reconhecivel
     */
    public static String normalizarCodigoSus(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        String limpo = bruto.trim();
        if (!limpo.matches("[0-9 .\\-]+")) {
            throw new IllegalArgumentException(
                    "Código SUS inválido: \"" + limpo + "\". Use os 10 dígitos do SIGTAP.");
        }
        String digitos = limpo.replaceAll("[^0-9]", "");
        if (digitos.length() == DIGITOS_CODIGO_SUS - 1) {
            digitos = "0" + digitos;
        }
        if (digitos.length() != DIGITOS_CODIGO_SUS) {
            throw new IllegalArgumentException(
                    "Código SUS inválido: \"" + limpo + "\". O código SIGTAP tem 10 dígitos.");
        }
        return digitos;
    }

    /**
     * Valor em reais com 2 casas, ou nulo quando nao informado.
     *
     * @throws IllegalArgumentException para valor negativo, com mais de duas
     *         casas decimais ou acima do que a coluna comporta
     */
    public static BigDecimal normalizarValor(BigDecimal bruto) {
        if (bruto == null) {
            return null;
        }
        if (bruto.signum() < 0) {
            throw new IllegalArgumentException("O valor unitário não pode ser negativo.");
        }
        if (bruto.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("O valor unitário aceita no máximo duas casas decimais.");
        }
        if (bruto.compareTo(VALOR_MAXIMO) > 0) {
            throw new IllegalArgumentException("O valor unitário é maior do que o sistema comporta.");
        }
        return bruto.setScale(2);
    }

    /** Compara valores ignorando escala (2.5 e 2.50 sao o mesmo preco) e tratando nulo. */
    public static boolean mesmoValor(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return a.compareTo(b) == 0;
    }
}
