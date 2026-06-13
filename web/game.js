'use strict';

// ══════════════════════════════════════════════════════════════════════
//  THEME DATA
// ══════════════════════════════════════════════════════════════════════

const THEMES = [
  { cat: 'FRUIT',     emoji: '🍎', en: 'apple',    enP: 'apples',    ko: '사과',    koP: '사과들',
    colors: ['#FF5252','#FF7043','#FFA726','#FFCA28'] },
  { cat: 'FRUIT',     emoji: '🍌', en: 'banana',   enP: 'bananas',   ko: '바나나',  koP: '바나나들',
    colors: ['#FFCA28','#FFA726','#FF7043','#FFD54F'] },
  { cat: 'FRUIT',     emoji: '🍇', en: 'grape',    enP: 'grapes',    ko: '포도',    koP: '포도들',
    colors: ['#AB47BC','#7B1FA2','#CE93D8','#E040FB'] },
  { cat: 'CAR',       emoji: '🚗', en: 'car',      enP: 'cars',      ko: '자동차',  koP: '자동차들',
    colors: ['#FF7043','#F48FB1','#FFCA28','#EF5350'] },
  { cat: 'CAR',       emoji: '🚌', en: 'bus',      enP: 'buses',     ko: '버스',    koP: '버스들',
    colors: ['#FFCA28','#FF7043','#FFA726','#FFD54F'] },
  { cat: 'VEGETABLE', emoji: '🥕', en: 'carrot',   enP: 'carrots',   ko: '당근',    koP: '당근들',
    colors: ['#FF7043','#FFA726','#FFCA28','#FF5722'] },
  { cat: 'VEGETABLE', emoji: '🥦', en: 'broccoli', enP: 'broccoli',  ko: '브로콜리', koP: '브로콜리들',
    colors: ['#66BB6A','#43A047','#A5D6A7','#2E7D32'] },
  { cat: 'VEGETABLE', emoji: '🍅', en: 'tomato',   enP: 'tomatoes',  ko: '토마토',  koP: '토마토들',
    colors: ['#EF5350','#F44336','#FF5252','#C62828'] },
];

// ══════════════════════════════════════════════════════════════════════
//  SETTINGS  (persisted in localStorage)
// ══════════════════════════════════════════════════════════════════════

const settings = (() => {
  try {
    const s = JSON.parse(localStorage.getItem('nc_settings') || '{}');
    return {
      maxNumber: s.maxNumber || 5,
      language:  s.language  || 'ko',
      mode:      s.mode      || 'OBJECTS_TO_NUMBER',
      categories: new Set(Array.isArray(s.categories) ? s.categories : ['FRUIT','CAR','VEGETABLE']),
      bgmEnabled: s.bgmEnabled ?? true,
      bgmVolume:  typeof s.bgmVolume === 'number' ? s.bgmVolume : 0.12,
    };
  } catch {
    return { maxNumber:5, language:'ko', mode:'OBJECTS_TO_NUMBER',
             categories:new Set(['FRUIT','CAR','VEGETABLE']), bgmEnabled:true, bgmVolume:0.12 };
  }
})();

function saveSettings() {
  try {
    localStorage.setItem('nc_settings', JSON.stringify({
      ...settings,
      categories: [...settings.categories],
    }));
  } catch {}
}

// ══════════════════════════════════════════════════════════════════════
//  GAME STATE
// ══════════════════════════════════════════════════════════════════════

let game = null;  // { score, targetNumber, options[4], theme, mode }
let roundToken = 0;
let locked = false;

// ══════════════════════════════════════════════════════════════════════
//  UTILITY
// ══════════════════════════════════════════════════════════════════════

function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function koreanNumber(n) {
  const w = ['','일','이','삼','사','오','육','칠','팔','구','십'];
  if (n < 1) return '0';
  if (n < w.length) return w[n];
  if (n < 20) return '십' + w[n % 10];
  return String(n);
}

function englishNumber(n) {
  const w = ['zero','one','two','three','four','five','six','seven','eight','nine','ten',
    'eleven','twelve','thirteen','fourteen','fifteen','sixteen','seventeen','eighteen','nineteen','twenty'];
  return w[n] ?? String(n);
}

// ══════════════════════════════════════════════════════════════════════
//  AUDIO
// ══════════════════════════════════════════════════════════════════════

const bgmEl     = document.getElementById('bgm');
const chimeOk   = document.getElementById('chime-correct');
const chimeNg   = document.getElementById('chime-wrong');

function initAudio() {
  bgmEl.volume = settings.bgmVolume;
  if (settings.bgmEnabled) bgmEl.play().catch(() => {});
}

function pauseBgm()  { try { bgmEl.pause(); } catch {} }
function resumeBgm() {
  if (!settings.bgmEnabled) return;
  bgmEl.play().catch(() => {});
}

function setBgmEnabled(on) {
  settings.bgmEnabled = on;
  on ? bgmEl.play().catch(() => {}) : bgmEl.pause();
}

function setBgmVolume(v) {
  settings.bgmVolume = v;
  bgmEl.volume = v;
  if (v > 0 && settings.bgmEnabled && bgmEl.paused) bgmEl.play().catch(() => {});
}

function playChime(correct) {
  const el = correct ? chimeOk : chimeNg;
  try { el.currentTime = 0; el.play().catch(() => {}); } catch {}
}

// SpeechSynthesis wrapper: resolves when utterance ends (or after 8s safety timeout)
function speakAsync(text, lang, rate) {
  return new Promise(resolve => {
    if (!window.speechSynthesis) { resolve(); return; }
    window.speechSynthesis.cancel();
    const utt = new SpeechSynthesisUtterance(text);
    utt.lang  = lang === 'ko' ? 'ko-KR' : 'en-US';
    utt.rate  = rate ?? 0.85;
    let done  = false;
    const finish = () => { if (!done) { done = true; resolve(); } };
    utt.onend  = finish;
    utt.onerror = finish;
    window.speechSynthesis.speak(utt);
    setTimeout(finish, 8000);
  });
}

function stopTts() { try { window.speechSynthesis?.cancel(); } catch {} }

// ══════════════════════════════════════════════════════════════════════
//  ROUND GENERATION
// ══════════════════════════════════════════════════════════════════════

function pickTheme() {
  const pool = THEMES.filter(t => settings.categories.has(t.cat));
  const src  = pool.length ? pool : THEMES;
  return src[Math.floor(Math.random() * src.length)];
}

function generateOptions(target, maxNumber) {
  const safeMax = Math.max(maxNumber, 4);
  const pool    = Array.from({length: safeMax}, (_, i) => i + 1).filter(n => n !== target);
  shuffle(pool);
  const opts    = [target, ...pool.slice(0, 3)];
  const taken   = new Set(opts);
  const extras  = Array.from({length: safeMax}, (_, i) => i + 1).filter(n => !taken.has(n));
  shuffle(extras);
  extras.slice(0, 4 - opts.length).forEach(n => opts.push(n));
  let pad = safeMax + 1;
  while (opts.length < 4) opts.push(pad++);
  shuffle(opts);
  return opts.slice(0, 4);
}

function buildRound(prevScore = 0, prevTarget = null) {
  const theme  = pickTheme();
  let target;
  let attempts = 0;
  do {
    target = Math.floor(Math.random() * settings.maxNumber) + 1;
    attempts++;
  } while (settings.maxNumber > 1 && target === prevTarget && attempts < 50);
  return {
    score:        prevScore,
    targetNumber: target,
    options:      generateOptions(target, settings.maxNumber),
    theme,
    mode:         settings.mode,
  };
}

// ══════════════════════════════════════════════════════════════════════
//  DOM REFS
// ══════════════════════════════════════════════════════════════════════

const displayArea    = document.getElementById('display-area');
const optionsGrid    = document.getElementById('options-grid');
const feedbackOverlay= document.getElementById('feedback-overlay');
const feedbackImg    = document.getElementById('feedback-img');
const nextBtn        = document.getElementById('next-btn');
const hintPanel      = document.getElementById('hint-panel');
const hintItems      = document.getElementById('hint-items');
const hintLabel      = document.getElementById('hint-label');
const scoreDisplay   = document.getElementById('score-display');
const settingsModal  = document.getElementById('settings-modal');

// ══════════════════════════════════════════════════════════════════════
//  RENDERING
// ══════════════════════════════════════════════════════════════════════

function renderScore(score) {
  if (score <= 0) {
    scoreDisplay.textContent = '☆';
    scoreDisplay.style.color = '#bbb';
  } else if (score >= 10) {
    scoreDisplay.textContent = '★ ' + score;
    scoreDisplay.style.color = '#1565C0';
  } else {
    scoreDisplay.textContent = '★'.repeat(score);
    scoreDisplay.style.color = '#F57F17';
  }
}

function emojiFontSize(count) {
  if (count <=  3) return 'clamp(3rem,  14vw, 5.5rem)';
  if (count <=  6) return 'clamp(2.4rem,10vw, 4rem)';
  if (count <=  9) return 'clamp(1.9rem, 8vw, 3.2rem)';
  return 'clamp(1.5rem, 6.5vw, 2.6rem)';
}

function renderEmojiGrid(container, emoji, count, highlightCount = 0) {
  const grid = document.createElement('div');
  grid.className = 'emoji-grid';
  const fs = emojiFontSize(count);
  for (let i = 0; i < count; i++) {
    const cell = document.createElement('span');
    cell.className = 'emoji-cell';
    cell.style.fontSize = fs;
    if (highlightCount > 0) {
      cell.classList.add(i < highlightCount ? 'highlighted' : 'dim');
    }
    cell.textContent = emoji;
    grid.appendChild(cell);
  }
  container.appendChild(grid);
}

function renderDisplay(g, highlightCount = 0) {
  displayArea.innerHTML = '';
  if (g.mode === 'OBJECTS_TO_NUMBER') {
    renderEmojiGrid(displayArea, g.theme.emoji, g.targetNumber, highlightCount);
  } else {
    const span = document.createElement('span');
    span.className = 'big-number';
    span.textContent = g.targetNumber;
    displayArea.appendChild(span);
  }
}

// emoji font size inside option buttons
function optEmojiSize(count) {
  if (count <= 3) return '1.9rem';
  if (count <= 5) return '1.5rem';
  if (count <= 8) return '1.2rem';
  return '1rem';
}

function renderOptions(g, selectedIdx = -1, correctResult = null) {
  optionsGrid.innerHTML = '';
  g.options.forEach((opt, i) => {
    const btn = document.createElement('button');
    btn.className = 'option-btn';
    btn.style.borderColor = g.theme.colors[i % g.theme.colors.length];

    if (g.mode === 'OBJECTS_TO_NUMBER') {
      const num = document.createElement('span');
      num.className = 'opt-number';
      num.textContent = opt;
      btn.appendChild(num);
    } else {
      // NUMBER_TO_OBJECTS: show `opt` emoji items
      const fs = optEmojiSize(opt);
      for (let j = 0; j < opt; j++) {
        const sp = document.createElement('span');
        sp.className = 'opt-emoji';
        sp.style.fontSize = fs;
        sp.textContent = g.theme.emoji;
        btn.appendChild(sp);
      }
    }

    if (selectedIdx === i) {
      btn.classList.add(correctResult ? 'correct' : 'wrong');
      if (!correctResult) btn.classList.add('shake');
    }

    if (locked || selectedIdx >= 0) btn.disabled = true;

    btn.addEventListener('click', () => tapOption(i));
    optionsGrid.appendChild(btn);
  });
}

function renderGame(g) {
  renderDisplay(g);
  renderOptions(g);
  renderScore(g.score);
}

// ══════════════════════════════════════════════════════════════════════
//  GAME FLOW
// ══════════════════════════════════════════════════════════════════════

function startRound(prevScore = 0, prevTarget = null) {
  roundToken++;
  locked = false;
  stopTts();
  feedbackOverlay.hidden = true;
  hintPanel.hidden       = true;
  nextBtn.hidden         = true;

  game = buildRound(prevScore, prevTarget);
  renderGame(game);
  updateTopBar();
}

function tapOption(idx) {
  if (locked || !game) return;
  locked = true;

  const selected = game.options[idx];
  const correct  = selected === game.targetNumber;

  renderOptions(game, idx, correct);
  try { navigator.vibrate?.(correct ? [40, 80, 40] : [220]); } catch {}

  if (correct) handleCorrect();
  else         handleWrong(idx);
}

function handleCorrect() {
  const token        = roundToken;
  const pendingScore = game.score + 1;
  const lang         = settings.language;
  const ttsRate      = lang === 'ko' ? 0.8 : 0.85;

  playChime(true);
  pauseBgm();

  feedbackImg.src    = 'assets/praise.png';
  feedbackImg.onerror = () => { feedbackImg.src = ''; feedbackImg.style.fontSize = '7rem'; feedbackImg.alt = '🎉'; };
  feedbackImg.alt    = '';
  feedbackOverlay.hidden = false;
  nextBtn.hidden     = true;

  (async () => {
    await speakAsync(lang === 'ko' ? '그래 잘했다!' : "That's right!", lang, ttsRate);
    if (roundToken !== token) return;

    resumeBgm();
    nextBtn.textContent = lang === 'ko' ? '다음' : 'Next';
    nextBtn.hidden = false;
    nextBtn.onclick = () => {
      if (roundToken !== token) return;
      startRound(pendingScore, game.targetNumber);
    };
  })();
}

function handleWrong() {
  const token   = roundToken;
  const lang    = settings.language;
  const ttsRate = lang === 'ko' ? 0.8 : 0.85;

  playChime(false);
  pauseBgm();

  feedbackImg.src    = 'assets/wrong.jpg';
  feedbackImg.onerror = () => { feedbackImg.src = ''; feedbackImg.style.fontSize = '7rem'; feedbackImg.alt = '😔'; };
  feedbackImg.alt    = '';
  feedbackOverlay.hidden = false;
  nextBtn.hidden     = true;

  (async () => {
    await speakAsync(lang === 'ko' ? '틀렸어요.' : 'Not quite.', lang, ttsRate);
    if (roundToken !== token) return;

    feedbackOverlay.hidden = true;
    await sleep(1000);
    if (roundToken !== token) return;

    await runCountHint(token);
  })();
}

// Counting hint: name the item, then count 1..target with emoji highlights
async function runCountHint(token) {
  if (roundToken !== token || !game) return;

  const lang    = settings.language;
  const ttsRate = lang === 'ko' ? 0.8 : 0.85;
  const theme   = game.theme;
  const target  = game.targetNumber;
  const label   = lang === 'ko' ? theme.ko : theme.en;

  hintPanel.hidden  = false;
  hintLabel.textContent = label.toUpperCase();

  // Initial: show all emojis, unhighlighted
  rebuildHintItems(theme.emoji, target, 0);

  await sleep(480);
  if (roundToken !== token) return;

  await speakAsync(label, lang, ttsRate);
  if (roundToken !== token) return;
  await sleep(250);
  if (roundToken !== token) return;

  for (let n = 1; n <= target; n++) {
    if (roundToken !== token) return;

    const spoken  = lang === 'ko' ? koreanNumber(n) : englishNumber(n);
    const display = lang === 'ko' ? String(n) : spoken.toUpperCase();

    rebuildHintItems(theme.emoji, target, n);
    hintLabel.textContent = display;

    await speakAsync(spoken, lang, ttsRate);
    if (roundToken !== token) return;
    await sleep(150);
    if (roundToken !== token) return;
  }

  // Brief pause then reset for retry
  await sleep(400);
  if (roundToken !== token) return;

  hintPanel.hidden = false; // keep hint open a moment
  rebuildHintItems(theme.emoji, target, target); // all lit
  await sleep(300);
  if (roundToken !== token) return;

  hintPanel.hidden = true;
  locked = false;
  renderOptions(game);
  resumeBgm();
}

function rebuildHintItems(emoji, total, highlighted) {
  hintItems.innerHTML = '';
  const fs = emojiFontSize(total);
  for (let i = 0; i < total; i++) {
    const sp = document.createElement('span');
    sp.style.fontSize   = fs;
    sp.style.lineHeight = '1';
    sp.style.opacity    = i < highlighted ? '1' : '0.18';
    sp.style.transform  = (i === highlighted - 1) ? 'scale(1.3)' : 'scale(1)';
    sp.style.transition = 'opacity .15s, transform .15s';
    sp.style.display    = 'inline-block';
    sp.textContent      = emoji;
    hintItems.appendChild(sp);
  }
}

// ══════════════════════════════════════════════════════════════════════
//  TOP BAR CONTROLS
// ══════════════════════════════════════════════════════════════════════

const diffGroup = document.getElementById('diff-group');
const modeGroup = document.getElementById('mode-group');
const langBtnEl = document.getElementById('lang-btn');

function updateTopBar() {
  diffGroup.querySelectorAll('.ctrl-btn').forEach(b =>
    b.classList.toggle('active', Number(b.dataset.diff) === settings.maxNumber));
  modeGroup.querySelectorAll('.ctrl-btn').forEach(b =>
    b.classList.toggle('active', b.dataset.mode === settings.mode));
  langBtnEl.textContent = settings.language === 'ko' ? '한/EN' : 'KO/EN';
  langBtnEl.classList.toggle('active', false); // never "active" style, always visible
}

diffGroup.addEventListener('click', e => {
  const btn = e.target.closest('.ctrl-btn[data-diff]');
  if (!btn) return;
  const v = Number(btn.dataset.diff);
  if (v === settings.maxNumber) return;
  settings.maxNumber = v;
  saveSettings();
  startRound(game?.score ?? 0);
});

modeGroup.addEventListener('click', e => {
  const btn = e.target.closest('.ctrl-btn[data-mode]');
  if (!btn) return;
  const m = btn.dataset.mode;
  if (m === settings.mode) return;
  settings.mode = m;
  saveSettings();
  startRound(game?.score ?? 0);
});

langBtnEl.addEventListener('click', () => {
  settings.language = settings.language === 'ko' ? 'en' : 'ko';
  saveSettings();
  updateTopBar();
  syncSettingsModalUI();
  if (!nextBtn.hidden) {
    nextBtn.textContent = settings.language === 'ko' ? '다음' : 'Next';
  }
});

// ══════════════════════════════════════════════════════════════════════
//  SETTINGS MODAL
// ══════════════════════════════════════════════════════════════════════

const settingsBtn    = document.getElementById('settings-btn');
const settingsClose  = document.getElementById('settings-close');
const bgmCheckbox    = document.getElementById('bgm-checkbox');
const bgmVolumeSlider= document.getElementById('bgm-volume-slider');
const catGroup       = document.getElementById('cat-group');
const langGroup      = document.getElementById('lang-group');

function openSettings()  { syncSettingsModalUI(); settingsModal.hidden = false; }
function closeSettings() { settingsModal.hidden = true; }

function syncSettingsModalUI() {
  const lang = settings.language;
  bgmCheckbox.checked     = settings.bgmEnabled;
  bgmVolumeSlider.value   = settings.bgmVolume;

  catGroup.querySelectorAll('.toggle-btn').forEach(b =>
    b.classList.toggle('active', settings.categories.has(b.dataset.cat)));
  langGroup.querySelectorAll('.ctrl-btn').forEach(b =>
    b.classList.toggle('active', b.dataset.lang === lang));

  document.getElementById('settings-title').textContent  = lang === 'ko' ? '설정' : 'Settings';
  document.getElementById('label-language').textContent   = lang === 'ko' ? '언어' : 'Language';
  document.getElementById('label-categories').textContent = lang === 'ko' ? '항목' : 'Categories';
  document.getElementById('label-bgm').textContent        = lang === 'ko' ? '배경음악' : 'Background Music';
}

settingsBtn  .addEventListener('click', openSettings);
settingsClose.addEventListener('click', closeSettings);
settingsModal.addEventListener('click', e => { if (e.target === settingsModal) closeSettings(); });

bgmCheckbox.addEventListener('change', () => {
  setBgmEnabled(bgmCheckbox.checked);
  saveSettings();
});
bgmVolumeSlider.addEventListener('input', () => {
  setBgmVolume(parseFloat(bgmVolumeSlider.value));
  saveSettings();
});

catGroup.addEventListener('click', e => {
  const btn = e.target.closest('.toggle-btn');
  if (!btn) return;
  const cat = btn.dataset.cat;
  if (settings.categories.has(cat)) {
    if (settings.categories.size <= 1) return;  // keep at least one
    settings.categories.delete(cat);
    btn.classList.remove('active');
  } else {
    settings.categories.add(cat);
    btn.classList.add('active');
  }
  saveSettings();
  startRound(game?.score ?? 0);
});

langGroup.addEventListener('click', e => {
  const btn = e.target.closest('.ctrl-btn[data-lang]');
  if (!btn) return;
  settings.language = btn.dataset.lang;
  saveSettings();
  syncSettingsModalUI();
  updateTopBar();
  if (!nextBtn.hidden) {
    nextBtn.textContent = settings.language === 'ko' ? '다음' : 'Next';
  }
});

// ══════════════════════════════════════════════════════════════════════
//  BROWSER AUTOPLAY UNLOCK
// ══════════════════════════════════════════════════════════════════════
// Browsers block autoplay until the first user gesture.
document.addEventListener('click', () => {
  if (settings.bgmEnabled && bgmEl.paused) bgmEl.play().catch(() => {});
}, { passive: true });

// ══════════════════════════════════════════════════════════════════════
//  INIT
// ══════════════════════════════════════════════════════════════════════

function init() {
  updateTopBar();
  syncSettingsModalUI();
  startRound(0);
  initAudio();
}

init();
