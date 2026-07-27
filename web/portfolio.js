/* ==========================================================================
   PORTFOLIO LOGIC & SCROLL-DRIVEN PROCEDURAL SYNTH SOUNDTRACK ENGINE
   ========================================================================== */

// --- Global App State & Timings ---
const PRESENTATION_DURATION = 60; // 60 seconds total duration for auto mode
let currentTime = 0;
let timelineInterval = null;
let currentSlide = 1;
let isScrollMode = false;
let currentUtterance = null;

// Scroll physics variables
let lastScrollTop = 0;
let lastScrollTime = Date.now();
let scrollSpeed = 0;

// Slides transitions timestamps (seconds)
const slideTimings = [
  { slide: 1, start: 0,  end: 10, marker: 'INTRO',       desc: 'ESTABLISHING SECURE PROFILE SCAN...' },
  { slide: 2, start: 10, end: 20, marker: 'SKILLS',      desc: 'RETRIEVING INTELLECTUAL CAPABILITIES...' },
  { slide: 3, start: 20, end: 30, marker: 'INTERNSHIPS',  desc: 'EXTRACTING TACTICAL FIELD RECORDS...' },
  { slide: 4, start: 30, end: 40, marker: 'HONOURS',      desc: 'DECRYPTING AWARDS & CERTIFICATIONS...' },
  { slide: 5, start: 40, end: 50, marker: 'PROJECTS',    desc: 'COMPILING ACTIVE CODE BASES...' },
  { slide: 6, start: 50, end: 60, marker: 'COMPILATION',  desc: 'FINALIZING COMPILATION & EMBLEM SHIELD...' }
];

// --- Web Audio Synth Variables ---
let audioCtx = null;
let masterGain = null;
let analyser = null;
let synthInterval = null;
let droneOscs = [];
let droneFilter = null;
let currentStep = 0;

// Musical scale for arpeggiator (C minor pentatonic: C, Eb, F, G, Bb)
const arpeggioNotes = [
  130.81, 155.56, 174.61, 196.00, 233.08, // Octave 3 (C3-Bb3)
  261.63, 311.13, 349.23, 392.00, 466.16  // Octave 4 (C4-Bb4)
];
const arpPattern = [0, 2, 4, 3, 7, 5, 8, 6, 9, 7, 5, 3];

// --- Initialization ---
window.addEventListener('DOMContentLoaded', () => {
  initClock();
  initCanvas();
  initVisualizerUI();
  
  // Launch Button Auto Mode
  document.getElementById('launch-auto-btn').addEventListener('click', () => {
    launchCinematicExperience('auto');
  });

  // Launch Button Scroll Mode
  document.getElementById('launch-scroll-btn').addEventListener('click', () => {
    launchCinematicExperience('scroll');
  });

  // Floating Controller Hooks
  document.getElementById('hud-toggle-mode').addEventListener('click', () => {
    toggleOperationalState();
  });
  document.getElementById('hud-system-reboot').addEventListener('click', () => {
    location.reload();
  });
});

// Real-time HUD clock updating
function initClock() {
  const clockEl = document.getElementById('hud-clock');
  setInterval(() => {
    const d = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    clockEl.textContent = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  }, 1000);
}

// --- Dynamic Canvas Neural Network Particles Background ---
let particles = [];
function initCanvas() {
  const canvas = document.getElementById('bg-canvas');
  const ctx = canvas.getContext('2d');
  
  let width = canvas.width = window.innerWidth;
  let height = canvas.height = window.innerHeight;
  
  window.addEventListener('resize', () => {
    width = canvas.width = window.innerWidth;
    height = canvas.height = window.innerHeight;
  });

  // Particle Class with Warp Physics
  class Particle {
    constructor() {
      this.x = Math.random() * width;
      this.y = Math.random() * height;
      this.baseVx = (Math.random() - 0.5) * 0.4;
      this.baseVy = (Math.random() - 0.5) * 0.4;
      this.vx = this.baseVx;
      this.vy = this.baseVy;
      this.radius = Math.random() * 2 + 1;
    }

    update() {
      // Apply warp multiplier when scrolling quickly
      const multiplier = 1 + scrollSpeed * 0.15;
      this.vx = this.baseVx * multiplier;
      this.vy = this.baseVy * multiplier;

      this.x += this.vx;
      this.y += this.vy;

      if (this.x < 0 || this.x > width) this.baseVx *= -1;
      if (this.y < 0 || this.y > height) this.baseVy *= -1;
    }

    draw() {
      ctx.beginPath();
      ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(0, 229, 255, 0.4)';
      ctx.fill();
    }
  }

  // Generate 60 particles
  particles = Array.from({ length: 60 }, () => new Particle());

  // Render loop
  function animate() {
    ctx.clearRect(0, 0, width, height);
    
    // Slow damp scroll speed down to 0
    if (scrollSpeed > 0.1) {
      scrollSpeed *= 0.94;
    } else {
      scrollSpeed = 0;
    }

    // Dynamic line connection limit based on scroll warp
    const lineLimit = 120 + scrollSpeed * 1.5;
    
    // Draw connections
    for (let i = 0; i < particles.length; i++) {
      particles[i].update();
      particles[i].draw();

      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x;
        const dy = particles[i].y - particles[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < lineLimit) {
          ctx.beginPath();
          ctx.moveTo(particles[i].x, particles[i].y);
          ctx.lineTo(particles[j].x, particles[j].y);
          
          // Color transitions to pink/purple warp streams when moving fast
          const opacity = 0.15 * (1 - dist / lineLimit);
          if (scrollSpeed > 5) {
            ctx.strokeStyle = `rgba(255, 0, 127, ${opacity * 1.5})`;
            ctx.lineWidth = 1.2;
          } else {
            ctx.strokeStyle = `rgba(189, 0, 255, ${opacity})`;
            ctx.lineWidth = 0.8;
          }
          ctx.stroke();
        }
      }
    }
    requestAnimationFrame(animate);
  }
  
  animate();
}

// --- Audio Visualizer UI Setup ---
function initVisualizerUI() {
  const container = document.getElementById('audio-visualizer');
  container.innerHTML = '';
  const barCount = 24;
  for (let i = 0; i < barCount; i++) {
    const bar = document.createElement('div');
    bar.className = 'visualizer-bar';
    container.appendChild(bar);
  }
}

// Update UI visualizer heights based on Web Audio Analyser frequency data
function updateVisualizerAnimation() {
  if (!analyser) return;

  const dataArray = new Uint8Array(analyser.frequencyBinCount);
  analyser.getByteFrequencyData(dataArray);

  const bars = document.querySelectorAll('.visualizer-bar');
  bars.forEach((bar, index) => {
    const dataIdx = Math.floor((index / bars.length) * dataArray.length);
    let value = dataArray[dataIdx];
    
    // Add fake bounce in scroll mode if not playing beats
    if (isScrollMode && value < 10) {
      value = 10 + Math.sin(Date.now() * 0.01 + index) * 30 + Math.random() * 10;
    }
    
    const heightPercent = Math.max(10, Math.min(100, (value / 255) * 100));
    bar.style.height = `${heightPercent}%`;
  });

  requestAnimationFrame(updateVisualizerAnimation);
}

// --- Cinematic Timeline Controller ---
function launchCinematicExperience(mode) {
  // Go fullscreen if supported
  const docEl = document.documentElement;
  if (docEl.requestFullscreen) {
    docEl.requestFullscreen().catch(() => { /* Fullscreen block catch */ });
  }

  // Set operational mode
  isScrollMode = (mode === 'scroll');

  // Start synthesis engines
  initSynthEngine();
  
  // Play robotic welcome voice greeting
  speakIntro();

  // Switch views
  document.getElementById('start-overlay').classList.add('hidden');
  document.getElementById('presentation').classList.remove('hidden');
  document.getElementById('floating-hud-controls').classList.remove('hidden');

  if (isScrollMode) {
    setupScrollMode();
  } else {
    setupAutoMode();
  }
}

// Setup Auto Mode Timings
function setupAutoMode() {
  currentTime = 0;
  currentSlide = 1;
  const presentationEl = document.getElementById('presentation');
  presentationEl.classList.remove('scroll-mode');
  
  // Make sure only slide 1 is active initially
  document.querySelectorAll('.slide').forEach((s, idx) => {
    s.classList.toggle('active', idx === 0);
  });

  updateTimelineUI();
  
  timelineInterval = setInterval(() => {
    currentTime += 0.1;
    if (currentTime >= PRESENTATION_DURATION) {
      currentTime = PRESENTATION_DURATION;
      clearInterval(timelineInterval);
      stopSynthEngine();
      showLoopOrEnd();
    }
    updateTimelineUI();
    checkSlideTransitions();
  }, 100);
}

// Setup Scroll Snapping & Observers
function setupScrollMode() {
  if (timelineInterval) clearInterval(timelineInterval);
  
  const presentationEl = document.getElementById('presentation');
  presentationEl.classList.add('scroll-mode');
  
  // Make all slides ready
  document.querySelectorAll('.slide').forEach(s => {
    s.classList.remove('active');
  });
  
  // Activate slide 1 initially
  document.getElementById('slide-1').classList.add('active');
  triggerSlideTypewriter(1);

  // Monitor Scrolling Events
  presentationEl.addEventListener('scroll', handlePortfolioScroll);
}

// Handle scroll tracking & velocity-based warp effects
function handlePortfolioScroll(e) {
  const container = e.target;
  const scrollTop = container.scrollTop;
  const scrollHeight = container.scrollHeight - container.clientHeight;
  
  // 1. Calculate scroll speed/velocity
  const now = Date.now();
  const timeDiff = now - lastScrollTime;
  if (timeDiff > 10) {
    const distDiff = Math.abs(scrollTop - lastScrollTop);
    scrollSpeed = Math.min(60, (distDiff / timeDiff) * 80); // Cap speed multiplier
    lastScrollTop = scrollTop;
    lastScrollTime = now;
  }

  // 2. Update Timeline HUD progress percentage
  const percentage = (scrollTop / scrollHeight) * 100;
  document.getElementById('timeline-fill').style.width = `${percentage}%`;
  document.getElementById('timeline-percentage').textContent = `DB_DEPTH: ${percentage.toFixed(0)}%`;

  // 3. Modulate CGPA hologram rings based on scroll position
  const hologram = document.querySelector('.cgpa-hologram');
  if (hologram) {
    hologram.style.transform = `rotateY(${scrollTop * 0.05}deg) rotateX(${scrollTop * 0.02}deg)`;
  }

  // 4. Detect which slide is currently in center of viewport
  const slides = document.querySelectorAll('.slide');
  const viewportHeight = window.innerHeight;
  let activeIndex = 0;
  let minDiff = Infinity;

  slides.forEach((slide, index) => {
    const rect = slide.getBoundingClientRect();
    const centerDiff = Math.abs(rect.top); // rect.top is relative to viewport
    if (centerDiff < minDiff) {
      minDiff = centerDiff;
      activeIndex = index;
    }
  });

  const targetSlideNum = activeIndex + 1;
  if (targetSlideNum !== currentSlide) {
    slides.forEach((s, idx) => {
      s.classList.toggle('active', idx === activeIndex);
    });

    currentSlide = targetSlideNum;
    
    // Update timeline markers
    const markers = document.querySelectorAll('.hud-timeline-markers .marker');
    markers.forEach((marker, idx) => {
      marker.classList.toggle('active', idx === activeIndex);
    });

    // Update log indicators & type content
    const currentTiming = slideTimings[activeIndex];
    document.getElementById('timeline-activity').textContent = currentTiming.desc;
    triggerSlideTypewriter(targetSlideNum);
  }
}

// Trigger smooth typewriter reveal logs for each section
function triggerSlideTypewriter(slideNum) {
  const activeSlide = document.getElementById(`slide-${slideNum}`);
  if (!activeSlide) return;

  const logsContainer = activeSlide.querySelector('.hud-log, .terminal-footer');
  if (!logsContainer) return;

  // Cache original inner HTML if not cached yet
  if (!logsContainer.dataset.originalContent) {
    logsContainer.dataset.originalContent = logsContainer.innerHTML;
  }

  // Parse lines
  const originalHTML = logsContainer.dataset.originalContent;
  const lines = originalHTML.split('</span>').map(l => l.replace(/<span[^>]*>/g, '').trim()).filter(l => l !== '');
  
  logsContainer.innerHTML = '';
  
  // Typewriter loops
  lines.forEach((line, idx) => {
    const span = document.createElement('span');
    span.className = 'log-line';
    span.style.display = 'block';
    span.textContent = '';
    logsContainer.appendChild(span);

    setTimeout(() => {
      let charIdx = 0;
      const interval = setInterval(() => {
        span.textContent += line[charIdx];
        charIdx++;
        if (charIdx >= line.length) {
          clearInterval(interval);
        }
      }, 20);
    }, idx * 600);
  });
}

function updateTimelineUI() {
  const percentage = (currentTime / PRESENTATION_DURATION) * 100;
  document.getElementById('timeline-fill').style.width = `${percentage}%`;
  
  const timeRemaining = Math.max(0, PRESENTATION_DURATION - currentTime).toFixed(1);
  document.getElementById('timeline-percentage').textContent = `T-MINUS ${timeRemaining}s`;

  // Update visual markers
  const markers = document.querySelectorAll('.hud-timeline-markers .marker');
  markers.forEach(marker => {
    const targetTime = parseFloat(marker.getAttribute('data-time'));
    if (currentTime >= targetTime) {
      marker.classList.add('active');
    } else {
      marker.classList.remove('active');
    }
  });
}

function checkSlideTransitions() {
  const currentTiming = slideTimings.find(t => currentTime >= t.start && currentTime < t.end);
  if (currentTiming && currentTiming.slide !== currentSlide) {
    const prevSlideEl = document.getElementById(`slide-${currentSlide}`);
    const nextSlideEl = document.getElementById(`slide-${currentTiming.slide}`);
    
    if (prevSlideEl) prevSlideEl.classList.remove('active');
    if (nextSlideEl) nextSlideEl.classList.add('active');
    
    currentSlide = currentTiming.slide;
    document.getElementById('timeline-activity').textContent = currentTiming.desc;
    triggerSlideTypewriter(currentSlide);
  }
}

function showLoopOrEnd() {
  document.getElementById('timeline-activity').textContent = 'CINEMATIC SEQUENCE COMPLETE. SYSTEM SECURE.';
}

// Switch operational state between scroll snap and auto playback on the fly
function toggleOperationalState() {
  const presentationEl = document.getElementById('presentation');
  
  if (isScrollMode) {
    // Switch to Auto Mode
    presentationEl.classList.remove('scroll-mode');
    presentationEl.scrollTop = 0;
    
    // Clean listener
    presentationEl.removeEventListener('scroll', handlePortfolioScroll);
    
    // Initialize auto variables & start loop
    isScrollMode = false;
    setupAutoMode();
  } else {
    // Switch to Scroll Mode
    clearInterval(timelineInterval);
    presentationEl.classList.add('scroll-mode');
    isScrollMode = true;
    setupScrollMode();
  }
}

// --- Epic Cinematic Chord Progression (Cm -> Ab -> Eb -> Gm) ---
const chordsList = [
  { name: 'Cm',  freqs: [130.81, 155.56, 196.00], root: 65.41,  arp: [261.63, 311.13, 392.00, 523.25, 587.33] },
  { name: 'Ab',  freqs: [103.83, 130.81, 155.56], root: 51.91,  arp: [207.65, 261.63, 311.13, 415.30, 523.25] },
  { name: 'Eb',  freqs: [155.56, 196.00, 233.08], root: 77.78,  arp: [311.13, 392.00, 466.16, 622.25, 783.99] },
  { name: 'Gm',  freqs: [98.00, 116.54, 146.83], root: 49.00,  arp: [196.00, 233.08, 293.66, 392.00, 466.16] }
];
let activePadOscs = [];
let delayNode = null;
let delayFeedback = null;

// --- Procedural Synth Engine (Web Audio API) ---
function initSynthEngine() {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    audioCtx = new AudioContextClass();
    
    masterGain = audioCtx.createGain();
    masterGain.gain.setValueAtTime(0.26, audioCtx.currentTime);
    
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 64;
    
    masterGain.connect(analyser);
    analyser.connect(audioCtx.destination);
    
    // Initialize delay feedback echo
    initDelayEffect();
    
    document.getElementById('hud-audio-status').textContent = 'ONLINE';
    document.getElementById('hud-audio-status').style.color = 'var(--neon-green)';

    startDrone();
    startSequencer();
    
    updateVisualizerAnimation();
  } catch (e) {
    console.error('Audio initialization failed', e);
  }
}

// Visual delay feedback echo setup
function initDelayEffect() {
  delayNode = audioCtx.createDelay(1.0);
  delayNode.delayTime.setValueAtTime(0.35, audioCtx.currentTime); // 350ms delay timing
  
  delayFeedback = audioCtx.createGain();
  delayFeedback.gain.setValueAtTime(0.44, audioCtx.currentTime); // feedback percentage
  
  delayNode.connect(delayFeedback);
  delayFeedback.connect(delayNode);
  delayNode.connect(masterGain);
}

// Synth Bass drone (Deep technology pad background)
function startDrone() {
  droneFilter = audioCtx.createBiquadFilter();
  droneFilter.type = 'lowpass';
  droneFilter.frequency.setValueAtTime(90, audioCtx.currentTime);
  droneFilter.Q.setValueAtTime(4.0, audioCtx.currentTime);
  droneFilter.connect(masterGain);

  const droneNotes = [32.70, 49.00, 65.41]; // Sub-bass frequencies (C1, G1, C2)
  droneNotes.forEach(freq => {
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(freq, audioCtx.currentTime);
    gain.gain.setValueAtTime(0.08, audioCtx.currentTime); // solid sub weight
    
    osc.connect(gain);
    gain.connect(droneFilter);
    osc.start();
    
    droneOscs.push({ osc, gain });
  });
}

// Scheduler loop for rhythm and arpeggiator (120 BPM -> 0.20s per step)
function startSequencer() {
  synthInterval = setInterval(() => {
    if (!audioCtx) return;
    const now = audioCtx.currentTime;

    // Modulate filter sweep
    if (droneFilter) {
      const sweepFreq = 90 + Math.sin(now * 0.3) * 40 + (currentTime * 4);
      droneFilter.frequency.setValueAtTime(Math.min(450, sweepFreq), now);
    }

    // Determine current active chord from list
    const chordIndex = Math.floor((currentStep % 64) / 16);
    const activeChord = chordsList[chordIndex];

    // Trigger lush cinematic chords every 16 steps (3.2 seconds)
    if (!isScrollMode && currentStep % 16 === 0) {
      playPadChords(activeChord.freqs, now, 3.2);
    }

    if (!isScrollMode) {
      // 1. Pulsing Bass (plays low rhythmic 8th notes)
      if (currentStep % 2 === 0) {
        playBassPulse(activeChord.root, now);
      }

      // 2. Kick drum (starts at 10s, 4-on-the-floor driving beat)
      if (currentTime >= 10 && currentStep % 4 === 0) {
        playKickDrum(now);
      }
      
      // 3. Snare drum (starts at 20s, backbeat)
      if (currentTime >= 20 && currentStep % 8 === 4) {
        playSnareDrum(now);
      }

      // 4. Hi-hat (starts at 30s, offbeats)
      if (currentTime >= 30 && currentStep % 4 === 2) {
        playHiHat(now);
      }

      // 5. Arpeggiator pattern playing (starts at 15s)
      if (currentTime >= 15) {
        const stepTrigger = (currentTime >= 40) ? 2 : 4; // double tempo arpeggios in climax
        if (currentStep % stepTrigger === 0) {
          const arpNotes = activeChord.arp;
          const noteIdx = arpPattern[currentStep % arpPattern.length];
          const freq = arpNotes[noteIdx % arpNotes.length];
          
          if (currentTime >= 45 && currentTime < 55) {
            playArpNote(freq, now, 0.12);
            playArpNote(arpNotes[(noteIdx + 2) % arpNotes.length], now + 0.1, 0.08);
          } else {
            playArpNote(freq, now, 0.22);
          }
        }
      }
    } else {
      // SCROLL MODE AUDIO FEEDBACK: Synthesize plucky chord-locked responses when scrolling quickly
      if (scrollSpeed > 5 && currentStep % 2 === 0) {
        const arpNotes = activeChord.arp;
        const index = Math.floor(Math.random() * arpNotes.length);
        const freq = arpNotes[index];
        playArpNote(freq, now, 0.16);
      }
    }

    currentStep++;
  }, 200);
}

// Synthesizes a pulsing, cyberpunk bass note
function playBassPulse(freq, time) {
  const osc = audioCtx.createOscillator();
  const gain = audioCtx.createGain();
  const filter = audioCtx.createBiquadFilter();
  
  osc.type = 'sawtooth';
  osc.frequency.setValueAtTime(freq, time);
  
  filter.type = 'lowpass';
  filter.frequency.setValueAtTime(80, time);
  filter.frequency.exponentialRampToValueAtTime(150, time + 0.1);
  
  gain.gain.setValueAtTime(0.22, time);
  gain.gain.exponentialRampToValueAtTime(0.001, time + 0.15);
  
  osc.connect(filter);
  filter.connect(gain);
  gain.connect(masterGain);
  
  osc.start(time);
  osc.stop(time + 0.16);
}

// Synthesizes a lush, detuned pad chord
function playPadChords(freqs, time, duration) {
  stopPadChords();
  
  const padFilter = audioCtx.createBiquadFilter();
  padFilter.type = 'lowpass';
  padFilter.frequency.setValueAtTime(180, time);
  padFilter.frequency.exponentialRampToValueAtTime(550, time + duration - 0.2);
  padFilter.Q.setValueAtTime(2.0, time);
  padFilter.connect(masterGain);

  freqs.forEach(freq => {
    const osc1 = audioCtx.createOscillator();
    const osc2 = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();
    
    osc1.type = 'sawtooth';
    osc1.frequency.setValueAtTime(freq, time);
    
    osc2.type = 'sawtooth';
    osc2.frequency.setValueAtTime(freq * 1.006, time); // detune
    
    gainNode.gain.setValueAtTime(0.0, time);
    gainNode.gain.linearRampToValueAtTime(0.045, time + 0.8); 
    gainNode.gain.setValueAtTime(0.045, time + duration - 0.8);
    gainNode.gain.exponentialRampToValueAtTime(0.001, time + duration);

    osc1.connect(gainNode);
    osc2.connect(gainNode);
    gainNode.connect(padFilter);
    
    osc1.start(time);
    osc2.start(time);
    osc1.stop(time + duration + 0.1);
    osc2.stop(time + duration + 0.1);
    
    activePadOscs.push({ osc1, osc2, gainNode });
  });
}

function stopPadChords() {
  activePadOscs.forEach(p => {
    try {
      p.gainNode.gain.cancelScheduledValues(audioCtx.currentTime);
      p.gainNode.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.3);
      setTimeout(() => {
        p.osc1.stop();
        p.osc2.stop();
      }, 400);
    } catch(e) {}
  });
  activePadOscs = [];
}

// Synthesizes a punchy cyber kick drum
function playKickDrum(time) {
  const osc = audioCtx.createOscillator();
  const gain = audioCtx.createGain();
  
  osc.connect(gain);
  gain.connect(masterGain);
  
  osc.type = 'sine';
  osc.frequency.setValueAtTime(150, time);
  osc.frequency.exponentialRampToValueAtTime(40, time + 0.14);
  
  gain.gain.setValueAtTime(0.65, time);
  gain.gain.exponentialRampToValueAtTime(0.01, time + 0.18);
  
  osc.start(time);
  osc.stop(time + 0.20);
}

// Synthesizes an 80s gated snare drum (bandpass white noise + triangle punch)
function playSnareDrum(time) {
  const osc = audioCtx.createOscillator();
  const gain = audioCtx.createGain();
  const noise = audioCtx.createBufferSource();
  const noiseGain = audioCtx.createGain();
  const filter = audioCtx.createBiquadFilter();
  
  const bufferSize = audioCtx.sampleRate * 0.14;
  const buffer = audioCtx.createBuffer(1, bufferSize, audioCtx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < bufferSize; i++) {
    data[i] = Math.random() * 2 - 1;
  }
  noise.buffer = buffer;
  
  filter.type = 'bandpass';
  filter.frequency.setValueAtTime(1100, time);
  filter.frequency.exponentialRampToValueAtTime(200, time + 0.12);
  
  osc.type = 'triangle';
  osc.frequency.setValueAtTime(170, time);
  osc.frequency.linearRampToValueAtTime(100, time + 0.09);
  
  gain.gain.setValueAtTime(0.28, time);
  gain.gain.exponentialRampToValueAtTime(0.01, time + 0.1);
  
  noiseGain.gain.setValueAtTime(0.35, time);
  noiseGain.gain.exponentialRampToValueAtTime(0.01, time + 0.14);
  
  osc.connect(gain);
  gain.connect(masterGain);
  
  noise.connect(filter);
  filter.connect(noiseGain);
  noiseGain.connect(masterGain);
  
  // Route portion of snare tail to feedback delay loop to make it spacious!
  if (delayNode) {
    const snareSend = audioCtx.createGain();
    snareSend.gain.setValueAtTime(0.12, time);
    noiseGain.connect(snareSend);
    snareSend.connect(delayNode);
  }
  
  osc.start(time);
  noise.start(time);
  osc.stop(time + 0.11);
  noise.stop(time + 0.16);
}

// Synthesizes highpass hi-hats
function playHiHat(time) {
  const bufferSize = audioCtx.sampleRate * 0.05;
  const buffer = audioCtx.createBuffer(1, bufferSize, audioCtx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < bufferSize; i++) {
    data[i] = Math.random() * 2 - 1;
  }
  
  const noise = audioCtx.createBufferSource();
  noise.buffer = buffer;
  
  const filter = audioCtx.createBiquadFilter();
  filter.type = 'highpass';
  filter.frequency.setValueAtTime(9500, time);
  
  const gain = audioCtx.createGain();
  gain.gain.setValueAtTime(0.06, time);
  gain.gain.exponentialRampToValueAtTime(0.005, time + 0.05);
  
  noise.connect(filter);
  filter.connect(gain);
  gain.connect(masterGain);
  
  noise.start(time);
  noise.stop(time + 0.06);
}

// Synthesizes a snappy melodic arpeggiator note
function playArpNote(freq, time, duration) {
  const osc = audioCtx.createOscillator();
  const gain = audioCtx.createGain();
  const filter = audioCtx.createBiquadFilter();
  
  osc.connect(filter);
  filter.connect(gain);
  gain.connect(masterGain);
  
  if (delayNode) {
    const delaySend = audioCtx.createGain();
    delaySend.gain.setValueAtTime(0.16, time);
    gain.connect(delaySend);
    delaySend.connect(delayNode);
  }
  
  osc.type = 'triangle';
  osc.frequency.setValueAtTime(freq, time);
  
  filter.type = 'peaking';
  filter.frequency.setValueAtTime(freq * 2.5, time);
  filter.frequency.exponentialRampToValueAtTime(freq, time + duration);
  filter.Q.setValueAtTime(4.0, time);
  
  gain.gain.setValueAtTime(0.14, time);
  gain.gain.exponentialRampToValueAtTime(0.001, time + duration);
  
  osc.start(time);
  osc.stop(time + duration + 0.05);
}

// --- Stop & Clean Up Audio Context ---
function stopSynthEngine() {
  clearInterval(synthInterval);
  stopPadChords();
  
  if (masterGain) {
    masterGain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 1.0);
  }
  
  setTimeout(() => {
    droneOscs.forEach(d => {
      try { d.osc.stop(); } catch(e){}
    });
    droneOscs = [];
    if (audioCtx) {
      audioCtx.close();
      audioCtx = null;
    }
    delayNode = null;
    delayFeedback = null;
  }, 1100);

  document.getElementById('hud-audio-status').textContent = 'OFFLINE';
  document.getElementById('hud-audio-status').style.color = 'var(--text-secondary)';
}

// --- Text-to-Speech Voice Synthesis ---
function speakIntro() {
  if ('speechSynthesis' in window) {
    // Stop any ongoing speech
    window.speechSynthesis.cancel();

    const text = "I am Santhosh B, B.Tech Artificial Intelligence and Data Science Student with C.G.P.A 9.17. Welcome to my portfolio.";
    currentUtterance = new SpeechSynthesisUtterance(text);

    const speakAction = () => {
      const voices = window.speechSynthesis.getVoices();
      
      // Target a male English voice (David, George, Male, Mark, Guy, etc.)
      let chosenVoice = voices.find(v => (v.name.toLowerCase().includes('male') || 
                                          v.name.toLowerCase().includes('david') || 
                                          v.name.toLowerCase().includes('george') || 
                                          v.name.toLowerCase().includes('mark') || 
                                          v.name.toLowerCase().includes('guy')) && v.lang.startsWith('en')) ||
                        voices.find(v => v.lang.startsWith('en') && 
                                         !v.name.toLowerCase().includes('zira') && 
                                         !v.name.toLowerCase().includes('hazel') && 
                                         !v.name.toLowerCase().includes('haruka')) ||
                        voices.find(v => v.lang.startsWith('en')) ||
                        voices[0];
      
      if (chosenVoice) {
        currentUtterance.voice = chosenVoice;
      }
      
      currentUtterance.rate = 0.95;  // Slightly slower majestic pace
      currentUtterance.pitch = 0.82; // Deeper pitch for a masculine/AI HUD voice tone
      currentUtterance.volume = 1.0;  // Full volume
      
      // AUDIO DUCKING: Duck synth volume when voice starts speaking
      currentUtterance.onstart = () => {
        if (masterGain && audioCtx) {
          masterGain.gain.setValueAtTime(0.05, audioCtx.currentTime); // Duck music to 5%
        }
      };

      // Restore normal synth volume when voice completes
      currentUtterance.onend = currentUtterance.onerror = () => {
        if (masterGain && audioCtx) {
          masterGain.gain.linearRampToValueAtTime(0.28, audioCtx.currentTime + 0.6); // smooth sweep back to 28%
        }
      };

      window.speechSynthesis.speak(currentUtterance);
    };

    if (window.speechSynthesis.getVoices().length > 0) {
      speakAction();
    } else {
      window.speechSynthesis.onvoiceschanged = speakAction;
    }
  }
}

