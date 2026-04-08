# Limb System

This document describes the addon limb-loss subsystem implemented under [`limb/`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/). It covers:

- limb identity and state enums,
- the persistent capability data model,
- severing probability and selection rules,
- regeneration via Reverse Cursed Technique,
- gameplay penalties for missing arms and legs,
- synchronization packets and client cache behavior,
- rendering and regrowth visualization,
- spawned severed-limb entities.

This system is one of the addon’s strongest examples of a server-authoritative gameplay feature with a dedicated client visualization pipeline.

## Source map

| Category | Primary files |
|---|---|
| enum + state model | [`LimbType.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbType.java:6), [`LimbState.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbState.java:6) |
| persistent data | [`LimbData.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:15), [`LimbCapabilityProvider.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbCapabilityProvider.java:55) |
| gameplay logic | [`LimbLossHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:67), [`LimbGameplayHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:39) |
| networking | [`LimbSyncPacket.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:39), [`ClientLimbCache.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:12) |
| rendering | [`LimbRenderHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRenderHandler.java:33), [`LimbRegrowthLayer.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRegrowthLayer.java:44) |
| severed body part entity | [`SeveredLimbEntity.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/SeveredLimbEntity.java:47) |

## High-level flow

| Phase | Main method | Result |
|---|---|---|
| capability attached | [`LimbCapabilityProvider.attachCapabilities(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbCapabilityProvider.java:88) | every `LivingEntity` gains limb data |
| damage event arrives | [`LimbLossHandler.onLivingDamage(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:71) | player sever chance is computed |
| limb selected | [`pickLimbToSever(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:134) | arm/leg/head chosen |
| state transition | [`severLimb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:159) | state becomes `SEVERED` or `REVERSING` |
| penalties applied | [`LimbGameplayHandler.applyLimbDebuffs(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:45) | attack and movement are reduced |
| tick maintenance | [`LimbLossHandler.onLivingTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:225) | cooldowns, blood particles, regeneration |
| sync to viewers | [`LimbSyncPacket.sendToTrackingPlayers(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:81) | client state cache updated |
| render pass | [`LimbRenderHandler.onRenderLivingPre(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRenderHandler.java:42) | hidden limbs or regrowth phase shown |

## Limb identity model

### [`LimbType`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbType.java:6)

The system tracks exactly five limbs.

| Enum value | Serialized name | Index | Notes |
|---|---|---:|---|
| `LEFT_ARM` | `left_arm` | `0` | considered an arm by [`isArm()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbType.java:29) |
| `RIGHT_ARM` | `right_arm` | `1` | considered an arm |
| `LEFT_LEG` | `left_leg` | `2` | considered a leg by [`isLeg()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbType.java:33) |
| `RIGHT_LEG` | `right_leg` | `3` | considered a leg |
| `HEAD` | `head` | `4` | special lethal handling |

Lookup helpers:

- [`fromIndex(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbType.java:37)
- [`fromName(String)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbType.java:45)

### [`LimbState`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbState.java:6)

The system uses exactly three states:

| State | Meaning |
|---|---|
| `INTACT` | normal limb |
| `SEVERED` | missing limb, not yet regrowing |
| `REVERSING` | regrowth state under Reverse Cursed Technique |

Important note: the code uses **`REVERSING`**, not “REGENERATING”.

Fallback conversion uses [`fromOrdinal(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbState.java:12), which returns `INTACT` for invalid ordinals.

## Persistent data model

### [`LimbData`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:15)

The authoritative state is stored in two `EnumMap`s:

- `EnumMap<LimbType, LimbState> states`
- `EnumMap<LimbType, Float> regenProgress`

Additional counters:

- `severCooldownTicks`
- `bloodDripTicks`

### Key methods

| Method | Purpose |
|---|---|
| [`getState(LimbType)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:28) | read current limb state |
| [`setState(LimbType, LimbState)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:32) | mutate state and clear progress when appropriate |
| [`getRegenProgress(LimbType)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:41) | read `0.0..1.0` regen fraction |
| [`setRegenProgress(LimbType, float)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:45) | clamps progress to `[0.0, 1.0]` |
| [`hasSeveredLimbs()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:49) | true for `SEVERED` or `REVERSING` |
| [`hasReversingLimbs()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:57) | true if any limb is `REVERSING` |
| [`isLimbMissing(LimbType)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:65) | true for `SEVERED` or `REVERSING` |
| [`countSeveredArms()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:70) | counts missing arms |
| [`countSeveredLegs()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:81) | counts missing legs |
| [`tickCooldown()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:100) | decrements sever and blood counters |
| [`serializeNBT()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:117) | writes NBT |
| [`deserializeNBT(CompoundTag)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:128) | reads NBT |
| [`copyFrom(LimbData)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:141) | clone/copy support |

### NBT format

The storage layout is explicit and stable:

| Key pattern | Type | Meaning |
|---|---|---|
| `<limb>_state` | `int` | ordinal of [`LimbState`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbState.java:6) |
| `<limb>_regen` | `float` | per-limb regeneration progress |
| `sever_cooldown` | `int` | global anti-repeat sever timer |
| `blood_drip` | `int` | blood particle persistence timer |

Example:

```text
left_arm_state
left_arm_regen
right_arm_state
right_arm_regen
left_leg_state
left_leg_regen
right_leg_state
right_leg_regen
head_state
head_regen
sever_cooldown
blood_drip
```

## Capability lifecycle

### [`LimbCapabilityProvider`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbCapabilityProvider.java:55)

The capability ID is:

```java
new ResourceLocation("jjkblueredpurple:limb_data")
```

The exposed capability token is [`LIMB_DATA`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbCapabilityProvider.java:59).

### Lifecycle events

| Event | Method | Effect |
|---|---|---|
| capability registration | [`registerCapabilities(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbCapabilityProvider.java:83) | registers `LimbData.class` |
| attach to entity | [`attachCapabilities(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbCapabilityProvider.java:88) | attaches to every `LivingEntity` |
| clone after dimension/respawn transfer | [`onPlayerClone(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbCapabilityProvider.java:95) | either resets on death or copies old data |
| server respawn sync | [`onPlayerRespawnServer(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbCapabilityProvider.java:118) | pushes snapshot to the respawned player |

### Death vs non-death clone behavior

If `event.isWasDeath()` is true, the system resets **all** limbs to `INTACT`, zeros regen progress, and clears both cooldown counters. That means limb loss is **not** retained through death.

If the clone is not caused by death, the old capability data is copied into the new entity using [`copyFrom(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:141).

## Severing rules

### Damage entrypoint

Limb severing is triggered from [`LimbLossHandler.onLivingDamage(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:71).

Restrictions before any sever roll happens:

- server-side only,
- only `Player` entities,
- max HP must be greater than `0.0f`,
- `severCooldownTicks` must be `0`.

### Damage thresholds

The system computes:

```java
hpAfter = currentHp - actualDamage;
scaledMinDmg = max(4.0f, maxHp * 0.05f);
bigHit = actualDamage >= currentHp * 0.3f && actualDamage >= scaledMinDmg;
wasLowHp = currentHp / maxHp < 0.3f;
dropsToLowHp = hpAfter > 0.0f && hpAfter / maxHp < 0.3f;
lethalHit = hpAfter <= 0.0f;
```

So a sever roll only begins if **any** of these are true:

- big hit,
- already below `30%` HP,
- drops below `30%` HP,
- lethal hit.

### Chance formula

The exact chance accumulator is:

```java
chance = 0.0f;
if (bigHit)      chance += 0.25f + actualDamage / maxHp * 0.3f;
if (dropsToLowHp) chance += 0.2f + (1.0f - hpAfter / maxHp) * 0.25f;
if (wasLowHp)     chance += 0.15f;
if (lethalHit)    chance += 0.15f;
chance = Math.min(chance, 0.85f);
```

If the player lacks the RCT advancement checked by [`hasRCTAdvancement(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:340), the final chance is multiplied by:

```java
0.2f
```

So the system has these exact sever constants:

| Constant / threshold | Value |
|---|---:|
| minimum scaled damage gate | `4.0f` |
| minimum fraction of max HP for scaled gate | `0.05` |
| big-hit fraction of current HP | `0.3` |
| low-HP threshold | `0.3` |
| chance cap | `0.85f` |
| no-RCT multiplier | `0.2f` |

### Limb selection

[`pickLimbToSever(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:134) first excludes already-missing limbs and usually excludes the head unless no other candidate remains.

Selection weights:

| Branch | Rule |
|---|---|
| arms | if arms exist and `roll < 0.5f`, choose a random arm |
| legs | else if legs exist and `roll < 0.85f`, choose a random leg |
| fallback | shuffle remaining candidates and choose first |
| head | only if no arm/leg candidates remain and head is still intact |

This makes arms slightly preferred, legs common, and head a last-resort lethal path.

## Sever application

### [`severLimb(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:159)

The sever function applies messaging, state change, particles, sounds, spawned entity behavior, gameplay penalties, and network sync.

### Shared post-sever timers

For normal limbs and head cases alike, the handler sets:

```java
severCooldownTicks = 40;
bloodDripTicks = 200;
```

### Head special case

If the severed limb is `HEAD`:

- without RCT effect: head becomes `SEVERED`, sever sounds/particles play, a severed-limb entity is spawned, the player is killed via `setHealth(0.0f)`.
- with RCT effect: head immediately becomes `REVERSING` and regen starts at `0.3f` instead of `0.0f`.

That head bootstrap is the only place where regen begins partway complete.

### Normal limb case

For arms and legs, the state becomes `SEVERED` immediately, then:

- [`LimbSounds.playSeverSound(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:194)
- [`LimbParticles.spawnSeverBloodBurst(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:195)
- optional directional blood spray when a direct source exists via [`spawnBloodSpray(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:198)
- severed-limb entity spawn via [`spawnSeveredLimbEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:209)
- gameplay penalties via [`LimbGameplayHandler.applyLimbDebuffs(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:45)
- packet sync to tracking players and the owner.

## Severed limb entity

### [`SeveredLimbEntity`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/SeveredLimbEntity.java:47)

This entity is spawned as a visible detached body part.

### Synced entity data fields

| Field | Type | Purpose |
|---|---|---|
| `LIMB_TYPE_ID` | `int` | which limb model to render |
| `OWNER_UUID` | `Optional<UUID>` | original owner identity |
| `OWNER_NAME` | `String` | display fallback / persistence |
| `SLIM_MODEL` | `boolean` | player-model variant flag |

### Runtime constants

| Constant | Value |
|---|---:|
| `MAX_LIFETIME` | `200` |
| blood drip particle size | `0.6f` |
| airborne `rotX` increment | `5.0f` |
| airborne `rotZ` increment | `3.0f` |
| gravity per tick | `0.04` |
| ground friction multiplier | `0.92` |
| bounce damping | `0.8` horizontal, `0.3` vertical |

### Save data

The entity persists:

- `LimbType`
- `OwnerUUID`
- `OwnerName`
- `SlimModel`
- `Lifetime`
- `RotX`
- `RotZ`

## Regeneration system

### Tick entrypoint

Regeneration is driven from [`LimbLossHandler.onLivingTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:225), which:

1. skips client side,
2. ticks cooldowns via [`tickCooldown()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbData.java:100),
3. spawns drip particles every `4` ticks,
4. spawns additional reversing drips every `8` ticks,
5. calls [`tickRegeneration(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:251).

### Regeneration gates

[`tickRegeneration(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:251) returns early when:

- entity is null,
- entity is not alive,
- entity is dead/dying/removed,
- current HP `<= 0.0f`,
- entity does not have the base mod [`REVERSE_CURSED_TECHNIQUE`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:260) effect.

### Regen rates

The exact rates are:

| Context | Regen rate |
|---|---:|
| normal RCT | `0.02f` |
| inside `ZONE` | `0.04f` |

Extra healing inside `ZONE`:

```java
if (inZone && entity.tickCount % 10 == 0) {
    heal 1.0f HP
}
```

### Regen state machine

For each limb:

| Current state | Transition |
|---|---|
| `SEVERED` | becomes `REVERSING`, progress reset to `0.0f`, regen-start sound plays |
| `REVERSING` | progress increases by regen rate each tick |
| `REVERSING` with `progress >= 1.0f` | becomes `INTACT`, progress reset to `0.0f`, completion sound and burst play |

Pulse cadence while reversing:

- regen particles every tick through [`spawnRegenParticles(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:282)
- regen pulse sound every `10` ticks through [`playRegenPulseSound(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:284)

When anything changes, the system refreshes gameplay penalties and resends sync packets.

## Gameplay penalties

### [`LimbGameplayHandler.applyLimbDebuffs(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:45)

The system removes prior modifiers first via [`removeAllModifiers(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:71), then reapplies penalties based on missing-arm and missing-leg counts.

### Attack penalties

For missing arms:

| Missing arms | Damage penalty |
|---|---:|
| `1` | `0.25f` |
| `2` | `0.5f` |

Applied to [`Attributes.ATTACK_DAMAGE`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:51) with `AttributeModifier.Operation.MULTIPLY_TOTAL` using UUID [`RIGHT_ARM_ATTACK_UUID`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:41).

The same float is also stored to player persistent data under:

```text
jjkbrp_strike_damage_penalty
```

### Movement penalties

For missing legs:

| Missing legs | Speed penalty |
|---|---:|
| `1` | `-0.6` |
| `2` | `-0.95` |

Applied to [`Attributes.MOVEMENT_SPEED`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:63) using UUID [`LEG_SPEED_UUID`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:42).

### Equipment enforcement

Per-player tick logic in [`onPlayerTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:90) forcibly drops held items:

| Missing limb | Effect |
|---|---|
| left arm | offhand item dropped |
| right arm | mainhand item dropped |
| both arms | selected hotbar item also dropped |
| both legs | sprinting disabled |

### Jump restriction

The tick handler tracks previous vs current vertical delta in `PREV_DELTA_Y`.

When upward motion begins:

| Missing legs | Vertical multiplier |
|---|---:|
| `1` | `0.4` |
| `2` | `0.0` |

So one missing leg heavily nerfs jumping, while two missing legs cancel it entirely.

## Networking

### [`LimbSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:39)

This packet is the authoritative limb-state sync channel.

#### Packet fields

| Field | Type | Meaning |
|---|---|---|
| `entityId` | `int` | target entity runtime ID |
| `states` | `Map<LimbType, LimbState>` | state snapshot |
| `regenProgress` | `Map<LimbType, Float>` | per-limb regen snapshot |

#### Encoding format

[`encode(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:56) writes:

1. `entityId` as `int`,
2. for each [`LimbType`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbType.java:6):
   - state ordinal as `byte`,
   - regen progress as `float`.

Equivalent schema:

```java
int entityId;
for (LimbType type : LimbType.values()) {
    byte stateOrdinal;
    float regenProgress;
}
```

### Sync fan-out rules

| Method | Behavior |
|---|---|
| [`sendToTrackingPlayers(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:81) | sends to server players within `distanceToSqr < 16384.0` |
| [`sendToPlayer(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:94) | direct packet to one player |

Tracking sync is sent:

- after severing,
- after regeneration changes,
- when a player starts tracking another entity with severed limbs,
- on login/respawn in certain cases.

### Client cache

[`ClientLimbCache`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:12) stores snapshots keyed by entity ID.

Important methods:

| Method | Purpose |
|---|---|
| [`update(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:15) | replace snapshot |
| [`get(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:19) | fetch snapshot |
| [`remove(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:23) | clear one entity |
| [`clear()`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:27) | clear all state |

The nested record [`EntityLimbSnapshot`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:31) exposes:

- [`getState(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:32)
- [`getRegenProgress(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:36)
- [`isLimbMissing(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:40)

## Rendering behavior

### Visibility masking

[`LimbRenderHandler.onRenderLivingPre(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRenderHandler.java:42) reads the client snapshot before the entity is rendered.

#### Player model rules

In [`applyPreVisibility(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRenderHandler.java:58):

| State | Render result |
|---|---|
| `SEVERED` | limb part and overlay hidden |
| `REVERSING` and `progress < 0.75f` | limb part and overlay hidden |
| `REVERSING` and `progress >= 0.75f` | limb becomes visible again |
| `INTACT` | normal visibility |

This uses the hard phase threshold:

```java
PHASE_FLESH = 0.75f
```

#### Generic humanoid rules

[`applyHumanoidModelVisibility(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRenderHandler.java:100) simply hides missing parts without the more advanced regrowth staging used for players.

### Regrowth layer

[`LimbRegrowthLayer`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRegrowthLayer.java:44) renders early-phase regrowth for player limbs.

#### Phase thresholds

| Phase constant | Value | Meaning |
|---|---:|---|
| `PHASE_BONE` | `0.25f` | earliest visible regrowth texture |
| `PHASE_MUSCLE` | `0.5f` | middle stage |
| `PHASE_FLESH` | `0.75f` | final pre-normal stage |
| `TEX_SIZE` | `16` | dynamic texture size |

Only limbs with:

- `state == REVERSING`
- `progress < 0.75f`

are rendered through this special layer.

#### Growth scale

The layer computes:

```java
normalizedProgress = min(1.0f, progress / 0.75f);
growthScale = max(0.05f, easeOutCubic(normalizedProgress));
```

with [`easeOutCubic(float)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRegrowthLayer.java:109):

```java
1.0f - (1.0f - t)^3
```

#### Dynamic textures

The regrowth layer generates three procedural textures:

| Texture | Resource path |
|---|---|
| bone | `jjkblueredpurple:dynamic/bone` |
| muscle | `jjkblueredpurple:dynamic/muscle` |
| flesh | `jjkblueredpurple:dynamic/flesh` |

These are generated entirely on the client using `NativeImage` and `DynamicTexture`.

## Login, tracking, and respawn sync

### Tracking sync

When a player begins tracking a living entity, [`onPlayerStartTracking(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:306) sends a limb snapshot if the target currently has severed limbs.

### Login sync

[`onPlayerLoggedIn(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:325) resends sever-state tracking info for the logging-in player and also calls [`ModNetworking.sendNearDeathCdSync(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:337), linking limb and near-death UI readiness.

### Client cache reset

[`LimbRenderHandler.onPlayerRespawn(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRenderHandler.java:37) clears the entire client limb cache on respawn.

## Practical balancing summary

The limb system creates three kinds of punishment:

1. **burst punishment** from sever rolls on heavy or low-HP damage,
2. **persistent combat punishment** from attack and movement debuffs,
3. **recovery dependence** on Reverse Cursed Technique and `ZONE` acceleration.

The most important balance numbers are:

| Mechanic | Value |
|---|---:|
| sever chance cap | `0.85` |
| no-RCT chance multiplier | `0.2` |
| sever cooldown | `40` ticks |
| blood drip timer | `200` ticks |
| regen rate | `0.02` |
| zone regen rate | `0.04` |
| zone heal pulse | `+1.0 HP / 10 ticks` |
| one-arm damage penalty | `25%` |
| two-arm damage penalty | `50%` |
| one-leg speed penalty | `-60%` |
| two-leg speed penalty | `-95%` |
| one-leg jump multiplier | `0.4` |
| two-leg jump multiplier | `0.0` |

## Cross references

- near-death and RCT Level 3: [`NEAR_DEATH_SYSTEM.md`](NEAR_DEATH_SYSTEM.md)
- combat consequences and survivability: [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
- networking details: [`NETWORKING.md`](NETWORKING.md)
- utility and support classes: [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- constant inventory: [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
- system dependency overview: [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)
