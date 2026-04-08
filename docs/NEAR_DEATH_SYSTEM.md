# Near-Death System

This document describes the addon near-death and RCT Level 3 progression system implemented primarily in [`RCTLevel3Handler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:53), with client state sync through [`NearDeathPacket.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/NearDeathPacket.java:19), [`NearDeathClientState.java`](../src/main/java/net/mcreator/jujutsucraft/addon/NearDeathClientState.java:14), and cooldown integration inside [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java:47).

This subsystem is closely coupled to:

- limb severing, especially forced head sever on failed recovery,
- Reverse Cursed Technique status checks,
- Black Flash cooldown interactions,
- advancement-based unlock flow for RCT Level 3,
- client HUD state synced through addon packets.

## Source map

| Category | Primary files |
|---|---|
| server logic | [`RCTLevel3Handler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:53) |
| live-state packet | [`NearDeathPacket.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/NearDeathPacket.java:19) |
| client cache | [`NearDeathClientState.java`](../src/main/java/net/mcreator/jujutsucraft/addon/NearDeathClientState.java:14) |
| cooldown sync | [`ModNetworking.NearDeathCdSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:801) |
| cooldown calculations | [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java:48) |
| limb consequence | [`LimbLossHandler.severLimb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:159) |

## High-level behavior

The near-death system gives eligible players a final survival window instead of dying immediately. If they heal enough with Reverse Cursed Technique before the timer expires, they survive and enter cooldown. If they fail, the system ends the state and forces a lethal head sever.

## Core constants

[`RCTLevel3Handler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:54) defines the main thresholds.

| Constant | Value | Meaning |
|---|---:|---|
| `NEAR_DEATH_WINDOW` | `20` | number of ticks the player has to recover |
| `NEAR_DEATH_COOLDOWN` | `18000` | cooldown after a successful near-death recovery |
| `HEAL_THRESHOLD` | `4.0f` | HP required to count as stabilized |
| `RCT_L3_UNLOCK_COUNT` | `20` | close-call count needed to unlock RCT Level 3 |
| `CLOSE_CALL_COOLDOWN` | `200` | minimum spacing between counted close calls |

Related cooldown-side combat constant from [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java:58):

| Constant | Value | Meaning |
|---|---:|---|
| `ND_COOLDOWN_REDUCTION` | `120` | generic BF-related near-death cooldown reduction |

There is also a separate attack-side reduction path documented in [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md), where [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:139) applies a `600` tick reduction in its post-hit BF flow.

## Persistent keys

[`RCTLevel3Handler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:59) stores state in persistent player NBT.

| Key | Meaning |
|---|---|
| `jjkbrp_near_death` | whether near-death mode is active |
| `jjkbrp_near_death_ticks` | remaining ticks in the active near-death window |
| `jjkbrp_near_death_cd` | remaining cooldown ticks before the next near-death trigger is allowed |
| `jjkbrp_rct_close_call` | accumulated close-call count toward RCT Level 3 |
| `jjkbrp_final_death` | guard flag to allow the forced real death after failed near-death |
| `jjkbrp_prev_crit_rct` | tracks whether the player was previously in critical HP while using RCT |
| `jjkbrp_last_close_call_tick` | last tick at which a close call was counted |
| `jjkbrp_bf_nd_handled` | prevents cooldown reduction from being double-applied in BF flow |
| `jjkbrp_bf_regen_boost` | BF-related regen-side marker |

## Activation requirements

Near-death activation is handled in [`onPlayerDeath(LivingDeathEvent)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:66).

A death is intercepted only if all of the following are true:

| Requirement | Rule |
|---|---|
| entity is a `ServerPlayer` | non-player deaths are ignored |
| server is running | guards shutdown edge cases |
| not already resolving final death | `jjkbrp_final_death == false` |
| not already on cooldown | `jjkbrp_near_death_cd <= 0` |
| has addon advancement | `jjkblueredpurple:rct_level_3` must be unlocked |

There is also a special branch:

| Case | Result |
|---|---|
| player is already in near-death and another death event occurs | death is canceled and HP is forced to `1.0f` |

## Activation sequence

When the near-death trigger succeeds, [`onPlayerDeath(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:66) performs these exact steps:

1. cancel death event,
2. set player health to `1.0f`,
3. attempt reflective write of `LivingEntity.portalTime = 40`,
4. remove [`MobEffects.REGENERATION`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:102),
5. remove [`MobEffects.ABSORPTION`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:103),
6. set `jjkbrp_near_death = true`,
7. set `jjkbrp_near_death_ticks = 20`,
8. apply [`MobEffects.MOVEMENT_SLOWDOWN`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:106) for `40` ticks at amplifier `0`,
9. send [`NearDeathPacket(true, 20)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:107),
10. display `§c§l♥ Near Death` to the player.

Equivalent activation snapshot:

```java
health = 1.0f;
nearDeath = true;
nearDeathTicks = 20;
movementSlowdown = 40 ticks;
```

## Tick lifecycle

The near-death runtime is processed in [`onPlayerTick(LivingTickEvent)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:111).

### Cooldown decrement

Every server tick, if `jjkbrp_near_death_cd > 0`, it is decremented by `1`.

### Branch split

The tick handler then splits into two modes:

| Mode | Condition | Purpose |
|---|---|---|
| inactive near-death | `jjkbrp_near_death == false` | close-call tracking and RCT Level 3 unlock progress |
| active near-death | `jjkbrp_near_death == true` | active survival countdown and fail/succeed resolution |

## Success condition

A player survives near-death when this condition becomes true during the active state:

```java
player.getHealth() >= 4.0f
&& player.hasEffect(REVERSE_CURSED_TECHNIQUE)
```

That is checked in [`RCTLevel3Handler.java:142`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:142).

If true, the handler calls [`exitNearDeath(player, true)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:204).

## Failure condition

If active near-death ticks reach `0` or less, the handler:

1. calls [`exitNearDeath(player, false)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:204),
2. sets `jjkbrp_final_death = true`,
3. retrieves limb capability,
4. forces [`LimbLossHandler.severLimb(..., LimbType.HEAD, ...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:160).

This is the explicit coupling between the near-death system and the limb system. A failed recovery does not simply “let the old death continue”; it converts failure into a head-sever death path.

## Near-death countdown loop

While active, each tick does the following:

| Action | Frequency |
|---|---|
| decrement `jjkbrp_near_death_ticks` | every tick |
| spawn head blood drip | every tick through limb capability block |
| spawn head blood burst | every `2` ticks |
| send [`NearDeathPacket(true, ticks)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:155) | every `2` ticks |

That means the client timer is kept continuously synchronized rather than being purely client-side.

## Exit logic

### [`exitNearDeath(ServerPlayer, boolean)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:204)

This helper always does the following:

- set `jjkbrp_near_death = false`
- set `jjkbrp_near_death_ticks = 0`
- remove `MOVEMENT_SLOWDOWN`
- send [`NearDeathPacket(false, 0)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:209)
- call [`ModNetworking.sendNearDeathCdSync(player)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:567)

If `survived == true`, it additionally:

- sets `jjkbrp_near_death_cd = 18000`
- displays `§a♥ Reversed! §eSurvived Near-Death!`

## Actual-death guard

The system uses `jjkbrp_final_death` to avoid endlessly canceling death.

Behavior in [`onPlayerDeath(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:77):

| Condition | Result |
|---|---|
| `jjkbrp_final_death == true` | flag cleared, event not intercepted, real death allowed |

Additionally, [`onActualDeath(LivingDeathEvent)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:235) clears cooldown only when the player dies outside active near-death:

```java
if (!nearDeathActive) {
    nearDeathCooldown = 0;
}
```

## Close-call tracking and RCT Level 3 unlock

The subsystem also tracks “close calls” before RCT Level 3 is unlocked.

### Gate conditions for counting a close call

Inside the inactive branch of [`onPlayerTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:126), a close call is counted only if all of these are true:

| Requirement | Rule |
|---|---|
| does **not** already have `jjkblueredpurple:rct_level_3` | still working toward unlock |
| has base advancement `jujutsucraft:reverse_cursed_technique_2` | prerequisite |
| has base advancement `jujutsucraft:sorcerer_grade_special` | prerequisite |
| previously was in critical RCT | `jjkbrp_prev_crit_rct == true` |
| still has RCT effect | active RCT |
| current HP >= `4.0f` | recovered over threshold |
| enough time since last count | `player.tickCount - last_close_call_tick > 200` |

### Critical-RCT detection

The handler computes:

```java
nowCritRct = rct && hp > 0.0f && hp < maxHp * 0.1f;
```

So “critical RCT” means the player is alive, under `10%` max HP, and currently under Reverse Cursed Technique.

### Unlock threshold

If the close-call count reaches `20`, [`checkAndGrantRCTLevel3(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:164) grants:

```text
jjkblueredpurple:rct_level_3
```

and displays:

```text
§b§l★ RCT Mastery Unlocked! ★
```

## Close-call progress UI

[`notifyCloseCall(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:181) builds a milestone-dependent message using [`buildProgressBar(int, int)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:189).

### Progress bar format

The progress bar always uses `20` cells:

- filled: `█`
- empty: `░`

Formula:

```java
filled = min(20, current * 20 / max)
empty = 20 - filled
```

### Message tiers

| Progress tier | Message style |
|---|---|
| default | basic `Close Call current/20 [bar]` |
| `>= 50%` | adds remaining count |
| `>= 75%` | highlights “more until RCT Mastery” |
| `>= 90%` | emphasizes final stretch |
| exactly `19` | `☠ LAST CHANCE!` |

## Advancement helpers

The system checks advancements using [`hasAdvancement(ServerPlayer, String)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:221) and awards them with [`grantAdvancement(ServerPlayer, String)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:253).

### Advancement IDs involved

| Advancement ID | Purpose |
|---|---|
| `jjkblueredpurple:rct_level_3` | unlocks near-death itself |
| `jujutsucraft:reverse_cursed_technique_2` | prerequisite for close-call counting and unlock |
| `jujutsucraft:sorcerer_grade_special` | prerequisite for close-call counting and unlock |

## Networking and client state

### Live near-death packet

[`NearDeathPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/NearDeathPacket.java:19) is the real-time near-death state packet.

#### Fields

| Field | Type |
|---|---|
| `active` | `boolean` |
| `ticksRemaining` | `int` |

#### Wire format

```java
boolean active;
int ticksRemaining;
```

On the client, [`NearDeathPacket.handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/NearDeathPacket.java:37) forwards into [`NearDeathClientState.update(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/NearDeathClientState.java:18).

### Client cache

[`NearDeathClientState`](../src/main/java/net/mcreator/jujutsucraft/addon/NearDeathClientState.java:14) stores:

| Field | Meaning |
|---|---|
| `active` | whether near-death overlay/state is active |
| `ticksRemaining` | client-visible countdown |

It exposes:

- [`isActive()`](../src/main/java/net/mcreator/jujutsucraft/addon/NearDeathClientState.java:23)
- [`getTicksRemaining()`](../src/main/java/net/mcreator/jujutsucraft/addon/NearDeathClientState.java:27)
- [`clientTick()`](../src/main/java/net/mcreator/jujutsucraft/addon/NearDeathClientState.java:31)

The client tick method simply decrements the local timer while active, but the server continues to refresh it every `2` ticks during real near-death.

### Cooldown sync packet

The longer-duration cooldown is not carried by [`NearDeathPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/NearDeathPacket.java:19). Instead, it is sent through [`NearDeathCdSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:801).

Its fields are:

| Field | Meaning |
|---|---|
| `cdRemaining` | current cooldown remaining |
| `cdMax` | cooldown maximum |
| `rctLevel3Unlocked` | whether the player has unlocked the feature |

Client storage is handled by [`ClientPacketHandler.updateNearDeathCooldown(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:37) and [`ClientNearDeathCdCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:101).

## Black Flash interactions

The near-death system is affected by Black Flash combat logic in [`CooldownTrackerEvents.onLivingHurt(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java:157).

### BF-based cooldown reduction

If the attacker is a `ServerPlayer` and a Black Flash just procced, the code checks:

```java
zone != null && zone.getDuration() >= 5990
```

If true, and `jjkbrp_bf_nd_handled` is false, then:

```java
newCd = max(0, currentNdCd - 120)
```

So the generic BF-side near-death cooldown reduction is exactly `120` ticks in this handler.

The same branch also sets:

- `jjkbrp_bf_regen_boost = true`
- `jjkbrp_bf_nd_handled = false`

### Important mismatch with ranged BF path

There is a documented mismatch across systems:

| Source | Reduction |
|---|---:|
| [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java:173) | `120` |
| [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:139) | `600` |

That discrepancy should be treated as real behavior, not normalized away in documentation.

## API-style method reference

| Method | Role |
|---|---|
| [`onPlayerDeath(LivingDeathEvent)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:66) | initial near-death interception |
| [`onPlayerTick(LivingTickEvent)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:111) | active countdown + passive unlock tracking |
| [`checkAndGrantRCTLevel3(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:164) | advancement unlock gate |
| [`notifyCloseCall(ServerPlayer, int)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:181) | progress feedback |
| [`buildProgressBar(int, int)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:189) | textual UI builder |
| [`exitNearDeath(ServerPlayer, boolean)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:204) | cleanup and cooldown application |
| [`isInNearDeath(Player)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:217) | convenience state check |
| [`hasAdvancement(ServerPlayer, String)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:221) | safe advancement lookup |
| [`grantAdvancement(ServerPlayer, String)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:253) | full award helper |

## State machine summary

| State | Entry | Exit |
|---|---|---|
| normal | default state | enters near-death on intercepted fatal hit |
| close-call tracking | normal + critical-RCT recovery path | increments unlock counter, remains normal |
| near-death active | fatal hit intercepted with RCT Level 3 unlocked | succeeds on `HP >= 4.0f && RCT active`, or fails when ticks reach `0` |
| near-death cooldown | successful survival | returns to normal after `18000` ticks |
| final death | failed near-death sets `jjkbrp_final_death` | one real death event clears the flag |

## Relationship to limb system

The limb subsystem is not just cosmetic here. Near-death failure explicitly uses the limb framework to make death concrete.

| Near-death event | Limb consequence |
|---|---|
| active near-death ticking | repeated head blood particles via limb particle helpers |
| failed recovery | forced head sever via [`LimbLossHandler.severLimb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:159) |
| head sever without active RCT | lethal outcome |

See [`LIMB_SYSTEM.md`](LIMB_SYSTEM.md) for the detailed head-sever branch.

## Practical balancing summary

The near-death system is shaped by three numbers:

1. **window:** `20` ticks
2. **survival threshold:** `4.0f` HP with active RCT
3. **cooldown:** `18000` ticks after success

That makes it a rare, extremely short, execution-heavy recovery mechanic rather than a passive cheat-death effect.

The unlock path is similarly strict:

- critical RCT under `10%` HP,
- recover to at least `4.0f` HP,
- repeat `20` times,
- spaced by more than `200` ticks.

## Cross references

- limb loss and head sever: [`LIMB_SYSTEM.md`](LIMB_SYSTEM.md)
- combat/BF interactions: [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
- networking details: [`NETWORKING.md`](NETWORKING.md)
- packet caches and utilities: [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- numeric thresholds: [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
- overall addon coupling: [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)
