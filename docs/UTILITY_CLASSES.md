# Utility Classes

This document describes the helper and support classes that glue the addon together without being primary gameplay systems on their own. These classes are still critical because they:

- normalize domain state queries,
- patch around base-mod edge cases,
- provide cached client state,
- compute domain costs and form policy,
- adapt rendering helpers from the original mod.

## Scope

This document focuses on:

- [`DomainAddonUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java)
- [`DomainRadiusUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java)
- [`DomainCostUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java)
- [`DomainFormPolicy.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java)
- [`BaseCapabilityCrashGuardEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BaseCapabilityCrashGuardEvents.java)
- [`ClientPacketHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java)
- base render helper [`AnimUtils.java`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java)

## Utility-layer role map

| Class | Main responsibility |
|---|---|
| [`DomainAddonUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:46) | shared domain-state, owner-resolution, cleanup-detection, radius, range, and particle helpers |
| [`DomainRadiusUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:13) | temporary radius suppression and restore for base calls |
| [`DomainCostUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:24) | expected domain form cost calculation |
| [`DomainFormPolicy`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java:9) | per-domain archetype and form-behavior policy table |
| [`BaseCapabilityCrashGuardEvents`](../src/main/java/net/mcreator/jujutsucraft/addon/BaseCapabilityCrashGuardEvents.java:25) | safety guard against missing base capability during heal events |
| [`ClientPacketHandler`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:18) | central client-side packet landing zone and cache updater |
| [`AnimUtils`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java:12) | base Geckolib/model bridge helper used for rendering overlays over bones |

## [`DomainAddonUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:46)

This is the most important general-purpose helper in the addon. It centralizes the logic that would otherwise be copied across domain mixins, projectile overrides, cleanup logic, and VFX.

### Core constants

| Constant | Value | Meaning |
|---|---:|---|
| `DEFAULT_DOMAIN_RADIUS` | `16.0` | fallback radius when no better value exists |
| `DOMAIN_MASTERY_COMBAT_TAG_GRACE_TICKS` | `100` | combat-tag grace window |
| `OWNER_NAME_RANGED_SEARCH_RADIUS` | `256.0` | search radius for `NameRanged` owner fallback |
| `TAG_DOMAIN_EXPANSION` | `"DomainExpansion"` | base persistent marker |
| `TAG_CLASH_OPPONENT` | `"jjkbrp_clash_opponent"` | clash tracking marker |
| `TAG_LAST_COMBAT_TICK` | `"addon_bf_last_combat_tick"` | combat-tag timestamp |
| `TAG_DOMAIN_BF_BONUS` | `"jjkbrp_domain_bf_bonus"` | domain BF bonus key |
| `TAG_OWNER_UUID` | `"OWNER_UUID"` | projectile/orb owner UUID key |
| `TAG_NAME_RANGED` | `"NameRanged"` | base ownership fallback key |
| `TAG_NAME_RANGED_RANGED` | `"NameRanged_ranged"` | ranged ownership fallback key |

### Major helper families

#### 1. Black Flash cleanup

[`cleanupBFBoost(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:60) removes temporary domain-side Black Flash modifiers.

Behavior:

- removes `jjkbrp_domain_bf_bonus`,
- if `jjkbrp_bf_cnt6_boost` exists, subtracts it back out of `cnt6`,
- then removes `jjkbrp_bf_cnt6_boost`.

This function is critical because multiple systems temporarily inflate `cnt6`, and the addon must restore the base combat state after the buff window ends.

#### 2. Actual domain radius and center resolution

[`getActualDomainRadius(LevelAccessor, CompoundTag)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:75) prefers runtime addon NBT over base map variables.

Resolution order:

1. if `jjkbrp_base_domain_radius` exists, use it with `jjkbrp_radius_multiplier`,
2. otherwise fall back to base [`MapVariables.DomainExpansionRadius`](../../jjc_decompile/net/mcreator/jujutsucraft/network/JujutsucraftModVariables.java),
3. if all else fails, use `16.0`.

Important clamping:

```java
radiusMultiplier = Math.max(0.5, radiusMultiplier)
return Math.max(1.0, baseRadius * radiusMultiplier)
```

[`getDomainCenter(Entity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:93) resolves domain center from:

- `x_pos_doma/y_pos_doma/z_pos_doma`,
- otherwise `jjkbrp_open_domain_cx/cy/cz`,
- otherwise the entity position.

[`getOpenDomainCenter(Entity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:107) prefers the open-domain cached center, then falls back to [`getDomainCenter(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:93).

#### 3. Domain-state classification

These helpers are used widely by mixins and orb overrides.

| Method | Purpose |
|---|---|
| [`isOpenDomainState(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:118) | detects active open-domain form |
| [`isIncompleteDomainState(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:140) | detects incomplete-domain form |
| [`isClosedDomainActive(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:161) | detects standard closed domain by exclusion |
| [`hasActiveDomainExpansion(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:251) | broader “domain exists” test |
| [`isInDomainClash(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:265) | checks clash-opponent tracking |
| [`isCombatTagged(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:276) | checks combat effect or recent combat timestamp |

Open-domain detection recognizes multiple representations:

- `jjkbrp_open_form_active`,
- `jjkbrp_domain_form_cast_locked == 2`,
- `jjkbrp_domain_form_effective == 2`,
- `cnt2 > 0.0`,
- or a `DOMAIN_EXPANSION` effect with amplifier `> 0`.

Incomplete-domain detection similarly accepts several forms, including string/int form-lock values and `cnt2 < 0.0`.

#### 4. Owner resolution for projectiles and orbs

[`resolveOwnerEntity(Entity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:165) and [`resolveOwnerEntity(LevelAccessor, Entity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:172) are central to Blue/Red/Purple logic.

Resolution order:

1. `OWNER_UUID` via [`resolveOwnerByUuid(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:219),
2. `NameRanged_ranged` via [`resolveOwnerByNameRanged(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:234),
3. otherwise `null`.

The fallback `NameRanged` search spans:

- all server players first,
- then living entities within a `256.0` radius AABB.

[`isOwnerInDomain(Entity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:200) combines owner resolution and form classification to answer whether a projectile/orb owner is currently in any domain state.

#### 5. Domain mutation locking helpers

The mastery UI and mixins use:

- [`isDomainMasteryMutationLocked(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:287)
- [`getDomainMasteryMutationLockReason(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:291)

The lock triggers if the entity:

- has an active domain,
- is in a clash,
- or is combat-tagged.

These are not UI-only helpers; they encode gameplay rules shared between networking and domain runtime.

#### 6. Build/active-domain detection and cleanup lookup

`DomainAddonUtils` contains several overloads of [`isDomainBuildOrActive(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:304), including:

- [`isDomainBuildOrActive(Player)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:304)
- [`isDomainBuildOrActive(LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:326)
- [`isDomainBuildOrActive(ServerLevel, Player)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:348)
- [`isDomainBuildOrActive(ServerLevel, LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:358)

These helpers recognize startup or active domain state through a combination of:

- `x_pos_doma` presence,
- active `DOMAIN_EXPANSION` effect,
- startup values such as `select`, `skill`, `skill_domain`, `jjkbrp_domain_id_runtime`,
- and required thresholds `cnt3 >= 20.0` and `cnt1 > 0.0`.

The server-level overloads additionally refuse to count a domain as live if a nearby cleanup entity is already breaking blocks.

Cleanup-support helpers include:

| Method | Purpose |
|---|---|
| [`hasBreakingCleanupEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:368) | looks for cleanup entities with `Break == true` |
| [`findMatchingLiveDomainPlayer(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:383) | nearest live domain player around a center |
| [`findMatchingLiveDomainCaster(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:398) | nearest live domain living caster around a center |

#### 7. Open-domain range helpers

These methods standardize the open-domain geometry used by VFX and combat:

| Method | Default/fallback behavior |
|---|---|
| [`getOpenDomainRange(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:413) | fallback `40.0`, multiplier clamped to at least `2.5` |
| [`getOpenDomainVisualRange(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:423) | fallback `48.0`, visual multiplier clamped to `[3.0, 4.5]` |
| [`getOpenDomainShellRadius(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:434) | fallback `16.0`, minimum shell radius `8.0` |

#### 8. Reflection-based effect mutation

[`setEffectDuration(MobEffectInstance, int)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:442) is a compatibility utility that tries multiple reflective paths to rewrite potion/effect duration.

Search order:

1. field named `effect`,
2. field named `duration`,
3. fallback scan of integer fields that are not clearly amplifier fields and currently hold plausible duration values.

This helper exists because the addon often needs to adjust active effect duration without direct setter APIs.

#### 9. Long-distance particle send helper

[`sendLongDistanceParticles(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:482) first attempts a reflective per-player long-distance particle method, then falls back to ordinary [`ServerLevel.sendParticles(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:501).

The reflective method is resolved by [`resolveLongDistanceParticleMethod()`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:504).

## [`DomainRadiusUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:13)

This is a small but important helper for temporary radius rewriting during base-procedure calls.

### Internal state

| Field | Type | Purpose |
|---|---|---|
| `SCALED_CTX_ORIGINAL` | `ThreadLocal<Double>` | stores original radius during scaled execution |

### Methods

| Method | Purpose |
|---|---|
| [`onScalingApplied(LevelAccessor, double)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:19) | records original radius into thread-local context |
| [`getOriginalRadiusIfScaling()`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:23) | reads stored original radius |
| [`clearScalingContext()`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:27) | removes thread-local state |
| [`suppressForBlock(LevelAccessor)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:31) | temporarily restores original radius before block-sensitive code |
| [`restoreAfterSuppressed(LevelAccessor, Double, double)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:44) | reapplies scaled radius after suppression |
| deprecated [`restoreAfterSuppressed(LevelAccessor, Double)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:55) | convenience overload using multiplier `1.0` |

### Practical role

The addon often needs base domain procedures to behave as if the original radius still exists for block placement or cleanup calculations, while other parts of the tick use the scaled runtime radius. This class is the synchronization tool that makes that possible.

## [`DomainCostUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:24)

This helper centralizes domain-cast cost preview and form multiplier logic.

### Methods

| Method | Purpose |
|---|---|
| [`isDomainTechniqueSelected(PlayerVariables)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:28) | checks whether the selected technique is domain slot `20` |
| [`resolveEffectiveForm(Player)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:35) | resolves current effective domain form from runtime NBT or capability |
| [`formMultiplier(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:43) | maps form to cost multiplier |
| [`resolveTechniqueBaseCost(Player, PlayerVariables)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:51) | computes base CE cost after status-item adjustments |
| [`resolveExpectedDomainCastCost(Player, PlayerVariables)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:75) | multiplies base cost by current form |

### Form multipliers

[`formMultiplier(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:43) uses:

| Form | Multiplier |
|---|---:|
| incomplete (`0`) | `0.55` |
| closed (`1`) | `1.0` |
| open (`2`) | `1.6` |

### Base-cost modifiers

[`resolveTechniqueBaseCost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:51) includes several gameplay-specific cost mutations:

| Condition | Effect |
|---|---|
| `STAR_RAGE` + physical attack outside live domain | `+ 10.0 + 9.0 * (amp + 1)` |
| `SUKUNA_EFFECT` | cost halved |
| `SIX_EYES` | multiplies cost by `0.1^(amp + 1)` |
| unused [`LOUDSPEAKER`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:69) | cost becomes `0.0` |

This class is why the wheel and domain UI can display expected CE usage without executing the cast itself.

## [`DomainFormPolicy`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java:9)

This class is a static policy table mapping numeric domain IDs to addon form behavior.

### Public API

| Method | Purpose |
|---|---|
| [`policyOf(double)`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java:16) | resolve the policy for a raw domain ID |

If no explicit entry exists, the fallback policy is:

```java
new Policy(Archetype.SPECIAL, false, 0.01, 12.0, 0.9, 1.1, 1.0, 0.5)
```

### Policy fields

[`Policy`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java:61) stores:

| Field | Meaning |
|---|---|
| `archetype` | high-level behavior family |
| `openAllowed` | whether open form is allowed |
| `incompletePenaltyPerTick` | incomplete upkeep penalty |
| `openRangeMultiplier` | open-domain range scalar |
| `openSureHitMultiplier` | sure-hit strength scalar |
| `openCeDrainMultiplier` | CE drain scalar |
| `openDurationMultiplier` | duration scalar |
| `barrierRefinement` | clash/barrier stability scalar |

### Archetypes

[`Archetype`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java:64) values are:

- `REFINED`
- `CONTROL`
- `SUMMON`
- `AOE`
- `UTILITY`
- `SPECIAL`

### Examples

The table contains explicit entries for selected domain IDs such as:

| Domain ID | Archetype | Open allowed | Notes |
|---:|---|---|---|
| `1` | `REFINED` | yes | open range `18.0`, barrier refinement `0.95` |
| `2` | `REFINED` | yes | open sure-hit `0.95`, CE drain `1.3` |
| `15` | `CONTROL` | yes | incomplete penalty `0.015` |
| `6` | `SUMMON` | no | refinement `0.3` |
| `4` | `AOE` | yes | CE drain `1.3` |
| `20` | `UTILITY` | no | incomplete penalty `0.02`, refinement `0.4` |

The class then fills any missing IDs from `1` through `50` with the default special-case policy.

## [`BaseCapabilityCrashGuardEvents`](../src/main/java/net/mcreator/jujutsucraft/addon/BaseCapabilityCrashGuardEvents.java:25)

This is a tiny but high-priority safety class.

### Method

| Method | Purpose |
|---|---|
| [`onLivingHeal(LivingHealEvent)`](../src/main/java/net/mcreator/jujutsucraft/addon/BaseCapabilityCrashGuardEvents.java:27) | cancels healing when the base player capability is missing |

### Behavior

The event runs at `EventPriority.HIGHEST` and:

- ignores non-players,
- ignores client side,
- checks whether [`JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY`](../src/main/java/net/mcreator/jujutsucraft/addon/BaseCapabilityCrashGuardEvents.java:36) exists,
- cancels healing if it does not.

This is effectively a crash-prevention shim for cases where addon healing logic would otherwise hit a missing base capability.

## [`ClientPacketHandler`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:18)

Although it is packet-oriented, this class is also a utility aggregator because it is the central client landing zone for multiple independent systems.

### Screen helpers

| Method | Purpose |
|---|---|
| [`openSkillWheel(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:22) | opens [`SkillWheelScreen`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:58) |
| [`openDomainMasteryScreen()`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:43) | opens [`DomainMasteryScreen`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryScreen.java) |

### State sync helpers

| Method | Purpose |
|---|---|
| [`updateCooldowns(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:26) | updates cooldown bar cache |
| [`updateBlackFlash(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:31) | updates BF HUD cache |
| [`updateNearDeathCooldown(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:37) | updates near-death cooldown cache |
| [`syncDomainMastery(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:47) | copies synced mastery data into the client capability |

### Embedded caches

#### [`ClientCooldownCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:55)

Stores:

- `techRemaining`
- `techMax`
- `combatRemaining`
- `combatMax`

Useful methods:

- [`updateTechnique(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:64)
- [`updateCombat(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:69)
- [`getRemaining(boolean)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:74)
- [`getMax(boolean)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:78)
- [`tickDecay()`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:82)

#### [`ClientBlackFlashCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:92)

Stores:

- `bfPercent`
- `mastery`
- `charging`

#### [`ClientNearDeathCdCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:101)

Stores:

- `cdRemaining`
- `cdMax` with default `6000`
- `rctLevel3Unlocked`

This class therefore serves as a central client-side utility registry for several otherwise unrelated subsystems.

## Base helper: [`AnimUtils`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java:12)

This is a small base-mod rendering helper used for overlaying vanilla `ModelPart`s onto Geckolib bones.

### Methods

| Method | Purpose |
|---|---|
| [`renderPartOverBone(ModelPart, GeoBone, PoseStack, VertexConsumer, int, int, float)`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java:14) | convenience overload using white RGB and custom alpha |
| [`renderPartOverBone(ModelPart, GeoBone, PoseStack, VertexConsumer, int, int, float, float, float, float)`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java:18) | full RGBA render helper |
| [`setupModelFromBone(ModelPart, GeoBone)`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java:23) | positions model part at the target bone pivot |

### Behavior

[`setupModelFromBone(...)`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java:23) copies pivot coordinates from the Geckolib [`GeoBone`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java:9) into the vanilla [`ModelPart`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java:10), then zeroes some internal offsets before rendering.

This class matters because the addon’s limb rendering and overlay strategies exist in a mod ecosystem that already uses mixed rendering layers and helper abstractions.

## Practical reading order

For helper-level debugging or extension, read these in order:

1. [`DomainAddonUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java)
2. [`DomainRadiusUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java)
3. [`DomainCostUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java)
4. [`DomainFormPolicy.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java)
5. [`ClientPacketHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java)
6. [`BaseCapabilityCrashGuardEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BaseCapabilityCrashGuardEvents.java)
7. [`AnimUtils.java`](../../jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java)

## Cross references

- domain runtime usage: [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md)
- domain mixin consumers: [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md)
- combat and BF cleanup coupling: [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
- skill wheel packet/cost preview usage: [`SKILL_WHEEL.md`](SKILL_WHEEL.md)
- packet landing and caches: [`NETWORKING.md`](NETWORKING.md)
- central numeric inventory: [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
- end-to-end coupling map: [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)
