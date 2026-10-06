package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila.FilaEsperaFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila.FilaEsperaPacienteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.FilaEsperaService;
import lombok.RequiredArgsConstructor;

/**
 * Fila de espera de pacientes (somente leitura).
 *
 * <p>Fica fora de {@code /api/solicitacoes/buscar/**} de proposito: aquele
 * prefixo ja foi publico, e esta lista traz dados nominais de pacientes. O
 * escopo por unidade e decidido no service, nunca pelo parametro da requisicao.
 */
@RestController
@RequestMapping("/api/fila-espera")
@RequiredArgsConstructor
public class FilaEsperaController {

    private final FilaEsperaService filaEsperaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_UNIDADE', 'GESTOR', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO')")
    public ResponseEntity<Page<FilaEsperaPacienteViewDTO>> listar(
            @RequestParam(required = false) Long especialidadeId,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false, name = "prioridade") List<String> prioridades,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Integer esperaMinimaDias,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte,
            @RequestParam(required = false) String ordem,
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        FilaEsperaFiltroDTO filtro = new FilaEsperaFiltroDTO(
                especialidadeId, categoria, status, prioridades, unidadeId, esperaMinimaDias, dataDe, dataAte, ordem,
                termo);
        return ResponseEntity.ok(filaEsperaService.listar(
                filtro, page, size, authentication != null ? authentication.getName() : null));
    }
}
