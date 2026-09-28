---
description: Orquestra prompt-reviewer → architecture-explorer (modo ajuste) → aprovação → testes de caracterização → implementação → code-reviewer para ALTERAR algo que já existe no SIRG.
argument-hint: <descrição do ajuste em algo existente>
---

Você vai orquestrar o ajuste de um comportamento **já existente** no SIRG a partir da
descrição do usuário:

$ARGUMENTS

Este fluxo é mais cauteloso que `/feature` porque o alvo já está em produção, com dados
reais e usuários dependendo do comportamento atual. O objetivo é o **menor diff seguro**,
não uma reescrita. Siga estas etapas **na ordem**, sem pular nenhuma e sem implementar antes
da etapa 5 (testes de caracterização) e da aprovação na etapa 4.

## 1. Revisão do pedido

Invoque o subagente `prompt-reviewer` com o pedido acima, deixando explícito que é um
**ajuste** (alteração de comportamento existente) e pedindo que, além do formato padrão,
ele inclua a seção **"O que NÃO deve mudar"** — os comportamentos, contratos de API,
formatos de dado ou telas que devem continuar exatamente como estão. Mostre o resultado
completo ao usuário.

## 2. Pausa

Pare aqui. Peça ao usuário para responder as ambiguidades e confirmar (ou completar) o que
não deve mudar. Não prossiga sem essa resposta.

## 3. Exploração de arquitetura em modo ajuste

Com o pedido revisado, invoque o subagente `architecture-explorer` explicitamente em
**modo ajuste**. Mostre o relatório completo ao usuário: comportamento atual (com arquivos e
métodos), todos os pontos que chamam ou dependem desse código, cobertura de testes
existente, plano de ajuste com o menor diff possível, riscos de regressão (incluindo dados
já gravados no banco) e, à parte, quaisquer refatorações notadas fora do escopo.

## 4. Pausa

Pare aqui. Peça ao usuário para confirmar que a descrição do **comportamento atual** está
correta (é a base de tudo que segue — se estiver errada, o plano inteiro pode estar errado)
e escrever "aprovado". Se houver correção ao comportamento atual ou ao plano, repita a
etapa 3 antes de prosseguir.

## 5. Testes de caracterização (se a área não tiver testes)

Verifique a cobertura de testes reportada na etapa 3. Se a área tocada pelo ajuste **não**
tiver testes cobrindo o comportamento atual, escreva testes que capturem esse comportamento
antes de qualquer mudança, e rode-os — eles devem **passar** contra o código atual, sem
alteração nenhuma. Isso garante uma rede de segurança para detectar regressão depois do
ajuste. Se a área já tem testes suficientes, avise o usuário e siga para a etapa 6.

## 6. Implementação

Implemente o ajuste seguindo exatamente o plano aprovado, com o menor diff possível. Não
faça refatorações fora do escopo aprovado, mesmo as sugeridas separadamente pelo
architecture-explorer — essas ficam para outra tarefa, a menos que o usuário peça
explicitamente para incluí-las agora.

## 7. Testes

Rode todos os testes da área tocada (os de caracterização escritos na etapa 5, se houver, e
os pré-existentes). Todos devem passar. Se algum teste de caracterização falhar, isso indica
uma regressão — pare e avise o usuário antes de prosseguir.

## 8. Revisão final

Invoque o subagente `code-reviewer`, passando o plano aprovado e a seção "o que NÃO deve
mudar" como referência, e pedindo atenção especial a **regressões** e a **mudanças fora do
escopo aprovado**. Mostre o relatório completo ao usuário.

## 9. Fechamento

Liste os arquivos alterados e sugira uma mensagem de commit. Não crie o commit sem o
usuário pedir explicitamente.
