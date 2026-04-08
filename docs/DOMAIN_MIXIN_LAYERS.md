# Domain Mixin Architecture

This document describes every domain-related addon mixin that rewires the original domain pipeline from [`DomainExpansionCreateBarrierProcedure.execute(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) through active ticking, clashes, cleanup, and barrier restoration.

The core idea is that the addon does **not** replace the original domain system wholesale. Instead, it layers targeted Sponge Mixin hooks over the base JujutsuCraft procedures so that:

- startup radius can be scaled or restored,
- runtime NBT can carry form- and mastery-dependent state,
- open and incomplete domains can coexist with the original closed-domain logic,
- clash outcomes can generate erosion, wrap pressure, and mastery XP,
- cleanup entities can be stabilized so terrain restoration is not lost,
- expiry logic can avoid restoring blocks while another live domain still owns the area.

## Source map

| Layer | Primary addon files | Base targets |
|---|---|---|
| Startup capture | [`DomainStartupRadiusMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainStartupRadiusMixin.java), [`DomainCreateBarrierMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java), [`DomainOpenClashCancelMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java) | base startup procedures, [`DomainExpansionCreateBarrierProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionCreateBarrierProcedure.java) |
| Barrier build | [`DomainExpansionBattleMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpansionBattleMixin.java), [`DomainBarrierMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java) | [`DomainExpansionBattleProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionBattleProcedure.java) |
| Active tick and mastery | [`DomainActiveTickRadiusMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainActiveTickRadiusMixin.java), [`DomainMasteryMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java), [`DomainCastCostMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCastCostMixin.java), [`DomainEffectStartDurationMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainEffectStartDurationMixin.java) | [`DomainExpansionOnEffectActiveTickProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionOnEffectActiveTickProcedure.java) |
| Clash control | [`DomainOpenClashCancelMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java), [`DomainClashPenaltyMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java), [`DomainClashXpMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java) | create-barrier and active-tick procedures |
| Cleanup entity control | [`DomainCleanupEntitySpawnMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntitySpawnMixin.java), [`DomainCleanupEntityRangeMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntityRangeMixin.java), [`DomainExpireBarrierFixMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java), [`DomainBarrierRestoreGuardMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierRestoreGuardMixin.java) | cleanup spawn/update/expiry procedures |
| Visual/open-domain presentation | [`DomainMasteryMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java), [`DomainOpenVfxMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenVfxMixin.java) | active tick / domain visuals |

## Architectural goals

Compared with the base logic documented in [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md), the mixin layer adds five major behaviors:

1. **Domain form selection** via [`DomainMasteryData`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java:15).
2. **Runtime scaling** of radius, duration, and clash power via [`DomainAddonUtils.getActualDomainRadius(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:75) and [`DomainRadiusUtils.suppressForBlock(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:31).
3. **Open/incomplete specialization** so the same base domain effects can run with different geometry and clash behavior.
4. **Persistent cleanup protection** so barrier blocks are not restored too early.
5. **Mastery-side rewards and penalties** including property buffs, erosion, tie/win/loss XP, and negative-property balancing.

## End-to-end hook order

| Phase | What happens | Main mixins |
|---|---|---|
| Startup procedure fires | original per-domain procedure calls barrier creation | [`DomainStartupRadiusMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainStartupRadiusMixin.java:36) |
| Barrier creation starts | runtime NBT, form state, radius multipliers, open/incomplete flags written | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69), [`DomainOpenClashCancelMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:54) |
| Domain effect instance applied | duration/amplifier can be promoted or rewritten | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:208), [`DomainEffectStartDurationMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainEffectStartDurationMixin.java) |
| Barrier blocks placed | placement redirected for incomplete/open geometry | [`DomainExpansionBattleMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpansionBattleMixin.java:41), [`DomainBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java:230) |
| Active tick runs | temporary radius scaling, property effects, VFX, clash logic, range cancel | [`DomainActiveTickRadiusMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainActiveTickRadiusMixin.java:30), [`DomainMasteryMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:96), [`DomainClashPenaltyMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java:83), [`DomainClashXpMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:56) |
| Cleanup entity spawns/ticks | cleanup center/range normalized and protected | [`DomainCleanupEntitySpawnMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntitySpawnMixin.java:38), [`DomainCleanupEntityRangeMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntityRangeMixin.java:41) |
| Domain expires | delayed restore sweep, fallback cleanup spawn, state purge | [`DomainExpireBarrierFixMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:71), [`DomainBarrierRestoreGuardMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierRestoreGuardMixin.java:34) |

## Shared runtime NBT keys

The domain mixin stack communicates primarily through persistent entity NBT on the caster and temporary cleanup entities.

| NBT key | Written by | Used by | Purpose |
|---|---|---|---|
| `jjkbrp_domain_form_effective` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | clash/range/cleanup mixins | final resolved form after mastery + unlock checks |
| `jjkbrp_domain_form_cast_locked` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | later active-tick logic | cast-time snapshot to stop mid-domain mutation |
| `jjkbrp_domain_radius_multiplier` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | radius mixins, cleanup, battle | mastery/property-scaled runtime multiplier |
| `jjkbrp_domain_base_radius` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | cleanup restore logic | original unscaled radius |
| `jjkbrp_open_domain_range` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | VFX, range cancel, clash | effective open sure-hit range |
| `jjkbrp_open_domain_visual_range` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | open-domain VFX | presentation radius separate from some gameplay checks |
| `jjkbrp_open_domain_shell_radius` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | cleanup and boundary effects | outer visual shell |
| `jjkbrp_incomplete_surface_multiplier` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:417) | barrier placement/clash | reduced surface coverage factor |
| `jjkbrp_incomplete_penalty_per_tick` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | upkeep/cost hooks | pressure tax for incomplete form |
| `jjkbrp_barrier_refinement` | [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | clash erosion | defensive stability coefficient |
| `jjkbrp_recent_clash_contact_tick` | [`DomainClashPenaltyMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java:118) | [`DomainClashXpMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:408) | confirms mutual recent clash participation |
| `jjkbrp_open_attacker_uuid` | [`DomainOpenClashCancelMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:149) | penalty/xp resolution | tracks open-domain erosion attacker |
| `jjkbrp_erosion_target_uuid` | [`DomainOpenClashCancelMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:149) | penalty/xp resolution | tracks erosion defender |
| `jjkbrp_incomplete_wrap_target_uuid` | [`DomainOpenClashCancelMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:168) | penalty/xp resolution | incomplete caster wrap target |
| `jjkbrp_incomplete_wrapper_uuid` | [`DomainOpenClashCancelMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:168) | penalty/xp resolution | target notes who is wrapping it |
| `jjkbrp_open_domain_center_x/y/z` | [`DomainMasteryMixin.jjkbrp$cacheOpenDomainCenter(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:254) | VFX and cleanup stabilization | stable center cache for open domains |

## Mixin-by-mixin reference

### 1. [`DomainStartupRadiusMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainStartupRadiusMixin.java)

**Target:** many per-domain startup procedures, injected immediately after the call to [`DomainExpansionCreateBarrierProcedure.execute(...)`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionCreateBarrierProcedure.java)

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(... INVOKE shift=AFTER)` | [`DomainStartupRadiusMixin.java:36`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainStartupRadiusMixin.java:36) | captures the base radius context right after domain startup |
| `@Inject(... RETURN)` | [`DomainStartupRadiusMixin.java:68`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainStartupRadiusMixin.java:68) | restores temporary radius state so later logic sees clean values |

This mixin exists because the base startup procedures are all separate classes such as `UnlimitedVoidProcedure`, `MalevolentShrineProcedure`, and other domain-specific launchers. The addon needs one common interception point to keep startup radius scaling consistent without editing every procedure body.

### 2. [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java)

**Target:** [`DomainExpansionCreateBarrierProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionCreateBarrierProcedure.java)

This is the first major mutation layer. It turns a base domain cast into a mastery-aware runtime configuration object.

#### Injection summary

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(HEAD)` | [`DomainCreateBarrierMixin.java:69`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:69) | resolves mastery data, form, radius multiplier, open/incomplete metadata, duration-related NBT, barrier refinement, and announcement state |
| `@Redirect LivingEntity.addEffect(...)` | [`DomainCreateBarrierMixin.java:208`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:208) | rewrites the applied domain effect instance so duration/amplifier can match form/mastery rules |
| `@Inject(RETURN)` | [`DomainCreateBarrierMixin.java:374`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:374) | final post-cast cleanup and state normalization |

#### Important helper methods

- [`promoteDomainAmplifier(Player, int)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:273)
- [`jjkbrp$useBaseOpenRangePath(double)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:303)
- [`jjkbrp$announceHalfChargeCallout(LevelAccessor, Player, double)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:308)
- [`jjkbrp$buildHalfChargeMessage(Player, String)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:338)
- [`jjkbrp$resolveDomainName(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:343)
- [`hasOpenBarrierAdvancement(Player)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:396)
- [`jjkbrp$resolveIncompleteSurfaceMultiplier(LevelAccessor, Player)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:417)
- [`jjkbrp$resolveDomainDurationTicks(Player, DomainMasteryData, int, int)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:473)

#### Functional role

This mixin is where the addon snapshots the chosen domain form from [`DomainMasteryData.setDomainTypeSelected(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java:108) and resolves it against advancement gates. It is also the most important writer of shared runtime NBT, which later mixins treat as authoritative.

It additionally introduces the half-charge callout path through [`jjkbrp$announceHalfChargeCallout(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:308), which is one of the clearest signs that open-domain startup is treated as a distinct cast experience instead of a simple radius scalar.

### 3. [`DomainOpenClashCancelMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java)

**Target:** [`DomainExpansionCreateBarrierProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionCreateBarrierProcedure.java)

This mixin intercepts the startup phase to stop the base system from treating every domain-vs-domain encounter as a normal symmetric clash. Open and incomplete forms receive special state wiring instead.

#### Injection summary

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(HEAD)` | [`DomainOpenClashCancelMixin.java:54`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:54) | scans nearby build-state casters, classifies form pairs, and initializes either erosion or incomplete-wrap clash state |

#### Important helper methods

- [`initErosionClash(LivingEntity, LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:149)
- [`initIncompleteWrapClash(LivingEntity, LivingEntity)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:168)
- [`jjkbrp$clearIncompleteWrapState(CompoundTag)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:179)
- [`jjkbrp$clearWrappedByIncompleteState(CompoundTag)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:184)
- [`jjkbrp$clearErosionAttackerState(CompoundTag)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:189)
- [`jjkbrp$clearErosionDefenderState(CompoundTag)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:194)
- [`jjkbrp$isDomainBuildState(CompoundTag)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:200)
- [`jjkbrp$resolveDomainForm(LivingEntity, boolean)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:212)
- [`jjkbrp$hasOpenBarrierAdvancement(Player)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:246)
- [`jjkbrp$isWithinBaseClashWindow(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:271)
- [`jjkbrp$baseClashRange(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:289)
- [`jjkbrp$isBaseStartupOpenState(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:294)

#### Functional role

This mixin is the bridge between startup and the later erosion/wrap algorithms. It writes explicit attacker/target UUID pairings so that [`DomainClashPenaltyMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java) and [`DomainClashXpMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java) can resolve outcomes deterministically instead of relying only on proximity.

### 4. [`DomainExpansionBattleMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpansionBattleMixin.java)

**Target:** [`DomainExpansionBattleProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionBattleProcedure.java)

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(HEAD)` | [`DomainExpansionBattleMixin.java:41`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpansionBattleMixin.java:41) | stores cleanup/radius state before block generation |
| `@Inject(RETURN)` | [`DomainExpansionBattleMixin.java:54`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpansionBattleMixin.java:54) | normalizes cleanup entities after battle/build completes |

Helper:
- [`jjkbrp$normalizeCleanupEntities(LevelAccessor, Player)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpansionBattleMixin.java:66)

This mixin mainly prevents later cleanup drift. Without it, open or scaled domains can spawn cleanup entities with centers/ranges that do not match the runtime form data.

### 5. [`DomainBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java)

**Target:** [`DomainExpansionBattleProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionBattleProcedure.java)

This is the geometry override layer. It turns the original “safe place block” calls into form-aware placement rules.

#### Injection summary

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(HEAD)` | [`DomainBarrierMixin.java:70`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java:70) | caches placement context for the current caster/domain |
| `@Inject(RETURN)` | [`DomainBarrierMixin.java:94`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java:94) | clears temporary placement state |
| `@Redirect placeBlockSafe(...)` | [`DomainBarrierMixin.java:230`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java:230) | selectively places, skips, or cleans wall/floor blocks based on domain form |

#### Important helper methods

- cached-position/context helpers in [`DomainBarrierMixin.java:108`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java:108) through [`DomainBarrierMixin.java:223`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java:223)
- [`jjkbrp$cleanAdjacentWallBlocks(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java:300)
- [`jjkbrp$shouldCleanEdgeShellPlacement(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java:336)

#### Functional role

The redirect allows incomplete domains to have partial walls/floors rather than the full closed shell. It also corrects floor `Y` alignment and performs shell-edge cleanup so stray barrier blocks do not remain when the shape is intentionally porous.

### 6. [`DomainActiveTickRadiusMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainActiveTickRadiusMixin.java)

**Target:** [`DomainExpansionOnEffectActiveTickProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionOnEffectActiveTickProcedure.java)

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(HEAD)` | [`DomainActiveTickRadiusMixin.java:30`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainActiveTickRadiusMixin.java:30) | temporarily applies runtime radius multiplier before base active-tick code runs |
| `@Inject(RETURN)` | [`DomainActiveTickRadiusMixin.java:58`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainActiveTickRadiusMixin.java:58) | restores original radius state after the tick |

This mixin is narrow but critical. It lets the original sure-hit and per-domain effect logic keep running against a temporary scaled radius without permanently mutating shared base variables.

### 7. [`DomainMasteryMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java)

**Target:** [`DomainExpansionOnEffectActiveTickProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionOnEffectActiveTickProcedure.java)

This is the largest and most behavior-dense domain mixin in the addon.

#### Redirects

| Redirect target | Lines | Why it exists |
|---|---|---|
| [`DomainActiveProcedure.execute(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:96) | active tick redirect | wraps base domain-active logic with form/mastery side effects |
| [`EffectCharactorProcedure.execute(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:108) | victim effect redirect | allows mastery-specific effect sequencing |
| [`LivingEntity.addEffect(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:118) | direct potion/effect application redirect | lets addon constrain or alter effect application during active ticks |

#### Return inject

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(RETURN)` | [`DomainMasteryMixin.java:148`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:148) | syncs mastery/form state, awards XP, applies property effects, caches open-domain centers, fires opening VFX, stabilizes cleanup entities, performs range-cancel logic |

#### Important helper methods

- cleanup and state:
  - [`jjkbrp$stabilizeDomainCleanupEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:208)
  - [`jjkbrp$findCleanupEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:231)
  - [`jjkbrp$isOpenDomainActive(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:239)
  - [`jjkbrp$cacheOpenDomainCenter(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:254)
- open-domain VFX gating:
  - [`jjkbrp$shouldFireOpeningVFX(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:288)
  - [`jjkbrp$decrementGracePeriod(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:306)
  - [`jjkbrp$fireOpeningVFX(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:320)
  - [`jjkbrp$playReferenceSound(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:406)
- VFX profile building:
  - [`fogColorForDomain(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:413)
  - [`dustScaleForDomain(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:441)
  - [`jjkbrp$fogProfileForDomain(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:454)
  - [`jjkbrp$mixColor(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:481)
  - [`presetForDomain(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:486)
  - [`applyArchetypeSignature(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:539)
  - [`jjkbrp$spawnOpenDomainBoundaryCurtain(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:586)
  - [`jjkbrp$spawnOpenDomainParticleBarrier(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:616)
  - [`jjkbrp$spawnOpenDomainGroundHaze(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:680)
  - [`jjkbrp$emitOpenDomainCorePulse(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:735)
  - [`applyOpenDomainVFX(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:744)
- range cancellation and buffs:
  - [`checkOpenDomainRangeCancel(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:805)
  - [`checkIncompleteDomainRangeCancel(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:889)
  - [`jjkbrp$applyIncompleteZoneOnlyBuff(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:922)
- property effect dispatch:
  - [`applyPropertyEffects(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:942)
  - [`applyVictimCEDrain(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:977)
  - [`applyBFChanceBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:999)
  - [`applyRCTHealBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:1019)
  - [`applyBlindEffect(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:1042)
  - [`applySlowEffect(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:1056)

#### Functional role

`DomainMasteryMixin` is effectively the addon’s domain-side runtime controller. The base active-tick procedure remains the execution spine, but this mixin decides how mastery rules attach to it.

It performs several distinct jobs in one pass:

1. **State sync** to keep capability form selection and active domain state aligned.
2. **XP award hooks** for long-lived or successfully maintained domains.
3. **Property application** using [`DomainMasteryProperties`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryProperties.java) values such as CE drain, BF chance boost, RCT heal boost, blind, and slow.
4. **Open-domain presentation** with domain-specific fog, dust, curtain, particle barrier, haze, and pulse presets.
5. **Range cancel enforcement** so open or incomplete domains can terminate or suppress targets based on geometric conditions rather than the base enclosed-shell assumptions.
6. **Cleanup stabilization** so a correct cleanup entity persists even when the domain is open and no literal wall shell exists.

#### Property-effect mapping

| Property effect | Helper method | Result |
|---|---|---|
| victim CE drain | [`applyVictimCEDrain(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:977) | drains cursed energy from nearby victims inside effect range |
| BF chance boost | [`applyBFChanceBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:999) | feeds Black Flash subsystem while inside domain |
| RCT heal boost | [`applyRCTHealBoost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:1019) | accelerates healing/regeneration state |
| blind | [`applyBlindEffect(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:1042) | applies blindness to victims in range |
| slow | [`applySlowEffect(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:1056) | applies movement slowdown to victims in range |

### 8. [`DomainClashPenaltyMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java)

**Target:** [`DomainExpansionOnEffectActiveTickProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionOnEffectActiveTickProcedure.java)

This mixin computes the actual clash degradation rules for open-vs-closed and incomplete-vs-target interactions.

#### Injection summary

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(HEAD)` | [`DomainClashPenaltyMixin.java:83`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java:83) | applies immediate incomplete-domain pre-penalties and temporary clash state setup |
| `@Inject(RETURN)` | [`DomainClashPenaltyMixin.java:118`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java:118) | resolves effective power, erosion, wrap pressure, mutual contact refresh, and loss-state transitions |

#### Constants

```java
private static final double JJKBRP$BASE_EROSION_RATE = 0.2;
private static final double JJKBRP$MAX_POWER_RATIO = 3.0;
private static final double JJKBRP$INCOMPLETE_WRAP_PRESSURE = 0.1;
private static final double JJKBRP$INCOMPLETE_WRAP_STABILITY = 0.45;
```

#### Core formulas

```java
double totalErosionThisTick = 0.2 * sureHitMult * (1.0 - barrierRef) * incompleteMultiplier * powerRatio;
totalErosionThisTick = Math.min(totalErosionThisTick, 2.0);
```

```java
double wrapPressure = 0.1 * ratio;
if (DomainClashPenaltyMixin.jjkbrp$isOpenDomainState(targetCaster, targetNbt)) {
    wrapPressure *= 0.7;
}
wrapPressure = Math.min(wrapPressure, 0.18);
```

#### Important helper families

The internal `@Unique` helpers in [`DomainClashPenaltyMixin.java:144`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java:144) through [`DomainClashPenaltyMixin.java:714`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java:714) cover:

- state classification for open/incomplete/closed casters,
- tracked-opponent lookup via UUID keys,
- effective clash power resolution,
- barrier refinement lookup,
- incomplete stability modifiers,
- loss-state tagging and clearing,
- recent contact bookkeeping.

#### Functional role

This mixin is the addon’s answer to the base mod’s all-or-nothing clash logic. Instead of instantly invalidating one domain, it produces **erosion over time** and **wrap pressure** that can lead to defeat, suppression, or collapse depending on form matchup and power ratio.

### 9. [`DomainClashXpMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java)

**Target:** [`DomainExpansionOnEffectActiveTickProcedure`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionOnEffectActiveTickProcedure.java)

This mixin turns clash resolution into mastery progression.

#### Injection summary

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(RETURN)` | [`DomainClashXpMixin.java:56`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:56) | checks clash context after each tick and resolves tie/win/loss XP outcomes |

#### Constants

```java
private static final int JJKBRP$WINNER_XP = 50;
private static final int JJKBRP$TIE_XP = 30;
private static final int JJKBRP$LOSER_XP = 10;
private static final long JJKBRP$TIE_WINDOW_TICKS = 5L;
private static final long JJKBRP$RECENT_CLASH_CONTACT_TICKS = 40L;
```

#### Important helper methods

- search and plausibility:
  - [`jjkbrp$buildClashSearchBox(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:112)
  - [`jjkbrp$hasClashContext(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:129)
  - [`jjkbrp$isPlausibleLocalClashOpponent(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:277)
  - [`jjkbrp$isFallbackDirectClashNeighbor(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:302)
- pending-outcome resolution:
  - [`jjkbrp$findPendingTieEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:153)
  - [`jjkbrp$canResolveTie(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:190)
  - [`jjkbrp$findPendingLoserForWinner(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:203)
  - [`jjkbrp$findWinnerEntity(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:240)
- explicit pairing and tracking:
  - [`jjkbrp$isExplicitClashPair(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:340)
  - [`jjkbrp$matchesTargetUuid(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:349)
  - [`jjkbrp$resolveTrackedOpponent(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:360)
  - [`jjkbrp$hasRecentMutualClashContact(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:408)
- result classification:
  - [`jjkbrp$isDomainCasterState(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:430)
  - [`jjkbrp$isActiveClashParticipant(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:435)
  - [`jjkbrp$isLossState(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:442)
  - [`jjkbrp$hasPendingWithinWindow(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:449)
  - [`jjkbrp$isPendingExpired(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:461)
  - [`jjkbrp$hasRecentClashContact(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:473)
- resolution + messaging:
  - [`jjkbrp$clearOutcomeTracking(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:485)
  - [`jjkbrp$markResolved(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:497)
  - [`jjkbrp$resolveWinLose(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:505)
  - [`jjkbrp$resolveTie(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:532)
  - [`sendOutcomeMessage(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:553)
  - [`jjkbrp$clashIcon(String)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:566)
  - [`grantXpIfNotMax(Player, int)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:582)

#### Functional role

The key design detail is that XP resolution uses both **explicit UUID pairing** and **fallback spatial plausibility**. That makes clash rewards robust even if one side’s state clears slightly earlier than the other. The mixin awards different XP for winner, loser, and tie while also generating combat log style feedback.

### 10. [`DomainCleanupEntitySpawnMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntitySpawnMixin.java)

**Target:** `DomainExpansionEntityOnInitialEntitySpawnProcedure`

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(RETURN)` | [`DomainCleanupEntitySpawnMixin.java:38`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntitySpawnMixin.java:38) | seeds cleanup entity center and range metadata immediately after spawn |

This keeps cleanup entities aligned with runtime domain geometry instead of whatever default values the base spawn procedure used.

### 11. [`DomainCleanupEntityRangeMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntityRangeMixin.java)

**Target:** `AIDomainExpansionEntityProcedure`

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(HEAD, cancellable=true)` | [`DomainCleanupEntityRangeMixin.java:41`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntityRangeMixin.java:41) | overrides cleanup scan radius and can keep cleanup dormant while a domain is still active |
| `@Inject(RETURN)` | [`DomainCleanupEntityRangeMixin.java:69`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntityRangeMixin.java:69) | restores the original cleanup radius state |

Helper block:
- [`DomainCleanupEntityRangeMixin.java:84`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntityRangeMixin.java:84) through [`DomainCleanupEntityRangeMixin.java:109`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntityRangeMixin.java:109)

This mixin prevents “cleanup wakes up too early and restores live-domain terrain” failures.

### 12. [`DomainBarrierRestoreGuardMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierRestoreGuardMixin.java)

**Target:** `JujutsuBarrierUpdateTickProcedure`

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(HEAD, cancellable=true)` | [`DomainBarrierRestoreGuardMixin.java:34`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierRestoreGuardMixin.java:34) | cancels restoration when another live domain still protects the block |

This is a narrow but essential protection layer for overlapping or back-to-back domains.

### 13. [`DomainExpireBarrierFixMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java)

**Target:** `DomainExpansionEffectExpiresProcedure`

This mixin handles the dangerous end-of-life phase where state can desynchronize and terrain can be restored incorrectly.

#### Injection summary

| Injection point | Lines | Effect |
|---|---|---|
| `@Inject(HEAD)` | [`DomainExpireBarrierFixMixin.java:71`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:71) | snapshots expiry context and suppresses premature failed/defeated cleanup branches |
| `@Inject(RETURN)` | [`DomainExpireBarrierFixMixin.java:87`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:87) | forces cleanup break signaling, spawns fallback cleanup entities if missing, clears runtime NBT, schedules delayed clash cleanup and final restore sweeps, optionally reduces fatigue duration |

#### Important helper methods

- [`jjkbrp$scheduleDelayedClashCleanup(ServerLevel, UUID)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:227)
- [`jjkbrp$resolveEffectiveRadius(LevelAccessor, CompoundTag)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:245)
- [`jjkbrp$runFinalRestoreSweep(ServerLevel, double, double, double, double)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:266)
- [`jjkbrp$isProtectedByOtherLiveDomain(ServerLevel, double, double, double, double, double, double)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:292)
- [`jjkbrp$spawnCleanupEntity(ServerLevel, double, double, double, double)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:306)
- [`jjkbrp$scheduleReducedFatigue(ServerLevel, UUID)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:326)

#### Functional role

`DomainExpireBarrierFixMixin` is effectively the addon’s fail-safe for domain teardown. It makes expiry idempotent enough to survive missing cleanup entities, overlapping live domains, and delayed clash state.

Notable behaviors from the current implementation:

- delayed clash cleanup scheduled for **80 ticks** through [`jjkbrp$scheduleDelayedClashCleanup(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:227),
- fallback cleanup entity spawned when the original cleanup path did not leave one behind,
- runtime keys removed so future domains do not inherit stale open/incomplete metadata,
- final restore sweep checks [`jjkbrp$isProtectedByOtherLiveDomain(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java:292) before replacing terrain,
- fatigue shortening can be granted when maximum `RCT_HEAL_BOOST` conditions are met.

### 14. [`DomainCastCostMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCastCostMixin.java)

This mixin participates in the active domain pipeline by adjusting cast/upkeep cost behavior so form-specific penalties or mastery discounts remain synchronized with the runtime NBT set by [`DomainCreateBarrierMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java).

Its practical role is cross-cutting rather than structural: it ensures incomplete/open domains pay the intended CE cost profile instead of the base closed-domain assumption.

### 15. [`DomainEffectStartDurationMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainEffectStartDurationMixin.java)

This mixin modifies the starting effect duration path so domain effect lifetime reflects the mastery-derived duration resolution used by [`jjkbrp$resolveDomainDurationTicks(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java:473) and [`DomainMasteryData.resolveFinalDurationTicks(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java:306).

It exists so all later active-tick hooks work from a correctly scaled duration window.

### 16. [`DomainOpenVfxMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenVfxMixin.java)

This mixin complements the larger VFX path in [`DomainMasteryMixin.applyOpenDomainVFX(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:744). Its purpose is presentation-specific support for open-domain visuals that do not fit neatly into the base domain effect or barrier procedures.

## Domain clash state model

The addon effectively replaces the base single-clash outcome with a small state machine.

| Matchup | Startup initializer | Tick penalty logic | XP resolution |
|---|---|---|---|
| open vs closed | [`initErosionClash(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:149) | [`DomainClashPenaltyMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java:118) erosion path | [`jjkbrp$resolveWinLose(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:505) or [`jjkbrp$resolveTie(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:532) |
| incomplete vs target | [`initIncompleteWrapClash(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:168) | wrap-pressure path in [`DomainClashPenaltyMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java:118) | same XP resolver after pending outcome settles |
| ordinary local clash fallback | proximity checks in [`jjkbrp$isWithinBaseClashWindow(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java:271) and [`jjkbrp$buildClashSearchBox(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:112) | tracked by recent contact tick | tie/win/loss fallback resolution |

## Why the cleanup stack is split across four mixins

The cleanup and restore problem is too broad for one injection point, so the addon separates responsibilities:

| Problem | Mixin solving it |
|---|---|
| cleanup entity spawned with wrong center/range | [`DomainCleanupEntitySpawnMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntitySpawnMixin.java) |
| cleanup entity starts restoring while domain is still active | [`DomainCleanupEntityRangeMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCleanupEntityRangeMixin.java) |
| individual barrier blocks restore even though another domain still covers them | [`DomainBarrierRestoreGuardMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierRestoreGuardMixin.java) |
| expiry path misses cleanup or clears state too early | [`DomainExpireBarrierFixMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java) |

## Relationship to utility classes

Several utility classes are effectively part of the mixin architecture:

| Utility | Role in mixin stack |
|---|---|
| [`DomainAddonUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java) | shared helpers for actual radius, center, owner resolution, open-domain range, cleanup detection, and duration mutation |
| [`DomainRadiusUtils`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java) | temporary suppression/restore helpers for radius-sensitive base calls |
| [`DomainMasteryData`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java) | authoritative mastery/form/property data store |
| [`DomainFormPolicy`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java) | per-domain archetype and form multiplier policy |
| [`DomainMasteryProperties`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryProperties.java) | defines property value scales consumed by [`DomainMasteryMixin.applyPropertyEffects(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java:942) |

## Practical reading order

For debugging or extending the domain stack, read the files in this order:

1. [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md)
2. [`DomainCreateBarrierMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainCreateBarrierMixin.java)
3. [`DomainOpenClashCancelMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainOpenClashCancelMixin.java)
4. [`DomainBarrierMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainBarrierMixin.java)
5. [`DomainMasteryMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainMasteryMixin.java)
6. [`DomainClashPenaltyMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java)
7. [`DomainClashXpMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java)
8. [`DomainExpireBarrierFixMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainExpireBarrierFixMixin.java)

## Cross references

- Base system flow: [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md)
- Networking and GUI sync for mastery UI: [`NETWORKING.md`](NETWORKING.md)
- Utility/helper deep dive: [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- Global constant inventory: [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
- Combat interactions with Black Flash and range attack mixins: [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
- Cross-system dependencies: [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)
