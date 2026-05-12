package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional.ProfissionalViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfissionalService {

    private final ProfissionalRepository profissionalRepository;
    private final UnidadeRepository unidadeRepository;

    @Transactional
    public ProfissionalViewDTO criar(ProfissionalCreateDTO dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new IllegalArgumentException("O nome do profissional é obrigatório.");
        }
        Profissional p = new Profissional();
        p.setNome(dto.nome());
        p.setConselho(dto.conselho());
        p.setNumeroRegistro(dto.numeroRegistro());
        p.setEspecialidadeAtuacao(dto.especialidadeAtuacao());
        p.setTelefone(dto.telefone());
        p.setAtivo(true);
        if (dto.unidadeId() != null) {
            Unidade unidade = unidadeRepository.findById(dto.unidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
            p.setUnidade(unidade);
        }
        return ProfissionalViewDTO.from(profissionalRepository.save(p));
    }

    @Transactional
    public ProfissionalViewDTO atualizar(Long id, ProfissionalUpdateDTO dto) {
        Profissional p = profissionalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado."));
        p.setNome(dto.nome());
        p.setConselho(dto.conselho());
        p.setNumeroRegistro(dto.numeroRegistro());
        p.setEspecialidadeAtuacao(dto.especialidadeAtuacao());
        p.setTelefone(dto.telefone());
        if (dto.unidadeId() != null) {
            Unidade unidade = unidadeRepository.findById(dto.unidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));
            p.setUnidade(unidade);
        } else {
            p.setUnidade(null);
        }
        return ProfissionalViewDTO.from(profissionalRepository.save(p));
    }

    @Transactional
    public ProfissionalViewDTO toggleAtivo(Long id) {
        Profissional p = profissionalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado."));
        p.setAtivo(!p.isAtivo());
        return ProfissionalViewDTO.from(profissionalRepository.save(p));
    }

    @Transactional(readOnly = true)
    public ProfissionalViewDTO buscarPorId(Long id) {
        return ProfissionalViewDTO.from(profissionalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado.")));
    }

    @Transactional(readOnly = true)
    public Page<ProfissionalViewDTO> buscar(int page, int size, String nome) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nome").ascending());
        if (nome == null || nome.isBlank()) {
            return profissionalRepository.findAll(pageable).map(ProfissionalViewDTO::from);
        }
        return profissionalRepository.findByNomeContainingIgnoreCase(nome, pageable)
                .map(ProfissionalViewDTO::from);
    }

    @Transactional(readOnly = true)
    public List<ProfissionalSimpleViewDTO> listarAtivosPorUnidade(Long unidadeId) {
        return profissionalRepository.findByUnidadeIdAndAtivoTrue(unidadeId).stream()
                .map(ProfissionalSimpleViewDTO::from).toList();
    }

    @Transactional
    public void deletar(Long id) {
        profissionalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado."));
        profissionalRepository.deleteById(id);
    }
}
