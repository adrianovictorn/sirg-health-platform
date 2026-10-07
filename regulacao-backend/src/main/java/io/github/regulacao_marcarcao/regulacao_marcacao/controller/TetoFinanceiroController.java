package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoFinanceiroCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoFinanceiroUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoFinanceiroViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.TetoFinanceiroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Teto financeiro por unidade, grupo de especialidades e mes.
 *
 * <p>Fica fora de {@code /api/cotas} de proposito: aqueles endpoints sao lidos
 * por ADMIN_UNIDADE, e aqui tudo e valor em reais. Leitura para ADMIN e
 * GESTOR; liberar e editar teto, so ADMIN.
 */
@RestController
@RequestMapping("/api/custos/tetos")
@RequiredArgsConstructor
public class TetoFinanceiroController {

    private final TetoFinanceiroService tetoFinanceiroService;

    /** Tetos de um mes (YYYY-MM); sem o parametro, o mes corrente. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<List<TetoFinanceiroViewDTO>> listar(@RequestParam(required = false) String periodo) {
        return ResponseEntity.ok(tetoFinanceiroService.listar(periodo));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TetoFinanceiroViewDTO>> criar(
            @Valid @RequestBody TetoFinanceiroCreateDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tetoFinanceiroService.criar(
                dto, authentication != null ? authentication.getName() : null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TetoFinanceiroViewDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody TetoFinanceiroUpdateDTO dto) {
        return ResponseEntity.ok(tetoFinanceiroService.atualizar(id, dto));
    }
}
