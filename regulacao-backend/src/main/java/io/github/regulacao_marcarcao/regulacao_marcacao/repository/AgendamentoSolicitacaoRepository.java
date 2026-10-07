package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;

public interface AgendamentoSolicitacaoRepository extends JpaRepository<AgendamentoSolicitacao, Long> {
    

        List<AgendamentoSolicitacao> findBySolicitacaoId(Long solicitacaoId);

        List<AgendamentoSolicitacao> findByDataAgendada(LocalDate data);
 
        List<AgendamentoSolicitacao> findByDataCriacaoBetween(LocalDateTime inicio, LocalDateTime fim);

        /** Agendamentos da data que ainda tem ao menos um item no status dado (lembrete do WhatsApp). */
        @Query("SELECT a FROM AgendamentoSolicitacao a WHERE a.dataAgendada = :data AND EXISTS ("
                + "SELECT 1 FROM SolicitacaoEspecialidade se "
                + "WHERE se.agendamentoSolicitacao = a AND se.status = :status)")
        List<AgendamentoSolicitacao> findComItemNoStatusPorData(@Param("data") LocalDate data,
                                                                @Param("status") StatusDaMarcacao status);

}
