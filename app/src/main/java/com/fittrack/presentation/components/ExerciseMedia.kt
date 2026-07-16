package com.fittrack.presentation.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.fittrack.presentation.theme.OutlineSubtle
import com.fittrack.presentation.theme.Primary
import com.fittrack.presentation.theme.Secondary
import com.fittrack.presentation.theme.SurfaceElevated

/**
 * Moldura padrão usada em qualquer exibição de GIF de exercício: cantos
 * arredondados, borda sutil e um "glow" de gradiente atrás da animação.
 * O glow ajuda a disfarçar a baixa resolução nativa do GIF (o plano
 * gratuito da ExerciseDB limita a 180px) ao evitar que a miniatura
 * fique "nua" sobre um fundo plano.
 */
@Composable
private fun MediaFrame(
    modifier: Modifier,
    cornerRadius: Dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Primary.copy(alpha = 0.16f), Secondary.copy(alpha = 0.12f))
                )
            )
            .border(1.dp, OutlineSubtle, shape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(750, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier.background(SurfaceElevated.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = Primary.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun FallbackIcon(modifier: Modifier = Modifier, iconSize: Dp) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.FitnessCenter, null, tint = Primary, modifier = Modifier.size(iconSize))
    }
}

/**
 * GIF do exercício com overlay de estado (shimmer enquanto carrega, ícone
 * de fallback em caso de erro). Usa AsyncImage + onState em vez de
 * SubcomposeAsyncImage para não depender de uma API que varia entre
 * versões do Coil.
 */
@Composable
private fun StatefulGif(
    gifUrl: String,
    contentDescription: String?,
    contentScale: ContentScale,
    crossfadeMillis: Int,
    iconSize: Dp
) {
    var state by remember(gifUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(gifUrl)
                .decoderFactory(
                    if (android.os.Build.VERSION.SDK_INT >= 28)
                        ImageDecoderDecoder.Factory() else GifDecoder.Factory()
                )
                .crossfade(crossfadeMillis)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onState = { state = it }
        )
        when (state) {
            is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty ->
                ShimmerPlaceholder(Modifier.fillMaxSize())
            is AsyncImagePainter.State.Error -> FallbackIcon(iconSize = iconSize)
            else -> Unit
        }
    }
}

/** Miniatura usada em listas (biblioteca de exercícios, cartões de treino). */
@Composable
fun ExerciseGifThumbnail(
    gifUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    cornerRadius: Dp = 14.dp
) {
    MediaFrame(modifier = modifier.size(size), cornerRadius = cornerRadius) {
        if (gifUrl.isBlank()) {
            FallbackIcon(iconSize = size * 0.4f)
        } else {
            StatefulGif(
                gifUrl = gifUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                crossfadeMillis = 200,
                iconSize = size * 0.4f
            )
        }
    }
}

/** Versão em destaque (ficha do exercício / bottom sheet), maior e com moldura mais elaborada. */
@Composable
fun ExerciseGifHero(
    gifUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp
) {
    MediaFrame(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.4f)),
        cornerRadius = 20.dp
    ) {
        if (gifUrl.isBlank()) {
            FallbackIcon(iconSize = 56.dp)
        } else {
            StatefulGif(
                gifUrl = gifUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                crossfadeMillis = 250,
                iconSize = 56.dp
            )
        }
    }
}
