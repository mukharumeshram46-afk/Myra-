import React, { useState, useEffect, useRef } from 'react';
import { NamuBridgeService, NodeElement } from './services/namuBridge';
import { GeminiLiveService } from './services/geminiLiveService';
import {
  Mic,
  MicOff,
  Radio,
  Layers,
  Smartphone,
  Play,
  Maximize2,
  Terminal,
  Activity,
  Compass,
  ArrowDown,
  Sparkles,
  CheckCircle2,
  ExternalLink
} from 'lucide-react';

export default function App() {
  const [emotion, setEmotion] = useState<'idle' | 'listening' | 'thinking' | 'speaking' | 'executing'>('idle');
  const [isLiveConnected, setIsLiveConnected] = useState(false);
  const [transcripts, setTranscripts] = useState<Array<{ sender: string; text: string; action?: string }>>([
    {
      sender: 'Namu',
      text: "Hey handsome! Namu is loaded and locked into Realme UI. Want me to open WhatsApp, tap something, or take over the screen?"
    }
  ]);
  const [inputText, setInputText] = useState('');
  const [logs, setLogs] = useState<string[]>([
    '[INIT] Namu Hybrid Bridge initialized on Android 14',
    '[ACCESSIBILITY] Service listening for gestures & node trees',
    '[OVERLAY] SYSTEM_ALERT_WINDOW HUD available'
  ]);
  const [inspectedTree, setInspectedTree] = useState<any>(null);
  const [activeTab, setActiveTab] = useState<'avatar' | 'actions' | 'tree' | 'logs'>('avatar');

  const liveServiceRef = useRef<GeminiLiveService | null>(null);

  const addLog = (entry: string) => {
    setLogs((prev) => [`[${new Date().toLocaleTimeString()}] ${entry}`, ...prev.slice(0, 50)]);
  };

  const handleExecuteIntent = async (command: string) => {
    const userMsg = { sender: 'User', text: command };
    setTranscripts((prev) => [...prev, userMsg]);
    setInputText('');

    setEmotion('thinking');
    NamuBridgeService.setEmotion('thinking');

    // Sassy companion responses & action dispatch
    setTimeout(async () => {
      const lower = command.toLowerCase();
      let reply = "On it! Don't blink.";
      let actionDesc = '';

      if (lower.includes('whatsapp') || lower.includes('rahul')) {
        reply = "Opening WhatsApp and pinging Rahul right now. Relax, I've got your back!";
        actionDesc = "Launched com.whatsapp & tapped 'Rahul'";
        await NamuBridgeService.openApp('com.whatsapp');
        setTimeout(() => NamuBridgeService.findAndClick('Rahul'), 800);
      } else if (lower.includes('youtube')) {
        reply = "Firing up YouTube! Put your headphones on, boss.";
        actionDesc = "Launched com.google.android.youtube";
        await NamuBridgeService.openApp('com.google.android.youtube');
      } else if (lower.includes('scroll')) {
        reply = "Scrolling down. Look at that buttery smooth 120Hz motion.";
        actionDesc = "Scrolled Screen Down";
        await NamuBridgeService.scroll('down');
      } else if (lower.includes('inspect') || lower.includes('screen')) {
        reply = "Eyeballing your active screen right now. Node hierarchy intercepted!";
        actionDesc = "Inspected Active Window Tree";
        const tree = await NamuBridgeService.inspectTree();
        setInspectedTree(tree);
      } else if (lower.includes('click') || lower.includes('tap')) {
        reply = "Target acquired: tapping coordinates!";
        actionDesc = "Tapped (540, 960)";
        await NamuBridgeService.click(540, 960);
      } else {
        reply = `"${command}"? Say less. Namu is executing this across your Android OS.`;
        actionDesc = "Autonomous Action Triggered";
      }

      setEmotion('speaking');
      NamuBridgeService.setEmotion('speaking');
      addLog(`[ACTION] ${actionDesc}`);

      setTranscripts((prev) => [
        ...prev,
        { sender: 'Namu', text: reply, action: actionDesc }
      ]);

      setTimeout(() => {
        setEmotion('idle');
        NamuBridgeService.setEmotion('idle');
      }, 2500);
    }, 600);
  };

  const toggleGeminiLive = async () => {
    if (isLiveConnected) {
      liveServiceRef.current?.disconnect();
      setIsLiveConnected(false);
      setEmotion('idle');
      addLog('[GEMINI LIVE] Disconnected voice WebSocket');
    } else {
      try {
        const apiKey = (window as any).GEMINI_API_KEY || '';
        liveServiceRef.current = new GeminiLiveService(
          apiKey,
          (text) => {
            setTranscripts((prev) => [...prev, { sender: 'Namu (Live)', text }]);
          },
          (emo) => setEmotion(emo as any),
          (name, args) => {
            addLog(`[GEMINI TOOL] ${name}(${JSON.stringify(args)})`);
            if (name === 'open_app') NamuBridgeService.openApp(args.packageName);
            if (name === 'click_coordinate') NamuBridgeService.click(args.x, args.y);
            if (name === 'find_and_click') NamuBridgeService.findAndClick(args.text);
            if (name === 'scroll_screen') NamuBridgeService.scroll(args.direction);
          }
        );
        await liveServiceRef.current.connect();
        setIsLiveConnected(true);
        addLog('[GEMINI LIVE] Bidirectional WebSocket streaming audio');
      } catch (err: any) {
        addLog(`[GEMINI LIVE ERROR] ${err.message}`);
      }
    }
  };

  return (
    <div className="flex flex-col h-screen max-w-md mx-auto bg-gradient-to-b from-[#080d1a] via-[#0d1527] to-[#050811] text-slate-100 font-sans shadow-2xl relative overflow-hidden">
      {/* Top Cyber Status Bar */}
      <header className="px-5 pt-4 pb-3 border-b border-cyan-950/60 bg-[#080d1a]/80 backdrop-blur-md flex items-center justify-between z-20">
        <div className="flex items-center space-x-2.5">
          <div className="relative">
            <span className="w-3 h-3 rounded-full bg-cyan-400 block animate-ping absolute inset-0 opacity-75"></span>
            <span className="w-3 h-3 rounded-full bg-cyan-400 block"></span>
          </div>
          <div>
            <h1 className="text-base font-bold tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-teal-300 to-pink-500 uppercase">
              Namu Mobile OS
            </h1>
            <p className="text-[10px] text-cyan-400/70 tracking-widest font-mono">
              REALME 12 PRO 5G // ANDROID 14
            </p>
          </div>
        </div>

        <button
          onClick={() => NamuBridgeService.startFloatingHUD()}
          className="flex items-center space-x-1 px-3 py-1.5 rounded-full bg-cyan-500/10 border border-cyan-500/30 text-cyan-300 text-xs hover:bg-cyan-500/20 active:scale-95 transition"
        >
          <ExternalLink className="w-3.5 h-3.5" />
          <span className="font-semibold">Float HUD</span>
        </button>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 overflow-y-auto px-4 py-3 space-y-4">
        {/* Holographic Avatar Showcase */}
        <section className="flex flex-col items-center justify-center pt-2 pb-1 relative">
          <div className="relative w-44 h-44 flex items-center justify-center">
            {/* Glowing Neon Cyber Rings */}
            <div
              className={`absolute inset-0 rounded-full border-2 transition-all duration-700 ${
                emotion === 'listening'
                  ? 'border-emerald-400 shadow-[0_0_35px_rgba(52,211,153,0.8)] scale-105 animate-pulse'
                  : emotion === 'thinking'
                  ? 'border-amber-400 shadow-[0_0_35px_rgba(251,191,36,0.8)] animate-spin'
                  : emotion === 'speaking'
                  ? 'border-pink-500 shadow-[0_0_40px_rgba(236,72,153,0.9)] scale-110'
                  : 'border-cyan-500/40 shadow-[0_0_20px_rgba(6,182,212,0.3)]'
              }`}
            />
            <div className="absolute inset-2 rounded-full border border-pink-500/20 animate-reverse-spin" />

            {/* 3D Holographic Companion Face */}
            <div className="w-36 h-36 rounded-full overflow-hidden border-2 border-cyan-300/60 shadow-inner bg-gradient-to-tr from-[#0b1329] via-[#1a1738] to-[#20102b] flex items-center justify-center relative group">
              <img
                src="/assets/namu_avatar.png"
                onError={(e) => {
                  (e.target as HTMLImageElement).src =
                    'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=400&q=80';
                }}
                alt="Namu AI Avatar"
                className="w-full h-full object-cover mix-blend-screen opacity-95 group-hover:scale-105 transition duration-500"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-cyan-950/70 via-transparent to-transparent pointer-events-none" />

              {/* Status Floating Pill */}
              <div className="absolute bottom-2 px-2.5 py-0.5 rounded-full bg-black/70 backdrop-blur-md border border-cyan-400/50 text-[10px] font-mono tracking-wider text-cyan-300 flex items-center space-x-1">
                <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse" />
                <span className="uppercase font-semibold">{emotion}</span>
              </div>
            </div>
          </div>

          <p className="mt-2 text-xs text-center text-slate-400 font-medium max-w-xs">
            "Sassy, hyper-intelligent, and ready to take the wheel."
          </p>
        </section>

        {/* Live Gemini WebSocket Audio Bar */}
        <div className="p-3 rounded-2xl bg-gradient-to-r from-cyan-950/40 to-purple-950/30 border border-cyan-800/40 flex items-center justify-between shadow-lg">
          <div className="flex items-center space-x-3">
            <button
              onClick={toggleGeminiLive}
              className={`p-3 rounded-full transition-all duration-300 ${
                isLiveConnected
                  ? 'bg-gradient-to-r from-pink-500 to-rose-600 text-white shadow-lg shadow-pink-500/40 animate-pulse'
                  : 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 hover:bg-cyan-500/30'
              }`}
            >
              {isLiveConnected ? <Mic className="w-5 h-5" /> : <MicOff className="w-5 h-5" />}
            </button>
            <div>
              <div className="text-xs font-semibold text-white flex items-center space-x-1.5">
                <span>Gemini Live Multimodal Voice</span>
                {isLiveConnected && (
                  <span className="px-1.5 py-0.5 rounded text-[9px] bg-pink-500/20 text-pink-300 border border-pink-500/40">
                    LIVE
                  </span>
                )}
              </div>
              <p className="text-[11px] text-slate-400">
                {isLiveConnected ? 'Streaming bidirectional 16kHz PCM' : 'Tap to start real-time voice chat'}
              </p>
            </div>
          </div>
          <Radio className={`w-4 h-4 ${isLiveConnected ? 'text-pink-400 animate-spin' : 'text-slate-600'}`} />
        </div>

        {/* Interaction Tabs */}
        <div className="flex rounded-xl bg-slate-900/60 p-1 border border-slate-800 text-xs font-medium">
          {(['avatar', 'actions', 'tree', 'logs'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`flex-1 py-1.5 rounded-lg capitalize transition ${
                activeTab === tab
                  ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 font-semibold'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {tab}
            </button>
          ))}
        </div>

        {/* Tab View: Avatar & Sassy Chat Feed */}
        {activeTab === 'avatar' && (
          <div className="space-y-3">
            <div className="space-y-2.5 max-h-56 overflow-y-auto pr-1">
              {transcripts.map((t, idx) => (
                <div
                  key={idx}
                  className={`p-3 rounded-2xl text-xs leading-relaxed ${
                    t.sender === 'User'
                      ? 'ml-8 bg-cyan-600/20 border border-cyan-500/30 text-cyan-100 rounded-tr-none'
                      : 'mr-8 bg-slate-900/80 border border-pink-500/30 text-slate-200 rounded-tl-none shadow-md'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span
                      className={`text-[10px] font-bold uppercase tracking-wider ${
                        t.sender === 'User' ? 'text-cyan-400' : 'text-pink-400'
                      }`}
                    >
                      {t.sender}
                    </span>
                    {t.action && (
                      <span className="text-[9px] px-1.5 py-0.5 rounded bg-pink-500/10 text-pink-300 border border-pink-500/20 font-mono">
                        {t.action}
                      </span>
                    )}
                  </div>
                  <p>{t.text}</p>
                </div>
              ))}
            </div>

            {/* Quick Sassy Chips */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-[11px]">
              <button
                onClick={() => handleExecuteIntent("Open WhatsApp and send that text to Rahul right now.")}
                className="whitespace-nowrap px-3 py-1.5 rounded-full bg-slate-800/80 hover:bg-slate-700 text-cyan-300 border border-cyan-800/50"
              >
                💬 Text Rahul on WhatsApp
              </button>
              <button
                onClick={() => handleExecuteIntent("Open YouTube and queue up some chill beats")}
                className="whitespace-nowrap px-3 py-1.5 rounded-full bg-slate-800/80 hover:bg-slate-700 text-pink-300 border border-pink-800/50"
              >
                ▶ Open YouTube
              </button>
              <button
                onClick={() => handleExecuteIntent("Scroll down and inspect on-screen nodes")}
                className="whitespace-nowrap px-3 py-1.5 rounded-full bg-slate-800/80 hover:bg-slate-700 text-emerald-300 border border-emerald-800/50"
              >
                ⬇ Scroll Feed
              </button>
            </div>
          </div>
        )}

        {/* Tab View: OS Quick Actions */}
        {activeTab === 'actions' && (
          <div className="grid grid-cols-2 gap-2.5">
            <button
              onClick={() => NamuBridgeService.openApp('com.whatsapp')}
              className="p-3 rounded-xl bg-slate-900/70 border border-cyan-900/50 hover:border-cyan-500 flex flex-col space-y-1.5 text-left transition"
            >
              <Smartphone className="w-5 h-5 text-emerald-400" />
              <span className="text-xs font-bold text-white">Open WhatsApp</span>
              <span className="text-[10px] text-slate-400">Launch package & hook chat</span>
            </button>
            <button
              onClick={() => NamuBridgeService.openApp('com.google.android.youtube')}
              className="p-3 rounded-xl bg-slate-900/70 border border-cyan-900/50 hover:border-pink-500 flex flex-col space-y-1.5 text-left transition"
            >
              <Play className="w-5 h-5 text-rose-400" />
              <span className="text-xs font-bold text-white">Open YouTube</span>
              <span className="text-[10px] text-slate-400">Launch media player</span>
            </button>
            <button
              onClick={() => NamuBridgeService.scroll('down')}
              className="p-3 rounded-xl bg-slate-900/70 border border-cyan-900/50 hover:border-cyan-500 flex flex-col space-y-1.5 text-left transition"
            >
              <ArrowDown className="w-5 h-5 text-cyan-400" />
              <span className="text-xs font-bold text-white">Scroll Down</span>
              <span className="text-[10px] text-slate-400">Physical swipe gesture</span>
            </button>
            <button
              onClick={async () => {
                const tree = await NamuBridgeService.inspectTree();
                setInspectedTree(tree);
                setActiveTab('tree');
              }}
              className="p-3 rounded-xl bg-slate-900/70 border border-cyan-900/50 hover:border-pink-500 flex flex-col space-y-1.5 text-left transition"
            >
              <Layers className="w-5 h-5 text-pink-400" />
              <span className="text-xs font-bold text-white">Scan Screen UI</span>
              <span className="text-[10px] text-slate-400">Extract active node tree</span>
            </button>
          </div>
        )}

        {/* Tab View: On-Screen Node Tree */}
        {activeTab === 'tree' && (
          <div className="p-3 rounded-2xl bg-black/60 border border-slate-800 text-[11px] font-mono text-cyan-300 max-h-64 overflow-y-auto space-y-2">
            <div className="flex items-center justify-between text-slate-400 border-b border-slate-800 pb-1">
              <span>ACTIVE FOREGROUND APP</span>
              <span className="text-emerald-400">{inspectedTree?.foregroundApp || 'com.whatsapp'}</span>
            </div>
            {inspectedTree?.elements ? (
              inspectedTree.elements.map((el: NodeElement, i: number) => (
                <div
                  key={i}
                  onClick={() => el.text && NamuBridgeService.findAndClick(el.text)}
                  className="p-2 rounded bg-slate-900/60 border border-slate-800/80 hover:border-cyan-400 cursor-pointer transition flex items-center justify-between"
                >
                  <div>
                    <div className="font-semibold text-white">{el.text || el.desc || 'Interactive Element'}</div>
                    <div className="text-[9px] text-slate-500">{el.className || el.id || 'View'}</div>
                  </div>
                  {el.clickable && (
                    <span className="px-1.5 py-0.5 rounded text-[9px] bg-cyan-500/20 text-cyan-300">
                      CLICKABLE
                    </span>
                  )}
                </div>
              ))
            ) : (
              <p className="text-slate-500 italic">Tap 'Scan Screen UI' or ask Namu to inspect the screen.</p>
            )}
          </div>
        )}

        {/* Tab View: Bridge Logs */}
        {activeTab === 'logs' && (
          <div className="p-3 rounded-2xl bg-black/80 border border-slate-800 text-[10px] font-mono text-emerald-400 max-h-60 overflow-y-auto space-y-1">
            {logs.map((log, i) => (
              <div key={i} className="leading-tight">
                {log}
              </div>
            ))}
          </div>
        )}
      </main>

      {/* Bottom Command Bar */}
      <footer className="p-3 border-t border-cyan-950/80 bg-[#080d1a]/95 backdrop-blur-md">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (inputText.trim()) handleExecuteIntent(inputText.trim());
          }}
          className="flex items-center space-x-2"
        >
          <input
            type="text"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            placeholder="Tell Namu what to do on your device..."
            className="flex-1 bg-slate-900/90 border border-slate-700/80 focus:border-cyan-400 rounded-xl px-3.5 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none transition shadow-inner"
          />
          <button
            type="submit"
            className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-teal-400 hover:from-cyan-400 hover:to-teal-300 text-slate-950 font-bold text-xs shadow-lg shadow-cyan-500/25 active:scale-95 transition"
          >
            Execute
          </button>
        </form>
      </footer>
    </div>
  );
}
