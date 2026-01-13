package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioCreateDTO;
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
    public ResponseEntity<GrupoRelatorioViewDTO> cadastrarGrupoRelatorio(@RequestBody GrupoRelatorioCreateDTO dto){
        return ResponseEntity.ok(service.criarGrupo(dto));
    }

    @GetMapping("/listar")
    public ResponseEntity<List<GrupoRelatorioViewDTO>> listarGrupoRelatorio(){
        return ResponseEntity.ok(service.listarGrupos());
    }

    

    @PatchMapping("/atualizar/{id}")
    public ResponseEntity<GrupoRelatorioViewDTO> atualizarGrupoRelatorio(@PathVariable Long id, @RequestBody GrupoRelatorioUpdateDTO dto){
        return ResponseEntity.ok(service.atualizarGrupo(id, dto));
    }

    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<Void> deletarGrupoRelatorio(@PathVariable Long id){
        service.deletarGrupoRelatorio(id);
        return ResponseEntity.noContent().build();
    }
}
