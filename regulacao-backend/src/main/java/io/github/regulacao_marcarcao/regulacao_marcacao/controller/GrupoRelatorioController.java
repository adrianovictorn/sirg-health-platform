package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.PaginaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioListaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.GrupoRelatorioService;

@RestController
@RequestMapping("/api/grupo-relatorio")
public class GrupoRelatorioController {
    

    private final GrupoRelatorioService service;

    public GrupoRelatorioController(GrupoRelatorioService service) {
        this.service = service;
    }

    @PostMapping("/cadastrar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrupoRelatorioViewDTO> cadastrarGrupoRelatorio(@RequestBody GrupoRelatorioCreateDTO dto){
        return ResponseEntity.ok(service.criarGrupo(dto));
    }

    /** Listagem paginada de /cadastrar/grupo-relatorio. {@code /listar} segue devolvendo a lista completa. */
    @GetMapping("/pagina")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaginaDTO<GrupoRelatorioListaDTO>> listarPagina(
            GrupoRelatorioFiltroDTO filtro,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listarPagina(filtro, page, size));
    }

    @GetMapping("/listar")
    public ResponseEntity<List<GrupoRelatorioViewDTO>> listarGrupoRelatorio(){
        return ResponseEntity.ok(service.listarGrupos());
    }

    

    @PatchMapping("/atualizar/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrupoRelatorioViewDTO> atualizarGrupoRelatorio(@PathVariable Long id, @RequestBody GrupoRelatorioUpdateDTO dto){
        return ResponseEntity.ok(service.atualizarGrupo(id, dto));
    }

    @DeleteMapping("/deletar/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletarGrupoRelatorio(@PathVariable Long id){
        service.deletarGrupoRelatorio(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/ativarOrDesativar/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> ativarOrDesativar(@PathVariable Long id){
        service.ativarOrInativarGrupo(id);
        return ResponseEntity.noContent().build();
    }
}
