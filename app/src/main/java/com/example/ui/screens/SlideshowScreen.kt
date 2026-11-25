package com.example.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.util.FolioImageLoader
import com.example.data.local.MediaItemEntity
import kotlinx.coroutines.delay

enum class SlideshowTransition(val label: String) {
    FADE("Fade"),
    SLIDE("Slide"),
    ZOOM("Zoom")
}

@Composable
fun SlideshowScreen(
    items: List<MediaItemEntity>,
    startMediaId: String?,
    reducedMotion: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val photosOnly = remember(items) { items.filter { !it.isVideo }.ifEmpty { items } }
    if (photosOnly.isEmpty()) {
        BackHandler { onBack() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No photos available for slideshow", color = Color.White)
        }
        return
    }

    var currentIndex by remember {
        mutableIntStateOf(
            startMediaId?.let { id -> photosOnly.indexOfFirst { it.id == id }.coerceAtLeast(0) } ?: 0
        )
    }
    var isPlaying by remember { mutableStateOf(true) }
    var transitionType by remember { mutableStateOf(SlideshowTransition.FADE) }
    var intervalSec by remember { mutableIntStateOf(3) }
    var showControls by remember { mutableStateOf(true) }
    var musicUri by remember { mutableStateOf<Uri?>(null) }

    val mediaPlayer = remember { MediaPlayer() }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            musicUri = uri
            runCatching {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(context, uri)
                mediaPlayer.isLooping = true
                mediaPlayer.prepare()
                if (isPlaying) mediaPlayer.start()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    LaunchedEffect(isPlaying, musicUri) {
        if (musicUri != null) {
            runCatching {
                if (isPlaying && !mediaPlayer.isPlaying) mediaPlayer.start()
                else if (!isPlaying && mediaPlayer.isPlaying) mediaPlayer.pause()
            }
        }
    }

    LaunchedEffect(isPlaying, intervalSec, photosOnly.size) {
        while (isPlaying && photosOnly.size > 1) {
            delay(intervalSec * 1000L)
            currentIndex = (currentIndex + 1) % photosOnly.size
        }
    }

    BackHandler { onBack() }
    val imageLoader = remember(context) { FolioImageLoader.get(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        AnimatedContent(
            targetState = photosOnly[currentIndex],
            transitionSpec = {
                val effective = if (reducedMotion) SlideshowTransition.FADE else transitionType
                when (effective) {
                    SlideshowTransition.FADE -> fadeIn(tween(650)) togetherWith fadeOut(tween(650))
                    SlideshowTransition.SLIDE -> slideInHorizontally(tween(500)) { it } togetherWith slideOutHorizontally(tween(500)) { -it }
                    SlideshowTransition.ZOOM -> (fadeIn(tween(600)) + scaleIn(initialScale = 1.08f, animationSpec = tween(700))) togetherWith fadeOut(tween(500))
                }
            },
            label = "slideshow_transition"
        ) { media ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.parse(media.uriString))
                    .crossfade(false)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = media.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Slideshow (${currentIndex + 1}/${photosOnly.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .navigationBarsPadding()
                    .padding(14.dp)
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SlideshowTransition.entries.forEach { mode ->
                        FilterChip(
                            selected = transitionType == mode,
                            onClick = { transitionType = mode },
                            label = { Text(mode.label) }
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    AssistChip(
                        onClick = { audioPicker.launch(arrayOf("audio/*")) },
                        leadingIcon = { Icon(Icons.Outlined.MusicNote, null, modifier = Modifier.size(16.dp)) },
                        label = { Text(if (musicUri == null) "Local Music" else "Change Audio") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 6).forEach { sec ->
                        FilterChip(
                            selected = intervalSec == sec,
                            onClick = { intervalSec = sec },
                            label = { Text("${sec}s") }
                        )
                    }
                }
            }
        }
    }
}
