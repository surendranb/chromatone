package com.fuseforge.chromatone

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuseforge.chromatone.ui.theme.ChromaToneTheme
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as NoiseForegroundService.LocalBinder
            mainViewModel.onServiceConnected(binder.getService())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mainViewModel.onServiceDisconnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        enableEdgeToEdge()
        setContent {
            ChromaToneTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(mainViewModel, Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, NoiseForegroundService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mainViewModel.onServiceDisconnected()
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val selectedNoise by viewModel.selectedNoise.observeAsState(NoiseType.White)
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val timerMinutes by viewModel.timerMinutes.observeAsState(null)
    val remainingSeconds by viewModel.remainingSeconds.observeAsState(null)
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf((timerMinutes ?: 0) / 15f) }

    LaunchedEffect(Unit) {
        viewModel.setAppContext(context)
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Top bar with app title and info button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ChromaTone",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                // Countdown timer (if active) on the top right
                if (isPlaying && remainingSeconds != null) {
                    val hrs = (remainingSeconds ?: 0) / 3600
                    val mins = ((remainingSeconds ?: 0) % 3600) / 60
                    val secs = (remainingSeconds ?: 0) % 60
                    Text(
                        text = String.format("%02d:%02d:%02d", hrs, mins, secs),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                NoiseGrid(
                    selected = selectedNoise,
                    isPlaying = isPlaying,
                    onSelect = { viewModel.selectNoise(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = { viewModel.toggleNoise() },
                    modifier = Modifier
                        .size(72.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            // Timer slider (seek bar) always visible, below play/pause
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Slider(
                    value = (remainingSeconds?.div(60f)?.div(15f)) ?: sliderPosition,
                    onValueChange = {
                        sliderPosition = it
                        val minutes = (it * 15).toInt().coerceAtMost(480)
                        viewModel.setTimer(minutes)
                    },
                    valueRange = 0f..32f,
                    steps = 31,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
                // Show countdown in HH:MM:SS if timer is set, else show "∞"
                val seconds = remainingSeconds ?: ((sliderPosition * 15).toInt() * 60).takeIf { it > 0 }
                if (seconds != null && seconds > 0) {
                    val hrs = seconds / 3600
                    val mins = (seconds % 3600) / 60
                    val secs = seconds % 60
                    Text(
                        text = String.format("%02d:%02d:%02d", hrs, mins, secs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = "∞",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
        // Minimal info dialog
        if (showInfoDialog) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { 
                    Text(
                        "ChromaTone",
                        style = MaterialTheme.typography.headlineSmall
                    ) 
                },
                text = { 
                    Column {
                        Text(
                            "Minimal, privacy-first ambient noise app for Android. No ads, no tracking, no network required—just pure focus, sleep, and relaxation sounds generated on your device.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "MIT License",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            "Built by Surendran",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "surendranb.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.clickable {
                                uriHandler.openUri("https://surendranb.com")
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("CLOSE")
                    }
                }
            )
        }
    }
}

// Minimal NoiseGrid (with black outline for color grid, no shadow or decorative elements)
@Composable
fun NoiseGrid(
    selected: NoiseType,
    isPlaying: Boolean,
    onSelect: (NoiseType) -> Unit,
    modifier: Modifier = Modifier
) {
    val noiseTypes = NoiseType.values()
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        val spacing = 12.dp
        
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            // Rigid 3x3 layout using equal weights for vertical stability
            for (row in 0..2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        if (index < noiseTypes.size) {
                            val noise = noiseTypes[index]
                            val isSelected = noise == selected
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        color = noise.color,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            Color.Black.copy(alpha = 0.2f),
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .clickable { onSelect(noise) },
                            ) {
                                Text(
                                    text = noise.purpose,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.Black, // High contrast black always
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
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
        NoiseType.Grey -> Color(0xFFECEFF1)
        NoiseType.Azure -> Color(0xFFE3F2FD)
        NoiseType.Red -> Color(0xFFFFEBEE)
    }

// Data model for noise types
enum class NoiseType(val displayName: String, val purpose: String) {
    White("White Noise", "FOCUS"),
    Pink("Pink Noise", "RELAX"),
    Brown("Brown Noise", "SLEEP"),
    Blue("Blue Noise", "REST"),
    Green("Green Noise", "CREATE"),
    Violet("Violet Noise", "STUDY"),
    Grey("Grey Noise", "CALM"),
    Azure("Azure Noise", "BRIGHT"),
    Red("Red Noise", "HEAVY")
}

// ViewModel for main screen
class MainViewModel : ViewModel() {
    private val _selectedNoise = MutableLiveData(NoiseType.White)
    val selectedNoise: LiveData<NoiseType> = _selectedNoise
    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying
    private val _timerMinutes = MutableLiveData<Int?>(null)
    val timerMinutes: LiveData<Int?> = _timerMinutes
    private val _remainingSeconds = MutableLiveData<Int?>(null)
    val remainingSeconds: LiveData<Int?> = _remainingSeconds
    private var appContext: Context? = null
    private var service: NoiseForegroundService? = null

    fun setAppContext(context: Context) {
        appContext = context.applicationContext
    }

    fun onServiceConnected(service: NoiseForegroundService) {
        this.service = service
        // Sync state from service
        _isPlaying.value = service.isPlaying()
        _selectedNoise.value = service.getSelectedNoise()
        _remainingSeconds.value = service.getRemainingSeconds()
        
        // Polling for state sync (simple approach for now)
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_isPlaying.value != service.isPlaying()) {
                    _isPlaying.value = service.isPlaying()
                }
                if (_remainingSeconds.value != service.getRemainingSeconds()) {
                    _remainingSeconds.value = service.getRemainingSeconds()
                }
            }
        }
    }

    fun onServiceDisconnected() {
        service = null
    }

    fun selectNoise(type: NoiseType) {
        _selectedNoise.value = type
        if (_isPlaying.value == true) {
            playNoise()
        }
    }

    fun setTimer(minutes: Int?) {
        _timerMinutes.value = minutes
        val seconds = if (minutes != null) minutes * 60 else null
        _remainingSeconds.value = seconds
        
        // Always sync timer state to service
        updateServiceTimer(seconds)
    }

    private fun updateServiceTimer(seconds: Int?) {
        val context = appContext ?: return
        val intent = Intent(context, NoiseForegroundService::class.java).apply {
            action = NoiseForegroundService.ACTION_SET_TIMER
            putExtra(NoiseForegroundService.EXTRA_TIMER_SECONDS, seconds ?: -1)
        }
        context.startService(intent)
    }

    fun startTimer() {
        // Now fully handled by the Service's onStartCommand (ACTION_SET_TIMER)
        // or implicitly by ACTION_PLAY if a timer was set previously.
        val minutes = _timerMinutes.value
        updateServiceTimer(if (minutes != null) minutes * 60 else null)
    }

    fun playNoise() {
        val context = appContext ?: return
        val type = _selectedNoise.value ?: NoiseType.White
        val intent = Intent(context, NoiseForegroundService::class.java).apply {
            putExtra(NoiseForegroundService.EXTRA_NOISE_TYPE, type.name)
            action = NoiseForegroundService.ACTION_PLAY
            
            // Fix: Use the live remainingSeconds (current) if it exists,
            // otherwise fall back to the initial timerMinutes.
            val currentSeconds = _remainingSeconds.value ?: _timerMinutes.value?.times(60)
            if (currentSeconds != null) {
                putExtra(NoiseForegroundService.EXTRA_TIMER_SECONDS, currentSeconds)
            }
        }
        context.startForegroundService(intent)
        _isPlaying.value = true
    }

    fun pauseNoise() {
        val context = appContext ?: return
        val intent = Intent(context, NoiseForegroundService::class.java).apply {
            action = NoiseForegroundService.ACTION_PAUSE
        }
        context.startService(intent)
        _isPlaying.value = false
    }

    fun stopNoise() {
        val context = appContext ?: return
        val intent = Intent(context, NoiseForegroundService::class.java).apply {
            action = NoiseForegroundService.ACTION_STOP
        }
        context.startService(intent)
        _isPlaying.value = false
        _remainingSeconds.value = null
    }
    
    fun toggleNoise() {
        if (_isPlaying.value == true) pauseNoise() else playNoise()
    }
    
    override fun onCleared() {
        super.onCleared()
    }
}