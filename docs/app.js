/**
 * ChromaTone Web Audio Engine: 'True Zen' Stable Edition
 */

let audioContext = null;
let noiseNode = null;
let isPlaying = false;
let currentNoiseType = "White";

// Timer State Machine
let remainingSeconds = 0;
let countdownInterval = null;

// Mathematical Noise Port (Kotlin -> JS)
const generateNoiseSample = (type, lastSamples) => {
    const white = Math.random() * 2 - 1;
    switch (type) {
        case "White": return white;
        case "Pink":
            if (!lastSamples.pink) lastSamples.pink = new Array(16).fill(0);
            const k = Math.floor(Math.random() * 16);
            lastSamples.pink[k] = white;
            return (lastSamples.pink.reduce((a, b) => a + b, 0) / 16) * 1.5;
        case "Brown":
            if (lastSamples.brown === undefined) lastSamples.brown = 0;
            lastSamples.brown = Math.max(-1, Math.min(1, lastSamples.brown + (0.02 * white)));
            return lastSamples.brown;
        case "Blue":
            if (lastSamples.lastWhite === undefined) lastSamples.lastWhite = 0;
            const blue = white - lastSamples.lastWhite;
            lastSamples.lastWhite = white;
            return blue * 0.5;
        case "Green":
            if (lastSamples.phase === undefined) lastSamples.phase = 0;
            lastSamples.phase += (2 * Math.PI * 500) / 44100;
            return white * Math.sin(lastSamples.phase);
        case "Violet":
            if (lastSamples.lastW === undefined) lastSamples.lastW = 0;
            if (lastSamples.lastB === undefined) lastSamples.lastB = 0;
            const b = white - lastSamples.lastW;
            const v = b - lastSamples.lastB;
            lastSamples.lastW = white; lastSamples.lastB = b;
            return v * 0.25;
        case "Grey":
             if (!lastSamples.pPool) lastSamples.pPool = new Array(16).fill(0);
             const j = Math.floor(Math.random() * 16);
             lastSamples.pPool[j] = white;
             const p = lastSamples.pPool.reduce((a, b) => a + b, 0) / 16;
             if (lastSamples.lGW === undefined) lastSamples.lGW = 0;
             const bl = white - lastSamples.lGW;
             lastSamples.lGW = white;
             return (p + bl) * 0.4;
        case "Azure":
            if (lastSamples.lW === undefined) lastSamples.lW = 0;
            if (lastSamples.lB === undefined) lastSamples.lB = 0;
            const b2 = white - lastSamples.lW;
            const az = b2 - lastSamples.lB;
            lastSamples.lW = white; lastSamples.lB = b2;
            return az * 0.3;
        case "Red":
            if (lastSamples.red === undefined) lastSamples.red = 0;
            lastSamples.red = Math.max(-1, Math.min(1, lastSamples.red + (0.05 * white)));
            return lastSamples.red;
        default: return white;
    }
};

const setupAudio = () => {
    audioContext = new (window.AudioContext || window.webkitAudioContext)();
    noiseNode = audioContext.createScriptProcessor(4096, 1, 1);
    const lastSamples = {};
    noiseNode.onaudioprocess = (e) => {
        const out = e.outputBuffer.getChannelData(0);
        for (let i = 0; i < 4096; i++) {
            out[i] = generateNoiseSample(currentNoiseType, lastSamples);
        }
    };
    noiseNode.connect(audioContext.destination);
};

// Timer Controls
const updateUI = () => {
    const label = document.getElementById('timer-label');
    if (remainingSeconds <= 0) {
        label.textContent = "∞";
    } else {
        const h = Math.floor(remainingSeconds / 3600);
        const m = Math.floor((remainingSeconds % 3600) / 60);
        const s = remainingSeconds % 60;
        label.textContent = `${h.toString().padStart(2,'0')}:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}`;
    }
};

const startCountdown = () => {
    if (countdownInterval) clearInterval(countdownInterval);
    countdownInterval = setInterval(() => {
        if (isPlaying && remainingSeconds > 0) {
            remainingSeconds--;
            updateUI();
            if (remainingSeconds === 0) stopPlayback();
        }
    }, 1000);
};

const stopCountdown = () => {
    if (countdownInterval) clearInterval(countdownInterval);
    countdownInterval = null;
};

const stopPlayback = () => {
    if (audioContext) audioContext.suspend();
    isPlaying = false;
    document.getElementById('play-icon').style.display = 'block';
    document.getElementById('pause-icon').style.display = 'none';
    stopCountdown();
};

const startPlayback = () => {
    if (!audioContext) setupAudio();
    audioContext.resume();
    isPlaying = true;
    document.getElementById('play-icon').style.display = 'none';
    document.getElementById('pause-icon').style.display = 'block';
    if (remainingSeconds > 0) startCountdown();
};

// Event Listeners
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.noise-item').forEach(el => {
        el.addEventListener('click', () => {
            currentNoiseType = el.getAttribute('data-noise');
            document.querySelectorAll('.noise-item').forEach(i => i.classList.remove('selected'));
            el.classList.add('selected');
        });
    });

    document.getElementById('play-btn').addEventListener('click', () => {
        if (isPlaying) stopPlayback(); else startPlayback();
    });

    document.getElementById('timer-slider').addEventListener('input', (e) => {
        const val = parseInt(e.target.value);
        if (val === 0) {
            remainingSeconds = 0;
            stopCountdown();
        } else {
            remainingSeconds = val * 900; // 15 min steps
            if (isPlaying) startCountdown();
        }
        updateUI();
    });
});
