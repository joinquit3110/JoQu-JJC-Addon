# JJK Blue/Red/Purple Addon

A Jujutsu Craft Forge mod addon that adds advanced mechanics inspired by the JJK anime/manga.

## Features

### Limb Loss System (`addon/limb/`)
- **Severance**: When taking heavy damage at low HP, players can lose limbs (arms, legs, head).
- **RCT Regeneration**: Reverse Cursed Technique automatically starts regenerating severed limbs. Progress is tracked in phases: bone → muscle → flesh.
- **Gameplay effects**:
  - Missing legs: movement speed severely reduced (1 leg: -60%, 2 legs: -95%). Jump height reduced/blocked.
  - Missing arms: held items are immediately dropped and continuously prevented.
- **Rendering**: Severed limbs appear as player-skinned dropped entities; regenerating limbs show solid-color overlays (bone/muscle/flesh) during regen.
- **RCT Level 3**: After 20 close-call survivals (RCT healing at critical HP), players unlock the ability to survive a lethal blow with a 1-second near-death window.

### Skill Wheel (`addon/SkillWheelScreen.java`)
- Radial technique selector (TAB key) with per-skill cooldown indicators, spirit summon pages for Geto, and confirm effects.

### Black Flash System (`addon/CooldownTrackerEvents.java`)
- BF chance increases while in combat and decays when out. Max 15% (30% for Yuji).
- After 500 successful BF hits (200 for Yuji), Black Flash Mastery unlocks.
- Mastery allows physical attack BF charging via the original game's mechanic.

### Blue/Red/Purple (`addon/BlueRedPurpleNukeMod.java`)
- **Red**: Enhanced flying orb (pushes mobs, grows over distance, spectacular explosion); Crouch + not fully charged = original Red; Crouch + fully charged = teleport behind target + explosion.
- **Blue**: Linger phase (waits for Red); Crouch + fully charged = aimed Blue with block destruction and mob pull.
- **Purple Nuke**: Fully charged Red collides with fully charged linger Blue → Hollow Purple is spawned. Requires >2000 CE and <30% HP.
- **Infinity Crusher** (Shift + Infinity): Directional crush cone. Mobs in the cone are pushed/flattened. After 15 ticks of contact they become "hard-locked" and are crushed in place. CE cost and damage scale up over time.

### Near-Death / RCT Mastery (`addon/limb/RCTLevel3Handler.java`)
- 20 close-call survivals (heal from below 4 HP while below 30% max HP with RCT active) unlock RCT Level 3.
- When unlocked, dying triggers a 20-tick near-death window. Survive with RCT → normal death otherwise.

## Project Structure

```
src/main/java/net/mcreator/jujutsucraft/
└── addon/
    ├── BlueRedPurpleNukeMod.java   — Main mod class: Red/Blue/Purple/Infinity Crusher
    ├── ClientEvents.java             — Client input and rendering registration
    ├── ModNetworking.java           — All network packets and client caches
    ├── SkillWheelScreen.java         — Radial technique selection UI
    ├── TechniqueRegistry.java        — Character-specific technique definitions
    ├── CooldownTrackerEvents.java  — Black Flash charge/combat mechanics
    ├── NearDeathOverlay.java         — Near-death vignette (client)
    ├── NearDeathCdOverlay.java       — RCT Level 3 cooldown ring (client)
    ├── NearDeathClientState.java     — Near-death client state
    ├── BlackFlashHudOverlay.java     — BF chance HUD (client)
    ├── limb/
    │   ├── LimbLossHandler.java       — Server-side severance + regen events
    │   ├── LimbGameplayHandler.java   — Movement/item penalties from limb loss
    │   ├── LimbRenderHandler.java     — Hide/show limbs during rendering (client)
    │   ├── LimbRegrowthLayer.java     — Bone/muscle/flesh overlay layer (client)
    │   ├── LimbData.java              — Limb state + regen progress per entity
    │   ├── LimbCapabilityProvider.java — Forge capability attachment
    │   ├── LimbSyncPacket.java        — Network sync for limb state
    │   ├── LimbType.java              — LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, HEAD
    │   ├── LimbState.java             — INTACT, SEVERED, REVERSING
    │   ├── LimbParticles.java         — Blood, regen, completion burst particles
    │   ├── LimbSounds.java            — Sever/regen sound effects
    │   ├── ClientLimbCache.java       — Client-side limb state cache
    │   ├── SeveredLimbEntity.java     — Dropped severed-limb entity
    │   ├── SeveredLimbRenderer.java   — Custom renderer for severed limbs
    │   ├── LimbEntityRegistry.java    — Entity type registration
    │   ├── RCTLevel3Handler.java      — Close-call tracking + near-death logic
    │   └── NearDeathPacket.java       — Near-death state network packet
    └── mixin/
        ├── RedEntityMixin.java                       — Override RedEntity AI
        ├── BlueEntityMixin.java                      — Cap Blue aim distance
        ├── RangeAttackProcedureMixin.java           — BF zone duration tracking
        ├── RangeAttackBlackFlashChanceMixin.java    — Adjust BF roll chance
        ├── AttackWeakProcedureMixin.java             — Adjust attack charge rates
        └── StartCursedTechniqueProcedureMixin.java  — Capture cooldowns on use
```

## Requirements

- Minecraft Forge (1.20.1 or compatible)
- Jujutsu Craft mod installed
- Java 17+

## Building

```bash
./gradlew build
```

The output JAR will be in `build/libs/`.

## Credits

- Base mod: Jujutsu Craft
- Addon author: [joinquit3110](https://github.com/joinquit3110)
