package com.fittrack.data.remote

import com.fittrack.BuildConfig

/**
 * Constrói a URL direta do GIF de demonstração de um exercício.
 *
 * O endpoint "/exercises" (e variantes: bodyPart, target, name, etc.) da ExerciseDB
 * deixou de incluir o campo "gifUrl" no JSON de resposta — hoje ele só devolve dados
 * textuais (id, name, bodyPart, target, equipment, instructions...). As animações
 * passaram a ser servidas por um endpoint dedicado, /image, que recebe o exerciseId
 * e devolve o GIF diretamente (Content-Type: image/gif), autenticado por query param.
 *
 * Como a chave vai na própria URL (?rapidapi-key=...), esta URL pode ser usada
 * diretamente como "model" do Coil/AsyncImage, sem precisar de headers customizados.
 *
 * resolution aceita 180, 360, 720 ou 1080 — mas o plano gratuito da RapidAPI só dá
 * acesso a 180px; pedir uma resolução maior do que o plano permite não dá erro,
 * a API simplesmente devolve a maior resolução disponível no plano.
 */
fun exerciseGifUrl(exerciseId: String, resolution: Int = 180): String {
    if (exerciseId.isBlank() || BuildConfig.EXERCISEDB_API_KEY.isBlank()) return ""
    return "https://exercisedb.p.rapidapi.com/image" +
        "?exerciseId=$exerciseId" +
        "&resolution=$resolution" +
        "&rapidapi-key=${BuildConfig.EXERCISEDB_API_KEY}"
}
