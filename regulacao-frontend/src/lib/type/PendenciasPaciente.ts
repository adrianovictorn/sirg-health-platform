export type PacientePendentes = {
    id: number | null,
    cns: string,
    cpfPaciente: string,
    especialidades: string,
    dataNascimento: [number, number, number],
    nomePaciente: String,
}

export type UrgenciaEmergenciaPaciente = {
    id: number | null,
    cns: string,
    cpfPaciente: string,
    nomePaciente: string,
    usfOrigem: string,
    dataNascimento: [number, number,number],
    itens: string
}

