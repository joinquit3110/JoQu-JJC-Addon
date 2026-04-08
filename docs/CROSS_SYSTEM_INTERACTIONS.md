# Cross-System Interactions

This document maps how the addon’s major systems depend on each other at runtime. The key point is that the addon is **not** a set of isolated features. Domain mastery, combat, Blue/Red/Purple, limb loss, near-death, networking, and client UI all exchange state through:

- persistent player/entity NBT,
- Forge capabilities,
- mixin redirects and return hooks,
- addon packets and client caches,
- shared utility helpers.

## System map

| System | Main docs | Core shared dependencies |
|---|---|---|
| domain runtime | [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md), [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md) | [`DomainMasteryData`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java:15), runtime NBT, cleanup entities, range scaling |
| combat / Black Flash | [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md) | `cnt6`, combat cooldowns, domain property buffs, near-death cooldown reduction |
| Blue/Red/Purple | [`BLUE_RED_PURPLE_SYSTEM.md`](BLUE_RED_PURPLE_SYSTEM.md) | owner resolution, domain-state checks, `cnt6`, BF charge state |
| limb loss / regrowth | [`LIMB_SYSTEM.md`](LIMB_SYSTEM.md) | RCT effects, near-death failure path, client sync |
| near-death / RCT Level 3 | [`NEAR_DEATH_SYSTEM.md`](NEAR_DEATH_SYSTEM.md) | limb severing, BF cooldown reduction, near-death packets |
| skill wheel / UI | [`SKILL_WHEEL.md`](SKILL_WHEEL.md) | networking, cooldown cache, domain cost helpers |
| networking / client caches | [`NETWORKING.md`](NETWORKING.md) | all client-facing systems |
| utility layer | [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md) | shared owner/radius/cost/mutation-lock logic |

## Shared state channels

The addon uses five major data-sharing mechanisms.

### 1. Persistent entity/player NBT

Examples:

| Key family | Used by |
|---|---|
| `jjkbrp_domain_*` | domain startup, clash, cleanup, mastery VFX |
| `addon_bf_*`, `jjkbrp_bf_*` | Black Flash, combat, domain BF boosts |
| `OWNER_UUID`, `NameRanged`, `NameRanged_ranged` | Blue/Red/Purple owner tracking |
| `jjkbrp_near_death*` | near-death and cooldown state |
| `addon_crusher_*` | Infinity Crusher hard-lock state |
| `data_cursed_spirit_manipulation*` | skill wheel Geto spirit pages |

### 2. Forge capabilities

| Capability | Used by |
|---|---|
| base [`PLAYER_VARIABLES_CAPABILITY`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:100) | technique selection, CE, cooldown-facing state, owner IDs |
| addon domain mastery capability | mastery form, XP, property levels, negative modify |
| addon limb capability | limb states, regen progress, cooldown timers |

### 3. Temporary mixin scaling / redirects

| Helper | Why it matters |
|---|---|
| [`DomainRadiusUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:13) | allows base domain code to run under addon-scaled radius contexts |
| redirects in [`DomainMasteryMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:96) | attach mastery/property logic to base active ticks |
| redirects in [`RangeAttackProcedureMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:66) | attach domain/BF logic to base attacks |
| redirects in [`BlueEntityMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/BlueEntityMixin.java:36) and [`RedEntityMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RedEntityMixin.java:39) | make projectile/orb behavior domain-aware |

### 4. Packets and client caches

| Packet/cache path | Systems connected |
|---|---|
| skill wheel packets | server selection logic ↔ client radial UI |
| cooldown / BF / near-death sync packets | server combat state ↔ overlays and UI |
| limb sync packet | server sever/regrowth state ↔ client render masking |
| domain mastery packets | server mastery capability ↔ client mastery screen |

### 5. Shared utility methods

| Utility | Cross-system role |
|---|---|
| [`DomainAddonUtils.resolveOwnerEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:172) | Blue/Red/Purple ownership, domain owner state checks |
| [`DomainAddonUtils.cleanupBFBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:60) | combat ↔ domain BF buff cleanup |
| [`DomainCostUtils.resolveExpectedDomainCastCost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:75) | domain mastery ↔ skill wheel display |
| [`ClientPacketHandler`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:18) | networking ↔ all client-facing UI systems |

## Domain ↔ Combat interactions

This is the densest interaction cluster in the addon.

### Domain mastery properties directly buff combat

[`DomainMasteryMixin.applyPropertyEffects(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:942) injects combat-relevant bonuses while a domain is active.

| Property | Combat effect |
|---|---|
| victim CE drain | drains enemy cursed energy, weakening technique use |
| BF chance boost | raises Black Flash chance while in domain |
| RCT heal boost | improves sustain and interacts with fatigue/regen windows |
| blind | reduces opponent effectiveness |
| slow | reduces opponent mobility |

### Domain form changes attack behavior

[`RangeAttackProcedureMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:66) reads domain form state and changes attack flow by:

- canceling some incomplete-domain sure-hit behavior,
- scaling/suppressing active radius,
- adjusting open-domain damage/range/CE assumptions,
- protecting Purple owner state during attack execution,
- detecting BF procs on return.

### Domain BF bonus cleanup

The domain system can temporarily boost BF-related state via `jjkbrp_domain_bf_bonus` and `jjkbrp_bf_cnt6_boost`. These are cleaned up by [`DomainAddonUtils.cleanupBFBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:60), which is called from [`BlueRedPurpleNukeMod.handleDomainBFBoostCleanup(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:257).

That creates this lifecycle:

1. domain applies BF bonus,
2. combat/BF logic consumes the higher state,
3. cleanup removes bonus and restores `cnt6`.

## Domain ↔ Networking interactions

### Domain mastery screen is server-authoritative

The mastery UI cannot mutate state locally. The flow is:

1. client requests open with [`DomainMasteryOpenPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:980),
2. server validates advancement and syncs mastery capability,
3. server sends [`DomainMasteryOpenScreenPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1007),
4. client opens screen,
5. all user actions send [`DomainPropertyPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:819),
6. server re-syncs full capability with [`DomainMasterySyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1021).

### Mutation locking spans UI and gameplay

There are two parallel lock implementations:

- runtime helper layer via [`DomainAddonUtils.isDomainMasteryMutationLocked(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:287),
- screen-side immediate client feedback in [`DomainMasteryScreen.refreshMutationLockState()`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryScreen.java:162).

Both use domain-active, clash, and combat conditions, which means the UI is a reflection of gameplay state rather than a separate ruleset.

## Domain ↔ Skill Wheel interactions

### Wheel preview uses domain cost helpers

When the wheel builds an entry for a domain technique, it calls:

- [`DomainCostUtils.resolveEffectiveForm(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:35)
- [`DomainCostUtils.formMultiplier(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:43)
- [`DomainCostUtils.resolveTechniqueBaseCost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:51)
- [`DomainCostUtils.resolveExpectedDomainCastCost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:75)

That means domain mastery is visible inside the skill wheel **before** a cast happens.

### Domain form metadata travels to the client

[`WheelTechniqueEntry`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1095) carries:

- `domainForm`
- `domainMultiplier`

so the wheel can display form-specific cost and form labels without re-deriving them client-side.

## Domain ↔ Blue/Red/Purple interactions

### Orbs read owner domain state

[`BlueEntityMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/BlueEntityMixin.java:40) and [`RedEntityMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RedEntityMixin.java:47) both resolve owner state and branch on:

- open domain,
- incomplete domain,
- closed active domain.

This affects whether:

- addon Blue aim is skipped,
- Red logs mismatch diagnostics,
- special orb behavior runs inside domain contexts.

### Shared owner-resolution path

Both systems depend on [`DomainAddonUtils.resolveOwnerEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:172), meaning ownership semantics are shared between domain logic and Gojo-orb logic.

### Purple safety during ranged combat

[`RangeAttackProcedureMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:66) temporarily protects Purple owner state during attack execution. This is one of the clearest places where Blue/Red/Purple and combat/domain patches intersect directly.

## Combat ↔ Near-Death interactions

### Black Flash can reduce near-death cooldown

There are two separate BF-to-near-death reduction paths:

| Source | Reduction |
|---|---:|
| [`CooldownTrackerEvents.onLivingHurt(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java:157) | `120` |
| [`RangeAttackProcedureMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:139) | `600` |

So combat performance can directly shorten access time to the next near-death survival window.

### Near-death cooldown is networked combat-facing state

The long near-death cooldown is exposed to the client via [`NearDeathCdSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:801) and stored in [`ClientNearDeathCdCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:101), putting it in the same packet-driven HUD category as cooldown and BF state.

## Near-Death ↔ Limb interactions

This is the strongest direct dependency outside the domain stack.

### Failed near-death triggers head sever

When near-death expires unsuccessfully, [`RCTLevel3Handler`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:157) calls [`LimbLossHandler.severLimb(..., LimbType.HEAD, ...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:159).

So the near-death system does not merely “allow death”; it routes failure through the limb system’s head-sever branch.

### Active near-death uses limb particles

During the active countdown, near-death repeatedly triggers:

- [`LimbParticles.spawnBloodDrip(..., LimbType.HEAD)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:149)
- [`LimbParticles.spawnSeverBloodBurst(..., LimbType.HEAD)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:151)

So limb visuals are the presentation layer for near-death suffering.

### Shared RCT dependency

Both systems depend on base [`REVERSE_CURSED_TECHNIQUE`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:142) or RCT-related advancements:

- limb regrowth requires active RCT effect,
- near-death survival requires reaching `4.0f` HP while RCT is active,
- RCT Level 3 unlocking requires repeated close-call recoveries under RCT.

## Limb ↔ Networking interactions

### Limb state is server authoritative, render is client cached

The limb system uses [`LimbSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:39) to push:

- per-limb state,
- per-limb regen progress,
- target entity ID.

The client stores this in [`ClientLimbCache`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:12), and render hooks read only that cache.

This creates a strict separation:

| Server | Client |
|---|---|
| decides sever/regrow state | hides limbs and renders regrowth phases |
| maintains capability | consumes snapshot cache |

## Skill Wheel ↔ Combat interactions

### Wheel cooldown visualization depends on combat cache

The wheel entry model includes cooldown snapshot values, and the client then locally decays them from `openTick`. That means the wheel is directly reflecting combat cooldown state rather than simply showing static technique names.

### Wheel selection affects combat readiness

Selecting a technique through [`SelectTechniquePacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:580) or spirits through [`SelectSpiritPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:761) immediately changes the player’s offensive option set, which then feeds back into combat, BF charge, and cost usage.

## Networking ↔ Client UI interactions

### One packet hub, many screen/cache consumers

[`ClientPacketHandler`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:18) is the common hub for:

- opening the skill wheel,
- opening the domain mastery screen,
- updating cooldown bars,
- updating BF HUD state,
- updating near-death cooldown state,
- syncing domain mastery capability.

This means client-facing features are interdependent through one packet consumption layer even when the gameplay systems are distinct.

## Utility layer ↔ Everything else

### Domain utilities are shared infrastructure

[`DomainAddonUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:46) is consumed by:

- domain mixins,
- Red/Blue mixins,
- combat cleanup paths,
- cleanup-entity logic,
- mutation-lock checks.

A bug in owner resolution, range computation, or cleanup detection here would therefore affect multiple systems at once.

### Crash guard protects cross-mod assumptions

[`BaseCapabilityCrashGuardEvents`](../src/main/java/net/mcreator/jujutsucraft/addon/BaseCapabilityCrashGuardEvents.java:25) exists only because addon healing/regeneration assumptions depend on the base capability being present. This is a direct sign that the addon is layered over fragile base-mod state rather than running independently.

## Most important shared keys and values

| Shared key / value | Systems touched |
|---|---|
| `cnt6` | combat, BF charge, Blue/Red/Purple charge, domain BF bonus cleanup |
| `jjkbrp_domain_form_*` | domain runtime, clash, wheel preview, combat behavior |
| `OWNER_UUID` | Red/Blue/Purple, owner resolution, domain-aware orb logic |
| `addon_bf_last_combat_tick` | combat tagging, mutation lock, BF buildup |
| `jjkbrp_near_death_cd` | near-death, combat BF reduction, HUD sync |
| `NameRanged` / `NameRanged_ranged` | projectile ownership, Blue/Red/Purple reflection and owner fallback |
| `x_pos_doma` | domain-center detection, cleanup matching, live-domain lookup |

## End-to-end examples

### Example 1: Domain → BF → Near-Death

1. player activates a domain,
2. [`DomainMasteryMixin.applyBFChanceBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:999) raises BF probability,
3. combat system procs Black Flash,
4. BF logic reduces near-death cooldown by `120` or `600` depending on path,
5. cooldown is synced to the client via [`NearDeathCdSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:801).

### Example 2: Near-Death failure → Limb death → Client render change

1. near-death countdown reaches zero,
2. [`RCTLevel3Handler`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:157) forces head sever,
3. limb capability updates and packets are sent,
4. client cache updates,
5. render hooks hide the head / render early regrowth phases if recovery ever begins later.

### Example 3: Wheel preview ← Domain mastery

1. server builds wheel entries,
2. domain techniques call [`DomainCostUtils.resolveEffectiveForm(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:35),
3. wheel entries carry `domainForm` and `domainMultiplier`,
4. the client radial UI shows adjusted CE cost and form label.

### Example 4: Domain state → Blue/Red orb behavior

1. orb mixin resolves owner,
2. utility layer detects open/incomplete/closed domain state,
3. Blue addon aim may be skipped,
4. Red diagnostic or special override behavior changes accordingly.

## Practical debugging order for cross-system bugs

If one bug appears to affect multiple systems, inspect in this order:

1. shared NBT keys and capability values,
2. utility helper methods in [`DomainAddonUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java) and [`DomainCostUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java),
3. packet send/receive flow in [`ModNetworking`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:72) and [`ClientPacketHandler`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:18),
4. the relevant gameplay mixin or event handler,
5. the client overlay/screen/cache consumer.

## Cross references

- overall index: [`README.md`](README.md)
- base architecture: [`ORIGINAL_MOD_ARCHITECTURE.md`](ORIGINAL_MOD_ARCHITECTURE.md)
- domain runtime: [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md)
- domain hook stack: [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md)
- combat and BF: [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
- Gojo-specific techniques: [`BLUE_RED_PURPLE_SYSTEM.md`](BLUE_RED_PURPLE_SYSTEM.md)
- limb system: [`LIMB_SYSTEM.md`](LIMB_SYSTEM.md)
- near-death / RCT Level 3: [`NEAR_DEATH_SYSTEM.md`](NEAR_DEATH_SYSTEM.md)
- radial UI and selection: [`SKILL_WHEEL.md`](SKILL_WHEEL.md)
- packet topology: [`NETWORKING.md`](NETWORKING.md)
- shared helpers: [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- numeric inventory: [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
