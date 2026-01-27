package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.FechamentoIndicadoresDiaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.FechamentoIndicadoresDia;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.FechamentoIndicadoresDiaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.GraficoGrupoPorDataProjection;

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

    //Consultar total de Paciente Agendado do Dia
    public long totalDePacientesAgendadosDoDia(LocalDate data){
        return fechamentoIndicadoresDiaRepository.totalDeAgendadosDoDia(data);
    }

    //Consultar total de Pacientes novos Cadastro do Dia
    public long totalDePacientesNovosCadastradosDoDia(LocalDate data){
        return fechamentoIndicadoresDiaRepository.totalDeSolicitacoesCadastradasDoDia(data);
    }

    //Consultar total de Solicitações de Especialidade Cadastrados do Dia
    public long totalSolicitacaoEspecialidadeDoDia(LocalDate data){
        return fechamentoIndicadoresDiaRepository.totalDeSolicitacoesEspecialidadesDoDia(data);
    }

    public Page<GraficoGrupoPorDataProjection> totalDeAgendamentoPorGrupoEPeriodo(int page, int size, LocalDate inicio, LocalDate intervalo){
        Pageable pagina = PageRequest.of(page, size);
        return fechamentoIndicadoresDiaRepository.totalDeAgendamentoPorGrupoEPeriodo(inicio, intervalo,  pagina);
    }

    private boolean passouDoLimite(LocalDate dataReferencia){
        ZoneId zone = ZoneId.of("America/Bahia");
        var agora = ZonedDateTime.now(zone);
        var limite = dataReferencia.plusDays(1).atStartOfDay(zone);
        return !agora.isBefore(limite);
    }
    
}
