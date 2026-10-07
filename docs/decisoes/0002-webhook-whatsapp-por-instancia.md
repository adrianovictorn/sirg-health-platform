---
titulo: Webhook do WhatsApp por instância, sem persistência na primeira fatia
tags:
  - sirg/adr
status: aceita
data: 2026-10-06
---

# 0002 — Webhook do WhatsApp por instância, sem persistência na primeira fatia

## Contexto

O pedido original foi "configurar webhooks da API oficial do WhatsApp para três eventos:
lembretes, conclusão de agendamento e dúvidas sobre exame". Na WhatsApp Business Cloud API,
webhook é só o canal de **entrada** (mensagens do paciente e status de entrega). Lembrete e
aviso de agendamento são mensagens de **saída**, enviadas com templates aprovados pela Meta.
O pedido juntava, portanto, três trabalhos de natureza diferente, com perguntas em aberto
sobre consentimento do paciente, telefone cadastrado, conteúdo das mensagens e atendimento.

A necessidade imediata era uma só: ter uma URL para preencher em "URL de callback" no painel
da Meta. O usuário decidiu, durante o `/feature`, entregar apenas isso agora.

## Decisão

**1. A entrega foi fatiada, e esta fatia é só a rota.** `GET` e `POST
/api/webhooks/whatsapp` validam a chamada e respondem. Envio de mensagens e tratamento de
dúvidas são fatias futuras, cada uma com seu próprio `/feature`.

**2. Nada é gravado.** O evento autenticado é resumido em log (tipo, id da conta, campo,
quantidades) e descartado. Sem migration, sem entidade.

> **Revogada em parte por [[0003-envio-whatsapp-fila-em-banco-e-registro-de-status]]:** os
> status de entrega passaram a atualizar o registro de envios, e as mensagens recebidas entram
> numa contagem diária. Texto, remetente e id das mensagens de pacientes continuam sem ser
> gravados.

**3. A integração é por instância.** Cada município terá o próprio número; a instância só
recebe os próprios eventos e não há roteamento entre VPS. Hoje só São Felipe está ligada.

**4. Instância sem configuração responde `404`.** Precisa de `app.whatsapp.verify-token` **e**
`app.whatsapp.app-secret`; com um só, conta como desligada. Token ou assinatura errados
respondem `403`.

**5. O HMAC é calculado sobre os bytes crus** (`@RequestBody byte[]`), e o controller devolve
o status direto, sem exceção.

## Alternativas consideradas

- **Persistir os eventos já nesta fatia.** Descartado: criaria um acervo de mensagens de
  pacientes (dado sensível) antes de existir decisão sobre retenção, quem consulta e
  consentimento.
- **`503` na instância desligada.** Mais explícito para quem opera, mas sugere retentativa a
  quem chama. O `404` diz que o recurso não existe nessa instância e ainda distingue, nos
  logs, "desligado" de "credencial errada" (`403`). Nenhum dos dois esconde a rota: as demais
  rotas inexistentes respondem 401/403 sem login.
- **`@ConditionalOnProperty` para não registrar a rota.** Não serve: o
  `docker-compose.prod.yaml` passa a variável como string vazia, e string vazia conta como
  propriedade presente. A checagem é em runtime, no service.
- **Receber o corpo como `String` ou DTO.** O projeto usa `@EnableWebMvc`, que deixa o
  conversor de `String` em ISO-8859-1; a assinatura falharia só em mensagens com acento ou
  emoji — um defeito intermitente e difícil de diagnosticar.
- **Desligar o access log do nginx para a rota**, porque o verify token trafega na query
  string do GET. Adiado: exigiria mexer no nginx de produção das duas VPS por um valor de
  baixo risco, que pode ser trocado depois da verificação.

## Consequências

- O painel da Meta pode ser configurado sem que o sistema passe a guardar ou enviar nada.
- Enquanto esta for a única fatia, **mensagens de pacientes chegam e são descartadas**. O
  número não deve ser divulgado como canal de atendimento antes da fatia de dúvidas.
- Quem implementar as próximas fatias não deve logar `from`, `wa_id`, `contacts`, `text` nem
  o id da mensagem, e não deve trocar `byte[]` por `String`/DTO antes da validação.
- A rota é a primeira do sistema que aceita POST de fora sem JWT. O matcher em
  `SecurityConfiguration` é de caminho exato; não ampliar para `/api/webhooks/**`
  (`SegurancaEndpointsIT` cobre).
- Falha no WhatsApp nunca deve travar, atrasar ou desfazer um agendamento, nem afetar consumo
  ou estorno de cota. Vale para as fatias de envio.
- **Em aberto para as próximas fatias:** a URL de callback da Meta é configurada por app. "Um
  número por município" provavelmente exige um app Meta por município, cada um com seu App
  Secret, ou um override de callback por conta. Confirmar na documentação da Meta antes de
  ligar Conceição do Almeida.
