# JoQu's JJC Addon

## Overview

This documentation set covers both the original **JujutsuCraft** base mod and the Forge addon with mod ID `jjkblueredpurple`.

The addon extends the original systems rather than replacing the whole mod. In practice, it layers new mechanics over the base procedure-driven architecture from [`JujutsucraftMod`](../jjc_decompile/net/mcreator/jujutsucraft/JujutsucraftMod.java), especially around domains, combat, technique selection, networking, limb loss, regeneration, and UI.

## Relationship to the Base Mod

- **Base mod**: owns the original registries, capabilities, menus, procedures, dimension setup, domain barrier construction, and vanilla JujutsuCraft combat flow.
- **Addon**: injects into those systems through mixins listed in [`mixins.jjkblueredpurple.json`](../src/main/resources/mixins.jjkblueredpurple.json), adds its own Forge networking channel in [`ModNetworking`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java), and introduces original systems such as Domain Mastery, the skill wheel, Black Flash tracking, limb severing, near-death survival, and Blue/Red/Purple expansion logic.

## Documentation Index

### Core architecture

- [`ORIGINAL_MOD_ARCHITECTURE.md`](ORIGINAL_MOD_ARCHITECTURE.md) — entrypoints, registries, base capabilities, menus, animation utilities, and dimension notes.
- [`NETWORKING.md`](NETWORKING.md) — packet-by-packet reference for both the base mod and addon.
- [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md) — utility helper reference.

### Domain system

- [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md) — end-to-end domain flow, mastery model, forms, costs, radius rules, GUI, and packets.
- [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md) — all domain-related mixins and their injection points.

### Combat and techniques

- [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md) — range attack flow, Black Flash logic, combat modifiers, and HUD behavior.
- [`BLUE_RED_PURPLE_SYSTEM.md`](BLUE_RED_PURPLE_SYSTEM.md) — addon-specific Blue, Red, Purple Nuke, and Infinity Crusher behavior.
- [`SKILL_WHEEL.md`](SKILL_WHEEL.md) — radial technique selection UI and its network flow.

### Survival and body-state systems

- [`LIMB_SYSTEM.md`](LIMB_SYSTEM.md) — limb capability, severing, regen, rendering, entity sync, particles, and gameplay penalties.
- [`NEAR_DEATH_SYSTEM.md`](NEAR_DEATH_SYSTEM.md) — RCT Level 3 unlock path, near-death window, packets, and overlays.

### Reference maps

- [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md) — grouped list of important hard-coded values.
- [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md) — how technique, domain, combat, limb, BF, near-death, and sync systems connect.

## Source Roots Covered

### Base mod decompile reference

- [`mod_work/jjc_decompile/net/mcreator/jujutsucraft/`](../jjc_decompile/net/mcreator/jujutsucraft/)

### Addon implementation

- [`src/main/java/net/mcreator/jujutsucraft/addon/`](../src/main/java/net/mcreator/jujutsucraft/addon/)
- [`src/main/java/net/mcreator/jujutsucraft/addon/limb/`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/)
- [`src/main/java/net/mcreator/jujutsucraft/addon/mixin/`](../src/main/java/net/mcreator/jujutsucraft/addon/mixin/)
- [`src/main/java/net/mcreator/jujutsucraft/addon/util/`](../src/main/java/net/mcreator/jujutsucraft/addon/util/)

## Recommended Reading Order

1. [`ORIGINAL_MOD_ARCHITECTURE.md`](ORIGINAL_MOD_ARCHITECTURE.md)
2. [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md)
3. [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md)
4. [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
5. [`BLUE_RED_PURPLE_SYSTEM.md`](BLUE_RED_PURPLE_SYSTEM.md)
6. [`LIMB_SYSTEM.md`](LIMB_SYSTEM.md)
7. [`NEAR_DEATH_SYSTEM.md`](NEAR_DEATH_SYSTEM.md)
8. [`SKILL_WHEEL.md`](SKILL_WHEEL.md)
9. [`NETWORKING.md`](NETWORKING.md)
10. [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
11. [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
12. [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)

## Quick Addon Feature Summary

- Domain Mastery capability, forms, property tree, negative modifiers, GUI, and admin commands.
- Domain mixin stack that patches startup, barrier creation, active tick logic, clash handling, cleanup timing, radius scaling, cost display, and expiry cleanup.
- Black Flash tracking with dynamic percentage calculation and HUD rendering.
- Skill wheel technique UI with multi-page Geto cursed-spirit support.
- Blue/Red/Purple upgrades plus Infinity Crusher.
- Limb severing, RCT limb reversal, regrowth rendering, and detached severed-limb entities.
- Near-death survival and RCT Level 3 progression.

## Notes

- The base mod remains heavily procedure-centric.
- The addon primarily modifies behavior through mixins, capability sync, and extra runtime NBT tags.
- Cross-references inside each document point to related files where the same data is consumed or transformed.
