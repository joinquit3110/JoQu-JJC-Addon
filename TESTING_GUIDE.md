# Addon Testing Guide — jjkblueredpurple

This document covers all testable features of the `jjkblueredpurple` addon mod. Use it as a checklist for QA.

**Mod file:** `mods/jjkblueredpurple-1.1.0.jar`  
**Minecraft version:** 1.20.1 Forge  
**Required base mod:** JujutsuCraft (jujutsucraft)  
**Permission level for commands:** OP level 2 (use `/op <player>` or run from server console)

---

## Table of Contents

1. [Prerequisites & Setup](#prerequisites--setup)
2. [Yuta Copy System](#yuta-copy-system)
3. [Black Flash System](#black-flash-system)
4. [Gojo Teleport (Double-Shift Blink)](#gojo-teleport-double-shift-blink)
5. [Skill Wheel](#skill-wheel)
6. [Domain Mastery](#domain-mastery)
7. [Multiplayer-Specific Tests](#multiplayer-specific-tests)

---

## Prerequisites & Setup

### Becoming Yuta (CT 5)

Use the base mod's character selection or set technique via command:
```
/data merge entity @s {ForgeData:{PlayerCurseTechnique:5.0d}}
```
Then re-select Yuta from the character menu to ensure all variables sync.

### Becoming Gojo (CT 2)

```
/data merge entity @s {ForgeData:{PlayerCurseTechnique:2.0d}}
```
Then re-select Gojo from the character menu.

### Useful base mod commands

| Command | Purpose |
|---------|---------|
| `/effect give @s jujutsucraft:domain_expansion 9999 0` | Force domain expansion effect |
| `/effect clear @s jujutsucraft:domain_expansion` | Remove domain |
| `/effect give @s jujutsucraft:cursed_technique 9999 0` | Force cursed technique active |
| `/data merge entity @s {ForgeData:{skill_domain:5.0d}}` | Set domain type to Yuta |
| `/data merge entity @s {ForgeData:{friend_num:12345.0d}}` | Set friend_num for shikigami ownership |

### Addon-specific commands

| Command | Permission | Purpose |
|---------|-----------|---------|
| `/jjkaddon_yuta_fake <techniqueId>` | OP 2 | Spawn a fake player with given CT (1-19) for copy testing |
| `/jjkaddon_yuta_fake_rct <techniqueId>` | OP 2 | Same but with RCT variant (higher HP) |
| `/jjkaddon_yuta_copy_reset` | OP 2 | Clear all copied technique records and cooldowns |
| `/jjkbrp_bf_reset_timing_cd` | OP 0 | Reset Black Flash timing cooldown |
| `/jjkbrp_bf_test_effects completion` | OP 0 | Test BF completion visual effects |
| `/jjkbrp_bf_test_effects blackflash` | OP 0 | Test BF hit feedback |
| `/jjkbrp_bf_test_effects released` | OP 0 | Test BF release feedback |
| `/jjkbrp_bf_test_effects failed` | OP 0 | Test BF fail feedback |
| `/jjkbrp_bf_test_effects ring` | OP 0 | Test BF timing ring overlay |
| `/domainmastery addxp <amount>` | OP 2 | Add domain mastery XP |
| `/domainmastery setlevel <level>` | OP 2 | Set domain mastery level |
| `/domainmastery setprop <prop> <level>` | OP 2 | Set a domain property level |
| `/domainmastery setnegative <prop> <level>` | OP 2 | Set negative property |
| `/domainmastery reset` | OP 2 | Reset all domain mastery |
| `/domainmastery info` | OP 2 | Show current domain mastery info |

---

## Yuta Copy System

### Setup for Yuta copy testing

1. Be Yuta (CT 5) and have Rika summoned (select Rika from technique wheel, skill 10).
2. Spawn fake targets: `/jjkaddon_yuta_fake 6` (Ten Shadows), `/jjkaddon_yuta_fake 2` (Limitless), etc.
3. Kill the fake targets while Rika is active — a "Cursed Technique Copy" catalyst item drops.
4. Right-click the catalyst item on Rika to feed it → Rika learns a random move from that technique.
5. Open skill wheel (hold V or configured key) → scroll to "Rika Copies" page.

### Test Cases

#### TC-Y1: Domain sword random cast

**Precondition:** Yuta has 3+ different copied techniques (e.g., Ten Shadows: Max Elephant, Ten Shadows: Rabbit Escape, Limitless: Blue).

1. Activate Yuta's domain (Authentic Mutual Love) — skill 20 from wheel.
2. Pick up the domain sword from the decoration entity (right-click the sword entity in domain).
3. Right-click the sword repeatedly (10+ times).
4. **Expected:** Different techniques activate each time (check chat messages). Should NOT always be the same one. Check server log for `[Yuta Copy] Domain sword cast player=... recordUuid=...` — recordUuid should vary.

#### TC-Y2: Shikigami cap 2 with auto-cull

**Precondition:** Yuta has copied at least 3 different Ten Shadows shikigami (e.g., Divine Dog White, Nue, Max Elephant).

1. Summon shikigami #1 from wheel (e.g., Divine Dog White).
2. Wait for it to spawn and confirm it exists.
3. Summon shikigami #2 (e.g., Nue).
4. Confirm both exist.
5. Summon shikigami #3 (e.g., Max Elephant).
6. **Expected:** One of the existing shikigami (the one with lowest weight) auto-despawns with portal particles. The new one spawns normally. You should NOT get a "blocked" message.

**Weight table:**
- Max Elephant = 2.0 (counts as 2 slots)
- Rabbit Escape = 0.025 (almost free)
- All others = 1.0

#### TC-Y3: Recall shikigami with portal VFX

1. Summon a copied Ten Shadows shikigami (e.g., Nue).
2. Open wheel → the entry now shows "Cancel: Nue" in orange color.
3. Click "Cancel: Nue".
4. **Expected:** Purple portal particle burst appears at the shikigami's position (80 particles for non-rabbit, 10 for rabbit). Shikigami disappears within 1-2 ticks.

#### TC-Y4: Skill wheel colors

1. Have multiple copied techniques from different sources (Ten Shadows, Limitless, Disaster Flames, Blood Manipulation).
2. Open skill wheel → scroll to Rika Copies page.
3. **Expected:**
   - Ten Shadows entries (Dog, Nue, Elephant, etc.) → teal/green color
   - Limitless: Blue → blue color
   - Limitless: Red → red color
   - Disaster Flames entries → red/orange color
   - Blood Manipulation entries → dark red/blood color
   - Any entry in "Cancel" state → orange color (0xFB923C)
   - NOT all purple anymore

#### TC-Y5: Despawn shikigami when Rika dies / domain ends

1. Summon Rika + summon 1-2 copied shikigami.
2. Kill Rika (or let Rika despawn naturally / use `/kill @e[type=jujutsucraft:rika]`).
3. **Expected:** All copied shikigami despawn silently within 1-2 ticks (vanilla portal particles from FollowEntityProcedure).

Alternative test:
1. Activate domain → summon shikigami inside domain.
2. End domain (wait for effect to expire or `/effect clear @s jujutsucraft:domain_expansion`).
3. Wait 5 seconds (grace period).
4. **Expected:** After grace period, shikigami despawn.

#### TC-Y6: Domain sword survives relog

1. Activate Yuta's domain (Authentic Mutual Love).
2. Pick up domain sword.
3. Disconnect from server (or quit to title in singleplayer).
4. Reconnect within 30 seconds (domain effect should still be active).
5. **Expected:** Domain sword is still in your inventory. Decorations (gravestones, awaji knots) still present.

#### TC-Y7: Copy reset command

1. Have some copied techniques.
2. Run `/jjkaddon_yuta_copy_reset`.
3. **Expected:** All records cleared, wheel shows no Rika Copies page, cooldowns reset.

---

## Black Flash System

### How Black Flash works in this addon

- When you attack with a cursed technique active, there's a chance to trigger Black Flash (Zone effect duration jumps to ~6000).
- The addon adds a **timing ring** overlay: a circular indicator appears on screen. You must release attack at the right moment.
- After enough successful Black Flashes, you unlock **mastery** (permanent improved chance).
- The addon tracks BF flow (consecutive hits) and provides visual/audio feedback.

### Test Cases

#### TC-BF1: Timing ring appears

1. Be any sorcerer with a technique active.
2. Attack a mob.
3. **Expected:** If Zone effect procs, a timing ring appears on the HUD (circular arc around crosshair).

#### TC-BF2: Visual feedback commands

1. Run `/jjkbrp_bf_test_effects blackflash` — should show success hit feedback (particles + sound).
2. Run `/jjkbrp_bf_test_effects failed` — should show failure feedback.
3. Run `/jjkbrp_bf_test_effects ring` — should show the timing ring overlay.
4. Run `/jjkbrp_bf_test_effects completion` — should show mastery completion effects.

#### TC-BF3: Reset timing cooldown

1. After a Black Flash attempt (success or fail), there's a cooldown before next attempt.
2. Run `/jjkbrp_bf_reset_timing_cd`.
3. **Expected:** Cooldown resets, can attempt again immediately.

#### TC-BF4: Domain mastery BF chance boost

1. Run `/domainmastery setprop bf_chance_boost 5`.
2. Activate domain → attack inside domain.
3. **Expected:** Higher Black Flash proc rate inside domain (hard to verify numerically, but check that `addon_bf_chance` in player NBT is higher than base).

---

## Gojo Teleport (Double-Shift Blink)

### How it works

- Only works when playing as **Gojo (CT 2)** with **Infinity active** (technique selected, not on cooldown).
- **Double-tap Shift (Sneak)** within 12 ticks (~0.6 seconds) to arm the teleport.
- After a 5-tick delay, Gojo blinks forward up to **18 blocks** in the look direction (raycast, stops at walls).
- Costs **60 CE** and has a **3-second cooldown** (60 ticks).
- Plays enderman teleport sound + portal particles at source and destination.

### Test Cases

#### TC-GT1: Basic teleport

1. Be Gojo with Infinity active (select Infinity from wheel, technique 5).
2. Look at an open area ~10 blocks away.
3. Double-tap Shift quickly (press-release-press within 0.6s).
4. **Expected:** After ~0.25s delay, you teleport to the target location. Enderman teleport sound plays. Portal particles at both source and destination.

#### TC-GT2: Wall collision

1. Face a wall 3 blocks away.
2. Double-tap Shift.
3. **Expected:** Teleport stops at the wall (does not go through). You appear just before the wall.

#### TC-GT3: Cooldown

1. Teleport once.
2. Immediately try to double-tap Shift again.
3. **Expected:** Nothing happens (3-second cooldown). After 3 seconds, teleport works again.

#### TC-GT4: CE cost

1. Set CE to exactly 60: check current CE, drain to 60.
2. Double-tap Shift.
3. **Expected:** Teleport succeeds, CE drops to 0.
4. Try again with CE < 60.
5. **Expected:** Teleport does NOT fire (insufficient CE).

#### TC-GT5: Only works with Infinity

1. Switch Gojo to Blue (technique 6) or any non-Infinity technique.
2. Double-tap Shift.
3. **Expected:** Nothing happens (teleport only works with Infinity selected/active).

#### TC-GT6: Does not work for non-Gojo

1. Switch to any other character (Sukuna, Yuta, etc.).
2. Double-tap Shift.
3. **Expected:** Nothing happens.

---

## Skill Wheel

### How to open

Hold the configured key (default: **V**) to open the radial skill wheel. Release to confirm selection.

### Test Cases

#### TC-SW1: Multi-page navigation

1. Be Yuta with copied techniques.
2. Open wheel.
3. **Expected:** Page 1 = base techniques. Scroll mouse wheel → Page 2+ = Rika Copies. Page indicator dots at bottom.

#### TC-SW2: Cooldown display

1. Use a technique that has cooldown.
2. Open wheel immediately after.
3. **Expected:** The used technique's slice is grayed out with a countdown timer (e.g., "2.5s"). Cannot select it until cooldown expires.

#### TC-SW3: Geto spirit pages (if testing Geto)

1. Be Geto (CT 12) with stored cursed spirits.
2. Open wheel.
3. **Expected:** Multiple pages — Techniques, Lower Grade spirits, Upper Grade spirits. Each spirit shows its grade color.

---

## Domain Mastery

### Overview

Domain Mastery is a progression system for domain expansion. Players earn XP by using domain, level up, and invest points into properties.

### Test Cases

#### TC-DM1: Open mastery screen

1. Have domain expansion unlocked (advancement `mastery_domain_expansion`).
2. Open the Domain Mastery screen (keybind or command).
3. **Expected:** Screen shows current level, XP bar, property cards with invest buttons.

#### TC-DM2: Add XP and level up

1. Run `/domainmastery addxp 1000`.
2. **Expected:** XP increases, may level up. Chat message confirms.

#### TC-DM3: Set property levels

1. Run `/domainmastery setprop victim_ce_drain 3`.
2. Run `/domainmastery info`.
3. **Expected:** Info shows victim_ce_drain at level 3.

#### TC-DM4: Reset

1. Run `/domainmastery reset`.
2. **Expected:** All properties reset to 0, points refunded, level/XP preserved.

---

## Multiplayer-Specific Tests

These tests require 2 players on a dedicated server or LAN.

#### TC-MP1: Two Yuta players independent stores

1. Player A and Player B both set CT to 5 (Yuta).
2. Player A copies a technique (e.g., Limitless: Blue).
3. Player B opens wheel.
4. **Expected:** Player B does NOT see Player A's copied technique. Stores are independent.

#### TC-MP2: Rika ownership

1. Player A summons Rika.
2. Player B (also Yuta) tries to feed a catalyst to Player A's Rika.
3. **Expected:** Rejected with message "Only Yuta with his valid Rika can consume this."

#### TC-MP3: Shikigami despawn on Rika death (multiplayer)

1. Player A summons Rika + copied shikigami.
2. Player B kills Player A's Rika.
3. **Expected:** Player A's copied shikigami despawn. Player B's entities (if any) unaffected.

#### TC-MP4: Domain sword after relog (multiplayer)

1. Player A activates domain, picks up sword.
2. Player A disconnects and reconnects.
3. **Expected:** Sword still in inventory after reconnect (5-second grace period).

#### TC-MP5: Gojo teleport multiplayer visibility

1. Player A is Gojo, Player B is nearby watching.
2. Player A double-shift teleports.
3. **Expected:** Player B sees/hears the teleport (enderman sound, portal particles at both locations). Player A moves on both screens.

#### TC-MP6: Black Flash in multiplayer

1. Player A attacks a mob and procs Black Flash.
2. **Expected:** Only Player A sees the timing ring HUD. Both players see the Zone particle effects on the mob.

---

## Known Limitations

- Domain decorations (gravestones, awaji knots, floating swords) are spawned by the **base mod** — the addon does not control their lifecycle beyond the domain sword cleanup.
- Black Flash mastery threshold varies by character (Gojo/Sukuna need more hits than others).
- Gojo teleport ghost renderer is currently a no-op (server-side teleport + particles are authoritative).
- Copied Ten Shadows shikigami use the vanilla summon pipeline — their AI, attacks, and animations are identical to Megumi's.

---

## Reporting Bugs

When reporting issues, please include:
1. **Steps to reproduce** (exact sequence).
2. **Expected vs actual behavior**.
3. **Server log** snippet (look for `[Yuta Copy]`, `[jjkblueredpurple]`, or Java stack traces).
4. **Client log** if it's a visual/UI issue.
5. **Single-player or multiplayer** (and if multiplayer: dedicated server or LAN).
6. **Other mods installed** that might conflict.
