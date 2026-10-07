package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.io.IOException;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoConfirmarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoPreviaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoImportacaoResultadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.EspecialidadeCustoUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.EspecialidadeCustoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CustoEspecialidadeService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CustoImportacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Preco e codigo SUS das especialidades.
 *
 * <p>Fica fora de {@code /api/catalog/especialidades} de proposito: aquele
 * prefixo e lido por qualquer usuario autenticado (combos), e custo e restrito.
 * Leitura para ADMIN e GESTOR; alterar preco e importar planilha, so ADMIN.
 */
@RestController
@RequestMapping("/api/custos")
@RequiredArgsConstructor
public class CustoEspecialidadeController {

    private final CustoEspecialidadeService custoEspecialidadeService;
    private final CustoImportacaoService custoImportacaoService;

    @GetMapping("/especialidades")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<Page<EspecialidadeCustoViewDTO>> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Long grupoRelatorioId,
            @RequestParam(defaultValue = "false") boolean somenteSemPreco,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                custoEspecialidadeService.listar(nome, grupoRelatorioId, somenteSemPreco, page, size));
    }

    @PutMapping("/especialidades/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EspecialidadeCustoViewDTO> atualizar(
            @PathVariable Long id,
            @RequestBody EspecialidadeCustoUpdateDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(custoEspecialidadeService.atualizar(
                id, dto, authentication != null ? authentication.getName() : null));
    }

    // ------------------------------------------------------------------
    // Importacao de planilha de precos
    // ------------------------------------------------------------------

    /** Le a planilha e devolve o confronto com o cadastro, SEM gravar nada. */
    @PostMapping(path = "/importacao", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustoImportacaoPreviaDTO> previaImportacao(
            @RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Envie a planilha de preços em .xlsx ou .csv.");
        }
        try {
            return ResponseEntity.ok(
                    custoImportacaoService.previa(arquivo.getBytes(), arquivo.getOriginalFilename()));
        } catch (IOException e) {
            // Falha de leitura do upload, nao do conteudo: e para tentar de novo.
            throw new IllegalStateException("Não foi possível ler o arquivo enviado. Tente novamente.");
        }
    }

    /** Grava os itens marcados na conferencia. Idempotente: reimportar nao altera nada. */
    @PostMapping("/importacao/confirmar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustoImportacaoResultadoDTO> confirmarImportacao(
            @Valid @RequestBody CustoImportacaoConfirmarDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(custoImportacaoService.confirmar(
                dto, authentication != null ? authentication.getName() : null));
    }
}
