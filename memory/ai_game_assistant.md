---
name: ai-game-assistant
description: "AI Game Assistant - standalone Electron overlay app that sees the game screen via screenshots, analyzes with Gemini, gives coaching tips, chat, and optional input automation"
metadata:
  node_type: memory
  type: project
  modified: 2026-09-10T00:00:00.000Z
---

New standalone project (2026-09-10), SEPARATE from the stickman repo: an executable AI assistant for video games.
Location: `C:\Users\jh4ck\OneDrive\Documents\AI Game Assistant\` (not a git repo, no `.git`).

User request: "crear ia ejecutable que te puede ayudar en los videojuegos" - asked via clarifying questions and
answered ALL THREE modes at once ("todos al mismo tiempo") + desktop app for ANY game ("cualquiera"):
1. **Coach mode** - screenshots analyzed for strategy/tips
2. **Assistant mode** - general gaming Q&A chat
3. **AutoCast mode** - tells you exactly what action to take now

**Architecture:** plain Electron app (no framework), 66MB portable exe built with `electron-builder --win portable`.
- `main.js` - app startup, transparent always-on-top overlay window, settings window, tray icon, global hotkeys
  (F8 capture, F9 show/hide overlay, F10 settings), `screenshot-desktop` capture, Gemini API calls, and a
  PowerShell-based input automation handler (`execute-action`: move_mouse / click / type / key / screenshot).
- `preload.js` - contextBridge exposing `window.api.*` IPC surface.
- `renderer/overlay.{html,css,js}` - the floating dark-glass overlay: mode tabs, chat scroll, input box, screenshot
  preview, auto-capture tick handling.
- `renderer/settings.{html,css,js}` - API key, model select, auto-capture interval, language, shortcuts help.

**KEY LESSON REUSED from [[gemini_quota_per_project]]:** the Gemini call uses the **OpenAI-compatible endpoint**
`https://generativelanguage.googleapis.com/v1beta/openai/chat/completions` with `Authorization: Bearer <key>`
and OpenAI-style `{model, messages:[{role,content}], ...}` body - NOT the native `:generateContent` endpoint.
This is because the user's keys (`AQ.Ab8RN6...` format) are verified working against the OpenAI-compat shim but
FAIL with 401 `API_KEY_SERVICE_BLOCKED` on the native endpoint. Image input rides as
`{type:"image_url", image_url:{url:"data:image/png;base64,..."}}` in the user message.

**Other implementation details:**
- Screen capture via `screenshot-desktop` (pure JS, no native build needed - good choice, robotjs/nut-js were
  avoided on purpose to skip node-gyp pain). Default 5s auto-capture interval; auto-capture also auto-sends a
  "what should I do now" analysis each tick.
- Settings stored as JSON at `app.getPath('userData')/settings.json` (fresh install = no settings yet, user must
  enter their Gemini key in F10 settings).
- Input automation (move/click/type) uses PowerShell + user32.dll mouse_event via child_process - no native npm
  deps. It's wired into `execute-action` IPC but the AI does NOT call it autonomously yet (no agentic loop) - it's
  ready for a future auto-play mode.
- **Build gotcha hit during setup:** npm's AllowScripts policy blocked electron's postinstall (binary download),
  `node_modules/electron/dist/electron.exe` was missing and the app threw "Electron failed to install correctly".
  Fix: `npm install-scripts approve electron` then manually `node node_modules/electron/install.js`. Remember if
  reinstalling deps from scratch on this machine.
- Build output: `dist/`AI Game Assistant 1.0.0.exe`` (portable, self-contained). Verified launch of both dev
  (`electron .`) and built (`dist/win-unpacked/AI Game Assistant.exe`) - runs clean, no errors.

**Not yet built / next ideas (user hasn't specified):** real auto-play bot loop (AI decides clicks and executes
them via the PowerShell automation), per-game profiles, OCR/HUD parsing of specific games, voice output,
multi-provider support (openai/groq/openrouter like the stickman project), packaging as a proper installer.
AutoCapture interval default 5s + screenshot analysis may burn Gemini quota fast (same shared-project quota lesson) -
a cheap key/model or longer interval is likely needed for real sustained use.