# Blue / Red / Purple System

This document describes the Gojo-focused addon subsystem implemented primarily in [`BlueRedPurpleNukeMod.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:156), with entity behavior interception through [`BlueEntityMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/BlueEntityMixin.java:32) and [`RedEntityMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RedEntityMixin.java:35).

The addon extends the original JujutsuCraft [`BlueEntity`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:89), [`RedEntity`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:91), and [`PurpleEntity`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:90) behaviors rather than replacing the entire base technique framework. The result is a layered system with:

- charge-aware Red projectile variants,
- full-charge Blue aiming and lingering states,
- Purple fusion from overlapping Red + Blue state,
- an alternate crouch-based Infinity Crusher mode,
- domain-aware behavior through owner-state resolution and mixin interception.

## Scope

This document focuses on:

- core implementation in [`BlueRedPurpleNukeMod.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:156),
- Red rerouting via [`RedEntityMixin.jjkblueredpurple$redirectAIRed(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RedEntityMixin.java:39),
- Blue rerouting via [`BlueEntityMixin.jjkblueredpurple$redirectAIBlue(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/BlueEntityMixin.java:36),
- owner/domain helper lookups through [`DomainAddonUtils.resolveOwnerEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:172),
- combat coupling with [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:66),
- Black Flash cleanup coupling through [`DomainAddonUtils.cleanupBFBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:60).

## Central class

The subsystem is bootstrapped from [`BlueRedPurpleNukeMod()`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:190), which:

- registers addon entities through [`LimbEntityRegistry.ENTITIES.register(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:192),
- attaches Forge event listeners on the main event bus via [`MinecraftForge.EVENT_BUS.register(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:193),
- registers addon packets through [`ModNetworking.register()`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:79),
- exposes mastery commands using [`DomainMasteryCommands.register(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:198).

## Core constants

The addon hardcodes most Blue/Red/Purple thresholds near the top of [`BlueRedPurpleNukeMod.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:157).

| Constant | Value | Role |
|---|---:|---|
| `LINGER_DURATION` | `200` | Blue linger lifetime |
| `NUKE_COLLISION_RADIUS` | `4.0` | Purple fusion detection radius |
| `TELEPORT_RANGE` | `32.0` | Red shift-cast target search range |
| `AIM_CONE_ANGLE` | `30.0` | forward cone for teleport target selection |
| `BEHIND_DISTANCE` | `2.5` | teleport-behind offset |
| `NUKE_CE_THRESHOLD` | `2000.0` | required cursed energy for Purple fusion |
| `NUKE_CE_COST` | `2000.0` | CE consumed when Purple successfully spawns |
| `NUKE_HP_THRESHOLD` | `0.3` | max HP ratio allowed for Purple activation |
| `RED_CAPTURE_RADIUS` | `5.0` | Red pull/capture influence region |
| `RED_MAX_RANGE_FULL` | `128.0` | full-charge Red max range ceiling |
| `RED_PREINIT_TICKS` | `5` | minimum ticks before Red launch initialization |
| `RED_CHARGE_ANCHOR_DISTANCE` | `2.2` | Red charge anchor forward distance |
| `RED_CHARGE_ANCHOR_Y_OFFSET` | `-0.1` | Red charge anchor vertical offset |
| `BLUE_CROUCH_MIN_DISTANCE` | `10.0` | minimum Blue aim distance |
| `BLUE_CROUCH_MAX_DISTANCE` | `20.0` | maximum Blue aim distance |
| `BLUE_AIM_ORB_SPEED` | `1.5` | Blue retarget step speed |
| `BLUE_AIM_DURATION` | `90` | max crouch-aim duration |
| `CRUSHER_MIN_RADIUS` | `1.0` | Infinity Crusher start radius |
| `CRUSHER_MAX_RADIUS` | `3.0` | Infinity Crusher cap radius |
| `CRUSHER_BASE_WALL_DAMAGE` | `3.0f` | Crusher contact wall damage baseline |
| `CRUSHER_CE_DAMAGE_SCALE` | `0.012` | additional wall damage per drained CE |
| `CRUSHER_PUSH_STRENGTH` | `0.5` | Crusher push force baseline |
| `CRUSHER_CONE_COS` | `0.5` | forward cone dot threshold |
| `CRUSHER_BASE_CE_DRAIN` | `0.5` | initial per-tick CE drain |
| `CRUSHER_CE_DRAIN_GROWTH` | `0.02` | extra CE drain added each tick |
| `CRUSHER_HARDLOCK_THRESHOLD` | `15` | ticks required to hard-lock a target |
| `BLUE_FULL_PULL_RADIUS` | `6.0` | full-charge crouch Blue pull radius |
| `BLUE_FULL_PULL_STRENGTH` | `1.2` | full-charge crouch Blue pull strength |
| `BLUE_FULL_BLOCK_RANGE` | `4` | crouch Blue block-destruction radius |
| `BLUE_FULL_BLOCK_POWER` | `4.0f` | crouch Blue environmental pressure marker |

## High-level state machine

| Stage | Main method | State keys |
|---|---|---|
| Red startup | [`handleRedTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:343) | `addon_red_init_done`, `addon_red_preinit_ticks`, `flag_start` |
| Red charge anchor | [`updateChargingRedOrbVisual(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:786) | `addon_red_charge_anchor_*`, `addon_red_charge_anchor_valid` |
| Red normal flight | [`initializeNormalRedOrb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:420), [`tickNormalRedOrb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:467) | `addon_red_normal_active`, `addon_red_speed`, `addon_red_max_range`, `addon_red_travel` |
| Red shift cast | [`handleRedTeleportBehind(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:716), [`handleShiftCastExplosion(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:655) | `addon_red_shift_cast`, `addon_red_shift_exploded` |
| Blue aiming | [`handleBlueTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1134), [`applyBlueAim(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1223) | `addon_aim_active`, `addon_aim_ticks`, `aim_ended` |
| Blue linger | [`handleBlueTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1150) | `linger_active`, `linger_timer`, `linger_x`, `linger_y`, `linger_z`, `linger_cnt6` |
| Purple fusion | [`checkAndActivatePurpleNuke(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:844) | consumes nearby lingering Blue + matching Red owner data |
| Infinity Crusher | [`handleInfinityCrusher(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:921), [`resetInfinityCrusher(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1082) | `addon_infinity_crusher_ticks`, `addon_infinity_crusher_total_ce`, `addon_crusher_lock_*` |

## Event integration

The addon runs Blue/Red/Purple logic from normal Forge event flow.

| Event | Lines | Effect |
|---|---|---|
| [`onLivingTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:212) | Blue entity maintenance and Red attachment validation |
| [`onPlayerTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:239) | per-player Infinity Crusher, Black Flash charge maintenance, domain BF cleanup |
| [`onPlayerLogout(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:203) | hard-reset Crusher lock state on logout |

The per-player tick loop explicitly calls:

- [`handleInfinityCrusher(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:921)
- [`handleBlackFlashCharge(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:268)
- [`handleDomainBFBoostCleanup(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:257)

That means the subsystem is not isolated to projectile entities; it also mutates player-side combat state continuously.

## Red system

### Red override path

The base Red entity AI is intercepted by [`RedEntityMixin.jjkblueredpurple$redirectAIRed(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RedEntityMixin.java:39).

| Decision path | Result |
|---|---|
| entity is not a living Red | fall back to base [`AIRedProcedure.execute(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RedEntityMixin.java:43) |
| addon override allowed | run [`BlueRedPurpleNukeMod.handleRedFromMixin(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:339) |
| base Purple-flagged Red | do not override |
| player-owned Red | always prefers addon path |

The helper [`jjkblueredpurple$shouldUseAddonOverride(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RedEntityMixin.java:68) blocks addon takeover for Purple-flagged entities and client-side execution, but otherwise routes player-owned Red entities into the custom logic.

### Red startup and launch

The main Red controller is [`handleRedTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:343).

#### Startup sequence

1. resolve owner from `OWNER_UUID`,
2. cache the strongest visible charge using `cnt6`, owner `cnt6`, and `addon_red_charge_cached`,
3. keep the orb in a pre-init charging state until either:
   - `flag_start && preInitTicks >= 5`, or
   - `preInitTicks >= 200`,
4. choose one of three paths:
   - original-style crouch cast at low charge,
   - shift cast at full crouch charge,
   - normal addon flight path.

#### Key startup state

| Key | Meaning |
|---|---|
| `addon_red_charge_cached` | strongest observed charge snapshot |
| `addon_red_init_done` | launch path already selected |
| `addon_red_preinit_ticks` | charging delay counter |
| `addon_red_use_og` | use original Red path |
| `addon_red_shift_cast` | enable teleport-behind variant |
| `addon_red_normal_active` | enable normal addon orb flight |

### Normal Red orb

The normal-flight path is configured by [`initializeNormalRedOrb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:420).

#### Formulae and thresholds

```java
chargeRatio = clamp(cnt6, 0.0, 5.0) / 5.0;
maxRange = 128.0 * (0.22 + 0.78 * chargeRatio);
```

Charge tiers are resolved by [`getRedChargeTier(double)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1443):

| `cnt6` range | Tier |
|---|---:|
| `< 3.0` | `1` |
| `>= 3.0 && < 5.0` | `2` |
| `>= 5.0` | `3` |

Normal travel speed is resolved by [`getNormalRedSpeed(double)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1453):

| Tier | Speed |
|---|---:|
| `1` | `2.3` |
| `2` | `3.2` |
| `3` | `4.3` |

#### Stored runtime fields

| Field | Purpose |
|---|---|
| `addon_red_dir_x/y/z` | normalized travel direction |
| `addon_red_travel` | cumulative traveled distance |
| `addon_red_ticks` | active flight tick counter |
| `addon_red_max_range` | max travel distance before forced detonation |
| `addon_red_speed` | cached movement speed |
| `addon_red_charge_tier` | integer tier `1..3` |
| `x_power/y_power/z_power` | projectile motion vector mirrored into base-compatible keys |
| `x_pos/y_pos/z_pos` | tracked world position |

### Normal Red tick behavior

[`tickNormalRedOrb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:467) handles runtime flight.

Per tick it:

- advances tracked position,
- clips against blocks and detonates on collision,
- drifts using scaled `direction * speed`,
- calls [`pullMobsWithRedOrb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:550),
- calls [`spawnBlueLikeOrbVisuals(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:518),
- grows the orb size attribute dynamically,
- detonates when `traveled >= maxRange`.

The size-growth formula uses:

```java
baseScale = 2.6 + chargeTier * 1.2;
growth = progress * (2.0 + chargeTier * 1.5) * chargeRatio;
pulse = sin((gameTime + tickCount) * 0.6) * (0.18 + chargeTier * 0.08);
```

### Red capture / pull behavior

[`pullMobsWithRedOrb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:550) searches within a `20.0` inflated AABB, then applies Red-specific control only to non-owner, non-Red, non-Blue, non-Purple living targets.

Important extracted constants used by this family:

- capture radius baseline: `5.0`
- maximum full range: `128.0`
- exclusion of Blue/Red/Purple entities from capture lists

### Red detonation

Normal-flight explosions are triggered by [`explodeNormalRedOrb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:586), with secondary victim handling via [`applyRedExplosionShockwave(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:825).

Shockwave values:

| Charge tier | Base damage |
|---|---:|
| `1` | `16.0f` |
| `2` | `28.0f` |
| `3` | `42.0f` |

Shockwave radius formula:

```java
radius = 4.0 + chargeTier * 2.4;
```

Victim damage falloff uses:

```java
falloff = 1.0 - min(1.0, distance / radius);
damage = baseDamage * (0.55 + 0.45 * falloff);
```

### Shift-cast Red

At crouch + full charge, [`handleRedTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:382) sets `addon_red_shift_cast` and immediately calls [`handleRedTeleportBehind(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:716).

#### Teleport selection rules

The target search uses:

- max range `32.0`,
- cone angle `30.0`, implemented as `Math.cos(Math.toRadians(30.0))`,
- behind offset `2.5`.

The teleport routine then:

1. finds the best target in front of the owner,
2. places the owner behind the target,
3. rotates owner yaw/pitch to face the target,
4. repositions the Red orb at the owner’s firing point,
5. writes `x_power/y_power/z_power` with a fixed `* 3.0` shot vector,
6. spawns `REVERSE_PORTAL` particles and plays [`SoundEvents.ENDERMAN_TELEPORT`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:783).

#### Shift explosion

After teleport setup, the live shift path uses [`handleShiftCastExplosion(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:655). This path is separate from ordinary flight and is checked before normal ticking continues.

## Blue system

### Blue override path

The base Blue AI call is intercepted by [`BlueEntityMixin.jjkblueredpurple$redirectAIBlue(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/BlueEntityMixin.java:36).

This redirect does several important things before and after calling the original [`AIBlueProcedure.execute(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/BlueEntityMixin.java:59):

| Behavior | Effect |
|---|---|
| resolve owner through [`DomainAddonUtils.resolveOwnerEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:172) | allows domain-aware branching |
| detect owner open/incomplete/closed domain state | can disable addon aim override inside domains |
| preserve owner velocity | restores player motion after base Blue logic would otherwise disturb it |
| clamp `cnt1` to `35.0` while aiming | prevents runaway base tick state during addon aim |
| freeze Blue entity velocity during aim | keeps the aiming orb stationary except for addon steering |

The key gating expression is conceptually:

```java
skipAddonAim = ownerDomainActive || lingering;
```

So active domains and lingering Blue states intentionally suppress the addon aim override path.

### Blue lifecycle in [`handleBlueTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1134)

Blue behavior splits into three distinct phases:

| Phase | Condition | Result |
|---|---|---|
| lingering | `linger_active` | freeze orb in place for up to `200` ticks |
| aiming | `circle && cnt6 >= 5.0 && !aimEnded && owner crouching` | steer orb dynamically in front of the player |
| normal post-charge transition | full charge but not actively aiming | convert into lingering Blue after the threshold is reached |

### Lingering Blue

When Blue enters linger mode, the controller writes:

- `linger_active = true`
- `linger_timer = 0`
- `linger_x/y/z`
- `linger_cnt6`
- `cnt1 = 0.0`
- `NameRanged_ranged = 0.0`

While lingering, [`handleBlueTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1150) teleports the orb back to the stored coordinates every tick, zeros movement, restores health, emits `SOUL_FIRE_FLAME`, emits `END_ROD` every `20` ticks, and discards the orb after `timer >= 200`.

### Full-charge Blue aiming

Aim mode begins when:

- `circle == true`,
- `cnt6 >= 5.0`,
- `aim_ended == false`,
- owner remains crouching,
- `addon_aim_ticks < 90`.

The controller then:

- increments `addon_aim_ticks`,
- sets `addon_aim_active = true`,
- gives [`MobEffects.LUCK`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1191) for `5` ticks at amplifier `15`,
- calls [`applyBlueAim(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1223).

### Blue aim geometry

[`applyBlueAim(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1223) raycasts from the player eye position out to `20.0` blocks, clamps the effective target distance to `[10.0, 20.0]`, and then moves the orb toward that target in `1.5`-ish steps through a normalized step capped by a `1.5` segment.

Key properties written during aim:

| Field | Meaning |
|---|---|
| `x_pos/y_pos/z_pos` | tracked orb position |
| `x_power/y_power/z_power` | soft forward motion at `0.2 * look` |
| `addon_aim_active` | client/server state marker for aim mode |

### Full-charge crouch Blue utility functions

The addon also exposes [`handleCrouchFullChargeBlueAim(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1342), which mirrors the same aim-distance clamp and then adds two extra effects:

- [`pullMobsWithCrouchFullChargeBlue(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1393)
- [`destroyBlocksNearCrouchBlue(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1410)

#### Crouch pull behavior

[`pullMobsWithCrouchFullChargeBlue(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1393) affects nearby living entities within `6.0` blocks.

Pull formula:

```java
strength = 1.2 * (1.0 + (1.0 - d / 6.0) * 2.0);
```

When `d < 2.0`, targets are also:

- set to no-gravity,
- teleported near the orb,
- offset by `lookDir * 0.8` behind the orb center.

#### Crouch block destruction

[`destroyBlocksNearCrouchBlue(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1410) scans a cube from `-4..4` on each axis, rejects anything beyond Euclidean distance `4.0`, and destroys blocks only if all of these are true:

- not air,
- not solid-block protected by special handling,
- not an essential block,
- hardness `>= 0.0f`,
- hardness `<= 10.0f`.

Essential blocks are defined by [`isEssentialBlock(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1429):

| Block | Protected |
|---|---|
| `BEDROCK` | yes |
| `BARRIER` | yes |
| `SPAWNER` | yes |
| `END_PORTAL_FRAME` | yes |

## Purple fusion

The most important fusion method is [`checkAndActivatePurpleNuke(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:844).

### Activation requirements

Purple only activates when **all** of the following are true:

| Requirement | Value / rule |
|---|---|
| Red owner UUID exists | `OWNER_UUID` non-empty |
| nearby Blue exists | within AABB inflate `4.0` |
| nearby Blue is lingering | `linger_active == true` |
| Blue owner matches Red owner | exact `OWNER_UUID` match |
| Blue charge is full | `blueCnt6 >= 5.0` |
| owner is a player | `owner instanceof Player` |
| cursed energy threshold met | `PlayerCursePower >= 2000.0` |
| health condition met | `health / maxHealth <= 0.3` |

Those thresholds correspond exactly to:

```java
NUKE_COLLISION_RADIUS = 4.0;
NUKE_CE_THRESHOLD = 2000.0;
NUKE_CE_COST = 2000.0;
NUKE_HP_THRESHOLD = 0.3;
```

### Purple spawn payload

When conditions are met, the addon issues a summon command for `jujutsucraft:purple`, then configures the first matching fresh Purple entity.

Important runtime values:

| Field | Value |
|---|---|
| Purple HP base | `800.0` |
| damage boost bonus | `+ 80` per amplifier level of `MobEffects.DAMAGE_BOOST` |
| Purple `cnt6` | `max(redCnt6, blueCnt6) * 2.0` |
| Purple size base | `24.0 * (0.5 + maxCnt6 * 0.2)` |
| Purple `cnt3` | `1.0` |
| motion source | copies Red `x_power/y_power/z_power` |

### Purple cost and cleanup

If Purple successfully spawns, the owner’s cursed energy is reduced by exactly `2000.0` via [`PlayerCursePower = finalCE - 2000.0`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:903), then both source orbs are discarded:

- [`redEntity.discard()`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:914)
- [`blueEntity.discard()`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:915)

Visual/audio feedback includes:

- `EXPLOSION_EMITTER` count `3`,
- `END_ROD` count `50`,
- `jujutsucraft:electric_shock`,
- [`SoundEvents.GENERIC_EXPLODE`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:913).

## Infinity Crusher

[`handleInfinityCrusher(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:921) is a separate Gojo utility mode layered onto the player, not a projectile entity.

### Activation gate

Crusher only runs if all of these conditions hold:

| Requirement | Rule |
|---|---|
| active technique is Gojo technique set | rounded technique id `== 2` |
| player has Infinity enabled | persistent `infinity == true` |
| selected curse technique | rounded `PlayerSelectCurseTechnique == 5` |
| player crouching | `isCrouching()` must be true |

Otherwise the system calls [`resetInfinityCrusher(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1082).

### Per-tick drain and growth

The controller increments `addon_infinity_crusher_ticks` each tick, then uses:

```java
ceDrain = 0.5 + activeTicks * 0.02;
growthFactor = min(1.0, activeTicks / 200.0);
currentRadius = 1.0 + 2.0 * growthFactor;
wallDamage = 3.0f + growthFactor * 8.0f + totalCEDrained * 0.012f;
```

This matches the declared constants:

- base CE drain `0.5`
- drain growth `0.02`
- radius min `1.0`
- radius max `3.0`
- base wall damage `3.0f`
- CE damage scaling `0.012`

### Projectile reflection

Crusher also reflects hostile ranged objects inside:

```java
reflectRadius = max(currentRadius + 1.0, (4.0 + playerWidth) / 2.0)
```

Objects count as reflectable if they are:

- a projectile, or
- tagged as `forge:ranged_ammo`, or
- carry nonzero `NameRanged_ranged`.

Non-owned projectiles have velocity reversed by `vel.scale(-1.5)`, and projectile ownership is reassigned to the player.

### Mob compression / hard lock

Nearby mobs are processed within `currentRadius + 3.0`.

Important combat values:

| Rule | Value |
|---|---:|
| forward cone dot threshold | `0.5` |
| hard lock threshold | `15` contact ticks |
| release range | `currentRadius + 5.0` |
| base push strength | `0.5` |

Push formula:

```java
pushStrength = 0.5 * (0.6 + 0.4 * dot) * (1.0 + growthFactor * 0.5);
```

When a target is hard-locked:

- it is pinned to stored `addon_crusher_lock_x/y/z`,
- gravity is disabled,
- it takes repeated generic damage `wallDamage`,
- Crusher VFX and anvil-impact sounds are emitted.

### Crusher reset

[`resetInfinityCrusher(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1082) clears:

- `addon_infinity_crusher_ticks`,
- `addon_infinity_crusher_total_ce`,
- all nearby victim `addon_crusher_lock_owner`,
- `addon_crusher_lock_x/y/z`,
- `addon_crusher_contact_ticks`.

This reset is called both when crouching stops and on logout.

## Domain interaction

Blue/Red behavior is explicitly domain-aware.

### Owner state checks in Blue mixin

[`BlueEntityMixin.jjkblueredpurple$redirectAIBlue(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/BlueEntityMixin.java:36) resolves:

- open domain through [`DomainAddonUtils.isOpenDomainState(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:118),
- incomplete domain through [`DomainAddonUtils.isIncompleteDomainState(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:140),
- closed active domain through [`DomainAddonUtils.isClosedDomainActive(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/BlueEntityMixin.java:43).

If any of those are true, `skipAddonAim` becomes true and the addon deliberately avoids the extra aim rewrite.

### Red state diagnostics

[`RedEntityMixin.jjkblueredpurple$redirectAIRed(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RedEntityMixin.java:39) compares entity-side flags such as:

- `jjkbrp_open_form_active`
- `jjkbrp_incomplete_form_active`
- `jjkbrp_incomplete_session_active`

against the owner’s resolved domain state, then logs mismatches for diagnostics. This indicates the author expected Red behavior to remain consistent with the owner’s current domain form.

## Black Flash coupling

The Blue/Red/Purple system is also tied into the Black Flash subsystem from [`handleBlackFlashCharge(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:268).

Important thresholds:

| Condition | Effect |
|---|---|
| selected technique id `> 2` | clears BF charging/guaranteed state |
| `cnt6 >= 4.5 && skill != 0.0` | sets `addon_bf_guaranteed` and `addon_bf_charging` |
| `cnt6 > 0.0 && skill != 0.0` | enables charging state |
| `cnt6 >= 5.0 && skill != 0.0 && !addon_bf_charge_announced` | displays bold `"Black Flash"` message |

This is relevant because Blue/Red charge progression shares the same `cnt6` charge economy that later influences BF preparation.

## Important persistent keys

| Key | Subsystem | Meaning |
|---|---|---|
| `OWNER_UUID` | Red/Blue/Purple | owner association for fusion and overrides |
| `flag_purple` | Red/Blue | marks entity as already in Purple-related flow |
| `cnt6` | Red/Blue/Purple/BF | primary charge magnitude |
| `cnt1` | Red/Blue | internal timer/counter reused by base procedures |
| `circle` | Blue | indicates special Blue state leading to aim/linger |
| `aim_ended` | Blue | Blue full-charge aiming already stopped |
| `addon_aim_active` | Blue | addon aim loop currently active |
| `linger_active` | Blue | Blue has converted into persistent orb |
| `linger_cnt6` | Blue | stored charge for later Purple fusion |
| `addon_red_shift_cast` | Red | teleport-behind variant active |
| `addon_red_normal_active` | Red | normal addon Red flight active |
| `addon_red_charge_used` | Red | charge snapshot actually used for the cast |
| `addon_infinity_crusher_ticks` | Crusher | active duration counter |
| `addon_infinity_crusher_total_ce` | Crusher | cumulative CE spent |
| `addon_crusher_lock_owner` | Crusher | UUID of player controlling the hard lock |

## Example Purple fusion logic

The fusion gate can be summarized like this:

```java
if (nearbyBlue.linger_active
    && sameOwner
    && blueCnt6 >= 5.0
    && ownerCE >= 2000.0
    && ownerHealthRatio <= 0.3) {
    summonPurple();
}
```

The actual implementation lives in [`checkAndActivatePurpleNuke(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:844).

## Example Crusher formulas

```java
ceDrain = 0.5 + activeTicks * 0.02;
currentRadius = 1.0 + 2.0 * min(1.0, activeTicks / 200.0);
wallDamage = 3.0f + growthFactor * 8.0f + totalCEDrained * 0.012f;
```

These values make Crusher both a mobility/compression tool and a sustained CE sink.

## Relationship to base systems

| Base system | Addon extension |
|---|---|
| base Blue AI via [`AIBlueProcedure.execute(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/BlueEntityMixin.java:59) | owner-velocity preservation, aim suppression, domain awareness |
| base Red AI via [`AIRedProcedure.execute(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RedEntityMixin.java:64) | player-owned override path, shift cast, Purple fusion hooks |
| base projectile ownership via `NameRanged` fields | reused for projectile reflection and cleanup ownership matching |
| base ranged combat | augmented by Purple safety and domain charge interactions in [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:66) |

## Cross references

- combat coupling and BF logic: [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
- domain-aware mixin behavior: [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md)
- domain runtime/forms: [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md)
- networking and HUD sync: [`NETWORKING.md`](NETWORKING.md)
- utility helper functions: [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- global numeric inventory: [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
- subsystem dependencies: [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)
