package com.example.jiotvnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// CHANGE THIS TO YOUR PC'S LOCAL IP ADDRESS RUNNING JIOTV GO
const val SERVER_URL = "http://192.168.29.167:5001"

data class ChannelResponse(val result: List<Channel>)
data class Channel(
    val channel_id: Int,
    val channel_name: String,
    val logoUrl: String,
    val isHD: Boolean
)

interface JioTvApi {
    @GET("/channels")
    suspend fun getChannels(): ChannelResponse
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = Retrofit.Builder()
            .baseUrl(SERVER_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JioTvApi::class.java)

        setContent {
            var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
            var selectedChannel by remember { mutableStateOf<Channel?>(null) }
            var errorMsg by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                try {
                    channels = api.getChannels().result
                } catch (e: Exception) {
                    errorMsg = "Cannot connect to $SERVER_URL. Make sure JioTV Go is running."
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
                when {
                    selectedChannel != null -> {
                        BackHandler { selectedChannel = null }
                        VideoPlayerScreen(channel = selectedChannel!!)
                    }
                    errorMsg != null -> {
                        Text(
                            text = errorMsg!!,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        ChannelGridScreen(
                            channels = channels,
                            onChannelClick = { selectedChannel = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelGridScreen(channels: List<Channel>, onChannelClick: (Channel) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "JioTV Live (${channels.size} Channels)",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(channels) { channel ->
                Card(
                    onClick = { onChannelClick(channel) },
                    modifier = Modifier.height(130.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AsyncImage(
                            model = "$SERVER_URL/jtvimage/${channel.logoUrl}",
                            contentDescription = channel.channel_name,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = channel.channel_name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(channel: Channel) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val streamUrl = "$SERVER_URL/live/${channel.channel_id}.m3u8"
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                        .setLicenseUri("$SERVER_URL/live/key/${channel.channel_id}")
                        .build()
                )
                .build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
