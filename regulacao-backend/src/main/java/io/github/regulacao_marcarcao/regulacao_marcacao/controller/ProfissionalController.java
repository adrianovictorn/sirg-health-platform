package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesImportacaoConfirmarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesImportacaoPreviaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesImportacaoResultadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalVinculoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalVinculoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.ProfissionalImportacaoService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.ProfissionalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/profissionais")
@RequiredArgsConstructor
public class ProfissionalController {

    private final ProfissionalService profissionalService;
    private final ProfissionalImportacaoService importacaoService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_UNIDADE', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO')")
    public ResponseEntity<ProfissionalViewDTO> criar(@RequestBody ProfissionalCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profissionalService.criar(dto));
    }

    @GetMapping("/buscar")
    public ResponseEntity<Page<ProfissionalViewDTO>> buscar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nome) {
        return ResponseEntity.ok(profissionalService.buscar(page, size, nome));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfissionalViewDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(profissionalService.buscarPorId(id));
    }

    @GetMapping("/unidade/{unidadeId}/ativos")
    public ResponseEntity<List<ProfissionalSimpleViewDTO>> listarAtivosPorUnidade(@PathVariable Long unidadeId) {
        return ResponseEntity.ok(profissionalService.listarAtivosPorUnidade(unidadeId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_UNIDADE', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO')")
    public ResponseEntity<ProfissionalViewDTO> atualizar(
            @PathVariable Long id,
            @RequestBody ProfissionalUpdateDTO dto) {
        return ResponseEntity.ok(profissionalService.atualizar(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_UNIDADE', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO')")
    public ResponseEntity<ProfissionalViewDTO> toggleAtivo(@PathVariable Long id) {
        return ResponseEntity.ok(profissionalService.toggleAtivo(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        profissionalService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Vinculos com estabelecimento executante (V88)
    // ------------------------------------------------------------------

    @GetMapping("/{id}/vinculos")
    public ResponseEntity<List<ProfissionalVinculoViewDTO>> listarVinculos(@PathVariable Long id) {
        return ResponseEntity.ok(profissionalService.listarVinculos(id));
    }

    /**
     * Quem atende num estabelecimento executante.
     *
     * <p>Alimenta o combo de profissionais da abertura de agenda. Difere de
     * {@code /unidade/{id}/ativos}, que ainda le o campo unico
     * {@code profissional.unidade} (deprecado desde a V88).
     */
    @GetMapping("/executante/{unidadeId}/ativos")
    public ResponseEntity<List<ProfissionalSimpleViewDTO>> listarAtivosPorExecutante(
            @PathVariable Long unidadeId) {
        return ResponseEntity.ok(profissionalService.listarAtivosPorExecutante(unidadeId));
    }

    @PostMapping("/{id}/vinculos")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<ProfissionalVinculoViewDTO> criarVinculo(
            @PathVariable Long id,
            @Valid @RequestBody ProfissionalVinculoCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profissionalService.criarVinculo(id, dto));
    }

    @PatchMapping("/vinculos/{vinculoId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<ProfissionalVinculoViewDTO> toggleVinculo(@PathVariable Long vinculoId) {
        return ResponseEntity.ok(profissionalService.toggleVinculo(vinculoId));
    }

    @DeleteMapping("/vinculos/{vinculoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<Void> deletarVinculo(@PathVariable Long vinculoId) {
        profissionalService.deletarVinculo(vinculoId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Importacao do CSV do CNES (fase 2)
    // ------------------------------------------------------------------

    /**
     * Le o arquivo e devolve o que encontrou, SEM gravar nada.
     *
     * <p>Duas etapas de proposito: o arquivo do DATASUS traz o estabelecimento
     * inteiro, e quase nunca e o estabelecimento inteiro que a coordenacao quer
     * cadastrar. A tela mostra a lista com a situacao de cada linha, o operador
     * marca, e so entao chama {@code /confirmar}.
     */
    @PostMapping(path = "/importar-cnes", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<CnesImportacaoPreviaDTO> previaImportacao(
            @RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Envie o arquivo CSV exportado do portal do CNES.");
        }
        try {
            return ResponseEntity.ok(
                    importacaoService.previa(arquivo.getBytes(), arquivo.getOriginalFilename()));
        } catch (IOException e) {
            // Falha de leitura do upload, nao do conteudo: o operador precisa saber
            // que e para tentar de novo, e nao que o arquivo esta errado.
            throw new IllegalStateException("Não foi possível ler o arquivo enviado. Tente novamente.");
        }
    }

    /** Grava as linhas marcadas na previa. Idempotente: reimportar nao duplica. */
    @PostMapping("/importar-cnes/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<CnesImportacaoResultadoDTO> confirmarImportacao(
            @Valid @RequestBody CnesImportacaoConfirmarDTO dto) {
        return ResponseEntity.ok(importacaoService.confirmar(dto));
    }
}
