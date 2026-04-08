# Original JujutsuCraft Architecture

## Purpose

This document describes the **base mod architecture** that the addon builds on top of. The original mod is heavily **procedure-driven**, with a thin Forge mod bootstrap layer and a large amount of runtime behavior implemented in generated procedures, saved data, capabilities, and menu containers.

Primary base-mod source references used for this document:

- `mod_work/jjc_decompile/net/mcreator/jujutsucraft/JujutsucraftMod.java`
- `mod_work/jjc_decompile/net/mcreator/jujutsucraft/PacketHandler.java`
- `mod_work/jjc_decompile/net/mcreator/jujutsucraft/PlayerVelocityPacket.java`
- `mod_work/jjc_decompile/net/mcreator/jujutsucraft/network/JujutsucraftModVariables.java`
- `mod_work/jjc_decompile/net/mcreator/jujutsucraft/world/inventory/SelectProfessionMenu.java`
- `mod_work/jjc_decompile/net/mcreator/jujutsucraft/world/inventory/SelectTechniqueMenu.java`
- `mod_work/jjc_decompile/net/mcreator/jujutsucraft/world/inventory/SelectTechnique2Menu.java`
- `mod_work/jjc_decompile/net/mcreator/jujutsucraft/utils/AnimUtils.java`
- `mod_work/jjc_decompile/net/mcreator/jujutsucraft/world/dimension/CursedSpiritManipulationDimensionDimension.java`

---

## 1. Entry Point: `JujutsucraftMod`

### Class role

The base mod entrypoint is the Forge mod class:

```java
@Mod("jujutsucraft")
public class JujutsucraftMod
```

It is responsible for:

- creating the main Forge network channel,
- registering registry holders on the mod event bus,
- exposing a generic packet registration helper,
- maintaining a delayed server work queue.

### Important fields

```java
public static final Logger LOGGER;
public static final String MODID = "jujutsucraft";
private static final String PROTOCOL_VERSION = "1";
public static final SimpleChannel PACKET_HANDLER;
private static int messageID;
private static final Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue;
```

### Constructor behavior

The constructor registers the mod instance to the Forge event bus and binds all DeferredRegister-based registries to the mod bus.

### Registry bootstrap order

The base mod registers the following systems from the constructor:

| System | Class |
|---|---|
| Sound registry | `JujutsucraftModSounds` |
| Block registry | `JujutsucraftModBlocks` |
| Block entity registry | `JujutsucraftModBlockEntities` |
| Item registry | `JujutsucraftModItems` |
| Entity registry | `JujutsucraftModEntities` |
| Creative tab registry | `JujutsucraftModTabs` |
| Mob effect registry | `JujutsucraftModMobEffects` |
| Particle type registry | `JujutsucraftModParticleTypes` |
| Villager profession registry | `JujutsucraftModVillagerProfessions.PROFESSIONS` |
| Menu registry | `JujutsucraftModMenus` |
| Attribute registry | `JujutsucraftModAttributes` |

This is the registry backbone referenced by the addon whenever it hooks base content.

### Generic network registration helper

The entrypoint exposes a helper used all over the base mod:

```java
public static <T> void addNetworkMessage(
    Class<T> messageType,
    BiConsumer<T, FriendlyByteBuf> encoder,
    Function<FriendlyByteBuf, T> decoder,
    BiConsumer<T, Supplier<NetworkEvent.Context>> messageConsumer
)
```

It registers packets onto `JujutsucraftMod.PACKET_HANDLER` and auto-increments `messageID`.

### Deferred work queue

The base mod also exposes:

```java
public static void queueServerWork(int tick, Runnable action)
```

This pushes delayed tasks into `workQueue`, which are consumed during `TickEvent.ServerTickEvent` on phase `END`.

---

## 2. Base Registry Systems

The original mod uses the common MCreator/Forge pattern of one registry-holder class per content type.

### Major registry categories explicitly present

- **Sounds** — `JujutsucraftModSounds`
- **Blocks** — `JujutsucraftModBlocks`
- **Block Entities** — `JujutsucraftModBlockEntities`
- **Items** — `JujutsucraftModItems`
- **Entities** — `JujutsucraftModEntities`
- **Creative Tabs** — `JujutsucraftModTabs`
- **Effects** — `JujutsucraftModMobEffects`
- **Particle Types / Particles** — `JujutsucraftModParticleTypes`, client-side `JujutsucraftModParticles`
- **Villager Professions** — `JujutsucraftModVillagerProfessions`
- **Menus** — `JujutsucraftModMenus`
- **Attributes** — `JujutsucraftModAttributes`

These registry classes are not where most game logic lives. Instead, they expose content handles used by procedures, effects, AI logic, and packet handlers.

---

## 3. Network Channels

The base mod uses **two distinct channels**.

## 3.1 Main channel: `JujutsucraftMod.PACKET_HANDLER`

### Channel definition

```java
public static final SimpleChannel PACKET_HANDLER =
    NetworkRegistry.newSimpleChannel(
        new ResourceLocation("jujutsucraft", "jujutsucraft"),
        () -> "1",
        "1"::equals,
        "1"::equals
    );
```

### Purpose

This is the primary channel for almost every normal mod packet, including:

- GUI button packets,
- keybind packets,
- capability sync packets,
- saved-data sync packets,
- animation packets,
- other generated gameplay messages.

### Registration pattern

Packets are usually registered via:

```java
JujutsucraftMod.addNetworkMessage(...)
```

---

## 3.2 Auxiliary channel: `PacketHandler.INSTANCE`

### Channel definition

```java
public static final SimpleChannel INSTANCE =
    NetworkRegistry.newSimpleChannel(
        new ResourceLocation("jujutsucraft", "main"),
        () -> "1",
        "1"::equals,
        "1"::equals
    );
```

### Purpose

This is a small auxiliary channel dedicated to the velocity packet system.

### Registered packet

Only one packet is registered here:

```java
PlayerVelocityPacket
```

Registered by:

```java
public static void registerMessages()
```

which is called during:

```java
@SubscribeEvent
public static void onCommonSetup(FMLCommonSetupEvent event)
```

---

## 3.3 `PlayerVelocityPacket`

### Class role

`PlayerVelocityPacket` is a minimal client-directed packet used to directly force player motion.

### Fields

```java
private final double vx;
private final double vy;
private final double vz;
```

### Constructor overloads

```java
public PlayerVelocityPacket(double vx, double vy, double vz)
public PlayerVelocityPacket(FriendlyByteBuf buf)
```

### Packet methods

```java
public static void encode(PlayerVelocityPacket message, FriendlyByteBuf buf)
public static PlayerVelocityPacket decode(FriendlyByteBuf buf)
public static void handle(PlayerVelocityPacket message, Supplier<NetworkEvent.Context> ctx)
```

### Handler logic

On the client, it fetches the local player and runs:

```java
player.setDeltaMovement(new Vec3(message.vx, message.vy, message.vz));
```

This is why the addon documentation treats `PacketHandler.INSTANCE` as the **auxiliary velocity channel**.

---

## 4. `PlayerVariables` Capability

The base mod’s central runtime player state lives in:

```java
JujutsucraftModVariables.PlayerVariables
```

This is a Forge capability attached to real players.

## 4.1 Capability registration and attachment

### Capability token

```java
public static final Capability<PlayerVariables> PLAYER_VARIABLES_CAPABILITY;
```

### Registration

`RegisterCapabilitiesEvent` registers the `PlayerVariables` class.

### Attachment rules

The provider attaches only to:

- instances of `Player`,
- excluding `FakePlayer`.

Capability ID:

```java
new ResourceLocation("jujutsucraft", "player_variables")
```

---

## 4.2 Sync lifecycle

The base mod aggressively synchronizes player variables on common player lifecycle events.

### Automatic sync events

- player login,
- player respawn,
- dimension change,
- player clone.

### Sync method

```java
public void syncPlayerVariables(Entity entity)
```

### Packet used

```java
PlayerVariablesSyncMessage
```

### Sync transport

`syncPlayerVariables()` sends the packet through `JujutsucraftMod.PACKET_HANDLER` to the entire dimension of the player, not just the owning client.

---

## 4.3 Important fields

The capability contains many fields, but the addon depends especially on the following:

```text
PlayerCursePower
PlayerCursePowerMAX
PlayerCursePowerChange
PlayerCurseTechnique
PlayerCurseTechnique2
PlayerSelectCurseTechnique
PlayerSelectCurseTechniqueCost
PlayerSelectCurseTechniqueCostOrgin
SecondTechnique
noChangeTechnique
PassiveTechnique
PhysicalAttack
```

### Meaning of the addon-critical fields

| Field | Meaning |
|---|---|
| `PlayerCursePower` | current cursed energy resource |
| `PlayerCursePowerMAX` | max cursed energy |
| `PlayerCursePowerChange` | delta accumulator used by many procedures to add/subtract CE |
| `PlayerCurseTechnique` | primary character/technique ID |
| `PlayerCurseTechnique2` | secondary technique ID |
| `PlayerSelectCurseTechnique` | currently selected skill slot / action ID |
| `PlayerSelectCurseTechniqueCost` | active displayed/final cost |
| `PlayerSelectCurseTechniqueCostOrgin` | original/base cost before runtime modifiers |
| `SecondTechnique` | whether the secondary technique set is active |
| `noChangeTechnique` | hard lock preventing switching |
| `PassiveTechnique` | whether selected action is passive |
| `PhysicalAttack` | whether selected action is treated as physical |

### Other important fields often adjacent in logic

```text
PlayerCharge
PlayerCursePowerFormer
PlayerProfession
PlayerTechniqueUsedNumber
PlayerExperience
PlayerFame
OverlayCost
OverlayCursePower
PlayerSelectCurseTechniqueName
BodyItem
use_mainSkill
```

---

## 4.4 Default values relevant to addon interoperability

The base constructor initializes several key fields to these defaults:

```text
PlayerCursePower = 10.0
PlayerCursePowerFormer = 10.0
PlayerCursePowerMAX = 10.0
PlayerCursePowerChange = 0.0
PlayerCurseTechnique = 0.0
PlayerCurseTechnique2 = 0.0
PlayerSelectCurseTechnique = 0.0
PlayerSelectCurseTechniqueCost = 0.0
PlayerSelectCurseTechniqueCostOrgin = 0.0
SecondTechnique = false
noChangeTechnique = false
PassiveTechnique = false
PhysicalAttack = false
OverlayCost = "Cost"
OverlayCursePower = "Cursed Energy"
PlayerSelectCurseTechniqueName = ""
```

---

## 4.5 Serialization: `writeNBT()` and `readNBT()`

### Method signatures

```java
public Tag writeNBT()
public void readNBT(Tag tag)
```

### Key serialized booleans relevant to addon behavior

```text
flag_shift
flag_sukuna
FlagSixEyes
noChangeTechnique
PassiveTechnique
PhysicalAttack
PlayerFlag_A
PlayerFlag_B
SecondTechnique
use_mainSkill
```

### Key serialized numeric fields relevant to addon behavior

```text
cnt_curse1
friend_num_keep
PlayerCharge
PlayerCursePower
PlayerCursePowerChange
PlayerCursePowerFormer
PlayerCursePowerMAX
PlayerCurseTechnique
PlayerCurseTechnique2
PlayerExperience
PlayerFame
PlayerLevel
PlayerProfession
PlayerSelectCurseTechnique
PlayerSelectCurseTechniqueCost
PlayerSelectCurseTechniqueCostOrgin
PlayerTechniqueUsedNumber
```

### Key serialized strings / compound payloads

```text
OVERLAY1
OVERLAY2
OverlayCost
OverlayCursePower
PlayerSelectCurseTechniqueName
BodyItem
```

The addon frequently reads and modifies these live fields rather than replacing this capability.

---

## 4.6 `PlayerVariablesSyncMessage`

### Class role

This packet mirrors the entire `PlayerVariables` payload to clients.

### Constructor overloads

```java
public PlayerVariablesSyncMessage(FriendlyByteBuf buffer)
public PlayerVariablesSyncMessage(PlayerVariables data, int entityid)
```

### Packet methods

```java
public static void buffer(PlayerVariablesSyncMessage message, FriendlyByteBuf buffer)
public static void handler(PlayerVariablesSyncMessage message, Supplier<NetworkEvent.Context> contextSupplier)
```

### Wire format

The packet sends:

1. full `CompoundTag` produced by `writeNBT()`,
2. target entity ID.

### Client application

The handler locates the entity by ID on the client and writes every synced field into that entity’s capability instance.

This sync path is essential for the addon because the addon:

- reads displayed cost fields,
- infers current technique and form context,
- reacts to `noChangeTechnique`, `PassiveTechnique`, and `PhysicalAttack`,
- reuses `PlayerCursePowerChange` for extra CE cost adjustments.

---

## 5. `MapVariables`

The base mod also defines saved-data-backed map-wide variables in:

```java
JujutsucraftModVariables.MapVariables
```

## 5.1 Important global field

The addon-critical field is:

```text
DomainExpansionRadius
```

### Default value

```text
DomainExpansionRadius = 22.0
```

### Storage key

```text
DomainExpansionRadius
```

### Serialization methods

- read from `CompoundTag` in `read(CompoundTag nbt)`
- write to `CompoundTag` in `m_7176_(CompoundTag nbt)`

### Sync behavior

`syncData(LevelAccessor world)` broadcasts a `SavedDataSyncMessage` to all clients.

### Why it matters to the addon

The addon temporarily scales or overrides this global value during:

- domain startup,
- active domain tick,
- range-attack sure-hit processing,
- cleanup entity logic,
- barrier placement interception.

The base mod treats this value as the authoritative radius source for many domain procedures.

---

## 6. Technique Selection Menus

The original mod uses lightweight MCreator container classes for selection UIs.

Covered classes:

- `SelectProfessionMenu`
- `SelectTechniqueMenu`
- `SelectTechnique2Menu`

## 6.1 Shared structure

All three menu classes are almost structurally identical.

### Class signature pattern

```java
public class SelectTechniqueMenu extends AbstractContainerMenu implements Supplier<Map<Integer, Slot>>
```

Equivalent structure exists for the other two menus.

### Shared fields

```text
public final Level world
public final Player entity
public int x
public int y
public int z
private ContainerLevelAccess access
private IItemHandler internal
private final Map<Integer, Slot> customSlots
private boolean bound
private Supplier<Boolean> boundItemMatcher
private Entity boundEntity
private BlockEntity boundBlockEntity
```

### Shared constructor signature

```java
public SelectTechniqueMenu(int id, Inventory inv, FriendlyByteBuf extraData)
```

Equivalent signatures exist for the profession and secondary-technique menus.

### Shared behavior

- stores the acting player in `entity`,
- stores the world in `world`,
- reads block position from `FriendlyByteBuf`,
- creates an empty `ItemStackHandler(0)`,
- behaves like a container with **zero real slots**,
- returns `ItemStack.EMPTY` on shift-click extraction,
- uses `guistate` and button packets elsewhere for actual selection.

## 6.2 Why these menus matter to the addon

The addon does **not** reuse these exact menus for the skill wheel or domain mastery UI, but they define the original UX model:

- selection is represented by lightweight server-aware containers,
- actual state changes happen via packets and procedures,
- there is very little menu-side inventory logic.

That same philosophy carries over into addon packet-driven screens.

---

## 7. `AnimUtils`

The base animation helper lives in:

```java
net.mcreator.jujutsucraft.utils.AnimUtils
```

It provides a tiny utility API used to align vanilla `ModelPart` instances with GeckoLib bones.

## 7.1 Methods

### Render directly over a bone using white tint

```java
public static void renderPartOverBone(
    ModelPart model,
    GeoBone bone,
    PoseStack stack,
    VertexConsumer buffer,
    int packedLightIn,
    int packedOverlayIn,
    float alpha
)
```

This overload forwards to the RGB version with `(1.0f, 1.0f, 1.0f, alpha)`.

### Render over a bone with explicit tint

```java
public static void renderPartOverBone(
    ModelPart model,
    GeoBone bone,
    PoseStack stack,
    VertexConsumer buffer,
    int packedLightIn,
    int packedOverlayIn,
    float r,
    float g,
    float b,
    float a
)
```

Internal flow:

1. call `setupModelFromBone(model, bone)`,
2. render the model part with the supplied tint.

### Apply bone pivot to model part

```java
public static void setupModelFromBone(ModelPart model, GeoBone bone)
```

It does two important things:

- copies the bone pivot to the model part,
- resets the model rotations to zero.

This helper is simple, but it is exactly the kind of low-level utility the addon can rely on when matching base model conventions.

---

## 8. CSM Dimension Setup

The base mod includes a custom dimension named:

```text
jujutsucraft:cursed_spirit_manipulation_dimension
```

This is commonly abbreviated as the **CSM dimension** in addon discussions.

## 8.1 Dimension registration reference

The decompiled dimension class is:

```text
mod_work/jjc_decompile/net/mcreator/jujutsucraft/world/dimension/CursedSpiritManipulationDimensionDimension.java
```

The custom effect is registered with resource location:

```text
jujutsucraft:cursed_spirit_manipulation_dimension
```

## 8.2 Functional role in the base mod

The dimension is deeply tied to Geto / cursed spirit manipulation behavior.

Base procedures repeatedly:

- test whether an entity is already inside this dimension,
- force-load chunks in it,
- teleport entities into it,
- store and retrieve spirit data associated with `data_cursed_spirit_manipulation*` persistent keys.

Examples of heavy dimension usage appear in procedures such as:

- `CursedSpiritBallFoodEatenProcedure`
- `GetoCancelTechniqueProcedure`
- `GetoHappySetProcedure`
- `SkillUzumakiProcedure`
- several cursed-spirit AI procedures

## 8.3 Relevance to the addon

The addon’s skill wheel specifically supports Geto spirit pages, and those pages are built from the base mod’s persistent cursed-spirit storage model. Even though the addon does not redesign the CSM dimension itself, its UI and selection logic depend on the same underlying data ecosystem.

---

## 9. Architectural Summary

The original JujutsuCraft mod is architected around four major pillars:

1. **Forge bootstrap + DeferredRegister content registration**
2. **procedure-centric gameplay logic**
3. **player and map state stored in capabilities / saved data**
4. **packet-assisted UI and synchronization**

The addon works because it can safely plug into these pillars:

- it reuses `PlayerVariables` instead of replacing it,
- it reads and temporarily overrides `MapVariables.DomainExpansionRadius`,
- it intercepts base procedures with mixins,
- it adds a separate addon networking layer while still interoperating with base packets and fields,
- it extends rather than discards the original domain and technique architecture.

---

## 10. Cross References

For systems that build directly on this architecture, continue with:

- `DOMAIN_SYSTEM.md`
- `DOMAIN_MIXIN_LAYERS.md`
- `NETWORKING.md`
- `SKILL_WHEEL.md`
- `CROSS_SYSTEM_INTERACTIONS.md`
