# Combat System

This document describes the combat-side architecture that the addon layers on top of the original JujutsuCraft damage, ranged attack, cooldown, and Black Flash pipeline.

The most important design fact is that the addon does **not** replace the whole combat engine. Instead, it patches high-value combat procedures and then synchronizes client HUD state through addon networking.

## Scope

This document focuses on:

- the original ranged combat entrypoint in [`RangeAttackProcedure.java`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/RangeAttackProcedure.java),
- addon interception through [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java),
- Black Flash chance accumulation and cooldown sync from [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java),
- domain-driven combat buffs from [`DomainMasteryMixin.applyBFChanceBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:999),
- HUD/client cache display through [`BlackFlashHudOverlay.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BlackFlashHudOverlay.java) and [`ClientPacketHandler.ClientBlackFlashCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:92),
- nearby combat-support mixins such as [`AttackWeakProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/AttackWeakProcedureMixin.java), [`StartCursedTechniqueProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/StartCursedTechniqueProcedureMixin.java), and [`OCostProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/OCostProcedureMixin.java).

## High-level combat flow

| Phase | Base or addon | Main file | Purpose |
|---|---|---|---|
| attack procedure begins | base | [`RangeAttackProcedure.java`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/RangeAttackProcedure.java) | resolves target hit, cursed-energy use, technique behavior, combat state |
| attack pre-processing | addon | [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:66) | adjusts sure-hit, domain behavior, open-domain effects, Purple safety, temporary radius scaling |
| base attack executes | base | [`RangeAttackProcedure.execute(...)`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/RangeAttackProcedure.java) | performs original damage/effect logic |
| attack post-processing | addon | [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:139) | restores temporary state, detects Black Flash event, updates counters, modifies near-death cooldown |
| ongoing combat tracking | addon | [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java) | maintains BF chance %, cooldowns, combat buildup and decay |
| client sync | addon | [`ModNetworking.sendBlackFlashSync(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:559) | pushes current BF state to client |
| HUD rendering | addon | [`BlackFlashHudOverlay.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BlackFlashHudOverlay.java) | renders percent/charge/mastery visualization |

## Original combat spine

The original mod routes ranged and many technique-driven offensive actions through [`RangeAttackProcedure.execute(...)`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/RangeAttackProcedure.java). In base JujutsuCraft this procedure is where sure-hit style logic, direct attack execution, and the original Black Flash roll are coupled together.

That original procedure matters because the addon still relies on it as the main execution spine. The mixin layer does **not** reimplement every branch. Instead, it temporarily changes the inputs and then lets the base logic run.

## Addon combat hook: [`RangeAttackProcedureMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java)

### Injection points

| Injection point | Lines | Purpose |
|---|---|---|
| `@Inject(method={"execute"}, at=@At("HEAD"), cancellable=true)` | [`RangeAttackProcedureMixin.java:66`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:66) | pre-attack validation and temporary combat-state mutation |
| `@Inject(method={"execute"}, at=@At("RETURN"))` | [`RangeAttackProcedureMixin.java:139`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:139) | restores state and performs addon post-hit effects |

### HEAD-stage responsibilities

From the current extracted behavior, the HEAD inject performs the following:

| Behavior | Description |
|---|---|
| incomplete sure-hit cancel | prevents certain sure-hit attacks from resolving when the caster is in incomplete-domain conditions that should not behave like a fully enclosed domain |
| runtime radius scaling | temporarily scales or suppresses radius-sensitive combat checks so attacks use the active domain form’s true radius |
| Purple owner invulnerability guard | prevents the owner from being wrongly damaged by Purple-related combat logic during the patched execution window |
| open-domain combat adjustments | modifies damage/range/CE behavior when the attacker is using open-domain state |
| `cnt6` suppression/boost | temporarily modifies a base combat value used by the original procedure so the attack executes as if it were built for the new domain form rules |

These temporary mutations are restored in the RETURN inject so the rest of the game state is not permanently polluted.

### RETURN-stage responsibilities

The RETURN inject then performs these addon-only behaviors:

| Behavior | Description |
|---|---|
| restore temporary radius | undoes pre-attack radius rewrites |
| restore Purple owner safety flags | removes temporary self-protection state |
| Black Flash detection | checks whether the hit should count as a Black Flash outcome |
| BF total-hit tracking | increments `addon_bf_total_hits` when the event qualifies |
| near-death cooldown reduction | shortens near-death cooldown after a qualifying BF path |
| BF cooldown set | applies the addon Black Flash cooldown lockout |

The current extracted post-attack BF detector uses the `ZONE >= 5990` signal noted from [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java), then applies:

```java
nearDeathCooldownReduction = 600;
blackFlashCooldown = 60;
```

This is important because it differs from the generic near-death cooldown reduction constant documented elsewhere in [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java).

## Black Flash subsystem

The Black Flash system is the most visible combat augmentation in the addon.

### Core Black Flash constants

The addon-side tracker in [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java) defines the following extracted constants:

| Constant | Value | Meaning |
|---|---:|---|
| `BF_MASTERY_THRESHOLD` | `500` | default mastery threshold to unlock mastery-state BF behavior |
| `BF_MASTERY_THRESHOLD_YUJI` | `200` | lower threshold for Yuji-specific path |
| `BF_ROLL_CHANCE` | `0.0012` | base BF random roll contribution |
| `BF_COMBAT_TIMEOUT` | `100` | time window before combat buildup decays/ends |
| `BF_COMBAT_RATE` | `0.004` | per-combat buildup rate |
| `BF_COMBAT_DECAY` | `0.06` | percent decay rate when buildup falls off |
| `BF_COMBAT_MAX_NORMAL` | `15.0` | maximum combat-driven BF percent for most characters |
| `BF_COMBAT_MAX_YUJI` | `28.0` | maximum combat-driven BF percent for Yuji |
| `BF_PERCENT_CAP_NORMAL` | `15.0` | hard cap on displayed/effective BF percent for normal path |
| `BF_PERCENT_CAP_YUJI` | `30.0` | hard cap on Yuji path |
| `ND_COOLDOWN_REDUCTION` | `120` | general near-death cooldown reduction constant used by tracker-side logic |

### Black Flash progression model

The combat tracker combines three ideas:

1. **base roll chance** through `BF_ROLL_CHANCE = 0.0012`,
2. **combat participation buildup** through `BF_COMBAT_RATE = 0.004`,
3. **character-specific caps** through separate normal and Yuji maxima.

That means the addon treats Black Flash as a hybrid of random proc and momentum meter, rather than a purely random event.

### Black Flash thresholds and caps

```java
private static final int BF_MASTERY_THRESHOLD = 500;
private static final int BF_MASTERY_THRESHOLD_YUJI = 200;
private static final double BF_ROLL_CHANCE = 0.0012;
private static final int BF_COMBAT_TIMEOUT = 100;
private static final double BF_COMBAT_RATE = 0.004;
private static final double BF_COMBAT_DECAY = 0.06;
private static final double BF_COMBAT_MAX_NORMAL = 15.0;
private static final double BF_COMBAT_MAX_YUJI = 28.0;
private static final double BF_PERCENT_CAP_NORMAL = 15.0;
private static final double BF_PERCENT_CAP_YUJI = 30.0;
```

### Important discrepancy: near-death cooldown reduction

There are **two** extracted near-death cooldown reductions involved in combat/BF flow:

| Source | Value | Context |
|---|---:|---|
| [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java) | `120` | generic tracker-side reduction constant |
| [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:139) | `600` | BF detection path after a resolved attack |

This should be documented exactly because it affects balancing and because the values are not identical.

## Domain-combat interaction

Combat and domain systems are tightly linked in the addon.

### Combat effects coming from domain mastery

[`DomainMasteryMixin.applyPropertyEffects(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:942) dispatches combat-relevant property bonuses while a domain is active.

| Property effect | Helper | Combat impact |
|---|---|---|
| victim CE drain | [`applyVictimCEDrain(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:977) | weakens enemy technique sustainability |
| BF chance boost | [`applyBFChanceBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:999) | raises BF probability during domain uptime |
| RCT heal boost | [`applyRCTHealBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:1019) | improves sustain during prolonged fights |
| blind | [`applyBlindEffect(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:1042) | degrades enemy effectiveness |
| slow | [`applySlowEffect(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:1056) | reduces enemy repositioning and escape |

### Domain form effects on combat execution

Through [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:66), the addon makes combat behave differently depending on domain form:

| Domain form | Combat consequence |
|---|---|
| closed | closest to base sure-hit combat behavior |
| open | attack range and effect logic can be expanded or rewritten without requiring a full wall shell |
| incomplete | sure-hit and range behavior can be partially suppressed to avoid granting full closed-domain guarantees |

See also [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md) and [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md).

## Cooldown and client-sync pipeline

The combat subsystem depends on addon networking so HUD elements match server truth.

### Server-side sync emitters

| Method | File | Purpose |
|---|---|---|
| [`sendCooldownSync(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:139) | [`ModNetworking.java`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java) | syncs technique/combat cooldown bars |
| [`sendBlackFlashSync(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:559) | [`ModNetworking.java`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java) | syncs BF percent, mastery state, charging state |
| [`sendNearDeathCdSync(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:567) | [`ModNetworking.java`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java) | syncs near-death cooldown UI |

### Combat-related packet structures

| Packet | Fields |
|---|---|
| [`CooldownSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:702) | `techRemaining`, `techMax`, `combatRemaining`, `combatMax` |
| [`BlackFlashSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:733) | `bfPercent`, `mastery`, `charging` |
| [`NearDeathCdSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:801) | `cdRemaining`, `cdMax`, `rctLevel3Unlocked` |

### Client-side caches

[`ClientPacketHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java) stores combat HUD data in three lightweight caches:

| Cache | Lines | Stored values |
|---|---|---|
| [`ClientCooldownCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:55) | cooldown bars | technique and combat cooldown remaining/max |
| [`ClientBlackFlashCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:92) | BF HUD | BF percent, mastery state, charging state |
| [`ClientNearDeathCdCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:101) | near-death HUD | cooldown remaining/max and unlock state |

The client update methods are:

- [`updateCooldowns(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:26)
- [`updateBlackFlash(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:31)
- [`updateNearDeathCooldown(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:37)

## HUD behavior

The Black Flash HUD is rendered from [`BlackFlashHudOverlay.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BlackFlashHudOverlay.java). Although this file is presentation-only, it reflects combat state exactly as synced by the server.

The extracted design points are:

| HUD behavior | Meaning |
|---|---|
| pulse | BF state is charging or actively emphasized |
| shake | high-intensity or proc-ready presentation |
| max-state logic | special presentation when BF percent reaches cap or mastery-enhanced state |

This makes Black Flash readable as a combat momentum mechanic rather than a hidden internal roll.

## Auxiliary combat-support mixins

The addon also patches adjacent combat procedures so the main range-attack flow works correctly.

### [`AttackWeakProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/AttackWeakProcedureMixin.java)

This mixin is part of the melee/light-attack support layer. Its role in the addon is to keep weak/basic attack branches compatible with the enhanced BF/combat-state model so that the player’s offensive rhythm is not split between “patched ranged attacks” and “unpatched basic hits”.

### [`StartCursedTechniqueProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/StartCursedTechniqueProcedureMixin.java)

This mixin participates at technique startup. Its practical combat role is to make sure the beginning of a cursed technique cast respects the addon’s cooldown, state, and combat-tag expectations before the actual offensive logic runs.

### [`OCostProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/OCostProcedureMixin.java)

This mixin modifies technique-cost behavior. It is combat-relevant because many offensive actions are gated by cursed-energy cost, and the addon needs those costs to remain consistent with open/incomplete domain adjustments and technique-specific enhancements.

## Example Black Flash sync model

The data contract between server and client is straightforward:

```java
public static class BlackFlashSyncPacket {
    float bfPercent;
    boolean mastery;
    boolean charging;
}
```

On receive, the packet updates [`ClientPacketHandler.updateBlackFlash(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:31), which then feeds [`ClientBlackFlashCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:92) and the overlay.

## Combat state categories

| Category | Main source | Notes |
|---|---|---|
| base hit execution | [`RangeAttackProcedure.java`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/RangeAttackProcedure.java) | original spine retained |
| temporary attack mutation | [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java) | adjusts domain-sensitive attack behavior |
| BF accumulation and decay | [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java) | persistent combat meter |
| domain bonus injection | [`DomainMasteryMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java) | active-domain combat boosts |
| client visualization | [`BlackFlashHudOverlay.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BlackFlashHudOverlay.java) | local display only |

## Balancing implications

The addon’s combat system is built around three reinforcing loops:

1. **offensive momentum** via BF buildup,
2. **temporary spike windows** via successful BF proc and technique follow-up,
3. **domain amplification** via mastery properties that boost CE drain, BF chance, crowd control, and sustain.

Because of that, even small numeric changes to constants like `BF_ROLL_CHANCE`, `BF_COMBAT_RATE`, `BF_PERCENT_CAP_*`, or the post-hit BF cooldown `60` can have very visible balance impact.

## Cross references

- base architecture: [`ORIGINAL_MOD_ARCHITECTURE.md`](ORIGINAL_MOD_ARCHITECTURE.md)
- domain runtime and forms: [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md)
- domain mixin hooks: [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md)
- Blue/Red/Purple combat extensions: [`BLUE_RED_PURPLE_SYSTEM.md`](BLUE_RED_PURPLE_SYSTEM.md)
- limb and survival consequences: [`LIMB_SYSTEM.md`](LIMB_SYSTEM.md) and [`NEAR_DEATH_SYSTEM.md`](NEAR_DEATH_SYSTEM.md)
- packet-level details: [`NETWORKING.md`](NETWORKING.md)
- utility helper references: [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- central number inventory: [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
- system coupling map: [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)
