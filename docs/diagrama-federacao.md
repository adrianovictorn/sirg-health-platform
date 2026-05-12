# Diagrama — Módulo de Federação (Pactos entre Municípios)

Cobre a rede de municípios cooperantes, os pactos de compartilhamento de filas e o sistema de notificações.

```mermaid
classDiagram

    class Municipio {
        <<Entity — tabela: municipio>>
        +UUID id  [gerado automaticamente]
        +String nome       [UNIQUE]
        +String cnes       [UNIQUE]
        +String rabbitQueueName  [UNIQUE]
        +String baseUrl
        +String publicKey
        +String apiKey     [UNIQUE]
        +Boolean discoverable
    }

    class Pacto {
        <<Entity — tabela: pactos>>
        +Long id
        +String nome
        +String descricao
        +StatusPacto status
        +LocalDateTime createdAt  [@PrePersist]
    }

    class PactoEvento {
        <<Entity — tabela: pacto_evento>>
        +Long id
        +UUID uuid          [UNIQUE]
        +Long solicitacaoLocalId
        +String municipioOrigem
        +UUID municipioOrigemId
        +String label
        +PactoEventoStatus status
        +String consumidoPorMunicipio
        +LocalDateTime publishedAt  [@PrePersist]
        +LocalDateTime consumidoAt
    }

    class PactoConvite {
        <<Entity — tabela: pacto_convite>>
        +Long id
        +UUID token          [UNIQUE]
        +Long pactoIdRemoto
        +String pactoNome
        +UUID convidadoMunicipioId
        +UUID remetenteMunicipioId
        +String remetenteNome
        +String mensagem
        +PactoConviteStatus status
        +LocalDateTime createdAt
        +LocalDateTime respondedAt
    }

    class PactoJoinRequest {
        <<Entity — tabela: pacto_join_request>>
        +Long id
        +UUID token              [UNIQUE]
        +Long pactoIdRemoto
        +UUID solicitanteMunicipioId
        +String solicitanteNome
        +String mensagem
        +PactoConviteStatus status
        +LocalDateTime createdAt
        +LocalDateTime respondedAt
    }

    class Notificacao {
        <<Entity — tabela: notificacao>>
        +Long id
        +UUID municipioDestinoId
        +String tipo
        +String resumo
        +String linkPath
        +String payload   [JSONB]
        +boolean lida
        +LocalDateTime createdAt  [@PrePersist]
    }

    class StatusPacto {
        <<enum>>
        ATIVO
        INATIVO
    }

    class PactoEventoStatus {
        <<enum>>
        PUBLICADO
        CONSUMIDO
        CANCELADO
    }

    class PactoConviteStatus {
        <<enum>>
        PENDENTE
        ACEITO
        RECUSADO
    }

    %% Relacionamentos estruturais

    Pacto "0..*" --> "1"    Municipio : municipioCriador\n(ManyToOne)
    Pacto "0..*" --> "0..*" Municipio : membros\n(ManyToMany, EAGER — tabela: pacto_membros)

    PactoEvento "0..*" --> "1" Pacto : pacto\n(ManyToOne, LAZY, NOT NULL)

    Pacto ..> StatusPacto
    PactoEvento  ..> PactoEventoStatus
    PactoConvite ..> PactoConviteStatus
    PactoJoinRequest ..> PactoConviteStatus

    %% Notas de integridade referencial
    %% PactoConvite e PactoJoinRequest usam IDs remotos (UUID/Long)
    %% em vez de FK direta, pois referem dados de outro município
```

## Fluxo de convite (PactoConvite)

```
Município A                      RabbitMQ                    Município B
     │                               │                             │
     ├─ cria PactoConvite ──────────►│── PactoConviteMensagemDTO ──►│
     │   status=PENDENTE             │                             │
     │                               │                             ├─ cria PactoConvite
     │                               │                             │   status=PENDENTE
     │                               │                             │
     │                               │◄─ PactoConviteAceiteMensagem─┤ aceita/recusa
     │                               │                             │
     ├─ atualiza status ◄────────────┤                             │
     ├─ adiciona B em Pacto.membros  │                             │
```

## Fluxo de join request (PactoJoinRequest)

```
Município B (quer entrar)       RabbitMQ                   Município A (dono do pacto)
     │                               │                             │
     ├─ cria PactoJoinRequest ───────►│── PactoJoinRequestMensagem ─►│
     │   status=PENDENTE             │                             │
     │                               │◄─ PactoJoinAceiteMensagem ──┤ aceita/recusa
     │                               │                             │
     ├─ atualiza status ◄────────────┤                             │
```

## Fluxo de compartilhamento de solicitação (PactoEvento)

```
Município A (origem)            RabbitMQ               Município B (membro do pacto)
     │                               │                             │
     ├─ cria PactoEvento ────────────►│── payload FHIR R4 ──────────►│
     │   status=PUBLICADO            │                             │
     │                               │                             ├─ cria PactoEvento local
     │                               │                             │   status=PUBLICADO
     │                               │                             │
     │                               │◄─ claim (interesse) ────────┤
     │                               │                             │
     ├─ marca evento CONSUMIDO ◄─────┤                             │
     ├─ cria Notificacao para A      │                             │
```

## Regras de negócio desta camada

| Regra | Onde é implementada |
|---|---|
| `PactoConvite` e `PactoJoinRequest` usam IDs remotos (não FK) porque o pacto pode existir em outro servidor | Campos `pactoIdRemoto: Long`, `remetenteMunicipioId: UUID` |
| Um `Municipio` tem `rabbitQueueName` único — é o endereço de entrega de mensagens | `@Column(unique=true)` |
| `PactoEvento.uuid` garante idempotência: o mesmo evento não é processado duas vezes | `@Column(unique=true)` em `uuid` |
| `Notificacao.payload` é JSONB — armazena o DTO completo da mensagem para auditoria | Campo `String payload` mapeado como text com cast JSONB no PostgreSQL |
| `discoverable = true` permite que o município apareça no registry público (`/api/registry`) | Filtro em `MunicipioRepository` |
| `Pacto.status` padrão é `ATIVO` ao persistir | `@PrePersist onCreate()` |

## Tabelas de junção

| Tabela | Entidades | Colunas |
|---|---|---|
| `pacto_membros` | Pacto ↔ Municipio | `pacto_id`, `municipio_id` |
