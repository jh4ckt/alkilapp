---
name: pc-mouse-control-alkilmouse
description: "AlkilMouse - standalone Windows program to control the PC mouse (move/click/drag/scroll), type text, and capture screenshots via localhost HTTP + interactive console; with security limits (token, clamping, blocked keys, audit log)"
metadata:
  node_type: memory
  type: project
  modified: 2026-09-13T00:00:00.000Z
---

Father's request 2026-09-13: "haz un programa que te permita acceder y usar el mouse de la pc",
then added "y ver la pantalla", "y tambien que puedas poner texto", "con limitaciones, para que no
acabe haciendo cualquier cosa en la pc". Built as a **standalone project** (chose "los dos primeros":
HTTP server + console; the location question went unanswered so it was placed standalone):
`C:\Users\jh4ck\OneDrive\Documents\MouseControl\`. No git repo, not on GitHub (not requested).

No Python on the machine (only a broken Microsoft Store stub); Node v24 is installed but unused.
Everything is pure PowerShell + Windows native APIs (user32 SendInput/mouse_event, System.Drawing
CopyFromScreen, System.Windows.Forms), same technique already proven in [[ai_game_assistant]].

**Files:**
- `mouse-core.ps1` - shared primitives + safety limits (this is the important file: all actions
  route through the limits). C# via Add-Type (NativeInput class) for SetCursorPos/GetCursorPos,
  mouse_event, SendInput (KEYEVENTF_UNICODE typing + Enter key), guarded by `if (-not ("NativeInput" -as [type]))`.
- `server.ps1` - System.Net.HttpListener on `http://127.0.0.1:8765/` (loopback only, no admin/UAC
  needed). Routes: `GET /pos`, `GET /limits`, `GET /screen?scale=N&b64=1` (saves `screen.png`),
  `POST /move {x,y}`, `POST /click {button,clicks,x,y}`, `POST /drag`, `POST /scroll {lines}`,
  `POST /type {text}`, `GET /log?n=`, `GET /` serves `web/control.html` (manual panel; note the
  control page is served BEFORE the auth check). Per-request `X-Token` header (or `?token=`); token
  auto-generated into `token.txt`, also writes `server.pid`.
- `console.ps1` - interactive REPL (same verbs in Spanish): pos/move/click/doble/scroll/arrastrar/
  texto/pantalla/limites/log/ayuda/salir.
- `config.json` - safety knobs: `port`, `maxTextLength` (500), `allowEnterInText` (false),
  `clickButtons` (left/right/middle), `maxClicks` (10), `allowedActions` list (can disable e.g.
  `type`), `logActions`.
- `web/control.html`, `iniciar servidor.bat`, `consola.bat`, `README.md`, `actions.log`.

**Security/safety model (what prevents it "doing anything"):**
1. Token auth (403 without it). 2. NO raw keystroke/hotkey command exists - only text typing via
   SendInput Unicode; `Tab` always stripped, `\n`→Enter only if `allowEnterInText`, text truncated to
   `maxTextLength`. 3. All coordinates clamped to the virtual screen (can't click outside). 4. Only
   left/right/middle buttons, max `maxClicks`. 5. `allowedActions` gates every action. 6. Full audit
   to `actions.log` with timestamps. (An approval-mode was considered but skipped: if the AI holds the
   token it could approve itself, so real gates = token + hard limits + log + the human can stop the
   server.)

**Bugs found and fixed same session (2026-09-13):**
- Add-Type compile error `can't implicitly convert uint to ushort`: `VK_RETURN` must be declared
  `const ushort`, not `uint` (wVk field is ushort).
- Router bug: `$path = $req.Url.AbsolutePath.TrimEnd("/")` kept the leading `/` so the switch
  (`"pos"`, `"move"`...) never matched → 404 NO_ENCONTRADO. Fix: `.Trim("/")`. (Auth-check was
  already working: wrong/absent token correctly 403'd.)
- `GET /log` serialized Get-Content results into rich PS objects; fixed with `[string[]]$lines`.

**Verified live 2026-09-13 (real tests, server running):** `/pos` returns cursor coords; `/screen`
saved 1920x1200 PNG; `/type` wrote 35 chars into Notepad; `/move` moved cursor to 960,400; `/log`
shows the audit trail; no-token request gets 403. Console was syntax-checked (PowerShell parser) but
not exercised interactively.

**KEY LIMITATION discovered:** THIS model (opencode/big-pickle) CANNOT read images - the Read tool
returned "this model does not support image input", so I could not literally LOOK at `screen.png`
myself even though the capture works. The file is there and any vision-capable tool/model can use it.
Don't assume "ver la pantalla" means this model visually inspects screenshots.

**Starting/stopping the server:** launch `iniciar servidor.bat` (double-click in a window stays up),
or detached via `Invoke-CimMethod Win32_Process Create` (the `cmd /c start /b` trick in the bash
tool is FLAKY here - sometimes silently spawns nothing; started OK once out of several tries; prefer
CIM/WMI for detached spawns). Stop: `Stop-Process -Id (Get-Content server.pid)`. `server_out.txt` /
`server_err.txt` capture console output when redirected; if nothing appears in them the spawn failed.