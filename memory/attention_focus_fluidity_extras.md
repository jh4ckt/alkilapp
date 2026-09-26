# Attention focus, fluid animations + extras (2026-09-05)

Shipped as Windows v2.19.0 / Android v1.18 (commit `f8cd723`), release
https://github.com/ineler15/alan-becker-aistickmans/releases/tag/v2.19.0. Queued mid-session by the
user, all landed together.

## Feature 1: "saber donde esta el mouse" + selector global de atencion
- **User decision:** the attention selector is GLOBAL (no per-character option). In "mouse" mode
  the camera is NOT removed - only the prompt emphasis changes (asked clarification, he said
  "solo enfatiza, no la quito").
- **PC:** new global setting `attentionFocus` ∈ {'camera','mouse'} - `src/pcSettings.js` (default
  `'camera'`, `applyToEnv` sets `process.env.ATTENTION_FOCUS`), getter in `src/config.js`,
  `<select id="attentionFocus">` in `renderer/settings.html` + save/load in `settings.js`.
  `src/loop/agentLoop.js` reads `input.getMousePosition()` (nut-js, absolute screen px) ONCE per
  round (workspace note: `src/actions/input.js`, and only `geminiProvider` attaches webcamBase64).
  Context now carries `mousePosition`, `attentionFocus` and a forceful `attentionNote` line. All 6
  providers stringify the whole `contextForModel` object, so the new fields flow automatically AND
  the 6 SYSTEM_PROMPTs each got a short mention (project lesson: update EVERY provider prompt
  when adding behavior).
- **Android:** analogy is the TOUCH position, not a mouse. New `overlay/TouchTracker.kt` (object,
  @Volatile xPercent/yPercent, 0-100) written by `CharacterOverlay.handleTouch()` on every
  ACTION_DOWN/MOVE, read once per tick round by `OverlayService.tickCharacterAi()` and passed to
  `GeminiClient.decide(...)` as `touchXPercent`/`touchYPercent` + `attentionFocus` (new
  `Prefs.attentionFocus`/`setAttentionFocus`, global Spinner `spinnerAttentionFocus` in
  `activity_main.xml`/`MainActivity`, options `Camara (webcam)` / `Donde toco la pantalla`).
  GeminiClient's SYSTEM_PROMPT updated to explain attentionFocus/touch semantics.

## Feature 2: animations mas fluidas
- Both engines tick `TICK_MS = 40` -> **25** (`src/jsEngine/characterState.js` and
  `overlay/CharacterState.kt`), now ~40fps.
- Speeds/frame-ticks SCALED to keep real-time cadence identical: WALK_SPEED 3->2, RUN 7->4,
  FALL 6->4, CLIMB 3->2; WALK_FRAME_TICKS 4->6, RUN 2->3, FALL 3->5, CLIMB 4->6
  (40/25=1.6 up for ticks, 25/40=0.625 down for px/tick). Do NOT re-tune by eye against old
  numbers - both changed together on purpose.
- Verify live: idle sway (standPose, 90-frame period) went from ~14.4s to ~13.5s per cycle -
  same as before, just sampled at 40fps.

## Feature 3: cerrar el programa desde el menu inicial (PC)
- "Salir (cerrar el programa)" button in `renderer/settings.html` -> `stickmanAPI.quitApp()` ->
  new `ipcMain.on('stickman:close-app')` in main.js -> `app.quit()`. Tray already had Salir.
- Bonus fix while touching main.js: the known `requestSingleInstanceLock()` bug - `app.quit()`
  without `return` let a second instance's `whenReady()` startup logic run anyway (cleared
  history files / EADDRINUSE). Added the `return`.

## Feature 4: chat privado por personaje (Android)
- Floating chat overlay (`overlay/ChatButtonOverlay.kt`) was group-only (`PendingMessages.setAll`).
  Added a recipient Spinner at the top of the panel: "(a todos)" or ONE character (uses the
  existing per-character delivery `PendingMessages.set(characterId, text)` - the plumbing already
  existed via ChatActivity). Snack "🍪" button also routes to the selected recipient now.
- Hint updates on selection ("Escribile algo a <nombre> (privado)..."). Panel restructured to a
  vertical LinearLayout (spinner row + content row).

**How to apply / not yet verified:** everything builds (`node --check` all PC files,
`:app:compileDebugKotlin` + `assembleDebug`), but NONE of this was visually confirmed live on
device. The 40fps tick change especially deserves a live look (sway sizing, battery/CPU on
tablet). Settings (`pc_settings.json` / SharedPreferences `attention_focus`) default to 'camera',
so a user who never touches the new selector sees old behavior except the smoother animation.