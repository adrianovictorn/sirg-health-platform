# Diagrama — Módulo de Transporte Sanitário

Cobre o agendamento de veículos para levar pacientes a consultas e exames.

```mermaid
classDiagram

    class AgendamentoTransporte {
        <<Entity — tabela: agendamento_transporte>>
        +Long id
        +LocalDate data
        +LocalTime horaSaida
        +StatusAgendamento status
        +Long version  [otimistic lock]
    }

    class AgendamentoTransportePaciente {
        <<Entity — tabela: agendamento_transporte_paciente>>
        +Long id
        +TurnoEnum turno
        +Boolean retornaMesmoDia
    }

    class Transporte {
        <<Entity — tabela: transporte>>
        +Long id
        +String nomeVeiculo
        +Long vagas
        +TipoVeiculoEnum tipoVeiculo
        +String modelo
    }

    class Motorista {
        <<Entity — tabela: motorista>>
        +Long id
        +String nome
        +String telefone
        +String observacoes
    }

    class Cidade {
        <<Entity — tabela: cidade>>
        +Long id
        +String codigoIBGE
        +String nomeCidade
        +String cep
    }

    class LocalAgendamento {
        <<Entity — tabela: local_agendamento>>
        +Long id
        +String nomeLocal
        +String endereco
        +String numero
        +String enumValue  [UNIQUE]
    }

    class Solicitacao {
        <<Entity — tabela: solicitacao>>
        +Long id
        +String nomePaciente
        +String cpfPaciente
        +String cns
    }

    class StatusAgendamento {
        <<enum>>
        AGENDADO
        CANCELADO
        PENDENTE
        CONFIRMADO
        REALIZADO
        GEL
    }

    class TipoVeiculoEnum {
        <<enum>>
        ONIBUS
        VAN
        AMBULANCIA
        CARRO
    }

    class TurnoEnum {
        <<enum>>
        MANHA
        TARDE
        NAO_INFORMADO
    }

    %% Relacionamentos

    AgendamentoTransporte "1"    --> "0..*" AgendamentoTransportePaciente : pacientes\n(OneToMany, CascadeType.ALL, orphanRemoval)
    AgendamentoTransporte "0..*" --> "1"    Transporte                    : transporte\n(ManyToOne, NOT NULL)
    AgendamentoTransporte "0..*" --> "1"    Cidade                        : cidade\n(ManyToOne, NOT NULL)
    AgendamentoTransporte "0..*" --> "0..1" Motorista                     : motorista\n(ManyToOne)
    AgendamentoTransporte "0..*" --> "0..*" LocalAgendamento              : locaisAgendamento\n(ManyToMany — tabela: agendamento_transporte_local_agendamento)

    AgendamentoTransportePaciente "0..*" --> "1"    AgendamentoTransporte : agendamento\n(ManyToOne, NOT NULL)
    AgendamentoTransportePaciente "0..*" --> "1"    Solicitacao           : solicitacao\n(ManyToOne, NOT NULL)
    AgendamentoTransportePaciente "0..*" --> "0..1" LocalAgendamento      : localAgendamento\n(ManyToOne)

    Cidade "1" --> "0..*" LocalAgendamento : localAgendamentos\n(OneToMany, CascadeType.ALL, orphanRemoval)

    AgendamentoTransporte         ..> StatusAgendamento
    AgendamentoTransportePaciente ..> TurnoEnum
    Transporte                    ..> TipoVeiculoEnum
```

## Regras de negócio desta camada

| Regra | Onde é implementada |
|---|---|
| `@Version` em `AgendamentoTransporte` evita double-booking simultâneo | JPA optimistic locking — lança `ObjectOptimisticLockingFailureException` |
| Um `AgendamentoTransporte` pertence a uma `Cidade` e pode ter vários locais de destino | `@ManyToMany locaisAgendamento` |
| Cada paciente dentro do transporte (`AgendamentoTransportePaciente`) aponta para uma `Solicitacao` existente | FK `solicitacao_id NOT NULL` |
| O paciente pode ter local de destino diferente do transporte geral | `AgendamentoTransportePaciente.localAgendamento` sobrescreve o local do agendamento |
| `retornaMesmoDia` controla se o paciente volta no mesmo veículo | Campo boolean em `AgendamentoTransportePaciente` |
| Motorista é opcional — transporte pode ser registrado sem motorista definido | `@ManyToOne` nullable em `AgendamentoTransporte` |

## Tabela de junção explícita

| Tabela | Entidades | Colunas |
|---|---|---|
| `agendamento_transporte_local_agendamento` | AgendamentoTransporte ↔ LocalAgendamento | `agendamento_transporte_id`, `local_agendamento_id` |
