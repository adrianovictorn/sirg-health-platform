package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalQuantitativoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalSolicitacaoDetalheProjection;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RelatorioSolicitacaoProfissionalService {

    private final SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;

    public Page<ProfissionalSolicitacaoDetalheProjection> listarDetalhado(
            LocalDate inicio, LocalDate fim, Long profissionalId, Long unidadeId, Pageable pageable) {
        return solicitacaoEspecialidadeRepository.listarDetalhadoPorProfissional(
                inicio, fim, profissionalId, unidadeId, pageable);
    }

    public List<ProfissionalSolicitacaoDetalheProjection> listarDetalhadoCompleto(
            LocalDate inicio, LocalDate fim, Long profissionalId, Long unidadeId) {
        return solicitacaoEspecialidadeRepository.listarDetalhadoPorProfissionalCompleto(
                inicio, fim, profissionalId, unidadeId);
    }

    public List<ProfissionalQuantitativoProjection> listarQuantitativo(
            LocalDate inicio, LocalDate fim, Long unidadeId) {
        return solicitacaoEspecialidadeRepository.listarQuantitativoPorProfissional(inicio, fim, unidadeId);
    }
}
