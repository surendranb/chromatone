package com.fuseforge.chromatone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.fuseforge.chromatone.ui.theme.ChromaToneTheme
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.fuseforge.chromatone.audio.NoisePlayer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import com.fuseforge.chromatone.audio.NoiseGenerator
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material3.ExperimentalMaterial3Api

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
    Column(modifier = modifier) {
        Text(
            text = "Selected Noise: ${selectedNoise.displayName}",
            modifier = Modifier.padding(bottom = 16.dp)
        )
        NoiseTypeSelector(selected = selectedNoise, onSelect = { viewModel.selectNoise(it) })
        VolumeSlider(volume = volume, onVolumeChange = { viewModel.setVolume(it) })
        TimerDropdown(timerMinutes = timerMinutes, onTimerChange = { viewModel.setTimer(it) })
        if (timerMinutes != null && remainingSeconds != null) {
            val min = (remainingSeconds ?: 0) / 60
            val sec = (remainingSeconds ?: 0) % 60
            Text(
                text = "Time left: %02d:%02d".format(min, sec),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        IconButton(onClick = { viewModel.toggleNoise() }) {
            if (isPlaying) {
                Icon(Icons.Filled.Pause, contentDescription = "Pause")
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
            }
        }
    }
}

@Composable
fun NoiseTypeSelector(selected: NoiseType, onSelect: (NoiseType) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 16.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        NoiseType.values().forEach { type ->
            val isSelected = type == selected
            Text(
                text = type.displayName,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(
                        color = if (isSelected) Color(0xFF90CAF9) else Color.LightGray,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelect(type) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (isSelected) Color.Black else Color.DarkGray
            )
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