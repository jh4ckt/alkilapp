---
name: survival-system-kitchen-fight
description: "Sistema de vida/hambre/sed en PC y Android: barras, cocina con comida visible, peleas que matan, muerte y revive desde el chat - shipped como Windows v2.20.0 / Android v1.19"
metadata:
  node_type: memory
  type: project
  modified: 2026-09-05T21:30:00.000Z
---

Life/hunger/thirst survival system, built PC-first then ported to Android 1:1, shipped 2026-09-05
as **Windows v2.20.0 / Android v1.19** (release `v2.20.0`, combined, both artifacts attached -
differs from the older separate vX/Y tags; latest release title pattern =
"Alan Becker AIStickmans v2.20.0 (Windows) / v1.19 (Android) - <desc>"). Commit `d84ae23`
("vida/hambre/sed + pelea + cocina en ambos plataformas").

**Core rules (identical constants both platforms):**
- `SURVIVAL_POLL_MS=10000` wall-clock drain (pcSettings/survival.js on PC, `Prefs.startSurvivalLoop`
  in OverlayService coroutine on Android): hunger `-0.5`, thirst `-0.75`; once EITHER hits 0, hp
  `-1.5` per poll too. `dead = hp<=0` -> `state.kill()`. Feature gated by a user toggle
  (`ENABLE_SURVIVAL` env on PC / `survival_enabled` pref on Android), ON by default; toggling OFF
  resets everyone to 100/100/100 alive (`Prefs.resetAllSurvival`, PC does the same at startup).
- Forced sleep/wander do NOT drain faster; death outranks every other state in `tick()`
  (CharacterState.js/.kt return `FrameKind.Sleep(0)` while dead so the body lies down).
- **Dead characters**: AI loop skips their turns entirely (no decisions, no wander). The ONLY
  revive path is a chat message - it revives + lets the character answer that same round. PC:
  agentLoop.js dead-gate + a "💖 Revivir" button in chat.js/chat.html. Android: dead-gate in
  `tickCharacterAi` after consuming `PendingMessages`. Drag is ignored when dead
  (`handleTouch` returns false on ACTION_DOWN).
- Bars over the head, same colors on both: vida `#e53935`, hambre `#fb8c00`, sed `#1e88e5`
  (barW 20/barH 4/gap 3 dp in `SurvivalBarsView.kt`; renderer/character.js on PC). Dead = bold
  red X (`#F2282828`, stroke 4, radius ~0.35·min(w,h)).

**Actions (fight/eat/drink)** in `actions.schema.js`/`ActionsSchema.kt` + executor/OverlayService:
- `eat`: +~45 hunger (`EAT_GAIN=45`), `drink`: +~45 thirst. BOTH require being within
  `KITCHEN_DISTANCE_PX=150` of the kitchen - else a history note ("camina con walk_to primero")
  and nothing happens.
- `fight`: requires survival on, an existing target, target alive, and `dist <= FIGHT_DISTANCE_PX=100`
  (Android compares local `overlays[targetId]` only - can't punch a PC ghost). Damage
  `min(40,max(5,round(strength||12)))`; eater: target `say("¡Auch! (X de daño)")` +
  `setEmotion('trip')` + `setFace('angry','frown')`, attacker `setEmotion('angry')`, kill on 0.
- Failures go into history so the AI learns ("fight bloqueado: ..."/"eat bloqueado: ...").
- Survival context (life/hunger/thirst + kitchen position as % width) is injected as a NOTE in
  the per-turn context (extraContext on Android, agentLoop survivalNote on PC) - NOT baked into
  the narrative system prompts; the tool schemas' `desc` texts describe the rules.

**Visible kitchen/food (mirror of PC src/jsEngine/foodProp.js + kitchen.js + character.js):**
- Kitchen = small square window (`kitchen.json`/`kitchen-1.json`/`kitchen-2.json` rigs; `microwave.json`
  exists too), cycles station with a 500ms fade each time someone eats/drinks. Static on PC
  (`kitchen.js` standalone window), `KitchenOverlay.kt` on Android (bottom-right, 18% width).
- Food prop: `FoodPropOverlay.kt`/`foodProp.js`, `FOOD_RIG_DP=46`; pizza slides from the kitchen to
  the character's mouth (`foodMouthPos`: head-top, biased by lookRight), shrinks by a wedge
  (arc from -90°, radius `max(w,h)/2·scale+4`) bite by bite (`BITE_MS=700`, `EAT_BITES=6`, jiggle
  ±0.07 rad) while CharacterState plays `Chew(frame)` (`chew=6·sin`, torso -6+chew, arm1 -55+chew,
  arm2 +30, legs +6); cup tilts `DRINK_GULP_MS=1200` ×3 (`drinkPose`: gulp=4·sin, torso +12+gulp·0.5)
  during `Drink`. `eat`/`drink` durations 5000/4200 ms must match between FoodPropOverlay and
  CharacterState. Slot slides back out to the kitchen when done.
- `FOOD_CANDIDATES` cycles so consecutive meals switch food visibly (only pizza exists so far).

**New low-level capability on Android:** polygons. `RigModel` parses slim fields `tri`/`triF`/
`triU`/`thS`/`thE`/`uS`/`uE`/`rdS`/`rdE`; `RigLayout` gained `perpDir()`, `polygonCorners()`
(matches renderer/character.js's triangleCorners/trapezoidCorners math), `ellipseCenter()/Radii()`,
and bounds() covering Ellipse/Triangle/Trapezoid; `RigView` draws them (ellipse via rotated oval,
triangle/trapezoid via filled path + rounded-end discs). The kitchen/food rigs NEED this (Trapezoid
tops/backs, Triangle blades, Ellipse pepperoni) - without it they render as broken lines. Also
`RigView.setFigure()` was added (kitchen station swap) with `figure`/`restBounds` now `private var`.

**Canon Alan Becker lore:** `src/ai/characterLore.js` + Android `ai/CharacterLore.kt` prepend a
per-character "who you are" (Red, Orange, Green, Blue, Yellow, Purple, TCO, TDL, victim) BEFORE the
personality line - works even for fresh characters with no define_personality. Same lesson applied
here as the set_emotion one: schema/tool description alone doesn't make the AI behave, but lore is
prompt text so prepending it to personality reaches every provider automatically.

**Known caveats (NOT verified live on a real device/launch, same as several prior features):**
- No on-device visual confirmation of the food timeline, kitchen station fade, death X, or an
  actual fight/drain happening - build + syntax checks passed, logic mirrors the PC behavior which
  itself was also built but not live-verified before packaging.
- Android `fight` is local-only (can't target a PC ghost); PC fight targets any enabled CHARACTERS.
- `KitchenOverlay.getPosition()` returns the window CENTER (px, absolute screen coords) - distance
  checks compare against `state.x` (character anchor). Kitchen y is informational only.
- Setting the toggle off while the overlay service is running won't remove an already-created
  kitchen overlay (it's created at `setupOverlays()`; `onDestroy` detaches it) - a full restart
  after toggling is the enforced path, same as PC's "restart to apply" for the character list.