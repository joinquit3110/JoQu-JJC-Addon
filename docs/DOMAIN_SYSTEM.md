# Domain System

## Scope

This document covers the full domain stack across:

1. the **base JujutsuCraft domain pipeline**, and
2. the **addon mastery / form / cost / radius / GUI extensions**.

Primary source references:

- Base mod:
  - [`JujutsucraftModVariables.java`](../../jjc_decompile/net/mcreator/jujutsucraft/network/JujutsucraftModVariables.java)
  - [`DomainExpansionCreateBarrierProcedure.java`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionCreateBarrierProcedure.java)
  - [`DomainExpansionBattleProcedure.java`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionBattleProcedure.java)
  - [`DomainExpansionOnEffectActiveTickProcedure.java`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionOnEffectActiveTickProcedure.java)
- Addon:
  - [`DomainMasteryData.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java)
  - [`DomainMasteryProperties.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryProperties.java)
  - [`DomainFormPolicy.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java)
  - [`DomainMasteryScreen.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryScreen.java)
  - [`DomainMasteryCommands.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryCommands.java)
  - [`ModNetworking.java`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java)
  - [`DomainAddonUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java)
  - [`DomainCostUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java)
  - [`DomainRadiusUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java)
  - domain mixins under [`addon/mixin/`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/)

Related documents:

- [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md)
- [`NETWORKING.md`](NETWORKING.md)
- [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)

---

## 1. High-Level Model

The original mod treats Domain Expansion as a **three-stage lifecycle**:

1. **Startup / charge**
2. **Barrier build / deployment**
3. **Active effect tick**

The addon extends that lifecycle with:

- a **Domain Mastery capability**,
- selectable forms: **Incomplete / Closed / Open**,
- per-domain **policy data**,
- runtime **NBT state** for cost, duration, range, and visuals,
- patched behavior through the mixin layer.

---

## 2. Base Mod Domain Flow

## 2.1 Startup phase: `DomainExpansionCreateBarrierProcedure.execute(...)`

### Method signature

```java
public static void execute(LevelAccessor world, double x, double y, double z, Entity entity)
```

### Core startup responsibilities

The startup procedure is the original domain cast bootstrap. It handles:

- charge-up timing,
- cursed-energy payment,
- anchor point acquisition,
- barrier mode choice,
- nearby domain conflict checks,
- final transition into barrier creation.

### Startup charge: `cnt3`

The base mod uses the persistent double key:

```text
cnt3
```

Behavior:

- if `cnt3 < 20.0`, the cast is still charging,
- on the first tick (`cnt3 == 0.0`) the procedure calls:

```java
ChangeCurseEnergyProcedure.execute(entity, entity.getPersistentData().getDouble("used_technique_cost"))
```

- `cnt3` then increments every tick by either:
  - `1`, or
  - `2` when the caster has `ZONE` or strong neutralization-adjusted startup support.

### CE consumption

The initial cast cost comes from:

```text
used_technique_cost
```

The procedure consumes CE through:

```java
ChangeCurseEnergyProcedure.execute(...)
```

Then, once the barrier actually starts, the procedure performs an additional inversion-style CE change using:

```java
ChangeCurseEnergyProcedure.execute(entity, entity.getPersistentData().getDouble("used_technique_cost") * -1.0)
```

That second call is exactly the call later intercepted by the addon’s domain cost mixin.

### Position lock

When the cast reaches the deployment stage, the procedure stores the domain center using these persistent keys:

```text
x_pos_doma
y_pos_doma
z_pos_doma
```

These are computed from:

- the caster location,
- facing direction,
- `MapVariables.DomainExpansionRadius`,
- optional target alignment.

The base mod also snapshots temporary probe positions with:

```text
x_pos_doma2
y_pos_doma2
z_pos_doma2
```

### Form decision via `cnt2`

The base mod uses:

```text
cnt2
```

as the original barrier-form marker.

Observed base meaning:

| `cnt2` value | Meaning |
|---|---|
| `1.0` | open / no-barrier behavior path |
| `0.0` | standard closed barrier |
| `-1.0` | incomplete-style path used by certain domains such as Megumi |

Base logic sets `cnt2` from:

- domain ID / selected skill,
- crouch state,
- open-barrier advancement or Sukuna/open-domain exceptions,
- special-case entities such as Megumi variants.

### Other important startup keys

```text
select
skill
skill_domain
Failed
Cover
cnt1
cnt5
cnt7
StartDomainAttack
```

### Startup-to-build transition

Once:

```text
cnt1 >= max(34.0, domainRadius * 2.0 + 1.0)
```

the procedure:

- adds `200.0` to `PlayerCursePowerChange`,
- marks `StartDomainAttack = true`,
- applies the `DOMAIN_EXPANSION` mob effect,
- sets the effect duration to `1200` ticks normally or `3600` for domain `29`,
- uses `cnt2` as the effect amplifier,
- locks `noChangeTechnique = true`,
- calls `KeyChangeTechniqueOnKeyPressedProcedure.execute(...)`.

---

## 2.2 Barrier construction: `DomainExpansionBattleProcedure.execute(...)`

### Method signature

```java
public static void execute(LevelAccessor world, double x, double y, double z, Entity entity)
```

### Core responsibilities

This procedure is the original barrier builder. It is responsible for:

- selecting the barrier block set,
- spawning the cleanup entity,
- expanding the shell over time,
- filling wall / floor / interior layers,
- repositioning overlapping entities onto the floor.

### Domain skin selection: `GetDomainBlockProcedure`

The procedure begins with:

```java
GetDomainBlockProcedure.execute(entity)
```

This populates runtime string fields such as:

```text
domain_outside
domain_inside
domain_floor
```

These strings are later fed into `placeBlockSafe(...)`.

### Cleanup entity: `DomainExpansionEntityEntity`

For closed barriers, the base builder manages a center cleanup entity of type:

```text
DomainExpansionEntityEntity
```

Spawn logic:

- if `close_type <= 0.0`, and
- this is the first build phase,
- it removes an existing non-breaking cleanup entity at the center,
- then spawns a new cleanup entity at `x_pos_doma / y_pos_doma / z_pos_doma`.

This entity is important for later barrier restoration and cleanup behavior.

### Sphere barrier construction

The base procedure constructs the barrier using nested loops over a radius cube and then keeps only blocks whose squared distance falls within shell bands.

Main parameters:

```text
range = MapVariables.DomainExpansionRadius
loop_num = round(range * 2.0 + 1.0)
cnt_type = Cover ? "cnt_cover" : "cnt1"
cnt2 = persistent(cnt_type) * speed
```

The shell logic uses checks like:

```text
dis < (range + 0.5)^2
```

and inner/outer bands such as:

```text
dis >= (range - 1.0)^2
dis >= (range - 2.0)^2
```

So the base mod effectively builds a **spherical shell with layered interior/floor logic**, not a simple cube.

### Open vs closed branch

The procedure sets:

```java
noBarrier = (close_type > 0.0)
```

Meaning:

- `cnt2 > 0` → no closed shell placement path,
- `cnt2 <= 0` → closed / incomplete barrier placement path.

### Helper method

```java
private static void placeBlockSafe(LevelAccessor world, BlockPos pos, String blockName)
```

This resolves block strings either via `BlockStateParser` or via a registry fallback.

---

## 2.3 Active phase: `DomainExpansionOnEffectActiveTickProcedure.execute(...)`

### Method signature

```java
public static void execute(LevelAccessor world, double x, double y, double z, Entity entity)
```

### Core responsibilities

Once the `DOMAIN_EXPANSION` effect is active, the base active-tick procedure handles:

- routing to the per-domain active procedure,
- checking domain presence around the center,
- applying sure-hit effects,
- applying neutralization in-domain,
- domain clash-style failure resolution,
- damage tracking through `oldHealth` and `totalDamage`.

### Route to `DomainActiveProcedure.execute(...)`

If `skill_domain > 0.0`, the procedure calls:

```java
DomainActiveProcedure.execute(world, x, y, z, entity)
```

This is the main domain-specific active-logic dispatch point.

The addon later redirects this call for incomplete domains.

### Sure-hit range formula

The base active tick computes range from global radius as:

```java
range = domainExpansionRadius * ((amp > 0) ? 18 : 2)
```

Where:

- `domainExpansionRadius` is `MapVariables.DomainExpansionRadius`,
- `amp` is the amplifier of the `DOMAIN_EXPANSION` effect.

This means:

| Amplifier | Effective sure-hit multiplier |
|---|---|
| `0` | `radius * 2` |
| `> 0` | `radius * 18` |

This is the key base formula reused by many addon clash and range-cancel systems.

### Sure-hit application path

Every 20 ticks, for targets in range, the base procedure calls:

```java
EffectCharactorProcedure.execute(world, caster, target)
```

For neutralization of entities with `select == 0.0`, it also applies:

```java
NEUTRALIZATION
```

with amplifier:

```text
skill_domain + 10.0
```

### Domain presence / failure checks

The active tick also:

- confirms the owner remains inside the domain influence range,
- can remove `DOMAIN_EXPANSION` if the owner is no longer valid,
- tracks total damage dealt / received through:

```text
oldHealth
totalDamage
```

Those values later become important to the addon clash-power formulas.

---

## 3. Addon Domain Mastery

The addon’s domain mastery state lives in the capability data class:

```java
public class DomainMasteryData
```

This is the main persistent domain progression model for players.

---

## 3.1 Forms

### Constants

```java
public static final int FORM_INCOMPLETE = 0;
public static final int FORM_CLOSED = 1;
public static final int FORM_OPEN = 2;
```

### Form names

| Value | Name |
|---|---|
| `0` | Incomplete |
| `1` | Closed |
| `2` | Open |

### Related methods

```java
public int getDomainTypeSelected()
public void setDomainTypeSelected(int type)
public void setDomainTypeSelected(int type, boolean hasOpenBarrierAdvancement)
public String getDomainFormName()
public int getDomainFormAmplifier()
public static boolean isClosedFormUnlocked(int masteryLevel)
public static boolean isOpenFormUnlocked(int masteryLevel, boolean hasOpenBarrierAdvancement)
public static int sanitizeFormSelection(int type, int masteryLevel)
public static int sanitizeFormSelection(int type, int masteryLevel, boolean hasOpenBarrierAdvancement)
```

### Unlock rules

| Form | Unlock condition |
|---|---|
| Incomplete | always available |
| Closed | `masteryLevel >= 1` |
| Open | `masteryLevel >= 5` **and** open-barrier advancement unlocked |

---

## 3.2 Property model

### Global limits

```java
public static final int MAX_PROPERTY_LEVEL = 10;
public static final int MIN_NEGATIVE_LEVEL = -5;
public static final int NEGATIVE_UNLOCK_LEVEL = 5;
public static final double NEGATIVE_DEBUFF_PER_POINT = 0.1;
```

### Eight tracked properties

The capability tracks eight upgradeable properties:

| Property enum | Short label | Max level |
|---|---|---|
| `VICTIM_CE_DRAIN` | CE_DRAIN | `10` |
| `BF_CHANCE_BOOST` | BF_CHANCE | `10` |
| `RCT_HEAL_BOOST` | RCT_HEAL | `10` |
| `BLIND_EFFECT` | BLIND | `10` |
| `SLOW_EFFECT` | SLOW | `10` |
| `DURATION_EXTEND` | DURATION | `10` |
| `RADIUS_BOOST` | RADIUS | `10` |
| `CLASH_POWER` | CLASH_POWER | `10` |

### Property value-per-level definitions

From `DomainMasteryProperties`:

| Property | Value per level | Unit |
|---|---|---|
| `VICTIM_CE_DRAIN` | `2.0` | `CE/0.5s` |
| `BF_CHANCE_BOOST` | `0.5` | `% BF` |
| `RCT_HEAL_BOOST` | `0.4` | `HP/s` |
| `BLIND_EFFECT` | `1.0` | `lvl` |
| `SLOW_EFFECT` | `1.0` | `lvl` |
| `DURATION_EXTEND` | `10.0` | `s` |
| `RADIUS_BOOST` | `25.0` | `%` |
| `CLASH_POWER` | `1.0` | empty unit |

### Related methods

```java
public int getPropertyLevel(DomainMasteryProperties prop)
public void setPropertyLevel(DomainMasteryProperties prop, int level)
public boolean upgradeProperty(DomainMasteryProperties prop)
public boolean downgradeProperty(DomainMasteryProperties prop)
public void refundAllProperties()
public int getEffectiveLevel(DomainMasteryProperties prop)
public int getEffectivePropertyLevel(DomainMasteryProperties prop)
public double getClashPowerBonus()
```

---

## 3.3 Negative modify system

Only three properties support negative levels:

```text
DURATION_EXTEND
RADIUS_BOOST
CLASH_POWER
```

### Rules

- unlocks at **Domain Mastery level 5**,
- minimum level is **`-5`**,
- only one property can be negative at a time,
- the target property must be at level `0` before negative decrease is applied.

### Methods

```java
public String getNegativeProperty()
public int getNegativeLevel()
public boolean hasNegativeModify()
public boolean isNegativeProperty(DomainMasteryProperties prop)
public boolean canSetNegative(DomainMasteryProperties prop)
public boolean decreaseNegative(DomainMasteryProperties prop)
public boolean increaseNegative(DomainMasteryProperties prop)
public int getNegativePoints()
```

### Exact minimum

```text
MIN_NEGATIVE_LEVEL = -5
```

### Negative runtime scaling

#### Duration

- positive bonus: `+200` ticks per level
- negative penalty: `-200` ticks per negative point
- final duration floor: `200` ticks

#### Radius

- positive bonus: `+0.25` multiplier per level
- negative penalty: `-0.1` multiplier per negative point
- minimum runtime multiplier after clamp: `0.1`

#### Clash Power

- positive bonus: `+0.1` runtime multiplier per level
- negative penalty: `-0.05` runtime multiplier per negative point
- flat clash bonus helper: `+1.0` per positive level, `-0.5` per negative point

### Related methods

```java
public int getDurationBonusTicks()
public int resolveFinalDurationTicks(int baseDurationTicks)
public double getDurationRuntimeMultiplier()
public double getRadiusRuntimeMultiplier()
public double getClashRuntimeMultiplier()
```

---

## 3.4 XP thresholds and mastery levels

### XP threshold table

```text
Level 0 -> 0 XP
Level 1 -> 300 XP
Level 2 -> 700 XP
Level 3 -> 1300 XP
Level 4 -> 2200 XP
Level 5 -> 3500 XP
```

### Source method

```java
private static int getXPRequiredForLevel(int level)
```

### Level-up behavior

`addDomainXP(double amount)` calls `checkLevelUp()`, which:

- increments mastery level up to `5`,
- grants `+1` property point per mastery level gained,
- recalculates form validity via `sanitizeFormSelection(...)`.

### Related methods

```java
public double getDomainXP()
public void setDomainXP(double xp)
public void addDomainXP(double amount)
public int getDomainMasteryLevel()
public void setDomainMasteryLevel(int level)
public double getXPProgress()
public int getXPToNextLevel()
public int getDomainPropertyPoints()
public void setDomainPropertyPoints(int points)
public boolean spendPropertyPoints(int cost)
```

---

## 3.5 Domain Mastery NBT serialization

### Method signatures

```java
public CompoundTag writeNBT()
public void readNBT(CompoundTag nbt)
```

### Primary serialized keys

```text
jjkbrp_domain_xp
jjkbrp_domain_mastery_level
jjkbrp_domain_type_selected
jjkbrp_domain_property_points
jjkbrp_prop_ce_drain
jjkbrp_prop_bf_chance
jjkbrp_prop_rct_heal
jjkbrp_prop_blind
jjkbrp_prop_slow
jjkbrp_prop_duration
jjkbrp_prop_radius
jjkbrp_prop_clash_power
jjkbrp_negative_property
jjkbrp_negative_level
negativeProperty
negativeLevel
```

### Notes

- both prefixed and legacy plain negative keys are written,
- `readNBT(...)` accepts either the prefixed or legacy negative keys,
- `openBarrierAdvancementUnlocked` is **not** persisted in the NBT payload; it is re-derived from advancement state and network sync.

---

## 4. Addon Domain Forms

The addon formalizes three domain forms and adds per-domain policies.

---

## 4.1 `DomainFormPolicy`

### Lookup method

```java
public static Policy policyOf(double rawDomainId)
```

### Policy record

```java
public record Policy(
    Archetype archetype,
    boolean openAllowed,
    double incompletePenaltyPerTick,
    double openRangeMultiplier,
    double openSureHitMultiplier,
    double openCeDrainMultiplier,
    double openDurationMultiplier,
    double barrierRefinement
)
```

### Archetypes

```text
REFINED
CONTROL
SUMMON
AOE
UTILITY
SPECIAL
```

### Coverage

Policies are explicitly defined for selected domain IDs and then backfilled for every ID from **`1` to `50`**.

Default fallback policy:

```text
Archetype.SPECIAL
openAllowed = false
incompletePenaltyPerTick = 0.01
openRangeMultiplier = 12.0
openSureHitMultiplier = 0.9
openCeDrainMultiplier = 1.1
openDurationMultiplier = 1.0
barrierRefinement = 0.5
```

---

## 4.2 Open domain

### Model

Open form is the addon’s explicit no-barrier domain state.

### Characteristics

- no closed shell placement,
- visual shell / particle curtain instead of physical enclosure,
- boosted sure-hit values,
- boosted CE drain cost while active or on attacks,
- explicit range-based self-cancel logic.

### Important runtime behavior

Common runtime fields consumed by mixins and utils:

```text
jjkbrp_open_form_active
jjkbrp_open_range_multiplier
jjkbrp_open_surehit_multiplier
jjkbrp_open_ce_drain_multiplier
jjkbrp_open_domain_cx
jjkbrp_open_domain_cy
jjkbrp_open_domain_cz
jjkbrp_open_center_locked
jjkbrp_opening_vfx_fired
jjkbrp_open_cast_game_time
```

### Utility helpers

```java
public static boolean isOpenDomainState(LivingEntity entity)
public static Vec3 getOpenDomainCenter(Entity entity)
public static double getOpenDomainRange(LevelAccessor world, Entity entity)
public static double getOpenDomainVisualRange(LevelAccessor world, Entity entity)
public static double getOpenDomainShellRadius(LevelAccessor world, Entity entity)
```

### Range formulas

`DomainAddonUtils` computes:

```text
Open gameplay range = actualRadius * max(2.5, jjkbrp_open_range_multiplier)
Open visual range = actualRadius * max(3.0, min(4.5, multiplier * 0.25))
Open shell radius = max(8.0, actualRadius)
```

---

## 4.3 Closed domain

Closed form is the standard barrier-based domain.

### Characteristics

- uses the original shell-building path,
- retains normal barrier cleanup entity behavior,
- uses normal sure-hit model,
- does not use open-domain range cancel rules,
- remains the baseline against which open-form erosion is judged in clashes.

This is effectively the least disruptive form relative to the base mod.

---

## 4.4 Incomplete domain

Incomplete form is an explicit addon-recognized domain state.

### Characteristics

- reduced / partial barrier construction,
- **no sure-hit**,
- `DomainAttack` forcibly disabled,
- `DomainActiveProcedure.execute(...)` bypassed,
- `EffectCharactorProcedure.execute(...)` bypassed,
- `NEUTRALIZATION` application skipped for non-caster targets,
- clash behavior shifts into wrap-pressure rules instead of normal completion.

### Cooldown cap

The addon enforces:

```text
600 ticks
```

through the startup mixin and cooldown clamp path.

### Relevant constants / keys

```text
JJKBRP$INCOMPLETE_DOMAIN_COOLDOWN_TICKS = 600
jjkbrp_incomplete_form_active
jjkbrp_incomplete_session_active
jjkbrp_domain_form_cast_locked = 0
jjkbrp_domain_form_effective = 0
```

### Incomplete support in base data

The addon still reuses the base convention that `cnt2 < 0.0` means incomplete behavior.

---

## 5. Addon Domain Cost

The addon centralizes domain cost logic in:

```java
public final class DomainCostUtils
```

---

## 5.1 Method reference

```java
public static boolean isDomainTechniqueSelected(JujutsucraftModVariables.PlayerVariables vars)
public static int resolveEffectiveForm(Player player)
public static double formMultiplier(int form)
public static double resolveTechniqueBaseCost(Player player, JujutsucraftModVariables.PlayerVariables vars)
public static double resolveExpectedDomainCastCost(Player player, JujutsucraftModVariables.PlayerVariables vars)
```

---

## 5.2 Domain-technique detection

A domain is considered selected when:

```java
Math.round(vars.PlayerSelectCurseTechnique) == 20L
```

So select ID **`20`** is the canonical domain-cast action.

---

## 5.3 Form multiplier

```java
public static double formMultiplier(int form)
```

Exact values:

| Form | Multiplier |
|---|---|
| Incomplete (`0`) | `0.55` |
| Closed (`1`) | `1.0` |
| Open (`2`) | `1.6` |

---

## 5.4 Base cost modifiers

`resolveTechniqueBaseCost(...)` starts from:

```text
PlayerSelectCurseTechniqueCostOrgin
```

Then applies these exact modifiers:

### `STAR_RAGE`

If:

- the player has `STAR_RAGE`,
- the selected action is physical,
- and the player is not currently in a stable domain-expansion state,

then:

```text
cost = round(cost + 10.0 + 9.0 * (amp + 1))
```

### `SUKUNA_EFFECT`

```text
cost = round(cost * 0.5)
```

### `SIX_EYES`

```text
cost = round(cost * pow(0.1, amp + 1))
```

### Loudspeaker override

If the main-hand item is `LOUDSPEAKER` and it has not been marked `Used`, then:

```text
cost = 0.0
```

### Final expected cast cost

```text
resolveExpectedDomainCastCost = resolveTechniqueBaseCost * formMultiplier(resolveEffectiveForm(player))
```

---

## 6. Addon Domain Radius

The addon turns radius into a runtime-scaled concept rather than a fixed global constant.

---

## 6.1 Default radius constant

In `DomainAddonUtils`:

```text
DEFAULT_DOMAIN_RADIUS = 16.0
```

This is the addon fallback when map-variable access fails.

Note that the **base mod** still defaults `MapVariables.DomainExpansionRadius` to **`22.0`**.

---

## 6.2 Actual radius resolution

### Method

```java
public static double getActualDomainRadius(LevelAccessor world, CompoundTag nbt)
```

### Behavior

If the runtime NBT contains:

```text
jjkbrp_base_domain_radius
jjkbrp_radius_multiplier
```

then actual radius is:

```text
max(1.0, baseRadius * max(0.5, radiusMultiplier))
```

Otherwise it falls back to:

```text
MapVariables.DomainExpansionRadius
```

and finally to:

```text
16.0
```

---

## 6.3 Runtime NBT keys used for radius and form state

Common keys seen across utils and mixins:

```text
jjkbrp_base_domain_radius
jjkbrp_radius_multiplier
jjkbrp_domain_form
jjkbrp_domain_form_runtime
jjkbrp_domain_form_cast_locked
jjkbrp_domain_form_effective
jjkbrp_expected_domain_duration
jjkbrp_incomplete_surface_multiplier
jjkbrp_open_range_multiplier
jjkbrp_open_surehit_multiplier
jjkbrp_open_ce_drain_multiplier
jjkbrp_domain_id_runtime
jjkbrp_caster_x_at_cast
jjkbrp_caster_y_at_cast
jjkbrp_caster_z_at_cast
```

These are the keys the addon uses to carry startup-time domain decisions into the base runtime.

---

## 6.4 `DomainRadiusUtils`

This helper exists to safely scale and temporarily suppress radius changes during block operations.

### Method reference

```java
public static void onScalingApplied(LevelAccessor world, double originalRadius)
public static Double getOriginalRadiusIfScaling()
public static void clearScalingContext()
public static Double suppressForBlock(LevelAccessor world)
public static void restoreAfterSuppressed(LevelAccessor world, Double original, double radiusMultiplier)
@Deprecated public static void restoreAfterSuppressed(LevelAccessor world, Double original)
```

### ThreadLocal pattern

The class stores the original radius in:

```java
private static final ThreadLocal<Double> SCALED_CTX_ORIGINAL
```

Flow:

1. radius scaling mixin stores the original radius,
2. barrier placement can temporarily suppress scaling so block math sees the original value,
3. the scaled value is restored afterward.

This prevents block-placement code from accidentally stacking multiple radius transforms.

---

## 7. Addon Domain GUI

The addon adds a complete custom domain mastery UI stack.

---

## 7.1 `DomainMasteryScreen`

### Class role

This is the main client UI for mastery editing.

### Class signature

```java
public class DomainMasteryScreen extends Screen
```

### Core presentation features

- animated panel opening sequence,
- explicit form buttons for **Incomplete / Closed / Open**,
- eight property cards with `+` and `-` controls,
- XP bar and level badge,
- tooltip system,
- combat/domain mutation lock messaging,
- hover / select / reset / close sound feedback.

### Key visual constants

The screen hardcodes many colors and layout values, including:

```text
PANEL_W = 640
PANEL_H = 588
HEADER_H = 154
FOOTER_H = 56
PROP_ROW = 82
PROP_GAP_X = 16
PROP_GAP_Y = 12
```

### Mutation lock rules

The screen locally mirrors server mutation-lock logic and blocks edits when the player is:

- inside active domain expansion,
- inside a domain clash,
- combat-tagged.

### Client interactions sent to the server

The screen triggers `DomainPropertyPacket` operations for:

- upgrade,
- refund,
- reset all,
- set form,
- cycle form,
- negative decrease,
- negative increase.

---

## 7.2 `DomainMasteryCommands`

### Registration entrypoint

```java
public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
```

### Root command

```text
domainmastery
```

### Supported subcommands

```text
addxp
setlevel
addpoints
setform
giveprop
setnegative
reset
info
```

### Purpose

This command suite is mainly an admin / debug control surface for:

- direct XP injection,
- direct level changes,
- property-point grants,
- form overrides,
- property-level assignment,
- negative modifier assignment,
- full reset,
- info dump.

---

## 7.3 Domain GUI packets

The GUI/network loop is driven by three main packet families.

### `DomainPropertyPacket`

Operation constants:

```text
OP_UPGRADE = 0
OP_REFUND = 1
OP_RESET_ALL = 2
OP_SET_FORM = 3
OP_CYCLE_FORM = 4
OP_NEGATIVE_DECREASE = 5
OP_NEGATIVE_INCREASE = 6
```

### `DomainMasteryOpenPacket`

Client requests the server to open the domain mastery UI.

### `DomainMasteryOpenScreenPacket`

Server tells the client to actually open the screen.

### `DomainMasterySyncPacket`

Server pushes current mastery data to the client, including:

```text
xp
level
form
points
propLevels[]
negativeProperty
negativeLevel
hasOpenBarrierAdvancement
```

For full packet details, see [`NETWORKING.md`](NETWORKING.md).

---

## 8. End-to-End Domain Flow with Addon Layer

The effective runtime flow after addon integration is:

1. player selects domain technique (`selectId = 20`),
2. UI and overlay show **expected** cost using `DomainCostUtils`,
3. startup begins in base `DomainExpansionCreateBarrierProcedure.execute(...)`,
4. mixins stamp runtime NBT for form, radius, duration, policy multipliers, and cost,
5. base startup still uses `cnt3`, `cnt1`, `cnt2`, and `x_pos_doma / y_pos_doma / z_pos_doma`,
6. barrier build proceeds through `DomainExpansionBattleProcedure.execute(...)`,
7. barrier shape may be redirected by addon form logic,
8. active tick proceeds through `DomainExpansionOnEffectActiveTickProcedure.execute(...)`,
9. addon mixins inject mastery XP, property effects, VFX, range cancel, clash rules, and cleanup control,
10. on expiration, cleanup mixins restore or dissolve the domain according to runtime form state.

---

## 9. Exact Constants Mentioned in This Document

### Base-side values

```text
cnt3 startup threshold = 20.0
standard active duration = 1200 ticks
special domain 29 duration = 3600 ticks
base sure-hit range multiplier = 2
open-style sure-hit range multiplier = 18
startup completion threshold = max(34.0, radius * 2.0 + 1.0)
PlayerCursePowerChange startup reward = +200.0
```

### Mastery values

```text
FORM_INCOMPLETE = 0
FORM_CLOSED = 1
FORM_OPEN = 2
MAX_PROPERTY_LEVEL = 10
MIN_NEGATIVE_LEVEL = -5
NEGATIVE_UNLOCK_LEVEL = 5
NEGATIVE_DEBUFF_PER_POINT = 0.1
XP thresholds = 0 / 300 / 700 / 1300 / 2200 / 3500
```

### Cost values

```text
Incomplete multiplier = 0.55
Closed multiplier = 1.0
Open multiplier = 1.6
STAR_RAGE bonus = 10.0 + 9.0 * (amp + 1)
SUKUNA_EFFECT multiplier = 0.5
SIX_EYES multiplier = 0.1^(amp + 1)
```

### Property values

```text
CE_DRAIN = 2.0 per level
BF_CHANCE = 0.5 per level
RCT_HEAL = 0.4 per level
BLIND = 1.0 per level
SLOW = 1.0 per level
DURATION = +10s per level
RADIUS = +25% per level
CLASH_POWER = +1.0 per level
Duration bonus tick step = 200
Duration minimum = 200 ticks
Radius negative penalty = 0.1 per point
Clash multiplier negative penalty = 0.05 per point
Clash flat negative penalty = 0.5 per point
```

### Radius values

```text
DomainAddonUtils.DEFAULT_DOMAIN_RADIUS = 16.0
base mod MapVariables.DomainExpansionRadius default = 22.0
open range multiplier floor = 2.5
open visual multiplier floor = 3.0
open visual multiplier cap = 4.5
open shell radius floor = 8.0
runtime radius multiplier floor = 0.5
actual radius floor = 1.0
```

### Incomplete values

```text
incomplete cooldown cap = 600 ticks
```

---

## 10. Cross References

- For all domain-related injections, see [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md).
- For packet formats, see [`NETWORKING.md`](NETWORKING.md).
- For helper methods such as open-range computation and cleanup lookup, see [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md).
- For how domains interact with combat, Black Flash, and clashes, see [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md) and [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md).
