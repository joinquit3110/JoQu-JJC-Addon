# Networking

This document describes the packet architecture used by both the original JujutsuCraft base mod and the addon `jjkblueredpurple`, with emphasis on:

- channel bootstrap,
- packet registration order,
- packet field layouts,
- client cache update flow,
- screen-opening packets,
- limb / near-death / domain-mastery sync,
- relationship between the addon channel and the base mod channels.

## Network architecture overview

There are effectively **three** important channels in the combined system:

| Channel | Source | Purpose |
|---|---|---|
| base main channel | [`JujutsucraftMod.PACKET_HANDLER`](../../jjc_decompile/net/mcreator/jujutsucraft/JujutsucraftMod.java) | base mod packet registration |
| base auxiliary channel | [`PacketHandler.INSTANCE`](../../jjc_decompile/net/mcreator/jujutsucraft/PacketHandler.java:21) | extra base packet registration such as velocity sync |
| addon main channel | [`ModNetworking.CHANNEL`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:76) | addon UI, cooldown, limb, near-death, and domain-mastery packets |

The addon does **not** replace the base channels. It adds its own dedicated `SimpleChannel` and uses it for new gameplay systems.

## Base mod networking

### Base auxiliary channel

[`PacketHandler.java`](../../jjc_decompile/net/mcreator/jujutsucraft/PacketHandler.java:18) defines a standalone base channel:

```java
INSTANCE = NetworkRegistry.newSimpleChannel(
    new ResourceLocation("jujutsucraft", "main"),
    () -> "1",
    "1"::equals,
    "1"::equals
);
```

Important fields:

| Field | Value |
|---|---|
| channel name | `jujutsucraft:main` |
| protocol version | `"1"` |

Registration happens in [`registerMessages()`](../../jjc_decompile/net/mcreator/jujutsucraft/PacketHandler.java:23), which currently registers [`PlayerVelocityPacket`](../../jjc_decompile/net/mcreator/jujutsucraft/PlayerVelocityPacket.java:14).

### Base velocity packet

[`PlayerVelocityPacket`](../../jjc_decompile/net/mcreator/jujutsucraft/PlayerVelocityPacket.java:14) is a simple example of the original packet pattern.

#### Constructors and methods

- [`PlayerVelocityPacket(double vx, double vy, double vz)`](../../jjc_decompile/net/mcreator/jujutsucraft/PlayerVelocityPacket.java:20)
- [`PlayerVelocityPacket(FriendlyByteBuf buf)`](../../jjc_decompile/net/mcreator/jujutsucraft/PlayerVelocityPacket.java:26)
- [`encode(...)`](../../jjc_decompile/net/mcreator/jujutsucraft/PlayerVelocityPacket.java:32)
- [`handle(...)`](../../jjc_decompile/net/mcreator/jujutsucraft/PlayerVelocityPacket.java:42)

#### Fields

| Field | Type |
|---|---|
| `vx` | `double` |
| `vy` | `double` |
| `vz` | `double` |

This base pattern informs how the addon structures its own packet classes.

## Addon main channel

### Channel definition

[`ModNetworking.CHANNEL`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:76) is defined as:

```java
NetworkRegistry.newSimpleChannel(
    new ResourceLocation("jjkblueredpurple", "main"),
    () -> "1",
    "1"::equals,
    "1"::equals
)
```

Important values:

| Field | Value |
|---|---|
| channel name | `jjkblueredpurple:main` |
| protocol version | `"1"` |
| packet ID counter | `packetId` starting from `0` |

### Registration order

[`ModNetworking.register()`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:79) registers packets in this exact order:

| Packet ID order | Packet class |
|---:|---|
| `0` | [`SelectTechniquePacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:580) |
| `1` | [`RequestWheelPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:616) |
| `2` | [`OpenWheelPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:651) |
| `3` | [`CooldownSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:702) |
| `4` | [`BlackFlashSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:733) |
| `5` | [`SelectSpiritPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:761) |
| `6` | [`LimbSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:39) |
| `7` | [`NearDeathPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/NearDeathPacket.java:19) |
| `8` | [`NearDeathCdSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:801) |
| `9` | [`DomainPropertyPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:819) |
| `10` | [`DomainMasteryOpenPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:980) |
| `11` | [`DomainMasteryOpenScreenPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1007) |
| `12` | [`DomainMasterySyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1021) |

## Shared addon networking patterns

Most addon packets follow the same layout:

1. constructor storing fields,
2. static [`encode(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:587),
3. static `decode(...)`,
4. static `handle(...)` using `ctx.enqueueWork(...)`,
5. `ctx.setPacketHandled(true)`.

Client-only actions typically use [`DistExecutor.unsafeRunWhenOn(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:697) so packets can safely exist on both logical sides.

## Technique wheel packets

### [`SelectTechniquePacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:580)

This packet sends a direct technique choice from client to server.

#### Fields

| Field | Type |
|---|---|
| `selectId` | `double` |

#### Wire format

```java
buf.writeDouble(pkt.selectId)
```

#### Server behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:595) performs these guards:

- sender exists,
- `noChangeTechnique` is false,
- technique is not locked for the current character.

If valid, it calls [`applyOriginalTechniqueSelection(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:99).

### [`RequestWheelPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:616)

This is a zero-payload request packet.

#### Wire format

```java
// no fields
```

#### Server behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:624) determines the active character, then:

- if `charId == 18`, builds Geto multi-page spirit data,
- otherwise builds a single page of technique entries,
- returns the result through [`OpenWheelPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:651).

### [`OpenWheelPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:651)

This is the main server-to-client packet for the wheel screen.

#### Fields

| Field | Type |
|---|---|
| `pages` | `List<List<WheelTechniqueEntry>>` |
| `currentSelect` | `double` |

#### Per-entry wire format

Each [`WheelTechniqueEntry`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1095) serializes these fields:

| Order | Field | Type |
|---:|---|---|
| `1` | `selectId` | `double` |
| `2` | `displayName` | `String` up to `256` chars |
| `3` | `finalCost` | `double` |
| `4` | `baseCost` | `double` |
| `5` | `color` | `int` |
| `6` | `passive` | `boolean` |
| `7` | `physical` | `boolean` |
| `8` | `domainForm` | `int` |
| `9` | `domainMultiplier` | `double` |
| `10` | `cooldownRemainingTicks` | `int` |
| `11` | `cooldownMaxTicks` | `int` |

#### Full packet structure

```java
int pageCount;
for each page {
    int entryCount;
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

#### Client behavior

[`OpenWheelPacket.handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:695) opens the UI through [`ClientPacketHandler.openSkillWheel(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:22).

### [`SelectSpiritPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:761)

This is the Geto-specific spirit selection packet.

#### Fields

| Field | Type |
|---|---|
| `spiritSlot` | `int` |

#### Wire format

```java
buf.writeInt(pkt.spiritSlot)
```

#### Server behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:776) checks whether the persistent slot exists in:

```text
data_cursed_spirit_manipulation<slot>
```

If valid, it updates base player variables:

- `PlayerSelectCurseTechnique = 12.0`
- `PlayerSelectCurseTechniqueName = <spirit name × count>`

## Cooldown and combat sync packets

### [`CooldownSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:702)

This packet pushes cooldown bars to the client.

#### Fields

| Field | Type |
|---|---|
| `techRemaining` | `int` |
| `techMax` | `int` |
| `combatRemaining` | `int` |
| `combatMax` | `int` |

#### Wire format

```java
int techRemaining;
int techMax;
int combatRemaining;
int combatMax;
```

#### Client behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:726) forwards into [`ClientPacketHandler.updateCooldowns(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:26), which updates [`ClientCooldownCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:55).

### [`BlackFlashSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:733)

This packet synchronizes Black Flash HUD state.

#### Fields

| Field | Type |
|---|---|
| `bfPercent` | `float` |
| `mastery` | `boolean` |
| `charging` | `boolean` |

#### Wire format

```java
float bfPercent;
boolean mastery;
boolean charging;
```

#### Client behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:754) calls [`ClientPacketHandler.updateBlackFlash(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:31), which writes into [`ClientBlackFlashCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:92).

## Limb and near-death packets

### [`LimbSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:39)

This packet is defined in the limb package but registered on the addon channel.

#### Fields

| Field | Type |
|---|---|
| `entityId` | `int` |
| `states` | `Map<LimbType, LimbState>` |
| `regenProgress` | `Map<LimbType, Float>` |

#### Per-limb wire format

For every [`LimbType`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbType.java:6), the packet writes:

| Field | Type |
|---|---|
| state ordinal | `byte` |
| regen progress | `float` |

#### Client behavior

[`LimbSyncPacket.handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/LimbSyncPacket.java:75) updates [`ClientLimbCache`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/ClientLimbCache.java:12).

### [`NearDeathPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/NearDeathPacket.java:19)

This packet carries live near-death state.

#### Fields

| Field | Type |
|---|---|
| `active` | `boolean` |
| `ticksRemaining` | `int` |

#### Wire format

```java
boolean active;
int ticksRemaining;
```

#### Client behavior

[`NearDeathPacket.handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/NearDeathPacket.java:37) updates [`NearDeathClientState.update(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/NearDeathClientState.java:18).

### [`NearDeathCdSyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:801)

This packet pushes the long cooldown UI state for near-death.

#### Fields

| Field | Type |
|---|---|
| `cdRemaining` | `int` |
| `cdMax` | `int` |
| `rctLevel3Unlocked` | `boolean` |

#### Wire format

```java
int cdRemaining;
int cdMax;
boolean rctLevel3Unlocked;
```

#### Actual sync values

[`sendNearDeathCdSync(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:567) sends:

- `cd = data.getInt("jjkbrp_near_death_cd")`
- `cdMax = 6000`
- `rctL3 = hasAdvancement("jjkblueredpurple:rct_level_3")`

Important note: this UI `cdMax` is `6000`, while the gameplay cooldown constant in [`RCTLevel3Handler`](../src/main/java/net/mcreator/jujutsucraft/addon/limb/RCTLevel3Handler.java:55) is `18000`. That difference should be documented exactly as implemented.

#### Client behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:812) calls [`ClientPacketHandler.updateNearDeathCooldown(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:37), which writes into [`ClientNearDeathCdCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:101).

## Domain mastery packets

### [`DomainPropertyPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:819)

This is the main mutation packet for the domain mastery screen.

#### Fields

| Field | Type |
|---|---|
| `operation` | `int` encoded as `byte` |
| `propertyIndex` | `int` encoded as `byte` |

#### Operation constants

| Constant | Value |
|---|---:|
| `OP_UPGRADE` | `0` |
| `OP_REFUND` | `1` |
| `OP_RESET_ALL` | `2` |
| `OP_SET_FORM` | `3` |
| `OP_CYCLE_FORM` | `4` |
| `OP_NEGATIVE_DECREASE` | `5` |
| `OP_NEGATIVE_INCREASE` | `6` |

#### Behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:844) validates sender, rejects locked mutations, then applies one of the following:

- upgrade property,
- refund property,
- reset all,
- set form,
- cycle form,
- deepen negative modify,
- reduce negative modify.

After mutation, it always calls:

```java
data.syncToClient(sender)
```

### [`DomainMasteryOpenPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:980)

This zero-payload client-to-server packet requests the domain mastery UI.

#### Behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:988) checks:

- sender exists,
- player has the Domain Expansion advancement.

If valid, it:

1. syncs mastery data to client,
2. sends [`DomainMasteryOpenScreenPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1007).

### [`DomainMasteryOpenScreenPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1007)

This zero-payload packet opens the client UI.

#### Client behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1015) calls [`ClientPacketHandler.openDomainMasteryScreen()`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:43).

### [`DomainMasterySyncPacket`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1021)

This packet synchronizes the full mastery state.

#### Fields

| Field | Type |
|---|---|
| `xp` | `double` |
| `level` | `int` encoded as `byte` |
| `form` | `int` encoded as `byte` |
| `points` | `int` encoded as varint |
| `negativeProperty` | `String` |
| `negativeLevel` | `int` |
| `hasOpenBarrierAdvancement` | `boolean` |
| `propLevels` | `int[]` encoded as byte-count + bytes |

#### Wire format

```java
double xp;
byte level;
byte form;
varInt points;
String negativeProperty;
int negativeLevel;
boolean hasOpenBarrierAdvancement;
byte propCount;
for each levelValue {
    byte levelValue;
}
```

#### Client behavior

[`handle(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:1086) forwards to [`ClientPacketHandler.syncDomainMastery(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:47).

## Server push helpers

Several helper methods send packets without the caller needing to construct packet objects directly.

| Helper | Purpose |
|---|---|
| [`sendCooldownSync(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:139) | cooldown bars |
| [`sendBlackFlashSync(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:559) | Black Flash HUD |
| [`sendNearDeathCdSync(ServerPlayer)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:567) | near-death cooldown HUD |
| [`syncDomainMasteryToClient(ServerPlayer, DomainMasteryData)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:574) | full domain mastery sync |

## Client packet handling layer

[`ClientPacketHandler`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:18) is the addon’s central landing zone for packet results.

### Screen open methods

| Method | Effect |
|---|---|
| [`openSkillWheel(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:22) | opens [`SkillWheelScreen`](../src/main/java/net/mcreator/jujutsucraft/addon/SkillWheelScreen.java:58) |
| [`openDomainMasteryScreen()`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:43) | opens [`DomainMasteryScreen`](../src/main/java/net/mcreator/jujutsucraft/addon/DomainMasteryScreen.java) |

### Cache update methods

| Method | Cache touched |
|---|---|
| [`updateCooldowns(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:26) | [`ClientCooldownCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:55) |
| [`updateBlackFlash(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:31) | [`ClientBlackFlashCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:92) |
| [`updateNearDeathCooldown(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:37) | [`ClientNearDeathCdCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:101) |
| [`syncDomainMastery(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:47) | domain mastery capability copy on client player |

## Cooldown cache semantics

[`ClientCooldownCache`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:55) stores:

| Field | Meaning |
|---|---|
| `techRemaining` | current technique cooldown |
| `techMax` | technique max for progress display |
| `combatRemaining` | current combat cooldown |
| `combatMax` | combat max for progress display |

It exposes:

- [`updateTechnique(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:64)
- [`updateCombat(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:69)
- [`getRemaining(boolean physical)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:74)
- [`getMax(boolean physical)`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:78)
- [`tickDecay()`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:82)

## Design contrasts: base vs addon networking

| Aspect | Base mod | Addon |
|---|---|---|
| channel count | multiple original channels | one dedicated addon channel layered on top |
| packet focus | generic mod sync, velocity, variables | UI, cooldowns, radial selector, limb state, near-death, mastery |
| packet style | classic `SimpleChannel` classes | same `SimpleChannel` style, but with more client-cache and screen-open helpers |
| client state model | more direct gameplay sync | multiple explicit caches and UI-only packets |

## Practical debugging notes

When debugging packet issues, inspect these in order:

1. channel registration in [`ModNetworking.register()`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:79)
2. sender-side helper such as [`sendCooldownSync(...)`](../src/main/java/net/mcreator/jujutsucraft/addon/ModNetworking.java:139)
3. packet `encode(...)` / `decode(...)`
4. packet `handle(...)`
5. final cache or screen method in [`ClientPacketHandler`](../src/main/java/net/mcreator/jujutsucraft/addon/ClientPacketHandler.java:18)

For base mod debugging, compare against [`PacketHandler.registerMessages()`](../../jjc_decompile/net/mcreator/jujutsucraft/PacketHandler.java:23) and [`PlayerVelocityPacket.handle(...)`](../../jjc_decompile/net/mcreator/jujutsucraft/PlayerVelocityPacket.java:42).

## Cross references

- wheel UI packet consumer: [`SKILL_WHEEL.md`](SKILL_WHEEL.md)
- combat sync and Black Flash HUD: [`COMBAT_SYSTEM.md`](COMBAT_SYSTEM.md)
- limb and near-death packet payloads: [`LIMB_SYSTEM.md`](LIMB_SYSTEM.md), [`NEAR_DEATH_SYSTEM.md`](NEAR_DEATH_SYSTEM.md)
- domain-mastery sync usage: [`DOMAIN_SYSTEM.md`](DOMAIN_SYSTEM.md) and [`DOMAIN_MIXIN_LAYERS.md`](DOMAIN_MIXIN_LAYERS.md)
- helper/cache classes: [`UTILITY_CLASSES.md`](UTILITY_CLASSES.md)
- global numbers and thresholds: [`CONSTANTS_AND_THRESHOLDS.md`](CONSTANTS_AND_THRESHOLDS.md)
- end-to-end subsystem coupling: [`CROSS_SYSTEM_INTERACTIONS.md`](CROSS_SYSTEM_INTERACTIONS.md)
