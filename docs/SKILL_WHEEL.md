# Skill Wheel

This document describes the radial technique-selection UI implemented by the addon through [`SkillWheelScreen`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:58), input hooks in [`ClientEvents`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java:63), and server-side page construction in [`ModNetworking`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:72).

The wheel is server-authored and client-rendered:

1. the client sends a request,
2. the server builds pages with technique metadata and cooldowns,
3. the server sends page data back,
4. the client renders a radial selector,
5. the client sends the chosen technique or spirit back to the server.

## Source map

| Layer | Primary files |
|---|---|
| keybinds and open conditions | [`ClientEvents.java`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java) |
| page construction | [`ModNetworking.buildWheelEntries(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:409), [`ModNetworking.buildGetoPages(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:501) |
| transport packets | [`RequestWheelPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:616), [`OpenWheelPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:651), [`SelectTechniquePacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:580), [`SelectSpiritPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:761) |
| client screen | [`SkillWheelScreen.java`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java) |
| base technique catalog | [`TechniqueRegistry.java`](../src/main/java/net/mcreator/jujutsucraft/addon/TechniqueRegistry.java:17) |

## Input and opening flow

### Keybinds

[`ClientEvents.ModBusEvents.registerKeys(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java:175) registers two important keys:

| Key mapping | Key code | Purpose |
|---|---:|---|
| `key.jjkblueredpurple.skill_wheel` | `258` | open the skill wheel |
| `key.jjkblueredpurple.domain_mastery` | `44` | open the domain mastery screen |

This document focuses on the first one.

### Open guards

[`ClientEvents.openWheel(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java:111) refuses to open the wheel when:

- no local player exists,
- the current screen is already [`SkillWheelScreen`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:58),
- the current screen is [`DomainMasteryScreen`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java:119),
- the base capability field `noChangeTechnique` is true.

If all checks pass, the client sends [`new ModNetworking.RequestWheelPacket()`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java:126).

### Key transition behavior

[`ClientEvents.onKeyInput(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java:94) uses edge-trigger logic:

- current state from [`isWheelKeyDown()`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java:139)
- previous state from `wasKeyDown`

The wheel therefore opens on key press, not every tick while held.

[`ClientEvents.isWheelKey(int, int)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientEvents.java:152) is later reused by [`SkillWheelScreen.keyReleased(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:615) to confirm a hovered choice when the same hotkey is released.

## Server-side page construction

### Core builder

[`ModNetworking.buildWheelEntries(ServerPlayer, int)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:409) creates the default technique page.

The builder:

1. snapshots the player’s current selection,
2. iterates candidate select IDs from `0` through `21`,
3. temporarily applies each original selection,
4. reads the resolved name/cost/type flags from base capability data,
5. calculates cooldown info,
6. adds domain-form metadata for domain entries,
7. restores the original selection snapshot,
8. sorts the final entries by `selectId`.

The scan loop is explicit:

```java
for (int i = 0; i <= 21; ++i)
```

### Duplicates and invalid entries

Entries are skipped when any of these apply:

- resolved select ID is invalid,
- that resolved ID has already been seen,
- display name is blank,
- display name is exactly `-----`.

This prevents the wheel from showing internal or placeholder technique states.

### Cooldown fields

For each entry, the builder resolves cooldown using a priority system:

| Case | Cooldown source |
|---|---|
| physical entry with `sid <= 2` | combat cooldown |
| known per-skill override | [`getPerSkillCooldownTicks(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:269) |
| generic physical / non-physical fallback | combat cooldown or technique cooldown |
| unstable-effect override | effect duration when appropriate |

If a cooldown is active, the builder also stores a max-cooldown tracker in persistent data:

```text
jjkbrp_cd_max_<sid>
```

That stored max becomes `cooldownMaxTicks`, allowing the client to draw a proportional cooldown slice instead of only a raw number.

### Domain technique metadata

When the selected entry is a domain technique, the builder resolves preview metadata through:

- [`DomainCostUtils.isDomainTechniqueSelected(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:452)
- [`DomainCostUtils.resolveEffectiveForm(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:453)
- [`DomainCostUtils.formMultiplier(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:454)
- [`DomainCostUtils.resolveTechniqueBaseCost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:455)
- [`DomainCostUtils.resolveExpectedDomainCastCost(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:456)

This is why the wheel can show both base cost and form-adjusted cost, plus form labels like incomplete / closed / open.

## Geto special multi-page path

[`RequestWheelPacket.handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:624) switches to a special paging mode when the active character ID is `18`.

In that case, the server calls [`buildGetoPages(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:501) instead of building a single technique page.

### Geto page rules

The Geto path constructs:

1. a normal technique page,
2. one or more lower-grade spirit pages,
3. one or more upper-grade spirit pages.

The normal page removes select IDs `11` through `13` before sending, because those slots are replaced by spirit-selection behavior.

### Spirit source data

The server scans persistent data using keys shaped like:

```text
data_cursed_spirit_manipulation<n>
data_cursed_spirit_manipulation<n>_name
data_cursed_spirit_manipulation<n>_num
```

for `n = 1..9999`, stopping when the base numeric slot becomes `0.0`.

### Spirit select IDs

Spirit entries use a numeric offset of `100`, matching [`SPIRIT_SELECT_OFFSET`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:64).

Construction rule:

```java
selectId = 100 + spiritSlot
```

The client identifies spirit entries using [`isSpiritEntry(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:105), which checks `selectId >= 100.0`.

### Spirit grade detection and color

[`detectSpiritGrade(String)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:469) assigns grades by name matching:

| Match rule | Grade |
|---|---:|---|
| contains `special grade` or `grade 0` | `0` |
| contains `semi grade 1` or `semi-grade 1` | `1` |
| contains `grade 1` | `1` |
| contains `grade 2` | `2` |
| contains `grade 3` | `3` |
| contains `grade 4` | `4` |
| fallback | `0` |

[`getSpiritGradeColor(int)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:492) maps grades to colors:

| Grade | Color int |
|---|---:|
| `0` | `13127872` |
| `1` | `13934615` |
| `2` | `4886745` |
| default (`3+`) | `7048811` |

### Page size

Each spirit page is capped at exactly `12` entries before a new page is created.

## Packet contracts

### [`SelectTechniquePacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:580)

Payload:

```java
double selectId
```

Server-side guards in [`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:595):

- sender exists,
- `noChangeTechnique` is false,
- technique is not locked for the current character.

If valid, the server calls [`applyOriginalTechniqueSelection(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:99).

### [`RequestWheelPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:616)

This packet carries no payload. It only requests a fresh server-built page list.

### [`OpenWheelPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:651)

This packet carries the full wheel UI model.

Its encoded structure is:

```java
int numPages;
for each page {
    int size;
    for each entry {
        double selectId;
        String displayName;
        double finalCost;
        double baseCost;
        int color;
        boolean passive;
        boolean physical;
        int domainForm;
        double domainMultiplier;
        int cooldownRemainingTicks;
        int cooldownMaxTicks;
    }
}
double currentSelect;
```

On the client, [`OpenWheelPacket.handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:695) opens the wheel through client packet handling.

### [`SelectSpiritPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:761)

Payload:

```java
int spiritSlot
```

If the slot exists, the server sets:

- `PlayerSelectCurseTechnique = 12.0`
- `PlayerSelectCurseTechniqueName = <spirit display string>`

## Entry data model

The UI entry record is [`WheelTechniqueEntry`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1095):

```java
public record WheelTechniqueEntry(
    double selectId,
    String displayName,
    double finalCost,
    double baseCost,
    int color,
    boolean passive,
    boolean physical,
    int domainForm,
    double domainMultiplier,
    int cooldownRemainingTicks,
    int cooldownMaxTicks
)
```

Meaning of each field:

| Field | Meaning |
|---|---|
| `selectId` | server selection ID |
| `displayName` | rendered label |
| `finalCost` | displayed CE cost after adjustments |
| `baseCost` | unmodified CE cost |
| `color` | main slice color |
| `passive` | passive indicator |
| `physical` | physical indicator |
| `domainForm` | `-1` when not a domain entry |
| `domainMultiplier` | form multiplier preview |
| `cooldownRemainingTicks` | cooldown remaining at open time |
| `cooldownMaxTicks` | maximum cooldown used for slice overlay |

## UI geometry and constants

[`SkillWheelScreen.java`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:60) defines the radial layout.

| Constant | Value | Purpose |
|---|---:|---|
| `WHEEL_RADIUS` | `90.0f` | outside ring radius |
| `INNER_RADIUS` | `35.0f` | empty center dead zone |
| `ICON_RADIUS` | `65.0f` | label orbit radius |
| `CENTER_DOT_RADIUS` | `6.0f` | center marker radius |
| `SPIRIT_SELECT_OFFSET` | `100` | spirit-entry ID offset |

Other important display numbers:

| Value | Meaning |
|---|---|
| `250.0f` | open animation divisor in [`render(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:150) |
| `24` | arc segment count in slice drawing and confirm ring particle count |
| `48` | circle outline segment count |
| `12` | page-indicator dot spacing |

## Screen state fields

The screen maintains these main fields:

| Field | Role |
|---|---|
| `pages` | all server-provided pages |
| `currentSelectId` | technique active when the wheel opened |
| `currentPage` | visible page index |
| `hoveredIndex` | currently hovered slice |
| `selectedIndex` | matching slice for current active technique |
| `openTime` | real-time start stamp |
| `openTick` | world tick when opened |
| `animProgress` | `0..1` animation progress |
| `pulseTime` | hover pulse accumulator |
| `selectionConfirmed` | prevents double selection |
| `closing` | suppresses repeated close logic |
| `confirmFlash` | temporary full-screen flash strength |

## Open animation and easing

[`SkillWheelScreen.render(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:149) computes:

```java
elapsed = System.currentTimeMillis() - openTime;
animProgress = min(elapsed / 250.0f, 1.0f);
ease = easeOutBack(animProgress);
```

The easing helper is [`easeOutBack(float)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:566):

```java
float c1 = 1.70158f;
float c3 = c1 + 1.0f;
return 1.0f + c3 * (t - 1)^3 + c1 * (t - 1)^2;
```

## Hover and selection logic

Hover is resolved from mouse angle only when the cursor is outside the center dead zone:

```java
dist > 35.0f * ease
```

Slice resolution uses:

```java
hoveredIndex = angle / (360.0 / techniques.size())
```

If the hovered entry is cooldown-blocked, the screen clears the hover back to `-1`.

The wheel supports two confirm flows:

| Action | Method |
|---|---|
| release wheel hotkey | [`keyReleased(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:615) |
| left mouse button release | [`mouseReleased(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:624) |

Both end up in [`confirmSelection()`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:572).

## Drawing behavior

### Main wheel

[`drawWheel(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:192) renders:

- slice fills,
- cooldown blackout sweep,
- colored cooldown edge line,
- hover/selected border outlines,
- radial separators,
- inner/outer ring outlines,
- center dot.

Important visual formulas:

```java
hoverPulse = sin(pulseTime * 3.0f) * 2.0 + 12.0
```

Cooldown sweep size is based on:

```java
cooldownRatio = clamp(skillCooldown / skillCooldownMax, 0.0f, 1.0f)
```

### Labels

[`drawLabels(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:271) truncates names longer than `14` characters into `12` characters plus `..`.

Hovered entry details:

| Entry type | Hover text |
|---|---|
| spirit | `✦ SUMMON` |
| normal | `CE: <finalCost>` and a type label |

Type labels are exactly:

- `PASSIVE+PHYSICAL`
- `PASSIVE`
- `PHYSICAL`
- `TECHNIQUE`

Cooldown timer display format:

| Remaining seconds | Rendered format |
|---|---|
| `>= 10.0f` | whole seconds, e.g. `12s` |
| `< 10.0f` | one decimal place, e.g. `1.4s` |

### Center info panel

[`drawCenterInfo(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:330) shows:

- a title,
- hovered full name,
- either spirit summon help or CE / domain / type details.

Page titles are hardcoded as:

| Page index | Title |
|---|---|
| `0` | `Techniques` |
| `1` | `Cursed Spirits (Lower)` |
| `2` | `Cursed Spirits (Upper)` |

If a domain entry is hovered, the form label is generated from `domainForm`:

| `domainForm` | Label |
|---|---|
| `2` | `Open` |
| `1` | `Closed` |
| default | `Incomplete` |

The final format is:

```text
Domain Form: <label> • Multiplier x<value>
```

### Page indicator

[`drawPageIndicator(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:378) draws page dots and the hint:

```text
◀ Scroll ▶
```

Scrolling changes page through [`mouseScrolled(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:130).

## Cooldown behavior on the client

The screen computes live cooldown remaining using [`getEntryCooldownRemaining(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:398):

```java
elapsed = currentGameTime - openTick;
remaining = max(0, entry.cooldownRemainingTicks() - elapsed)
```

That means the client does not need constant cooldown resync while the wheel is open. It can locally decay the cooldown from the server snapshot.

Spirit entries always return `0` cooldown from both [`getEntryCooldownRemaining(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:398) and [`getEntryCooldownMax(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:407).

## Confirmation behavior

[`confirmSelection()`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:572) follows this order:

1. ignore if already confirmed,
2. close immediately if no valid hovered slice exists,
3. close immediately if the hovered entry is cooldown-blocked,
4. trigger flash, particles, and sound,
5. send either technique or spirit packet,
6. mirror the chosen state into local base capability fields,
7. close the screen.

### Technique confirm path

For normal techniques, the client sends [`new ModNetworking.SelectTechniquePacket(tech.selectId())`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:601) and mirrors:

- `PlayerSelectCurseTechnique`
- `PlayerSelectCurseTechniqueName`
- `PlayerSelectCurseTechniqueCost`
- `PlayerSelectCurseTechniqueCostOrgin`

### Spirit confirm path

For spirits, the client computes:

```java
spiritSlot = round(selectId) - 100
```

then sends [`SelectSpiritPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:592), while mirroring local selection name and setting `PlayerSelectCurseTechnique = 12.0`.

## Confirm particles and sounds

[`spawnConfirmParticles(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:517) creates three particle groups around the player:

| Group | Count | Particle |
|---|---:|---|
| colored dust ring | `24` | `DustParticleOptions` using entry color |
| enchant burst | `8` | `ENCHANT` |
| end-rod sparkle | `6` | `END_ROD` |

[`playConfirmSound(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:552) layers sounds based on entry type:

| Entry type | Primary sound |
|---|---|
| spirit | `WARDEN_SONIC_BOOM` |
| physical | `CHAIN_PLACE` |
| passive | `BEACON_ACTIVATE` |
| normal technique | `ENCHANTMENT_TABLE_USE` |

All paths also add a secondary `AMETHYST_BLOCK_CHIME`.

## Technique registry relationship

[`TechniqueRegistry`](../src/main/java/net/mcreator/jujutsucraft/addon/TechniqueRegistry.java:17) is not the direct runtime source for wheel pages, but it documents the addon’s intended character-to-technique layout.

Notable examples:

| Character ID | Highlighted entries |
|---|---|
| `2` | Gojo: Infinity, Blue, Red, Purple, Unlimited Void |
| `18` | Geto special path is handled by multi-page spirit logic |
| `21` | Yuji / Sukuna-related entries |

The static common techniques are select IDs `0.0`, `1.0`, and `2.0`, while the cancel-domain entry is fixed at `21.0` in [`TechniqueRegistry.java`](../src/main/java/net/mcreator/jujutsucraft/addon/TechniqueRegistry.java:86).

## Practical behavior summary

The wheel combines several systems into one selector:

- base technique selection,
- cooldown preview,
- domain-form cost preview,
- Geto spirit page browsing,
- immediate local audiovisual feedback,
- authoritative server validation on final selection.

That makes it both a UI element and a compact gameplay-status dashboard.

## Cross references

- packet details: [`NETWORKING.md`](NETWORKING.md)
- combat and cooldown behavior: [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
- domain form costs and preview data: [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md)
- Gojo technique examples shown in the wheel: [`BLUE_RED_PURPLE_SYSTEM.md`](BLUE_RED_PURPLE_SYSTEM.md)
- utility/cache relationships: [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- numeric inventory: [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
- system-level dependency map: [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)
