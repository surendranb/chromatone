package com.fuseforge.chromatone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fuseforge.chromatone.audio.NoiseGenerator
import com.fuseforge.chromatone.audio.NoisePlayer
import com.fuseforge.chromatone.ui.theme.ChromaToneTheme
import kotlinx.coroutines.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChromaToneTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(mainViewModel, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val selectedNoise by viewModel.selectedNoise.observeAsState(NoiseType.White)
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val volume by viewModel.volume.observeAsState(0.7f)
    val timerMinutes by viewModel.timerMinutes.observeAsState(null)
    val remainingSeconds by viewModel.remainingSeconds.observeAsState(null)
    var showVolume by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }

    // Helper to get next/previous noise type
    fun nextNoiseType(current: NoiseType): NoiseType {
        val values = NoiseType.values()
        return values[(current.ordinal + 1) % values.size]
    }
    fun prevNoiseType(current: NoiseType): NoiseType {
        val values = NoiseType.values()
        return values[(current.ordinal - 1 + values.size) % values.size]
    }

    Box(
        modifier = modifier
            .background(Color.White)
            .fillMaxSize()
    ) {
        // Top left: Timer button
        IconButton(
            onClick = { showTimer = true },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Filled.AccessTime, contentDescription = "Timer", tint = Color.Black)
        }
        // Top right: Volume button
        IconButton(
            onClick = { showVolume = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.VolumeUp, contentDescription = "Volume", tint = Color.Black)
        }
        // Center: Large colored circle with left/right arrows, then text, then buttons
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { viewModel.selectNoise(prevNoiseType(selectedNoise)) }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Previous", tint = Color.Black)
                }
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(selectedNoise.color)
                        .clickable { viewModel.selectNoise(nextNoiseType(selectedNoise)) }
                )
                IconButton(onClick = { viewModel.selectNoise(nextNoiseType(selectedNoise)) }) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Next", tint = Color.Black)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = selectedNoise.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { viewModel.toggleNoise() }) {
                    if (isPlaying) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color.Black, modifier = Modifier.size(48.dp))
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { viewModel.stopNoise() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.Black, modifier = Modifier.size(48.dp))
                }
            }
        }
        // Timer overlay
        if (showTimer) {
            Dialog(onDismissRequest = { showTimer = false }) {
                Surface(shape = MaterialTheme.shapes.medium, color = Color.White) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Set Timer (minutes)", color = Color.Black, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        var sliderValue by remember { mutableStateOf((timerMinutes ?: 0) / 15f) }
                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                sliderValue = it
                                viewModel.setTimer((it * 15).toInt().coerceAtMost(480))
                            },
                            valueRange = 0f..32f,
                            steps = 31,
                            modifier = Modifier.width(240.dp)
                        )
                        Text(
                            text = if ((sliderValue * 15).toInt() == 0) "No Timer" else "${(sliderValue * 15).toInt()} min",
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showTimer = false }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
        // Volume overlay
        if (showVolume) {
            Dialog(onDismissRequest = { showVolume = false }) {
                Surface(shape = MaterialTheme.shapes.medium, color = Color.White) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Volume", color = Color.Black, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        var sliderValue by remember { mutableStateOf(volume) }
                        Slider(
                            value = sliderValue,
                            onValueChange = { v ->
                                sliderValue = v
                                viewModel.setVolume(v)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.height(180.dp)
                        )
                        Text(
                            text = "${(sliderValue * 100).toInt()}%",
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showVolume = false }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
        // Timer countdown (if active)
        if (timerMinutes != null && remainingSeconds != null && (remainingSeconds ?: 0) > 0) {
            val min = (remainingSeconds ?: 0) / 60
            val sec = (remainingSeconds ?: 0) % 60
            Text(
                text = "Time left: %02d:%02d".format(min, sec),
                color = Color.Black,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            )
        }
    }
}

// Assign a distinct color to each noise type
val NoiseType.color: Color
    get() = when (this) {
        NoiseType.White -> Color(0xFFF5F5F5)
        NoiseType.Pink -> Color(0xFFFFC1E3)
        NoiseType.Brown -> Color(0xFFD7CCC8)
        NoiseType.Green -> Color(0xFFC8E6C9)
        NoiseType.Blue -> Color(0xFFBBDEFB)
        NoiseType.Violet -> Color(0xFFE1BEE7)
    }

@Composable
fun NoiseTypeCarousel(selected: NoiseType, onSelect: (NoiseType) -> Unit) {
    val noiseTypes = NoiseType.values().toList()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(noiseTypes) { type ->
            val isSelected = type == selected
            val cardColor by animateColorAsState(
                if (isSelected) type.color else Color.White,
                animationSpec = tween(durationMillis = 400), label = "cardColor"
            )
            Card(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .scale(if (isSelected) 1.1f else 1f)
                    .shadow(if (isSelected) 12.dp else 2.dp)
                    .clickable(onClick = { onSelect(type) }),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(if (isSelected) 12.dp else 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = type.displayName,
                        color = if (isSelected) Color.Black else Color.DarkGray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
fun VolumeSlider(volume: Float, onVolumeChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = "Volume: ${(volume * 100).toInt()}%", modifier = Modifier.padding(bottom = 4.dp))
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF90CAF9),
                activeTrackColor = Color(0xFF90CAF9)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerDropdown(timerMinutes: Int?, onTimerChange: (Int?) -> Unit) {
    val options = listOf(5, 10, 15, 30, 60)
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf(timerMinutes?.let { "$it min" } ?: "No Timer") }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Timer") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("No Timer") },
                onClick = {
                    selectedText = "No Timer"
                    onTimerChange(null)
                    expanded = false
                }
            )
            options.forEach { min ->
                DropdownMenuItem(
                    text = { Text("$min min") },
                    onClick = {
                        selectedText = "$min min"
                        onTimerChange(min)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Data model for noise types
enum class NoiseType(val displayName: String) {
    White("White Noise"),
    Pink("Pink Noise"),
    Brown("Brown Noise"),
    Green("Green Noise"),
    Blue("Blue Noise"),
    Violet("Violet Noise")
}

// ViewModel for main screen
class MainViewModel : ViewModel() {
    private val _selectedNoise = MutableLiveData(NoiseType.White)
    val selectedNoise: LiveData<NoiseType> = _selectedNoise
    private var noisePlayer: NoisePlayer? = null
    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying
    private val _volume = MutableLiveData(0.7f)
    val volume: LiveData<Float> = _volume
    private val _timerMinutes = MutableLiveData<Int?>(null)
    val timerMinutes: LiveData<Int?> = _timerMinutes
    private val _remainingSeconds = MutableLiveData<Int?>(null)
    val remainingSeconds: LiveData<Int?> = _remainingSeconds
    private var timerJob: Job? = null

    fun selectNoise(type: NoiseType) {
        _selectedNoise.value = type
        if (_isPlaying.value == true) {
            stopNoise()
            playNoise()
        }
    }

    fun setVolume(vol: Float) {
        _volume.value = vol
        if (_isPlaying.value == true) {
            stopNoise()
            playNoise()
        }
    }

    fun setTimer(minutes: Int?) {
        _timerMinutes.value = minutes
        _remainingSeconds.value = if (minutes != null) minutes * 60 else null
        if (minutes == null) {
            timerJob?.cancel()
            timerJob = null
        }
    }

    fun startTimer() {
        val totalSeconds = _timerMinutes.value?.times(60) ?: return
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            var seconds = totalSeconds
            _remainingSeconds.value = seconds
            while (seconds > 0 && _isPlaying.value == true) {
                delay(1000)
                seconds--
                _remainingSeconds.value = seconds
            }
            if (seconds == 0) {
                stopNoise()
                setTimer(null)
            }
        }
    }

    fun playNoise() {
        val type = _selectedNoise.value ?: NoiseType.White
        val vol = _volume.value ?: 0.7f
        noisePlayer = NoisePlayer(
            bufferProvider = { bufferSize -> NoiseGenerator.getNoiseBuffer(type, bufferSize) },
            volume = vol
        )
        noisePlayer?.start()
        _isPlaying.value = true
        if (_timerMinutes.value != null) {
            startTimer()
        }
    }
    fun stopNoise() {
        noisePlayer?.stop()
        noisePlayer = null
        _isPlaying.value = false
    }
    fun toggleNoise() {
        if (_isPlaying.value == true) stopNoise() else playNoise()
    }
    override fun onCleared() {
        super.onCleared()
        noisePlayer?.stop()
        timerJob?.cancel()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ChromaToneTheme {
        Greeting("Android")
    }
}