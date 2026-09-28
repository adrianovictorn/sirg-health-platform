---
titulo: Mapa - Operação
tags:
  - sirg/mapa
  - sirg/operacao
gerado: manual
---

# Mapa — Operação

## Rodar local

Pré-requisitos e comandos em [[12 - Guia de Execução]]: Java 21, Node, PostgreSQL, e RabbitMQ para exercitar a federação. Backend em `:8080` (Maven), frontend em `:5173` (Vite), Flyway aplica as migrações na subida com `ddl-auto=validate`.

O `application.properties` de desenvolvimento está transcrito em [[11 - Dependências e Configurações]] — incluindo `app.municipio.*`, que define qual município aquela instância é.

## Produção

Duas VPS, uma por município, **mesmo repositório**; o `.env` local diferencia. Deploy por GitHub Actions, nginx como proxy reverso, SSL via certbot com renovação em cron. Passo a passo, secrets, backup e o roteiro de "adicionando um novo município" em [[Infraestrutura]].

| Município | Domínio |
|---|---|
| Conceição do Almeida | sirg.com.br |
| São Felipe | sirgsaofelipe.com |

O backend lê o município de `InstanceContext.java`; feature exclusiva de um município é `if` sobre `instanceContext.getMunicipioLocal().getNome()`.

## Migrações

Uma migração Flyway por mudança de schema, nunca editada depois de aplicada. Ordem de deploy quando há migração nova, e as armadilhas conhecidas, em [[Infraestrutura]] (seções "Antes de um deploy com migrations novas" e "Notas sobre migrations"). Histórico das migrações que importam em [[06 - Banco de Dados]].

## Histórico

[[Changelog]] — a versão documentada atual é a **1.6**. É o lugar para entender *por que* algo foi feito de um jeito: as entradas explicam a decisão, não só a mudança.

---

Volta para [[Início]] · vizinhos: [[Mapa - Arquitetura]], [[Mapa - Segurança e Acesso]]
