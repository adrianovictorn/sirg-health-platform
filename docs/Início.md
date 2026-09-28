---
titulo: Início
tags:
  - sirg/mapa
gerado: manual
---

# SIRG — Início

**SIRG (Sistema Integrado de Regulação e Gestão)** é o sistema web que faz a **regulação e marcação de consultas e exames especializados** em municípios do interior da Bahia. Ele substituiu planilhas manuais: hoje concentra solicitação do paciente, agendamento por especialidade, cota por unidade, transporte sanitário, relatórios de produção e a troca de filas entre municípios pactuados.

Duas VPS rodam o **mesmo código**, uma por município (Conceição do Almeida e São Felipe). O que diferencia cada instância é o `.env` — ver [[Infraestrutura]].

| Camada | Stack |
|---|---|
| Backend | Spring Boot 3.4.3 · Java 21 · Maven |
| Frontend | SvelteKit 2 · Svelte 5 (runes) · Tailwind 4 |
| Dados | PostgreSQL · Flyway |
| Auth | JWT (Auth0 java-jwt) |
| Integração | RabbitMQ (federação) · HAPI FHIR R4 |

---

## Por onde começar

Se a ideia é **entender o contexto**, leia nesta ordem:

1. [[01 - Visão Geral do Projeto]] — o que o sistema faz e com o quê.
2. [[Mapa - Domínio]] — as entidades e o vocabulário do negócio (solicitação, especialidade, cota, pacto).
3. [[05 - Fluxos do Sistema]] — o caminho real de um paciente, do cadastro ao agendamento.
4. [[Mapa - Arquitetura]] — como o código está organizado, camada por camada.
5. [[Mapa - Operação]] — como isso sobe em produção e como se roda local.

Atalhos úteis dentro do Obsidian: `Ctrl+O` abre qualquer nota pelo nome, `Ctrl+Shift+F` busca no vault inteiro, e o ícone de grafo (barra lateral esquerda) mostra como as notas se ligam.

---

## Mapas

- [[O que consultar]] — **vou mexer em X, o que leio antes?** Ponteiros por tarefa, as armadilhas e o que está deprecado.
- [[Mapa - Domínio]] — entidades, enums, regras de negócio.
- [[Mapa - Arquitetura]] — backend em camadas, frontend, padrões adotados.
- [[Mapa - Segurança e Acesso]] — JWT, roles, escopo por unidade.
- [[Mapa - Operação]] — infra, deploy, execução local, histórico.

## Diagramas (Mermaid, renderizam no Obsidian)

- [[diagrama-solicitacoes]] — o fluxo central do sistema.
- [[diagrama-usuarios]] — usuários, roles e autenticação.
- [[diagrama-transporte]] — transporte sanitário.
- [[diagrama-federacao]] — pactos entre municípios.

## Documentação técnica

[[Índice Técnico]] — as 12 seções da documentação técnica, uma nota por seção.

## Especificações

O que foi pedido pelas centrais de regulação e ainda não está no código. Escritas à mão.

- [[Agenda e Oferta]] — abertura de agenda, distribuição de cotas por unidade, estabelecimento executante e CNES (São Felipe, 25/09/2026).

## Referência

- [[Changelog]] — o que mudou em cada versão.
- [[Infraestrutura]] — VPS, domínios, `.env`, deploy.
- [[Visão Institucional]] — a apresentação do projeto.

---

## Como este vault funciona

A pasta `docs/` **é** o vault. As notas de `tecnica/` e `referencia/` são **geradas** a partir dos documentos-fonte na raiz do repositório — não edite essas notas direto, elas são sobrescritas. Edite a fonte e regenere:

```bash
python docs/_build/gerar_vault.py
```

| Vault | Fonte |
|---|---|
| `tecnica/*.md` | `DOCUMENTACAO_TECNICA.md` (uma nota por seção `## N.`) |
| `referencia/Changelog.md` | `CHANGELOG.md` |
| `referencia/Infraestrutura.md` | `INFRA.md` |
| `referencia/Visão Institucional.md` | `README.md` |

Escrito à mão, e nunca sobrescrito pelo script: esta nota, `mapas/`, `especificacoes/` e os
quatro `diagrama-*.md`.

> [!warning] Clonou o repositório agora?
> `tecnica/` e `referencia/` **não são versionadas** — são derivadas, ficariam duplicando diff
> em todo PR e envelheceriam em silêncio. Rode `python docs/_build/gerar_vault.py` uma vez e
> as 16 notas aparecem.
