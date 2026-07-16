# Walkthrough - Rearquitetura Offline-First & Localização em Português

Eu transformei o **ShadyFit** em uma aplicação robusta e moderna, seguindo as melhores práticas de arquitetura da Google e agora totalmente localizada em **Português**.

## Mudanças Principais

### 1. Localização em Português (Wger)
- **Idioma Nativo**: Configurei a API do Wger para solicitar dados no idioma **Português (ID 14)**.
- **Fallback Automático**: Implementei a lógica onde, se um exercício não possuir tradução para o português, a API retorna automaticamente a versão em inglês, garantindo que nenhum dado seja perdido.
- **Categorias Traduzidas**: As categorias de exercícios e cores dos grupos musculares foram mapeadas para funcionar tanto em português quanto em inglês.

### 2. Clean Architecture
Reorganizei o projeto em uma estrutura de camadas profissional:
- `core`: Infraestrutura global (Banco de dados, Rede, Workers).
- `data`: Implementações de Repositórios, DAOs, Entidades Room e DTOs.
- `domain`: Modelos de negócio puros (Exercise, Workout, etc.) e interfaces.
- `presentation`: UI em Jetpack Compose e ViewModels utilizando StateFlow.

### 3. Banco de Dados Normalizado (Room v3)
O banco de dados foi redesenhado para ser a **Única Fonte de Verdade (SSOT)**:
- **Tabelas Relacionais**: Exercícios, Músculos, Equipamentos, Categorias, Imagens e Aliases.
- **Paging 3**: A listagem de exercícios agora carrega os dados sob demanda do Room, garantindo performance fluida mesmo com milhares de itens.

### 4. Sincronização e Offline-First
- **WorkManager**: O `ExerciseSyncWorker` baixa o catálogo completo no primeiro lançamento e agenda atualizações automáticas a cada 7 dias.
- **Busca Local**: A pesquisa agora é 100% offline e inclui busca em **apelidos** (Aliases). Por exemplo, pesquisar "Supino" encontrará "Bench Press" se o alias estiver cadastrado.

### 5. Correção de Erros de Build
- Resolvi problemas de compatibilidade de versões das bibliotecas (Paging e WorkManager) com o AGP 8.5.2.
- Adicionei a dependência `room-paging` necessária para o suporte nativo ao Paging 3 no Room.

## Verificação
- O projeto compila 100% com `./gradlew :app:compileDebugKotlin`.
- A sincronização inicial foi configurada para forçar a atualização dos dados antigos em inglês para os novos em português.

> [!TIP]
> Ao abrir o app, os exercícios começarão a aparecer em português conforme o Worker processa os dados do Wger em segundo plano.
