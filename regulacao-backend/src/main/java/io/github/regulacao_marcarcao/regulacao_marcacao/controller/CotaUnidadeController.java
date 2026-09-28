package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeSaldoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CotaUnidadeService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.UnidadeAcessoService;
import lombok.RequiredArgsConstructor;

/**
 * Cotas por Unidade e por Grupo de Unidades.
 *
 * Definir cota continua sendo prerrogativa do ADMIN global — é ele quem distribui
 * a capacidade entre as unidades. O ADMIN_UNIDADE apenas <b>consulta</b> as cotas,
 * e somente as da sua própria unidade de lotação: os endpoints que recebem um
 * {@code unidadeId} passam pelo {@link UnidadeAcessoService}, então trocar o id na
 * chamada direta à API não dá acesso aos dados de outra unidade.
 */
@RestController
@RequestMapping("/api/cotas")
@RequiredArgsConstructor
public class CotaUnidadeController {

    private final CotaUnidadeService cotaService;
    private final UnidadeAcessoService unidadeAcessoService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CotaUnidadeViewDTO> criar(@RequestBody CotaUnidadeCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cotaService.criar(dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CotaUnidadeViewDTO>> listarTodas() {
        return ResponseEntity.ok(cotaService.listarTodas());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CotaUnidadeViewDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(cotaService.buscarPorId(id));
    }

    @GetMapping("/unidade/{unidadeId}")
    public ResponseEntity<List<CotaUnidadeViewDTO>> listarPorUnidade(
            @PathVariable Long unidadeId,
            Authentication authentication) {
        exigirAcesso(authentication, unidadeId);
        return ResponseEntity.ok(cotaService.listarPorUnidade(unidadeId));
    }

    @GetMapping("/grupo-unidades/{grupoUnidadesId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_UNIDADE', 'GESTOR')")
    public ResponseEntity<List<CotaUnidadeViewDTO>> listarPorGrupo(@PathVariable Long grupoUnidadesId) {
        return ResponseEntity.ok(cotaService.listarPorGrupo(grupoUnidadesId));
    }

    @GetMapping("/unidade/{unidadeId}/periodo/{periodo}")
    public ResponseEntity<List<CotaUnidadeViewDTO>> listarPorUnidadeEPeriodo(
            @PathVariable Long unidadeId,
            @PathVariable String periodo,
            Authentication authentication) {
        exigirAcesso(authentication, unidadeId);
        return ResponseEntity.ok(cotaService.listarPorUnidadeEPeriodo(unidadeId, periodo));
    }

    @GetMapping("/saldo")
    public ResponseEntity<CotaUnidadeSaldoDTO> consultarSaldo(
            @RequestParam Long unidadeId,
            @RequestParam(required = false) Long especialidadeId,
            @RequestParam String periodo,
            Authentication authentication) {
        exigirAcesso(authentication, unidadeId);
        return ResponseEntity.ok(cotaService.consultarSaldo(unidadeId, especialidadeId, periodo));
    }

    @GetMapping("/saldo-data")
    public ResponseEntity<CotaUnidadeSaldoDTO> consultarSaldoPorData(
            @RequestParam Long unidadeId,
            @RequestParam(required = false) Long especialidadeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            Authentication authentication) {
        exigirAcesso(authentication, unidadeId);
        return ResponseEntity.ok(cotaService.consultarSaldoPorData(unidadeId, especialidadeId, data));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CotaUnidadeViewDTO> atualizar(
            @PathVariable Long id,
            @RequestBody CotaUnidadeUpdateDTO dto) {
        return ResponseEntity.ok(cotaService.atualizar(id, dto));
    }

    private void exigirAcesso(Authentication authentication, Long unidadeId) {
        unidadeAcessoService.exigirAcessoA(
                authentication != null ? authentication.getName() : null, unidadeId);
    }
}
