package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade.UnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnidadeService {

    private final UnidadeRepository unidadeRepository;

    @Transactional
    public UnidadeViewDTO criar(UnidadeCreateDTO dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new IllegalArgumentException("O nome da unidade é obrigatório.");
        }
        unidadeRepository.findByNome(dto.nome()).ifPresent(u -> {
            throw new IllegalArgumentException("Já existe uma unidade com esse nome.");
        });
        String cnes = blankToNull(dto.cnes());
        if (cnes != null) {
            unidadeRepository.findByCnes(cnes).ifPresent(u -> {
                throw new IllegalArgumentException("Já existe uma unidade com esse CNES.");
            });
        }
        Unidade unidade = new Unidade();
        unidade.setNome(dto.nome());
        unidade.setCodigo(blankToNull(dto.codigo()));
        unidade.setCnes(cnes);
        unidade.setTelefone(blankToNull(dto.telefone()));
        unidade.setEndereco(blankToNull(dto.endereco()));
        unidade.setAtivo(true);
        return UnidadeViewDTO.from(unidadeRepository.save(unidade));
    }

    @Transactional
    public UnidadeViewDTO atualizar(Long id, UnidadeUpdateDTO dto) {
        Unidade unidade = unidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
        unidade.setNome(dto.nome());
        unidade.setCodigo(blankToNull(dto.codigo()));
        unidade.setCnes(blankToNull(dto.cnes()));
        unidade.setTelefone(blankToNull(dto.telefone()));
        unidade.setEndereco(blankToNull(dto.endereco()));
        return UnidadeViewDTO.from(unidadeRepository.save(unidade));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    @Transactional
    public UnidadeViewDTO toggleAtivo(Long id) {
        Unidade unidade = unidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
        unidade.setAtivo(!unidade.isAtivo());
        return UnidadeViewDTO.from(unidadeRepository.save(unidade));
    }

    @Transactional(readOnly = true)
    public UnidadeViewDTO buscarPorId(Long id) {
        return UnidadeViewDTO.from(unidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada.")));
    }

    @Transactional(readOnly = true)
    public List<UnidadeViewDTO> listarTodas() {
        return unidadeRepository.findAll().stream().map(UnidadeViewDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UnidadeSimpleViewDTO> listarAtivas() {
        return unidadeRepository.findByAtivoTrue().stream().map(UnidadeSimpleViewDTO::from).toList();
    }
}
