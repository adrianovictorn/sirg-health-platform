package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeSaldoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CotaUnidadeService {

    private final CotaUnidadeRepository cotaRepository;
    private final UnidadeRepository unidadeRepository;
    private final EspecialidadeRepository especialidadeRepository;

    @Transactional
    public CotaUnidadeViewDTO criar(CotaUnidadeCreateDTO dto) {
        if (dto.periodo() == null || !dto.periodo().matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Período inválido. Use o formato YYYY-MM (ex: 2026-05).");
        }
        if (dto.quantidadeTotal() == null || dto.quantidadeTotal() < 0) {
            throw new IllegalArgumentException("A quantidade total deve ser maior ou igual a zero.");
        }

        Unidade unidade = unidadeRepository.findById(dto.unidadeId())
                .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada."));

        Especialidade especialidade = null;
        if (dto.especialidadeId() != null) {
            especialidade = especialidadeRepository.findById(dto.especialidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Especialidade não encontrada."));
            final Long espId = especialidade.getId();
            cotaRepository.findByUnidadeEspecialidadePeriodo(dto.unidadeId(), espId, dto.periodo())
                    .ifPresent(c -> {
                        throw new IllegalArgumentException("Cota já cadastrada para essa unidade/especialidade/período.");
                    });
        } else {
            cotaRepository.findCotaGeralByUnidadePeriodo(dto.unidadeId(), dto.periodo())
                    .ifPresent(c -> {
                        throw new IllegalArgumentException("Cota geral já cadastrada para essa unidade/período.");
                    });
        }

        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setEspecialidade(especialidade);
        cota.setPeriodo(dto.periodo());
        cota.setQuantidadeTotal(dto.quantidadeTotal());
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        return CotaUnidadeViewDTO.from(cotaRepository.save(cota));
    }

    @Transactional
    public CotaUnidadeViewDTO atualizar(Long id, CotaUnidadeUpdateDTO dto) {
        CotaUnidade cota = cotaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cota não encontrada."));
        if (dto.quantidadeTotal() < 0) {
            throw new IllegalArgumentException("A quantidade total deve ser maior ou igual a zero.");
        }
        cota.setQuantidadeTotal(dto.quantidadeTotal());
        cota.setAtivo(dto.ativo());
        return CotaUnidadeViewDTO.from(cotaRepository.save(cota));
    }

    @Transactional
    public void incrementarUtilizacao(Long unidadeId, Long especialidadeId, String periodo) {
        CotaUnidade cota;
        if (especialidadeId != null) {
            cota = cotaRepository.findByUnidadeEspecialidadePeriodo(unidadeId, especialidadeId, periodo)
                    .orElse(null);
        } else {
            cota = cotaRepository.findCotaGeralByUnidadePeriodo(unidadeId, periodo).orElse(null);
        }
        if (cota == null || !cota.isAtivo()) return;
        if (cota.getQuantidadeUtilizada() >= cota.getQuantidadeTotal()) {
            throw new IllegalStateException("Cota esgotada para essa unidade/especialidade/período.");
        }
        cota.setQuantidadeUtilizada(cota.getQuantidadeUtilizada() + 1);
        cotaRepository.save(cota);
    }

    @Transactional(readOnly = true)
    public List<CotaUnidadeViewDTO> listarPorUnidade(Long unidadeId) {
        return cotaRepository.findByUnidadeId(unidadeId).stream()
                .map(CotaUnidadeViewDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CotaUnidadeViewDTO> listarPorUnidadeEPeriodo(Long unidadeId, String periodo) {
        return cotaRepository.findByUnidadeIdAndPeriodo(unidadeId, periodo).stream()
                .map(CotaUnidadeViewDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CotaUnidadeViewDTO> listarTodas() {
        return cotaRepository.findAll().stream().map(CotaUnidadeViewDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public CotaUnidadeSaldoDTO consultarSaldo(Long unidadeId, Long especialidadeId, String periodo) {
        CotaUnidade cota;
        if (especialidadeId != null) {
            cota = cotaRepository.findByUnidadeEspecialidadePeriodo(unidadeId, especialidadeId, periodo)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Cota não encontrada para essa unidade/especialidade/período."));
        } else {
            cota = cotaRepository.findCotaGeralByUnidadePeriodo(unidadeId, periodo)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Cota geral não encontrada para essa unidade/período."));
        }
        int saldo = cota.getQuantidadeTotal() - cota.getQuantidadeUtilizada();
        return new CotaUnidadeSaldoDTO(
                cota.getUnidade().getId(),
                cota.getUnidade().getNome(),
                cota.getEspecialidade() != null ? cota.getEspecialidade().getId() : null,
                cota.getEspecialidade() != null ? cota.getEspecialidade().getNome() : null,
                cota.getPeriodo(),
                cota.getQuantidadeTotal(),
                cota.getQuantidadeUtilizada(),
                saldo,
                saldo > 0 && cota.isAtivo());
    }
}
