# MapExit 0.27.0 verification

Seven optional challenge types use isolated first-person mazes: lost maze, seven-second memory preview, collapsing paths, ordered rune seals, finite torchlight with real visibility changes, three timed physical mechanisms, and a finite step budget. Portals occur once in each 11–14, 26–29 and 41–44 window, with stable cycle schedules and uniform kind selection.

Core regressions cover reachable/non-destructive portal placement, kind distribution, all seven successful routes, intro cancellation, pause/preview boundaries, exact step exhaustion, collapse deadlines, seal order/penalties, torch refills, three hazard windows and mistake limits, host restoration, legacy saves, distinct maps after decode, one-time rewards, death/inheritance and training isolation.

Android instrumentation covers actual portal entry, pause/recreate/resume, memory-map hiding, step planning, normal/compact scenes for every kind with no minimap or combat controls, real torch dimming, rune interaction, wait/cross controls for all mechanisms, reward equip and no-loss failure return. Authored atlas, core suite, lint and APK assembly run before emulator tests. CI is authoritative for the result.

The challenge timer is an active-play clock. Intro, preview (for the escape clock), explicit pause and background time do not consume it. Preview itself permits seven seconds of visible-map time. Each crossing and visual mechanism uses the same saved phase. Rewards are applied atomically with their saved receipt before displaying results.
