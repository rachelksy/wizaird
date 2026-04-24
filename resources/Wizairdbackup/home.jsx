// Home screen — pixel art learning app
// Character (wizard gif) dances on top of a text input; a chat bubble
// above their head streams bite-sized AI-generated facts.

const { useState, useEffect, useRef, useCallback } = React;

// ── palette (parchment + forest + wizard-hood coral) ──────────────
const PAL = {
  paper:    '#f2e6c7',   // background parchment
  paperDk:  '#d9c59a',   // parchment shadow
  ink:      '#2a1f14',   // near-black brown (text)
  inkSoft:  '#5b4326',
  forest:   '#2f5d3a',   // deep green
  forestDk: '#1a3a23',
  moss:     '#6b8e4e',
  coral:    '#e0563a',   // wizard hood
  coralDk:  '#a6361e',
  gold:     '#e5b14a',   // accent
  bubble:   '#fffaed',   // chat bubble fill
};

const FONT = "'Press Start 2P', 'VT323', monospace";

// ── pixel-art window / panel ──────────────────────────────────────
// Chunky 9-slice frame built with layered box-shadows so edges stay
// sharp at any size. No rounded corners; 2px "hard" bevel.
function PixelPanel({ children, bg = PAL.paper, border = PAL.ink, bevel = PAL.paperDk, style = {}, inset = 8 }) {
  const p = 3; // pixel unit
  return (
    <div style={{
      position: 'relative',
      background: bg,
      // 4-layer hard shadow: outer black border + inner bevel
      boxShadow: [
        `0 0 0 ${p}px ${border}`,           // outer stroke
        `inset 0 ${p}px 0 0 #fff6`,          // top highlight
        `inset 0 -${p}px 0 0 ${bevel}`,      // bottom shadow
      ].join(', '),
      padding: inset,
      imageRendering: 'pixelated',
      ...style,
    }}>
      {children}
    </div>
  );
}

// ── pixel speech bubble with tail pointing down ──────────────────
function ChatBubble({ text, loading }) {
  const p = 3;
  return (
    <div style={{ position: 'relative', width: 260, maxWidth: '85%' }}>
      {/* bubble body — layered rectangles for pixel-stepped silhouette */}
      <div style={{
        position: 'relative',
        background: PAL.bubble,
        boxShadow: [
          `0 0 0 ${p}px ${PAL.ink}`,
          `inset 0 ${p}px 0 0 #fff`,
          `inset 0 -${p}px 0 0 ${PAL.paperDk}`,
        ].join(', '),
        padding: '14px 14px 16px',
        color: PAL.ink,
        fontFamily: FONT,
        fontSize: 9,
        lineHeight: '16px',
        letterSpacing: 0.3,
        minHeight: 52,
      }}>
        {loading
          ? <span style={{ opacity: 0.6 }}>thinking<Dots /></span>
          : <span style={{ whiteSpace: 'pre-wrap' }}>{text}</span>}
      </div>
      {/* tail — stepped pixel triangle */}
      <div style={{ position: 'absolute', left: 32, top: '100%', width: 18, height: 18, pointerEvents: 'none' }}>
        {/* row stripes approximate a 3-step triangle with clean outline */}
        <div style={{ position: 'absolute', left: 0,  top: 0,  width: 18, height: p, background: PAL.ink }} />
        <div style={{ position: 'absolute', left: 0,  top: 0,  width: p,  height: p, background: PAL.bubble }} />
        <div style={{ position: 'absolute', left: 15, top: 0,  width: p,  height: p, background: PAL.bubble }} />

        <div style={{ position: 'absolute', left: p,  top: p,  width: 12, height: p, background: PAL.bubble }} />
        <div style={{ position: 'absolute', left: 0,  top: p,  width: p,  height: p, background: PAL.ink }} />
        <div style={{ position: 'absolute', left: 15, top: p,  width: p,  height: p, background: PAL.ink }} />

        <div style={{ position: 'absolute', left: p*2, top: p*2, width: 6, height: p, background: PAL.bubble }} />
        <div style={{ position: 'absolute', left: p,   top: p*2, width: p, height: p, background: PAL.ink }} />
        <div style={{ position: 'absolute', left: 12,  top: p*2, width: p, height: p, background: PAL.ink }} />

        <div style={{ position: 'absolute', left: p*3, top: p*3, width: p, height: p, background: PAL.ink }} />
        <div style={{ position: 'absolute', left: p*2, top: p*3, width: p, height: p, background: PAL.ink }} />
        <div style={{ position: 'absolute', left: 9,   top: p*3, width: p, height: p, background: PAL.ink }} />
      </div>
    </div>
  );
}

function Dots() {
  const [n, setN] = useState(1);
  useEffect(() => {
    const id = setInterval(() => setN(x => (x % 3) + 1), 400);
    return () => clearInterval(id);
  }, []);
  return <span>{'.'.repeat(n)}</span>;
}

// ── pixel button (hard square, two-tone) ─────────────────────────
function PixelButton({ children, onClick, color = PAL.forest, textColor = '#fff', style = {}, disabled }) {
  const p = 3;
  const [down, setDown] = useState(false);
  return (
    <button
      onMouseDown={() => setDown(true)}
      onMouseUp={() => setDown(false)}
      onMouseLeave={() => setDown(false)}
      onClick={disabled ? undefined : onClick}
      style={{
        fontFamily: FONT,
        fontSize: 9,
        letterSpacing: 0.5,
        color: textColor,
        background: color,
        border: 'none',
        padding: '12px 14px',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        boxShadow: down
          ? `0 0 0 ${p}px ${PAL.ink}, inset 0 ${p}px 0 0 rgba(0,0,0,0.25)`
          : `0 0 0 ${p}px ${PAL.ink}, inset 0 ${p}px 0 0 rgba(255,255,255,0.28), inset 0 -${p}px 0 0 rgba(0,0,0,0.3)`,
        transform: down ? 'translateY(1px)' : 'none',
        imageRendering: 'pixelated',
        ...style,
      }}>
      {children}
    </button>
  );
}

// ── tiled decorative borders around scene (pixel stars/sparkles) ─
function Sparkle({ style }) {
  return (
    <div style={{ position: 'absolute', width: 9, height: 9, ...style }}>
      <div style={{ position: 'absolute', left: 3, top: 0, width: 3, height: 9, background: PAL.gold }} />
      <div style={{ position: 'absolute', left: 0, top: 3, width: 9, height: 3, background: PAL.gold }} />
    </div>
  );
}

// ── Status bar + nav bar (pixel-themed, replaces Material) ───────
function PixelStatusBar() {
  return (
    <div style={{
      height: 28, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '0 14px', fontFamily: FONT, fontSize: 8, color: PAL.ink,
      background: PAL.paper, borderBottom: `3px solid ${PAL.ink}`,
      letterSpacing: 0.5,
    }}>
      <span>9:30</span>
      <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
        {/* signal bars */}
        <div style={{ display: 'flex', gap: 1, alignItems: 'flex-end', height: 9 }}>
          {[3,5,7,9].map((h,i) => <div key={i} style={{ width: 2, height: h, background: PAL.ink }} />)}
        </div>
        {/* battery */}
        <div style={{ width: 16, height: 8, border: `2px solid ${PAL.ink}`, position: 'relative', padding: 1, boxSizing: 'border-box' }}>
          <div style={{ width: '70%', height: '100%', background: PAL.forest }} />
          <div style={{ position: 'absolute', right: -3, top: 1, width: 1, height: 2, background: PAL.ink }} />
        </div>
      </div>
    </div>
  );
}

function PixelNavBar() {
  return (
    <div style={{
      height: 22, display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: PAL.paper, borderTop: `3px solid ${PAL.ink}`,
    }}>
      <div style={{ width: 90, height: 4, background: PAL.ink }} />
    </div>
  );
}

// ── App header: title + settings gear ────────────────────────────
function Header({ onSettings }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '10px 12px 12px',
      borderBottom: `3px solid ${PAL.ink}`,
      background: PAL.paper,
      position: 'relative',
    }}>
      {/* little grimoire badge */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 28, height: 28, background: PAL.coral,
          boxShadow: `0 0 0 3px ${PAL.ink}, inset 0 3px 0 0 rgba(255,255,255,0.35), inset 0 -3px 0 0 ${PAL.coralDk}`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: '#fff', fontFamily: FONT, fontSize: 12,
        }}>W</div>
        <div>
          <div style={{ fontFamily: FONT, fontSize: 13, color: PAL.ink, letterSpacing: 1 }}>WIZAIRD</div>
          <div style={{ fontFamily: FONT, fontSize: 6, color: PAL.inkSoft, marginTop: 3, letterSpacing: 1 }}>LV.3 APPRENTICE</div>
        </div>
      </div>
      <GearButton onClick={onSettings} />
    </div>
  );
}

function GearButton({ onClick }) {
  // pixel gear rendered as a small grid
  const on = '#2a1f14';
  const cells = [
    '...XX.XX...',
    '..XXXXXXX..',
    '.XX.....XX.',
    'XX...XX..XX',
    'X...XXXX..X',
    'X...XXXX..X',
    'XX...XX..XX',
    '.XX.....XX.',
    '..XXXXXXX..',
    '...XX.XX...',
  ];
  const size = 3;
  return (
    <button onClick={onClick} aria-label="Settings" style={{
      width: 36, height: 36, background: 'transparent', border: 'none', cursor: 'pointer', padding: 0,
      display: 'grid', gridTemplateColumns: `repeat(11, ${size}px)`, gridAutoRows: `${size}px`, justifyContent: 'center', alignContent: 'center',
    }}>
      {cells.flatMap((row, y) => row.split('').map((c, x) => (
        <div key={`${x}-${y}`} style={{ width: size, height: size, background: c === 'X' ? on : 'transparent' }} />
      )))}
    </button>
  );
}

// ── The Wizard character standing on the input ───────────────────
function Wizard({ bob }) {
  // Source gif is 800x600 with the character roughly inside x:285-390, y:205-420.
  // Crop with background-position; scale with background-size for clean pixelation.
  const SCALE = 0.75;
  const cx = 285, cy = 205, cw = 110, ch = 220;
  return (
    <div style={{
      width: cw * SCALE, height: ch * SCALE, overflow: 'hidden',
      backgroundImage: 'url(assets/wizard.gif)',
      backgroundRepeat: 'no-repeat',
      backgroundSize: `${800 * SCALE}px ${600 * SCALE}px`,
      backgroundPosition: `-${cx * SCALE}px -${cy * SCALE}px`,
      imageRendering: 'pixelated',
      transform: `translateY(${bob}px)`,
      transition: 'transform 180ms linear',
      pointerEvents: 'none',
    }} />
  );
}

// ── Shadow blob beneath character on the input panel ─────────────
function Shadow() {
  return (
    <div style={{
      width: 70, height: 8, background: PAL.ink, opacity: 0.18,
      margin: '0 auto', borderRadius: 0,
    }} />
  );
}

// ── Text input (pixel panel with blinking caret) ─────────────────
function PixelInput({ value, onChange, onSubmit, placeholder }) {
  const inputRef = useRef(null);
  return (
    <PixelPanel bg={PAL.bubble} style={{ padding: 0 }}>
      <div style={{ display: 'flex', alignItems: 'stretch' }}>
        <input
          ref={inputRef}
          value={value}
          onChange={e => onChange(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') onSubmit(); }}
          placeholder={placeholder}
          style={{
            flex: 1,
            border: 'none',
            outline: 'none',
            background: PAL.bubble,
            fontFamily: FONT,
            fontSize: 9,
            color: PAL.ink,
            padding: '14px 12px',
            letterSpacing: 0.5,
            minWidth: 0,
          }}
        />
        <button
          onClick={onSubmit}
          aria-label="Send"
          style={{
            width: 56, background: PAL.coral, border: 'none', cursor: 'pointer',
            color: '#fff', fontFamily: FONT, fontSize: 11,
            borderLeft: `3px solid ${PAL.ink}`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            letterSpacing: 1,
          }}>SEND</button>
      </div>
    </PixelPanel>
  );
}

// ── Topic chip row (clickable prompts) ───────────────────────────
function TopicChip({ label, active, onClick, icon }) {
  const p = 3;
  return (
    <button onClick={onClick} style={{
      fontFamily: FONT, fontSize: 7, letterSpacing: 0.5,
      padding: '9px 10px',
      background: active ? PAL.forest : PAL.paper,
      color: active ? '#fff' : PAL.ink,
      border: 'none',
      cursor: 'pointer',
      boxShadow: active
        ? `0 0 0 ${p}px ${PAL.ink}, inset 0 ${p}px 0 0 rgba(255,255,255,0.22), inset 0 -${p}px 0 0 ${PAL.forestDk}`
        : `0 0 0 ${p}px ${PAL.ink}, inset 0 ${p}px 0 0 #fff8, inset 0 -${p}px 0 0 ${PAL.paperDk}`,
      whiteSpace: 'nowrap',
      display: 'flex', alignItems: 'center', gap: 6,
    }}>
      <span>{icon}</span>{label}
    </button>
  );
}

// ── HP/XP style stat bars ────────────────────────────────────────
function StatBar({ label, value, max, color }) {
  const pct = Math.max(0, Math.min(1, value / max));
  const segs = 10;
  const filled = Math.round(pct * segs);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span style={{ fontFamily: FONT, fontSize: 7, color: PAL.ink, width: 22 }}>{label}</span>
      <div style={{ display: 'flex', gap: 1, padding: 2, background: PAL.ink }}>
        {Array.from({ length: segs }).map((_, i) => (
          <div key={i} style={{
            width: 8, height: 8,
            background: i < filled ? color : '#00000033',
          }} />
        ))}
      </div>
      <span style={{ fontFamily: FONT, fontSize: 6, color: PAL.inkSoft }}>{value}/{max}</span>
    </div>
  );
}

// ── Settings modal (pixel) ───────────────────────────────────────
function SettingsModal({ open, onClose, cfg, setCfg }) {
  if (!open) return null;
  return (
    <div style={{
      position: 'absolute', inset: 0, background: 'rgba(20,12,6,0.55)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 20,
      padding: 16,
    }}>
      <PixelPanel bg={PAL.paper} style={{ width: '100%', maxWidth: 320 }}>
        <div style={{ fontFamily: FONT, fontSize: 11, color: PAL.ink, marginBottom: 10, letterSpacing: 1 }}>⚙ SETTINGS</div>
        <div style={{ borderTop: `3px dashed ${PAL.ink}`, margin: '6px 0 12px' }} />
        <Field label="AI PROVIDER">
          <select value={cfg.provider} onChange={e => setCfg({ ...cfg, provider: e.target.value })} style={pxSelect}>
            <option value="claude">Claude (built-in)</option>
            <option value="openai">OpenAI</option>
            <option value="gemini">Google Gemini</option>
            <option value="custom">Custom endpoint</option>
          </select>
        </Field>
        <Field label="API KEY">
          <input type="password" value={cfg.apiKey} onChange={e => setCfg({ ...cfg, apiKey: e.target.value })} placeholder="sk-..." style={pxInput} />
        </Field>
        <Field label="MODEL">
          <input value={cfg.model} onChange={e => setCfg({ ...cfg, model: e.target.value })} style={pxInput} />
        </Field>
        <Field label="TEMPERATURE">
          <input type="range" min="0" max="100" value={cfg.temp} onChange={e => setCfg({ ...cfg, temp: +e.target.value })} style={{ width: '100%' }} />
          <div style={{ fontFamily: FONT, fontSize: 7, color: PAL.inkSoft, marginTop: 4 }}>{(cfg.temp/100).toFixed(2)}</div>
        </Field>
        <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
          <PixelButton onClick={onClose} color={PAL.paperDk} textColor={PAL.ink} style={{ flex: 1 }}>CANCEL</PixelButton>
          <PixelButton onClick={onClose} color={PAL.forest} style={{ flex: 1 }}>SAVE</PixelButton>
        </div>
      </PixelPanel>
    </div>
  );
}
function Field({ label, children }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ fontFamily: FONT, fontSize: 7, color: PAL.inkSoft, marginBottom: 4, letterSpacing: 1 }}>{label}</div>
      {children}
    </div>
  );
}
const pxInput = {
  width: '100%', fontFamily: FONT, fontSize: 9, color: PAL.ink,
  background: PAL.bubble, border: `3px solid ${PAL.ink}`,
  padding: '8px 8px', outline: 'none', boxSizing: 'border-box',
};
const pxSelect = { ...pxInput };

// ── Home screen ──────────────────────────────────────────────────
function HomeScreen({ tweaks }) {
  const [bubble, setBubble] = useState({ text: 'Hail, apprentice! Ask anything — I hoard strange facts.', loading: false });
  const [input, setInput] = useState('');
  const [topic, setTopic] = useState(tweaks.topic || 'Space');
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [bob, setBob] = useState(0);
  const [cfg, setCfg] = useState({ provider: 'claude', apiKey: '', model: 'claude-haiku-4-5', temp: 70 });
  const typingRef = useRef(null);

  // idle bob (gif already animates; this is a secondary float)
  useEffect(() => {
    let t = 0;
    const id = setInterval(() => {
      t += 1;
      setBob(Math.round(Math.sin(t / 3) * 2));
    }, 120);
    return () => clearInterval(id);
  }, []);

  // Type-out effect
  const typeOut = useCallback((fullText) => {
    if (typingRef.current) clearInterval(typingRef.current);
    setBubble({ text: '', loading: false });
    let i = 0;
    typingRef.current = setInterval(() => {
      i += 1;
      setBubble({ text: fullText.slice(0, i), loading: false });
      if (i >= fullText.length) {
        clearInterval(typingRef.current);
        typingRef.current = null;
      }
    }, 22);
  }, []);

  const askAI = useCallback(async (prompt) => {
    setBubble({ text: '', loading: true });
    try {
      const sys = `You are a playful pixel-art wizard tutor inside an Android learning app called Wizaird. Give ONE bite-sized fact or explanation. Max 160 characters. No emoji. No preamble. Plain text only.`;
      const userMsg = prompt?.trim()
        ? prompt
        : `Tell me one surprising bite-sized fact about ${topic}.`;
      const text = await window.claude.complete({
        messages: [
          { role: 'user', content: `${sys}\n\nUser: ${userMsg}` },
        ],
      });
      const clean = (text || '').trim().replace(/^["']|["']$/g, '').slice(0, 200);
      typeOut(clean || 'The stars are quiet tonight. Try again, apprentice.');
    } catch (e) {
      typeOut('My scroll is torn! Check settings and try again.');
    }
  }, [topic, typeOut]);

  // auto-rotate a fresh bite every ~18s on the current topic
  useEffect(() => {
    askAI();
    const id = setInterval(() => askAI(), 20000);
    return () => clearInterval(id);
  }, [topic, askAI]);

  const submit = () => {
    const q = input.trim();
    if (!q) return;
    setInput('');
    askAI(q);
  };

  const topics = [
    { label: 'SPACE',   icon: '★' },
    { label: 'HISTORY', icon: '♜' },
    { label: 'MATH',    icon: 'π' },
    { label: 'NATURE',  icon: '✿' },
    { label: 'CODE',    icon: '{}' },
  ];

  const bg = tweaks.nightMode
    ? { base: '#1b1a2e' }
    : { base: PAL.paper };

  return (
    <div style={{
      width: '100%', height: '100%', position: 'relative', overflow: 'hidden',
      background: bg.base,
      fontFamily: FONT,
      imageRendering: 'pixelated',
    }}>
      {/* sparkles in corners */}
      <Sparkle style={{ left: 18, top: 72 }} />
      <Sparkle style={{ right: 24, top: 110 }} />
      <Sparkle style={{ left: 30, bottom: 180 }} />
      <Sparkle style={{ right: 32, bottom: 220 }} />

      <PixelStatusBar />
      <Header onSettings={() => setSettingsOpen(true)} />

      {/* Stats strip */}
      <div style={{ padding: '10px 14px 0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
        <StatBar label="HP" value={8} max={10} color={PAL.coral} />
        <StatBar label="XP" value={6} max={10} color={PAL.gold} />
      </div>

      {/* Topic chips */}
      <div style={{ padding: '12px 14px 0', display: 'flex', gap: 8, overflowX: 'auto', scrollbarWidth: 'none' }}>
        {topics.map(t => (
          <TopicChip
            key={t.label}
            label={t.label}
            icon={t.icon}
            active={topic === t.label}
            onClick={() => setTopic(t.label)}
          />
        ))}
      </div>

      {/* Main stage: bubble + wizard + input */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 22 + 24,
        padding: '0 16px',
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6,
      }}>
        <ChatBubble text={bubble.text} loading={bubble.loading} />

        {/* wizard standing on input panel — overlapping so feet are on the input */}
        <div style={{ position: 'relative', width: '100%', marginTop: 4 }}>
          <div style={{
            display: 'flex', justifyContent: 'center',
            marginBottom: 0,               // sit ON TOP of the input, not inside it
            position: 'relative', zIndex: 2,
            pointerEvents: 'none',
          }}>
            <Wizard bob={bob} />
          </div>
          <PixelInput
            value={input}
            onChange={setInput}
            onSubmit={submit}
            placeholder="ASK THE WIZARD..."
          />
          <div style={{
            display: 'flex', justifyContent: 'space-between', marginTop: 8,
            fontFamily: FONT, fontSize: 6, color: PAL.inkSoft, letterSpacing: 1,
          }}>
            <span>↵ SEND</span>
            <span>AUTO-QUEST ON</span>
          </div>
        </div>
      </div>

      <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        <PixelNavBar />
      </div>

      <SettingsModal
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        cfg={cfg}
        setCfg={setCfg}
      />
    </div>
  );
}

// ── Thin device frame (custom — replaces Material chrome) ────────
function PixelDevice({ children }) {
  return (
    <div style={{
      width: 412, height: 892, borderRadius: 18, overflow: 'hidden',
      background: PAL.paper,
      border: `8px solid #3a2a1a`,
      boxShadow: '0 30px 80px rgba(0,0,0,0.35)',
      position: 'relative',
      boxSizing: 'border-box',
    }}>
      {children}
    </div>
  );
}

// ── App root ─────────────────────────────────────────────────────
function App() {
  const { tweaks, Panel } = window.useTweaksPanel({
    topic: 'Space',
    nightMode: false,
    autoRefreshSec: 20,
  });

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: '#1a1411',
      backgroundImage: `
        radial-gradient(circle at 30% 20%, #2a1f14 0%, transparent 60%),
        radial-gradient(circle at 70% 80%, #2f1a14 0%, transparent 55%)
      `,
      padding: 24,
      fontFamily: FONT,
    }}>
      <PixelDevice>
        <HomeScreen tweaks={tweaks} />
      </PixelDevice>
      {Panel}
    </div>
  );
}

// Tweaks wrapper (lightweight, uses the starter's hooks/components)
window.useTweaksPanel = function useTweaksPanel(defaults) {
  const [tweaks, setTweak] = window.useTweaks(defaults);
  const Panel = (
    <window.TweaksPanel title="Tweaks">
      <window.TweakSection label="Content" />
      <window.TweakSelect
        label="Default topic"
        value={tweaks.topic}
        onChange={v => setTweak('topic', v)}
        options={['Space','History','Math','Nature','Code']}
      />
      <window.TweakSlider
        label="Auto-refresh"
        value={tweaks.autoRefreshSec}
        onChange={v => setTweak('autoRefreshSec', v)}
        min={5} max={60} step={5} unit="s"
      />
      <window.TweakSection label="Look" />
      <window.TweakToggle
        label="Night mode"
        value={tweaks.nightMode}
        onChange={v => setTweak('nightMode', v)}
      />
    </window.TweaksPanel>
  );
  return { tweaks, Panel };
};

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
