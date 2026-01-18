package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.FechamentoIndicadoresDiaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.FechamentoIndicadoresDia;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.FechamentoIndicadoresDiaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;

@Service
public class FechamentoIndicadoresDiaService {
    
    private final FechamentoIndicadoresDiaRepository fechamentoIndicadoresDiaRepository;
    private final SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    private final LocalAgendamentoRepository localAgendamentoRepository;
    private final GrupoRelatorioRepository grupoRelatorioRepository;

    public FechamentoIndicadoresDiaService(FechamentoIndicadoresDiaRepository fechamentoIndicadoresDiaRepository,
            SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository,
            LocalAgendamentoRepository localAgendamentoRepository,
            GrupoRelatorioRepository grupoRelatorioRepository) {

        this.fechamentoIndicadoresDiaRepository = fechamentoIndicadoresDiaRepository;
        this.solicitacaoEspecialidadeRepository = solicitacaoEspecialidadeRepository;
        this.localAgendamentoRepository = localAgendamentoRepository;
        this.grupoRelatorioRepository = grupoRelatorioRepository;
    }

    @Transactional
    public FechamentoIndicadoresDiaDTO obterIndicadores(LocalDate data, Long localId, Long grupoId){

        var existente = fechamentoIndicadoresDiaRepository.findByDataReferenciaAndLocalAgendamentoIdAndGrupoRelatorioId(data, localId, grupoId);

        if (existente.isPresent()) {
            return FechamentoIndicadoresDiaDTO.fromDTO(existente.get());
        }

        if (!passouDoLimite(data)) {
            return montarPrevia(data, localId, grupoId);
        }

        var f = new FechamentoIndicadoresDia();
         f.setDataReferencia(data);
        f.setLocalAgendamento(localAgendamentoRepository.getReferenceById(localId));
        f.setGrupoRelatorio(grupoRelatorioRepository.getReferenceById(grupoId));
        

        f.setAgendadosTotal((int) solicitacaoEspecialidadeRepository.countAgendamentosPorGrupoEData(grupoId, localId, data, "AGENDADO"));
        f.setAtendidosTotal((int) solicitacaoEspecialidadeRepository.countAgendamentosPorGrupoEData(grupoId, localId, data, "REALIZADO"));
        f.setCanceladosTotal((int) solicitacaoEspecialidadeRepository.countAgendamentosPorGrupoEData(grupoId, localId, data, "CANCELADO"));
        f.setFaltososTotal((int) solicitacaoEspecialidadeRepository.countAgendamentosPorGrupoEData(grupoId, localId, data, "FALTOU")); //Não vou adicionar agora !
        f.setSolicitacoesCadastradasTotal(0);

        f.setFechadoEm(LocalDateTime.now(ZoneId.of("America/Bahia")));

        FechamentoIndicadoresDia salvo = fechamentoIndicadoresDiaRepository.save(f);
        return FechamentoIndicadoresDiaDTO.fromDTO(salvo);
    }

    
    public FechamentoIndicadoresDiaDTO montarPrevia(LocalDate data, Long localId, Long grupoId){
        var f = new FechamentoIndicadoresDia();
        f.setDataReferencia(data);
        f.setLocalAgendamento(localAgendamentoRepository.getReferenceById(localId));
        f.setGrupoRelatorio(grupoRelatorioRepository.getReferenceById(grupoId));
        

        f.setAgendadosTotal((int) solicitacaoEspecialidadeRepository.countAgendamentosPorGrupoEData(grupoId, localId, data, "AGENDADO"));
        f.setAtendidosTotal((int) solicitacaoEspecialidadeRepository.countAgendamentosPorGrupoEData(grupoId, localId, data, "REALIZADO"));
        f.setCanceladosTotal((int) solicitacaoEspecialidadeRepository.countAgendamentosPorGrupoEData(grupoId, localId, data, "CANCELADO"));
        f.setFaltososTotal((int) solicitacaoEspecialidadeRepository.countAgendamentosPorGrupoEData(grupoId, localId, data, "FALTOU"));
        f.setSolicitacoesCadastradasTotal(0);

        f.setFechadoEm(LocalDateTime.MIN);

        return FechamentoIndicadoresDiaDTO.fromDTO(f);
    }



    private boolean passouDoLimite(LocalDate dataReferencia){
        ZoneId zone = ZoneId.of("America/Bahia");
        var agora = ZonedDateTime.now(zone);
        var limite = dataReferencia.plusDays(1).atStartOfDay(zone);
        return !agora.isBefore(limite);
    }
    
}
