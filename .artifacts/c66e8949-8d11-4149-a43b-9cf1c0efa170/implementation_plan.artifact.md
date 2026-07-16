# Implementation Plan - Refinamento de Catálogo e Busca "Live"

Este plano visa resolver dois problemas: a presença de múltiplos idiomas indesejados no catálogo e a barra de busca que não está filtrando os resultados em tempo real.

## Proposed Changes

### 1. Refinamento de Idioma (Português & English Fallback)

#### [MODIFY] [ExerciseMapper.kt](file:///D:/Documents/GitHub/ShadyFit/app/src/main/java/com/fittrack/data/mapper/ExerciseMapper.kt)
- Ajustar `toEntity` para retornar `null` se o exercício não tiver tradução em **Português (ID 7)** ou **Inglês (ID 2)**.
- Isso garantirá que idiomas como Espanhol ou Alemão não entrem no banco de dados local.

#### [MODIFY] [ExerciseRepository.kt](file:///D:/Documents/GitHub/ShadyFit/app/src/main/java/com/fittrack/data/repository/ExerciseRepository.kt)
- Atualizar a lógica de sincronização para ignorar exercícios sem os idiomas permitidos.

---

### 2. Busca "Live" em Tempo Real

#### [MODIFY] [CatalogDao.kt](file:///D:/Documents/GitHub/ShadyFit/app/src/main/java/com/fittrack/data/local/dao/CatalogDao.kt)
- Atualizar `getPagedExercises` para aceitar um parâmetro `query` opcional.
- A query SQL usará `LIKE %query%` para permitir a filtragem por partes do nome (ex: "Sup" encontrará "Supino").

#### [MODIFY] [ExerciseRepository.kt](file:///D:/Documents/GitHub/ShadyFit/app/src/main/java/com/fittrack/data/repository/ExerciseRepository.kt)
- Atualizar `getPagedExercises` para passar a query para o DAO.

#### [MODIFY] [ExerciseViewModel.kt](file:///D:/Documents/GitHub/ShadyFit/app/src/main/java/com/fittrack/presentation/screens/exercise/ExerciseViewModel.kt)
- Transformar `pagedExercises` em um `StateFlow` que reage a mudanças no `searchQuery`.
- Utilizar `flatMapLatest` para reiniciar o fluxo de paginação sempre que o usuário digitar algo novo.

## Verification Plan

### Manual Verification
- **Idioma**: Limpar os dados do app e verificar se apenas Português e Inglês aparecem.
- **Busca**: Digitar "Sup" e verificar se o "Supino" aparece instantaneamente.
- **Performance**: Garantir que a lista continua fluida durante a digitação.
