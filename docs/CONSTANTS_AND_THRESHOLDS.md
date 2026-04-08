# Constants and Thresholds

This document collects the most important hardcoded numbers, caps, thresholds, timers, multipliers, colors, and packet-visible limits across the addon and the original domain integration points.

The goal is to make all balance-critical values searchable in one place.

## Scope

This file aggregates constants from:

- [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java)
- [`BlueRedPurpleNukeMod.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java)
- [`DomainMasteryData.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java)
- [`DomainMasteryProperties.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryProperties.java)
- [`DomainFormPolicy.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java)
- [`DomainClashPenaltyMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java)
- [`DomainClashXpMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java)
- [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java)
- [`StartCursedTechniqueProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/StartCursedTechniqueProcedureMixin.java)
- [`RCTLevel3Handler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java)
- [`LimbLossHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java)
- [`LimbRenderHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRenderHandler.java)
- [`LimbRegrowthLayer.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRegrowthLayer.java)
- [`SkillWheelScreen.java`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java)
- [`DomainMasteryScreen.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryScreen.java)
- [`DomainAddonUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java)
- base references such as [`DomainExpansionOnEffectActiveTickProcedure.java`](../../jjc_decompile/net/mcreator/jujutsucraft/procedures/DomainExpansionOnEffectActiveTickProcedure.java) and [`PacketHandler.java`](../../jjc_decompile/net/mcreator/jujutsucraft/PacketHandler.java)

## Domain form constants

### Domain mastery form IDs

From [`DomainMasteryData.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java:16):

| Constant | Value | Meaning |
|---|---:|---|
| `FORM_INCOMPLETE` | `0` | incomplete domain |
| `FORM_CLOSED` | `1` | closed domain |
| `FORM_OPEN` | `2` | open domain |

### Domain cast multipliers

From [`DomainCostUtils.formMultiplier(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainCostUtils.java:43):

| Form | Multiplier |
|---|---:|
| incomplete | `0.55` |
| closed | `1.0` |
| open | `1.6` |

### Domain mastery unlock thresholds

From [`DomainMasteryData.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java:146):

| Rule | Threshold |
|---|---:|
| closed form unlock | mastery level `>= 1` |
| open form unlock | mastery level `>= 5` **and** open-barrier advancement |
| max mastery level | `5` |
| max property level | `10` |
| negative modify minimum | `-5` |
| negative modify unlock level | `5` |
| generic negative debuff per point | `0.1` |

### XP required per mastery level

From [`getXPRequiredForLevel(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java:88):

| Level | XP required |
|---|---:|
| `0` | `0` |
| `1` | `300` |
| `2` | `700` |
| `3` | `1300` |
| `4` | `2200` |
| `5` | `3500` |
| `> 5` | `Integer.MAX_VALUE` |

### Runtime minimums and scaling

From [`DomainMasteryData.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryData.java:306):

| Rule | Value |
|---|---:|
| minimum final duration ticks | `200` |
| duration bonus per positive level | `200` ticks |
| duration reduction per negative point | `200` ticks |
| radius bonus per positive level | `0.25` |
| radius reduction per negative point | `0.1` |
| clash bonus per positive level | `0.1` |
| clash reduction per negative point | `0.05` |
| runtime multiplier floor | `0.1` |
| direct clash-power bonus per level | `1.0` |
| direct clash-power negative reduction per point | `0.5` |

## Domain mastery property constants

From [`DomainMasteryProperties.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryProperties.java:9):

| Property | Value per level | Unit | Max level | Point cost |
|---|---:|---|---:|---:|
| `VICTIM_CE_DRAIN` | `2.0` | `CE/0.5s` | `10` | `1` |
| `BF_CHANCE_BOOST` | `0.5` | `% BF` | `10` | `1` |
| `RCT_HEAL_BOOST` | `0.4` | `HP/s` | `10` | `1` |
| `BLIND_EFFECT` | `1.0` | `lvl` | `10` | `1` |
| `SLOW_EFFECT` | `1.0` | `lvl` | `10` | `1` |
| `DURATION_EXTEND` | `10.0` | `s` | `10` | `1` |
| `RADIUS_BOOST` | `25.0` | `%` | `10` | `1` |
| `CLASH_POWER` | `1.0` | `` | `10` | `1` |

Negative-modify support is restricted to:

- `DURATION_EXTEND`
- `RADIUS_BOOST`
- `CLASH_POWER`

Formatting rules from [`formatNegativeValue(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryProperties.java:67):

| Property | Negative scaling per point |
|---|---:|
| duration | `10s` |
| clash power | `0.5` |
| radius | `10.0%` |
| default property path | property `valuePerLevel` |

## Domain form policy defaults

From [`DomainFormPolicy.policyOf(double)`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java:16), the fallback policy is:

| Field | Default value |
|---|---:|
| archetype | `SPECIAL` |
| open allowed | `false` |
| incomplete penalty per tick | `0.01` |
| open range multiplier | `12.0` |
| open sure-hit multiplier | `0.9` |
| open CE drain multiplier | `1.1` |
| open duration multiplier | `1.0` |
| barrier refinement | `0.5` |

### Representative domain policies

From [`DomainFormPolicy.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainFormPolicy.java:28):

| Domain ID | Archetype | Open allowed | Incomplete penalty | Open range | Sure-hit | CE drain | Duration | Refinement |
|---:|---|---|---:|---:|---:|---:|---:|---:|
| `1` | `REFINED` | yes | `0.005` | `18.0` | `1.0` | `1.25` | `0.95` | `0.95` |
| `2` | `REFINED` | yes | `0.005` | `16.0` | `0.95` | `1.3` | `0.9` | `0.95` |
| `15` | `CONTROL` | yes | `0.015` | `14.0` | `0.9` | `1.15` | `1.0` | `0.55` |
| `6` | `SUMMON` | no | `0.02` | `13.0` | `0.85` | `1.05` | `1.05` | `0.3` |
| `4` | `AOE` | yes | `0.008` | `15.0` | `1.05` | `1.3` | `0.9` | `0.65` |
| `20` | `UTILITY` | no | `0.02` | `13.0` | `0.9` | `1.08` | `1.05` | `0.4` |

## Base and addon domain radius constants

### Base sure-hit formula

From the base active-tick domain procedure documented in [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md), the effective sure-hit range is:

```java
radius * (amp > 0 ? 18 : 2)
```

So the base amplifier split is:

| Amplifier condition | Multiplier |
|---|---:|
| `amp > 0` | `18` |
| otherwise | `2` |

### Addon domain utility defaults

From [`DomainAddonUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:47):

| Constant | Value |
|---|---:|
| default domain radius | `16.0` |
| combat-tag grace ticks | `100` |
| owner `NameRanged` search radius | `256.0` |

### Open-domain helper minimums

From [`DomainAddonUtils.getOpenDomainRange(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:413) and nearby methods:

| Rule | Value |
|---|---:|
| open-domain range fallback | `40.0` |
| open range multiplier floor | `2.5` |
| open visual range fallback | `48.0` |
| visual multiplier floor | `3.0` |
| visual multiplier cap | `4.5` |
| open shell radius fallback | `16.0` |
| open shell minimum | `8.0` |
| live-domain cleanup break-distance check | `4.0` |
| live-domain caster search box half-size | `64.0` |

### Radius suppression helper

From [`DomainRadiusUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainRadiusUtils.java:44):

| Rule | Value |
|---|---:|
| restored scaled radius multiplier floor | `1.0` |

## Domain clash constants

### Clash erosion and wrap pressure

From [`DomainClashPenaltyMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashPenaltyMixin.java:59):

| Constant | Value |
|---|---:|
| base erosion rate | `0.2` |
| max power ratio | `3.0` |
| incomplete wrap pressure base | `0.1` |
| incomplete wrap stability | `0.45` |

Important formulas already extracted:

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

So the derived caps are:

| Derived cap | Value |
|---|---:|
| erosion per tick cap | `2.0` |
| open-target wrap penalty scalar | `0.7` |
| wrap pressure cap | `0.18` |

### Clash XP outcomes

From [`DomainClashXpMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/DomainClashXpMixin.java:50):

| Constant | Value |
|---|---:|
| winner XP | `50` |
| tie XP | `30` |
| loser XP | `10` |
| tie window ticks | `5` |
| recent clash contact ticks | `40` |

## Combat and Black Flash constants

From [`CooldownTrackerEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java:48):

| Constant | Value |
|---|---:|
| BF mastery threshold | `500` |
| BF mastery threshold for Yuji | `200` |
| BF roll chance | `0.0012` |
| BF combat timeout | `100` |
| BF combat rate | `0.004` |
| BF combat decay | `0.06` |
| BF combat max normal | `15.0` |
| BF combat max Yuji | `28.0` |
| BF percent cap normal | `15.0` |
| BF percent cap Yuji | `30.0` |
| generic near-death cooldown reduction | `120` |

From [`RangeAttackProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java:57):

| Constant | Value |
|---|---:|
| BF proc cooldown ticks | `60` |
| BF near-death cooldown reduction | `600` |

Important mismatch to preserve:

| Source | ND cooldown reduction |
|---|---:|
| tracker-side BF logic | `120` |
| range-attack BF proc logic | `600` |

From [`StartCursedTechniqueProcedureMixin.java`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/StartCursedTechniqueProcedureMixin.java:48):

| Constant | Value |
|---|---:|
| incomplete-domain cooldown ticks | `600` |

## Blue / Red / Purple constants

From [`BlueRedPurpleNukeMod.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:157):

| Constant | Value |
|---|---:|
| linger duration | `200` |
| Purple collision radius | `4.0` |
| teleport range | `32.0` |
| aim cone angle | `30.0` |
| behind distance | `2.5` |
| Purple CE threshold | `2000.0` |
| Purple CE cost | `2000.0` |
| Purple HP threshold ratio | `0.3` |
| Red capture radius | `5.0` |
| Red full max range | `128.0` |
| Red preinit ticks | `5` |
| Red charge anchor distance | `2.2` |
| Red charge anchor Y offset | `-0.1` |
| Blue crouch min distance | `10.0` |
| Blue crouch max distance | `20.0` |
| Blue aim orb speed | `1.5` |
| Blue aim duration | `90` |
| Crusher min radius | `1.0` |
| Crusher max radius | `3.0` |
| Crusher base wall damage | `3.0f` |
| Crusher CE damage scale | `0.012` |
| Crusher push strength | `0.5` |
| Crusher cone cosine threshold | `0.5` |
| Crusher base CE drain | `0.5` |
| Crusher CE drain growth | `0.02` |
| Crusher hardlock threshold | `15` |
| Blue full pull radius | `6.0` |
| Blue full pull strength | `1.2` |
| Blue full block range | `4` |
| Blue full block power | `4.0f` |

### Red charge tiers and speeds

From [`getRedChargeTier(double)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1443) and [`getNormalRedSpeed(double)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1453):

| `cnt6` range | Tier | Speed |
|---|---:|---:|
| `< 3.0` | `1` | `2.3` |
| `>= 3.0 && < 5.0` | `2` | `3.2` |
| `>= 5.0` | `3` | `4.3` |

### Red shockwave values

From the extracted logic in [`applyRedExplosionShockwave(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:825):

| Charge tier | Base damage |
|---|---:|
| `1` | `16.0f` |
| `2` | `28.0f` |
| `3` | `42.0f` |

Shockwave radius formula:

```java
radius = 4.0 + chargeTier * 2.4
```

### Blue linger / aim values

From [`handleBlueTick(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/BlueRedPurpleNukeMod.java:1134):

| Rule | Value |
|---|---:|
| Blue linger lifetime | `200` ticks |
| full-charge Blue aim cutoff | `90` ticks |
| Blue aim minimum distance | `10.0` |
| Blue aim maximum distance | `20.0` |
| Blue aim soft movement step | `1.5` |
| full-charge crouch pull radius | `6.0` |
| close pull snap threshold | `2.0` |
| crouch Blue block destroy radius | `4.0` |
| destroy-block hardness cap | `10.0f` |

## Limb-system constants

From [`LimbLossHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbLossHandler.java:92):

| Constant or threshold | Value |
|---|---:|
| scaled minimum damage gate | `4.0f` |
| scaled minimum based on max HP | `5%` of max HP |
| big-hit threshold vs current HP | `30%` |
| low-HP threshold | `30%` |
| sever chance big-hit base | `0.25f` |
| sever chance big-hit damage scalar | `0.3f` |
| sever chance drops-low base | `0.2f` |
| sever chance drops-low scalar | `0.25f` |
| sever chance already-low bonus | `0.15f` |
| sever chance lethal bonus | `0.15f` |
| sever chance cap | `0.85f` |
| no-RCT chance multiplier | `0.2f` |
| sever cooldown | `40` ticks |
| blood drip timer | `200` ticks |
| regen rate | `0.02f` |
| zone regen rate | `0.04f` |
| zone extra heal | `1.0f` every `10` ticks |
| regen pulse sound cadence | every `10` ticks |
| blood drip cadence | every `4` ticks |
| reversing extra drip cadence | every `8` ticks |

### Limb gameplay penalties

From [`LimbGameplayHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbGameplayHandler.java:45):

| Missing limbs | Penalty |
|---|---:|
| 1 arm | `0.25f` attack damage penalty |
| 2 arms | `0.5f` attack damage penalty |
| 1 leg | `-0.6` movement speed modifier |
| 2 legs | `-0.95` movement speed modifier |
| 1 leg jump multiplier | `0.4` vertical carryover |
| 2 legs jump multiplier | `0.0` |

### Severed limb rendering constants

From [`SeveredLimbEntity.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/SeveredLimbEntity.java:53):

| Constant | Value |
|---|---:|
| severed-limb blood drip particle scale | `0.6f` |
| max lifetime | `200` ticks |
| airborne rotX step | `5.0f` |
| airborne rotZ step | `3.0f` |
| gravity subtraction per tick | `0.04` |
| ground horizontal friction | `0.92` |
| bounce horizontal multiplier | `0.8` |
| bounce vertical multiplier | `0.3` |

### Limb regrowth rendering phases

From [`LimbRegrowthLayer.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRegrowthLayer.java:46) and [`LimbRenderHandler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbRenderHandler.java:34):

| Constant | Value |
|---|---:|
| bone phase | `0.25f` |
| muscle phase | `0.5f` |
| flesh phase | `0.75f` |
| regrowth dynamic texture size | `16` |

## Near-death constants

From [`RCTLevel3Handler.java`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:54):

| Constant | Value |
|---|---:|
| near-death window | `20` ticks |
| near-death cooldown | `18000` ticks |
| heal threshold to survive | `4.0f` HP |
| RCT Level 3 unlock count | `20` close calls |
| close-call cooldown | `200` ticks |
| critical-RCT HP threshold | `< 10%` max HP |
| on activation health | `1.0f` |
| movement slowdown duration | `40` ticks |
| initial near-death packet ticks | `20` |

### Near-death UI constants

From packet/UI paths:

| Constant | Value |
|---|---:|
| synced near-death cooldown max UI value | `6000` |
| overlay max ticks | `20` |
| cooldown ring radius | `8` |
| cooldown ring thickness | `2` |
| cooldown ring segments | `48` |

Important implementation mismatch:

| Context | Value |
|---|---:|
| gameplay near-death cooldown | `18000` |
| near-death cooldown packet `cdMax` | `6000` |

## Skill wheel constants

From [`SkillWheelScreen.java`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:60):

| Constant | Value |
|---|---:|
| wheel radius | `90.0f` |
| inner radius | `35.0f` |
| icon radius | `65.0f` |
| center dot radius | `6.0f` |
| spirit select offset | `100` |
| open animation divisor | `250.0f` |
| arc slice segments | `24` |
| ring outline segments | `48` |
| page dot spacing | `12` |
| confirm dust particles | `24` |
| enchant confirm particles | `8` |
| end-rod confirm particles | `6` |
| name truncation threshold | `14` chars |
| truncated visible name | `12` chars + `..` |
| cooldown highlight threshold | `< 1.5f` seconds |

### Skill wheel keybinds

From [`ClientEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java:176):

| Key | Code |
|---|---:|
| skill wheel | `258` |
| domain mastery | `44` |

## Domain mastery screen constants

From [`DomainMasteryScreen.java`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryScreen.java:53):

### Layout sizes

| Constant | Value |
|---|---:|
| panel width | `640` |
| panel height | `588` |
| header height | `154` |
| footer height | `56` |
| property row height | `82` |
| property column width | `143` |
| property gap X | `16` |
| property gap Y | `12` |

### Core colors

| Constant | Value |
|---|---:|
| panel bg | `463645` |
| panel border | `3718648` |
| header bg | `728106` |
| CE drain color | `3718648` |
| BF chance color | `16096779` |
| RCT heal color | `2278750` |
| blind color | `16007006` |
| slow color | `6333946` |
| duration color | `16486972` |
| radius color | `1357990` |
| clash color | `16478597` |
| incomplete form color | `4674921` |
| closed form color | `165063` |
| open form color | `1483594` |
| locked form color | `2042167` |
| text white | `15857145` |
| text mid | `9741240` |
| text dim | `4674921` |
| text gold | `16569165` |
| text lock | `4937059` |
| reset button bg | `1013358` |
| reset button hover | `1357990` |
| close button bg | `1976635` |
| close button hover | `3359061` |
| plus button bg | `366185` |
| plus button hover | `1096065` |
| minus button bg | `14427686` |
| minus button hover | `0xEF4444` |

## Technique registry color constants

From [`TechniqueRegistry.java`](../src/main/java/net/mcreator/jujutsucraft/addon/TechniqueRegistry.java:19):

| Constant | Value |
|---|---:|
| gray | `-9737365` |
| red | `-14935012` |
| blue | `-16758529` |
| cyan | `-16722689` |
| purple | `-5635841` |
| gold | `-22016` |
| orange | `-39424` |
| green | `-11163051` |
| teal | `-16733526` |
| pink | `-39254` |
| dark red | `-3394799` |
| yellow | `-8960` |
| blood | `-6750157` |

### Common technique IDs

From [`TechniqueRegistry.java`](../src/main/java/net/mcreator/jujutsucraft/addon/TechniqueRegistry.java:86):

| Technique | Select ID |
|---|---:|
| common attack 1 | `0.0` |
| common attack 2 | `1.0` |
| common attack 3 | `2.0` |
| cancel domain | `21.0` |

## Miscellaneous utility and render constants

From [`DomainAddonUtils.java`](../src/main/java/net/mcreator/jujutsucraft/addon/util/DomainAddonUtils.java:482):

| Rule | Value |
|---|---:|
| no particle send when count <= | `0` |

From [`BaseCapabilityCrashGuardEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/BaseCapabilityCrashGuardEvents.java:26):

| Rule | Value |
|---|---:|
| crash-guard heal listener priority | `EventPriority.HIGHEST` |

## Most important balance mismatches to remember

| Mismatch | Values |
|---|---|
| near-death cooldown reduction | `120` in [`CooldownTrackerEvents`](../src/main/java/net/mcreator/jujutsucraft/addon/CooldownTrackerEvents.java) vs `600` in [`RangeAttackProcedureMixin`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/RangeAttackProcedureMixin.java) |
| near-death gameplay cooldown vs UI max | `18000` in [`RCTLevel3Handler`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java) vs `6000` in [`ModNetworking.sendNearDeathCdSync(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:567) |
| open-domain radius source | base radius from map vars vs addon runtime `jjkbrp_base_domain_radius * jjkbrp_radius_multiplier` |

## Cross references

- detailed domain runtime and forms: [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md)
- clash-specific formulas and mixins: [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md)
- combat and Black Flash context: [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
- Gojo-specific values: [`BLUE_RED_PURPLE_SYSTEM.md`](BLUE_RED_PURPLE_SYSTEM.md)
- limb penalties and regen thresholds: [`LIMB_SYSTEM.md`](LIMB_SYSTEM.md)
- near-death timers and cooldowns: [`NEAR_DEATH_SYSTEM.md`](NEAR_DEATH_SYSTEM.md)
- wheel and UI geometry: [`SKILL_WHEEL.md`](SKILL_WHEEL.md)
- packet-visible max/cooldown values: [`NETWORKING.md`](NETWORKING.md)
- helper defaults and utility floors: [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- end-to-end subsystem coupling: [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)
