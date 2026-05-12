# Diagrama — Módulo de Solicitações

Cobre o fluxo central do sistema: desde o cadastro de um paciente até o agendamento de suas especialidades.

```mermaid
classDiagram

    class Solicitacao {
        <<Entity — tabela: solicitacao>>
        +Long id
        +UsfEnum usfOrigem
        +String nomePaciente
        +String cpfPaciente  [UNIQUE]
        +String cns
        +String telefone
        +LocalDate dataNascimento
        +String observacoes
        +LocalDate dataMalote
        +UUID origemMunicipioId
        +String origemMunicipioNome
    }

    class SolicitacaoEspecialidade {
        <<Entity — tabela: solicitacao_especialidade>>
        +Long id
        +StatusDaMarcacao status
        +PrioridadeDaMarcacaoEnum prioridade
        +String especialidadeCodigoLegacy
        +LocalDateTime dataDeCadastro
    }

    class AgendamentoSolicitacao {
        <<Entity — tabela: agendamento_solicitacao>>
        +Long id
        +LocalDeAgendamentoEnum localAgendado
        +LocalDate dataAgendada
        +TurnoEnum turno
        +String observacoes
        +LocalDateTime dataCriacao
    }

    class Especialidade {
        <<Entity — tabela: especialidade>>
        +Long id
        +String codigo  [UNIQUE]
        +String nome    [UNIQUE]
        +ItemCategoria categoria
        +Boolean ativo
        +Integer vagas
    }

    class GrupoRelatorio {
        <<Entity — tabela: grupo_relatorio>>
        +Long id
        +String codigo
        +String nome
        +Boolean ativo
        +boolean direcionadoHospital
    }

    class CID {
        <<Entity — tabela: cid>>
        +Long idCid
        +String codigo
        +String descricao
    }

    class LocalAgendamento {
        <<Entity — tabela: local_agendamento>>
        +Long id
        +String nomeLocal
        +String endereco
        +String numero
        +String enumValue  [UNIQUE]
    }

    class StatusDaMarcacao {
        <<enum>>
        AGUARDANDO
        AGENDADO
        FALTOU
        CANCELADO
        REALIZADO
        RETORNO
        RETORNO_POLICLINICA
        GEL
    }

    class PrioridadeDaMarcacaoEnum {
        <<enum>>
        NORMAL
        URGENTE
    }

    class ItemCategoria {
        <<enum>>
        CONSULTA
        EXAME
        PROCEDIMENTO
    }

    class TurnoEnum {
        <<enum>>
        MANHA
        TARDE
        NAO_INFORMADO
    }

    class UsfEnum {
        <<enum>>
        USF1 .. USFn
    }

    %% Relacionamentos

    Solicitacao "1" --> "0..*" SolicitacaoEspecialidade : especialidades\n(OneToMany, CascadeType.ALL)
    Solicitacao "1" --> "0..*" AgendamentoSolicitacao   : agendamentos\n(OneToMany)
    Solicitacao "0..*" --> "0..*" CID                   : cids\n(ManyToMany — tabela: solicitacao_cid)

    SolicitacaoEspecialidade "0..*" --> "1" Solicitacao           : solicitacao\n(ManyToOne)
    SolicitacaoEspecialidade "0..*" --> "0..1" AgendamentoSolicitacao : agendamentoSolicitacao\n(ManyToOne)
    SolicitacaoEspecialidade "0..*" --> "0..1" Especialidade           : especialidadeSolicitada\n(ManyToOne)

    AgendamentoSolicitacao "0..*" --> "1" Solicitacao     : solicitacao\n(ManyToOne)
    AgendamentoSolicitacao "0..*" --> "0..1" LocalAgendamento : localAgendamento\n(ManyToOne, LAZY)
    AgendamentoSolicitacao "1"    --> "0..*" SolicitacaoEspecialidade : especialidades\n(OneToMany, LAZY)

    Especialidade "0..*" --> "0..1" GrupoRelatorio : grupoRelatorio\n(ManyToOne, LAZY)
    GrupoRelatorio "1"   --> "0..*" Especialidade  : especialidades\n(OneToMany, CascadeType.ALL)

    SolicitacaoEspecialidade ..> StatusDaMarcacao
    SolicitacaoEspecialidade ..> PrioridadeDaMarcacaoEnum
    Especialidade            ..> ItemCategoria
    AgendamentoSolicitacao   ..> TurnoEnum
    Solicitacao              ..> UsfEnum
```

## Regras de negócio desta camada

| Regra | Onde é implementada |
|---|---|
| CPF do paciente é único por solicitação | `@Column(unique = true)` em `Solicitacao.cpfPaciente` |
| Ao criar uma solicitação, todas as especialidades nascem com `status = AGUARDANDO` | `SolicitacaoService.criar()` |
| Uma especialidade só recebe `status = AGENDADO` ao ser vinculada a um `AgendamentoSolicitacao` | `AgendamentoService` |
| `origemMunicipioId != null` indica que a solicitação veio de um município federado | Lógica de federação em `PactoService` |
| `especialidadeCodigoLegacy` existe apenas para compatibilidade com dados migrados do enum antigo | Não deve ser usado em novos registros |
| `vagas` em Especialidade controla o limite mensal de agendamentos | Verificado no serviço de agendamento |

## Tabelas de junção implícitas

| Tabela | Entidades | Colunas |
|---|---|---|
| `solicitacao_cid` | Solicitacao ↔ CID | `solicitacao_id`, `cid_id` |
