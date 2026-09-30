export interface PacienteProjection{
    id: number
    nomePaciente: string
    cpfPaciente: string
    cns: string
    usfOrigem: string
    dataNascimento: string
    especialidade: string
    prioridade: string
    solicitacaoEspecialidadeId: number
    profissionalExecutanteNome: string | null
    horaAgendada: string | null
}


