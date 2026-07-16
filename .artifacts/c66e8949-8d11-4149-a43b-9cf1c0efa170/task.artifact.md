# Tarefas - Refinamento de Catálogo e Busca Live

## Fase 1: Limpeza de Idiomas
- [ ] Ajustar `ExerciseMapper.kt` para aceitar apenas PT/EN
- [ ] Atualizar `ExerciseRepository.kt` para filtrar idiomas durante a sincronização

## Fase 2: Busca Live (Paging 3)
- [ ] Atualizar `CatalogDao.kt` com suporte a `LIKE` na paginação
- [ ] Refatorar `ExerciseRepository.kt` para passar o termo de busca
- [ ] Atualizar `ExerciseViewModel.kt` para tornar a busca reativa

## Fase 3: Verificação
- [ ] Verificar compilação
- [ ] Validar busca no emulador
