package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;


import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;

public interface AgendamentoSolicitacaoRepository extends JpaRepository<AgendamentoSolicitacao, Long> {
    

        List<AgendamentoSolicitacao> findBySolicitacaoId(Long solicitacaoId);

        List<AgendamentoSolicitacao> findByDataAgendada(LocalDate data);
 

}
