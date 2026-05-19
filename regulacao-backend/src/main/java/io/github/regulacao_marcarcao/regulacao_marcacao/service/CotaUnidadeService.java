package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeSaldoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
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
        TipoPeriodoCota tipo = dto.tipoPeriodo() != null ? dto.tipoPeriodo() : TipoPeriodoCota.MENSAL;

        if (tipo == TipoPeriodoCota.MENSAL) {
            if (dto.periodo() == null || !dto.periodo().matches("\\d{4}-\\d{2}")) {
                throw new IllegalArgumentException("Período inválido. Use o formato YYYY-MM (ex: 2026-05).");
            }
        } else {
            if (dto.dataEspecifica() == null) {
                throw new IllegalArgumentException("Data específica é obrigatória para cota por data.");
            }
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
            if (tipo == TipoPeriodoCota.MENSAL) {
                cotaRepository.findByUnidadeEspecialidadePeriodo(dto.unidadeId(), espId, dto.periodo())
                        .ifPresent(c -> {
                            throw new IllegalArgumentException("Cota mensal já cadastrada para essa unidade/especialidade/período.");
                        });
            } else {
                cotaRepository.findByUnidadeEspecialidadeData(dto.unidadeId(), espId, dto.dataEspecifica())
                        .ifPresent(c -> {
                            throw new IllegalArgumentException("Cota por data já cadastrada para essa unidade/especialidade/data.");
                        });
            }
        } else {
            if (tipo == TipoPeriodoCota.MENSAL) {
                cotaRepository.findCotaGeralByUnidadePeriodo(dto.unidadeId(), dto.periodo())
                        .ifPresent(c -> {
                            throw new IllegalArgumentException("Cota geral mensal já cadastrada para essa unidade/período.");
                        });
            } else {
                cotaRepository.findCotaGeralByUnidadeData(dto.unidadeId(), dto.dataEspecifica())
                        .ifPresent(c -> {
                            throw new IllegalArgumentException("Cota geral por data já cadastrada para essa unidade/data.");
                        });
            }
        }

        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setEspecialidade(especialidade);
        cota.setTipoPeriodo(tipo);
        cota.setPeriodo(tipo == TipoPeriodoCota.MENSAL ? dto.periodo() : null);
        cota.setDataEspecifica(tipo == TipoPeriodoCota.DATA ? dto.dataEspecifica() : null);
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

    /**
     * Valida e incrementa as cotas aplicáveis (MENSAL e/ou DATA) para a unidade/especialidade na data informada.
     * Lança exceção se alguma cota ativa estiver esgotada.
     * Não faz nada se nenhuma cota estiver configurada (cota inexistente = sem restrição).
     */
    @Transactional
    public void incrementarUtilizacao(Long unidadeId, Long especialidadeId, LocalDate dataAtual) {
        String periodo = dataAtual.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        Optional<CotaUnidade> cotaMensal = especialidadeId != null
                ? cotaRepository.findByUnidadeEspecialidadePeriodo(unidadeId, especialidadeId, periodo)
                : cotaRepository.findCotaGeralByUnidadePeriodo(unidadeId, periodo);

        Optional<CotaUnidade> cotaData = especialidadeId != null
                ? cotaRepository.findByUnidadeEspecialidadeData(unidadeId, especialidadeId, dataAtual)
                : cotaRepository.findCotaGeralByUnidadeData(unidadeId, dataAtual);

        verificarEIncrementar(cotaMensal.orElse(null));
        verificarEIncrementar(cotaData.orElse(null));
    }

    private void verificarEIncrementar(CotaUnidade cota) {
        if (cota == null || !cota.isAtivo()) return;
        if (cota.getQuantidadeUtilizada() >= cota.getQuantidadeTotal()) {
            String descricao = cota.getTipoPeriodo() == TipoPeriodoCota.MENSAL
                    ? "período " + cota.getPeriodo()
                    : "data " + cota.getDataEspecifica();
            throw new IllegalStateException(
                    "Cota esgotada para a unidade '" + cota.getUnidade().getNome() + "' no " + descricao + ".");
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
