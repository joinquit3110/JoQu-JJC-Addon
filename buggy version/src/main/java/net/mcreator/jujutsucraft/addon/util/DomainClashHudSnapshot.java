package net.mcreator.jujutsucraft.addon.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Immutable server-authored HUD snapshot for one player's current domain clash view.
 *
 * <p>This snapshot is derived directly from the authoritative registry state and is
 * only created for player participants. Non-player participants still fully exist in
 * the registry and can appear as opponents inside the snapshot.</p>
 */
public final class DomainClashHudSnapshot {

    private final UUID viewerUuid;
    private final boolean active;
    private final long syncedGameTime;
    private final float casterPower;
    private final int casterDomainId;
    private final int casterForm;
    private final String casterName;
    private final List<OpponentSnapshot> opponents;

    private DomainClashHudSnapshot(UUID viewerUuid, boolean active, long syncedGameTime,
                                   float casterPower, int casterDomainId, int casterForm,
                                   String casterName, List<OpponentSnapshot> opponents) {
        this.viewerUuid = viewerUuid;
        this.active = active;
        this.syncedGameTime = syncedGameTime;
        this.casterPower = Math.max(0.0f, casterPower);
        this.casterDomainId = casterDomainId;
        this.casterForm = casterForm;
        this.casterName = casterName == null ? "" : casterName;
        this.opponents = Collections.unmodifiableList(new ArrayList<>(opponents));
    }

    public static DomainClashHudSnapshot inactive(UUID viewerUuid, long syncedGameTime, String viewerName) {
        return new DomainClashHudSnapshot(viewerUuid, false, syncedGameTime, 0.0f, 0, 1,
                viewerName == null ? "" : viewerName, Collections.emptyList());
    }

    @Nullable
    public static DomainClashHudSnapshot fromRegistry(LivingEntity viewer, long syncedGameTime) {
        if (viewer == null) {
            return null;
        }
        DomainParticipantSnapshot casterEntry = DomainClashRegistry.getEntry(viewer.getUUID());
        List<ClashSessionSnapshot> sessions = DomainClashRegistry.getSessionsFor(viewer.getUUID());
        if (casterEntry == null || sessions.isEmpty()) {
            return inactive(viewer.getUUID(), syncedGameTime, viewer.getName().getString());
        }

        List<OpponentSnapshot> opponents = new ArrayList<>();
        for (ClashSessionSnapshot session : sessions) {
            UUID opponentUuid = session.getOpponent(viewer.getUUID());
            if (opponentUuid == null) {
                continue;
            }
            DomainParticipantSnapshot opponentEntry = DomainClashRegistry.getEntry(opponentUuid);
            if (opponentEntry == null || opponentEntry.getDomainId() <= 0 || !isLiveEntity(viewer, opponentUuid)) {
                continue;
            }
            String opponentName = resolveDisplayName(viewer, opponentUuid);
            if (isInvalidDisplayName(opponentName)) {
                continue;
            }
            opponents.add(new OpponentSnapshot(
                    opponentUuid,
                    (float) Math.max(0.0, opponentEntry.getEffectivePower()),
                    opponentEntry.getForm().getId(),
                    opponentEntry.getDomainId(),
                    opponentName));
        }

        if (opponents.isEmpty()) {
            return inactive(viewer.getUUID(), syncedGameTime, viewer.getName().getString());
        }

        opponents.sort(Comparator.comparingDouble((OpponentSnapshot entry) -> -entry.power()));
        return new DomainClashHudSnapshot(
                viewer.getUUID(),
                true,
                syncedGameTime,
                (float) Math.max(0.0, casterEntry.getEffectivePower()),
                casterEntry.getDomainId(),
                casterEntry.getForm().getId(),
                resolveDisplayName(viewer, viewer.getUUID()),
                opponents);
    }

    public UUID getViewerUuid() {
        return this.viewerUuid;
    }

    public boolean isActive() {
        return this.active;
    }

    public long getSyncedGameTime() {
        return this.syncedGameTime;
    }

    public float getCasterPower() {
        return this.casterPower;
    }

    public int getCasterDomainId() {
        return this.casterDomainId;
    }

    public int getCasterForm() {
        return this.casterForm;
    }

    public String getCasterName() {
        return this.casterName;
    }

    public List<OpponentSnapshot> getOpponents() {
        return this.opponents;
    }

    private static boolean isLiveEntity(LivingEntity viewer, UUID targetUuid) {
        if (viewer == null || targetUuid == null) {
            return false;
        }
        if (viewer.level() instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntity(targetUuid);
            if (entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) {
                return true;
            }
            return serverLevel.getServer().getPlayerList().getPlayer(targetUuid) != null;
        }
        return false;
    }

    private static boolean isInvalidDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase("Caster")) {
            return true;
        }
        try {
            UUID.fromString(trimmed);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String resolveDisplayName(LivingEntity viewer, UUID targetUuid) {
        if (viewer == null || targetUuid == null || viewer.level() == null) {
            return "";
        }
        if (viewer instanceof net.minecraft.server.level.ServerPlayer serverPlayer && serverPlayer.server != null) {
            for (net.minecraft.server.level.ServerPlayer onlinePlayer : serverPlayer.server.getPlayerList().getPlayers()) {
                if (onlinePlayer != null && targetUuid.equals(onlinePlayer.getUUID())) {
                    Component displayName = onlinePlayer.getTabListDisplayName();
                    if (displayName != null) {
                        return displayName.getString();
                    }
                    return onlinePlayer.getGameProfile().getName();
                }
            }
        }
        if (viewer.level() instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntity(targetUuid);
            if (entity instanceof LivingEntity living) {
                return living.getName().getString();
            }
        }
        return "";
    }

    public record OpponentSnapshot(UUID uuid, float power, int form, int domainId, String name) {
        public OpponentSnapshot {
            power = Math.max(0.0f, power);
            name = name == null ? "" : name;
        }
    }
}
