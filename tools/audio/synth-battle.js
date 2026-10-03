// Gera a trilha de combate (normal e intensa) e o ambiente do mar por síntese —
// sem samples nem dependências: tudo sai de osciladores, ruído e filtros simples.
//
//   node tools/audio/synth-battle.js <pasta-de-saida>
//
// Saída: music_battle.wav, music_battle_intense.wav, ambient_sea.wav (mono, 44,1 kHz,
// 16 bits). As faixas de combate fecham o laço em 16 compassos exatos a 72 bpm, e o
// que sobra de cauda depois do fim é somado de volta ao começo — o laço não tem emenda.
// Converter depois com ffmpeg (.ogg para o Android, .m4a para o iOS; ver docs/BUILD.md).

const fs = require("fs");
const path = require("path");

const SR = 44100;
const BPM = 72;
const BEAT = 60 / BPM;
const BAR = BEAT * 4;
const BARS = 16;
const LOOP = BAR * BARS;

// ------------------------------------------------------------------ utilidades

function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

const midi = (n) => 440 * Math.pow(2, (n - 69) / 12);

/** Buffer com cauda: escreve em [0, len + tail) e depois dobra a cauda sobre o começo. */
function makeBuffer(seconds, tailSeconds) {
  return { len: Math.round(seconds * SR), data: new Float32Array(Math.round((seconds + tailSeconds) * SR)) };
}

function foldTail(buf) {
  const out = new Float32Array(buf.len);
  for (let i = 0; i < buf.data.length; i++) out[i % buf.len] += buf.data[i];
  return out;
}

function normalize(x, peakDb) {
  let peak = 0;
  for (const v of x) peak = Math.max(peak, Math.abs(v));
  const target = Math.pow(10, peakDb / 20);
  const g = peak > 0 ? target / peak : 1;
  for (let i = 0; i < x.length; i++) x[i] *= g;
  return x;
}

/** Saturação suave — cola as camadas e segura os picos dos tambores. */
function softClip(x, drive) {
  for (let i = 0; i < x.length; i++) x[i] = Math.tanh(x[i] * drive) / Math.tanh(drive);
  return x;
}

function writeWav(file, x) {
  const n = x.length;
  const b = Buffer.alloc(44 + n * 2);
  b.write("RIFF", 0);
  b.writeUInt32LE(36 + n * 2, 4);
  b.write("WAVE", 8);
  b.write("fmt ", 12);
  b.writeUInt32LE(16, 16);
  b.writeUInt16LE(1, 20);
  b.writeUInt16LE(1, 22);
  b.writeUInt32LE(SR, 24);
  b.writeUInt32LE(SR * 2, 28);
  b.writeUInt16LE(2, 32);
  b.writeUInt16LE(16, 34);
  b.write("data", 36);
  b.writeUInt32LE(n * 2, 40);
  for (let i = 0; i < n; i++) {
    const v = Math.max(-1, Math.min(1, x[i]));
    b.writeInt16LE(Math.round(v * 32767), 44 + i * 2);
  }
  fs.writeFileSync(file, b);
}

/** Passa-baixa de um polo; [cut] em Hz pode variar por amostra. */
function lowpass() {
  let y = 0;
  return (x, cut) => {
    const a = 1 - Math.exp((-2 * Math.PI * cut) / SR);
    y += a * (x - y);
    return y;
  };
}

/** Passa-faixa ressonante (biquad RBJ), para o ranger do casco. */
function bandpass(freq, q) {
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  return (x, f = freq) => {
    const w = (2 * Math.PI * f) / SR;
    const alpha = Math.sin(w) / (2 * q);
    const a0 = 1 + alpha;
    const b0 = alpha / a0, b2 = -alpha / a0;
    const a1 = (-2 * Math.cos(w)) / a0, a2 = (1 - alpha) / a0;
    const y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1; x1 = x; y2 = y1; y1 = y;
    return y;
  };
}

// ------------------------------------------------------------------ harmonia (ré menor)

// um acorde por 4 compassos: Dm, Bb, Gm, A — a dominante maior puxa de volta ao começo
const CHORDS = [
  { root: 38, notes: [50, 53, 57] }, // Dm
  { root: 34, notes: [46, 50, 53] }, // Bb
  { root: 31, notes: [43, 46, 50] }, // Gm
  { root: 33, notes: [45, 49, 52] }, // A
];
const chordAtBar = (bar) => CHORDS[Math.floor(bar / 4) % 4];

// ostinato em colcheias, em semitons acima da fundamental do acorde
const OSTINATO = [0, 0, 12, 0, 3, 0, 12, 1];

// ------------------------------------------------------------------ vozes

/** Dente-de-serra desafinado em três, filtrado — o naipe de cordas graves e o pad. */
function addSaw(buf, start, dur, freq, amp, cutoff, attack, release, detune = 0.006) {
  const s0 = Math.round(start * SR);
  const n = Math.round((dur + release) * SR);
  const lp1 = lowpass(), lp2 = lowpass();
  const phases = [0, 0.33, 0.66];
  const ratios = [1 - detune, 1, 1 + detune];
  for (let i = 0; i < n && s0 + i < buf.data.length; i++) {
    const t = i / SR;
    let env = t < attack ? t / attack : 1;
    if (t > dur) env *= Math.max(0, 1 - (t - dur) / release);
    let v = 0;
    for (let k = 0; k < 3; k++) {
      phases[k] += (freq * ratios[k]) / SR;
      phases[k] -= Math.floor(phases[k]);
      v += 2 * phases[k] - 1;
    }
    const c = typeof cutoff === "function" ? cutoff(t) : cutoff;
    buf.data[s0 + i] += lp2(lp1(v / 3, c), c) * amp * env;
  }
}

/** Nota curta de ostinato: ataque rápido, queda exponencial. */
function addPluck(buf, start, freq, amp, decay, cutoff) {
  const s0 = Math.round(start * SR);
  const n = Math.round(decay * 5 * SR);
  const lp1 = lowpass(), lp2 = lowpass();
  let p1 = 0, p2 = 0.5;
  for (let i = 0; i < n && s0 + i < buf.data.length; i++) {
    const t = i / SR;
    const env = Math.min(1, t / 0.006) * Math.exp(-t / decay);
    p1 += freq / SR; p1 -= Math.floor(p1);
    p2 += (freq * 1.004) / SR; p2 -= Math.floor(p2);
    const v = (2 * p1 - 1 + 2 * p2 - 1) / 2;
    const c = cutoff * (0.35 + 0.65 * Math.exp(-t / (decay * 0.6)));
    buf.data[s0 + i] += lp2(lp1(v, c), c) * amp * env;
  }
}

/** Tambor grave (taiko): seno com queda de afinação mais um estalo de ruído. */
function addDrum(buf, start, f0, f1, amp, decay, rand, noiseAmt = 0.35) {
  const s0 = Math.round(start * SR);
  const n = Math.round(decay * 6 * SR);
  const lp = lowpass();
  let ph = 0;
  for (let i = 0; i < n && s0 + i < buf.data.length; i++) {
    const t = i / SR;
    const f = f1 + (f0 - f1) * Math.exp(-t / 0.045);
    ph += f / SR;
    const body = Math.sin(2 * Math.PI * ph) * Math.exp(-t / decay);
    const noise = lp(rand() * 2 - 1, 900) * Math.exp(-t / 0.03) * noiseAmt * 3;
    buf.data[s0 + i] += (body + noise) * amp;
  }
}

/** Caixa abafada: ruído em faixa média com queda curta. */
function addSnare(buf, start, amp, rand) {
  const s0 = Math.round(start * SR);
  const n = Math.round(0.35 * SR);
  const bp = bandpass(1800, 0.8);
  for (let i = 0; i < n && s0 + i < buf.data.length; i++) {
    const t = i / SR;
    const tone = Math.sin(2 * Math.PI * 190 * t) * Math.exp(-t / 0.05) * 0.5;
    buf.data[s0 + i] += (bp(rand() * 2 - 1) * 2.2 + tone) * Math.exp(-t / 0.08) * amp;
  }
}

/** Zumbido grave contínuo, respirando a cada 2 compassos. */
function addDrone(buf, amp) {
  let p1 = 0, p2 = 0;
  for (let i = 0; i < buf.len; i++) {
    const t = i / SR;
    p1 += midi(26) / SR;
    p2 += midi(38) / SR;
    const breathe = 0.75 + 0.25 * Math.sin((2 * Math.PI * t) / (BAR * 2));
    buf.data[i] += (Math.sin(2 * Math.PI * p1) * 0.7 + Math.sin(2 * Math.PI * p2) * 0.3) * amp * breathe;
  }
}

// ------------------------------------------------------------------ faixas

function battle(intense) {
  const buf = makeBuffer(LOOP, 4);
  const rand = rng(intense ? 7 : 3);

  addDrone(buf, intense ? 0.16 : 0.2);

  // pad: um acorde por 4 compassos, abrindo o filtro devagar
  for (let seg = 0; seg < 4; seg++) {
    const chord = CHORDS[seg];
    const start = seg * 4 * BAR;
    for (const note of chord.notes) {
      addSaw(buf, start, 4 * BAR - 0.4, midi(note), intense ? 0.09 : 0.1,
        (t) => (intense ? 900 : 520) + 380 * Math.sin(Math.min(1, t / (4 * BAR)) * Math.PI),
        1.6, 1.8);
    }
  }

  // ostinato de cordas graves: colcheias no normal, semicolcheias no intenso
  const step = intense ? BEAT / 4 : BEAT / 2;
  const steps = Math.round(LOOP / step);
  for (let k = 0; k < steps; k++) {
    const t = k * step;
    const bar = Math.floor(t / BAR);
    const chord = chordAtBar(bar);
    const offset = OSTINATO[k % OSTINATO.length];
    const accent = k % (intense ? 4 : 2) === 0 ? 1 : 0.7;
    addPluck(buf, t, midi(chord.root + 12 + offset), (intense ? 0.15 : 0.17) * accent, intense ? 0.11 : 0.16, intense ? 1500 : 1100);
  }

  // tambores: esparsos no normal, marcando todo tempo no intenso
  for (let bar = 0; bar < BARS; bar++) {
    const t0 = bar * BAR;
    if (bar % 4 === 0) addDrum(buf, t0, 70, 38, 0.75, 0.55, rand, 0.25);
    addDrum(buf, t0, 120, 62, 0.5, 0.3, rand);
    addDrum(buf, t0 + BEAT * 2.5, 120, 62, 0.38, 0.28, rand);
    if (bar % 4 === 3) {
      [3, 3.5, 3.75].forEach((b, j) => addDrum(buf, t0 + BEAT * b, 140, 70, 0.3 + j * 0.08, 0.22, rand));
    }
    if (intense) {
      addDrum(buf, t0 + BEAT, 120, 62, 0.34, 0.25, rand);
      addDrum(buf, t0 + BEAT * 3, 120, 62, 0.34, 0.25, rand);
      addSnare(buf, t0 + BEAT, 0.16, rand);
      addSnare(buf, t0 + BEAT * 3, 0.16, rand);
    }
  }

  // intenso: cordas agudas em trêmulo na quinta do acorde, a tensão que sobe
  if (intense) {
    for (let seg = 0; seg < 4; seg++) {
      const chord = CHORDS[seg];
      const start = seg * 4 * BAR;
      const s0 = Math.round(start * SR);
      const tmp = makeBuffer(4 * BAR, 2);
      addSaw(tmp, 0, 4 * BAR - 0.3, midi(chord.notes[2] + 12), 0.07, 2200, 0.8, 1.2, 0.004);
      addSaw(tmp, 0, 4 * BAR - 0.3, midi(chord.notes[0] + 12), 0.05, 2200, 0.8, 1.2, 0.004);
      for (let i = 0; i < tmp.data.length && s0 + i < buf.data.length; i++) {
        const trem = 0.55 + 0.45 * Math.sin((2 * Math.PI * i) / SR / (BEAT / 4));
        buf.data[s0 + i] += tmp.data[i] * trem;
      }
    }
  }

  return normalize(softClip(foldTail(buf), 1.4), -1.5);
}

function ambient() {
  const SEC = 40;
  const buf = makeBuffer(SEC, 4);
  const rand = rng(11);

  // ondas: ruído filtrado com duas ondulações de período que divide o laço
  const lpA = lowpass(), lpB = lowpass(), lpC = lowpass();
  for (let i = 0; i < buf.len; i++) {
    const t = i / SR;
    const swellA = Math.pow(0.5 + 0.5 * Math.sin((2 * Math.PI * t) / 8), 2.2);
    const swellB = Math.pow(0.5 + 0.5 * Math.sin((2 * Math.PI * t) / (40 / 7) + 1.3), 2.6);
    const n = rand() * 2 - 1;
    const low = lpB(lpA(n, 380 + 520 * swellA), 380 + 520 * swellA);
    const hiss = lpC(n, 2400) - low;
    buf.data[i] += low * (0.25 + 0.75 * swellA) * 1.6 + hiss * swellB * 0.18;
  }

  // casco rangendo: trem de pulsos irregular passando por um filtro ressonante
  for (const at of [5.5, 18.2, 29.7]) {
    const s0 = Math.round(at * SR);
    const dur = 1.4;
    const bp = bandpass(170, 9);
    let ph = 0;
    for (let i = 0; i < dur * SR; i++) {
      const t = i / SR;
      const env = Math.sin(Math.PI * Math.min(1, t / dur)) ** 2;
      ph += (38 + 22 * Math.sin(t * 7.3) + rand() * 8) / SR;
      const pulse = ph % 1 < 0.08 ? 1 : 0;
      buf.data[s0 + i] += bp(pulse, 150 + 40 * (1 - t / dur)) * env * 0.9;
    }
  }

  // ping de sonar ocasional, com dois ecos
  for (const [delay, gain] of [[0, 1], [0.48, 0.32], [0.96, 0.11]]) {
    const s0 = Math.round((22 + delay) * SR);
    const lp = lowpass();
    for (let i = 0; i < 2.2 * SR; i++) {
      const t = i / SR;
      const env = Math.min(1, t / 0.004) * Math.exp(-t / 0.55);
      const v = Math.sin(2 * Math.PI * 1180 * t) + 0.25 * Math.sin(2 * Math.PI * 2360 * t);
      buf.data[s0 + i] += lp(v, 3000) * env * 0.12 * gain;
    }
  }

  return normalize(foldTail(buf), -3);
}

// ------------------------------------------------------------------ saída

const out = process.argv[2] || ".";
fs.mkdirSync(out, { recursive: true });
writeWav(path.join(out, "music_battle.wav"), battle(false));
writeWav(path.join(out, "music_battle_intense.wav"), battle(true));
writeWav(path.join(out, "ambient_sea.wav"), ambient());
console.log(`laço de combate: ${LOOP.toFixed(2)} s · ambiente: 40 s`);
