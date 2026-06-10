package net.mcreator.jujutsucraft.addon.clash.net;

import static net.mcreator.jujutsucraft.addon.util.DomainClashConstants.SAMPLING_INTERVAL_TICKS_DEFAULT;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import net.mcreator.jujutsucraft.addon.AddonGameRules;
import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.clash.model.ClashOutcome;
import net.mcreator.jujutsucraft.addon.clash.model.ClashSession;
import net.mcreator.jujutsucraft.addon.clash.resolve.ClashResolver;
import net.mcreator.jujutsucraft.addon.util.DomainForm;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.network.chat.Component;

public class ClashSyncNetwork implements ClashResolver.SyncSink {
    private static final class LastSendState {
        double lastPowerA;
        double lastPowerB;
        long lastSendTick;

        LastSendState(double a, double b, long t) {
            this.lastPowerA = a;
            this.lastPowerB = b;
            this.lastSendTick = t;
        }
    }

    private final Map<UUID, LastSendState> lastSendStates = new HashMap<>();
    private final LongSupplier serverTickSupplier;

    public ClashSyncNetwork() {
        this(() -> 0L);
    }

    public ClashSyncNetwork(LongSupplier serverTickSupplier) {
        this.serverTickSupplier = serverTickSupplier == null ? () -> 0L : serverTickSupplier;
    }

    @Override
    public void sendInitial(ClashSession session) {
        if (sendInternal(session)) {
            notifyViewers(session, "Domain Clash started");
            lastSendStates.put(
                session.sessionId,
                new LastSendState(session.clashPowerA(), session.clashPowerB(), serverTickSupplier.getAsLong())
            );
        } else {
            lastSendStates.remove(session.sessionId);
        }
    }

    @Override
    public void sendSampled(ClashSession session) {
        if (!isHudEnabled(session)) {
            dispatchSnapshot(session, ClashOutcome.CANCELLED);
            lastSendStates.remove(session.sessionId);
            return;
        }
        long now = serverTickSupplier.getAsLong();
        LastSendState state = lastSendStates.get(session.sessionId);
        if (state != null) {
            long ticksSince = now - state.lastSendTick;
            if (ticksSince < sampleInterval(session)) {
                return;
            }
        }
        sendInternal(session);
        if (state == null) {
            lastSendStates.put(session.sessionId, new LastSendState(session.clashPowerA(), session.clashPowerB(), now));
        } else {
            state.lastPowerA = session.clashPowerA();
            state.lastPowerB = session.clashPowerB();
            state.lastSendTick = now;
        }
    }

    @Override
    public void sendFinal(ClashSession session) {
        if (sendInternal(session)) {
            notifyViewers(session, session.outcome() == null ? "Domain Clash ended" : "Domain Clash result: " + session.outcome().name());
        }
        lastSendStates.remove(session.sessionId);
    }

    @Override
    public void sendCancelled(ClashSession session) {
        dispatchSnapshot(session, ClashOutcome.CANCELLED);
        if (isHudEnabled(session)) {
            notifyViewers(session, "Domain Clash cancelled");
        }
        lastSendStates.remove(session.sessionId);
    }

    private void notifyViewers(ClashSession session, String text) {
    }

    private boolean sendInternal(ClashSession session) {
        if (!isHudEnabled(session)) {
            dispatchSnapshot(session, ClashOutcome.CANCELLED);
            return false;
        }
        dispatchSnapshot(session, null);
        return true;
    }

    private void dispatchSnapshot(ClashSession session, ClashOutcome forcedOutcome) {
        LivingEntity a = session.casterA.get();
        LivingEntity b = session.casterB.get();
        if (!(a instanceof ServerPlayer) && !(b instanceof ServerPlayer)) {
            return;
        }
        int formIdA = getFormId(a);
        int formIdB = getFormId(b);
        int domainIdA = getDomainId(a);
        int domainIdB = getDomainId(b);

        dispatchForParticipant(session, a, b, session.pair.a(), session.pair.b(), session.clashPowerA(), session.clashPowerB(), formIdA, domainIdA, formIdB, domainIdB, forcedOutcome);
        dispatchForParticipant(session, b, a, session.pair.b(), session.pair.a(), session.clashPowerB(), session.clashPowerA(), formIdB, domainIdB, formIdA, domainIdA, forcedOutcome);
        dispatchForNearbyViewers(session, a, b, formIdA, domainIdA, formIdB, domainIdB, forcedOutcome);
    }

    private void dispatchForNearbyViewers(ClashSession session, LivingEntity a, LivingEntity b, int formIdA, int domainIdA, int formIdB, int domainIdB, ClashOutcome forcedOutcome) {
        LivingEntity anchor = a != null ? a : b;
        if (anchor == null || anchor.level().isClientSide()) {
            return;
        }
        AABB box = anchor.getBoundingBox().inflate(96.0D);
        for (ServerPlayer player : anchor.level().getEntitiesOfClass(ServerPlayer.class, box)) {
            if (a instanceof ServerPlayer && player.getUUID().equals(a.getUUID())) {
                continue;
            }
            if (b instanceof ServerPlayer && player.getUUID().equals(b.getUUID())) {
                continue;
            }
            DomainClashHudSnapshotPacket pkt = new DomainClashHudSnapshotPacket(
                session.sessionId,
                session.pair.a(),
                session.pair.b(),
                (float) session.clashPowerA(),
                (float) session.clashPowerB(),
                session.remainingTicks(),
                formIdA,
                domainIdA,
                formIdB,
                domainIdB,
                getDisplayName(a),
                getDisplayName(b),
                forcedOutcome != null ? forcedOutcome : session.outcome()
            );
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        }
    }

    protected void dispatchForParticipant(
        ClashSession session,
        LivingEntity entity,
        LivingEntity otherEntity,
        UUID selfUuid,
        UUID otherUuid,
        double selfPower,
        double otherPower,
        int selfFormId,
        int selfDomainId,
        int otherFormId,
        int otherDomainId,
        ClashOutcome forcedOutcome
    ) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        DomainClashHudSnapshotPacket pkt = new DomainClashHudSnapshotPacket(
            session.sessionId,
            selfUuid,
            otherUuid,
            (float) selfPower,
            (float) otherPower,
            session.remainingTicks(),
            selfFormId,
            selfDomainId,
            otherFormId,
            otherDomainId,
            getDisplayName(entity),
            getDisplayName(otherEntity),
            forcedOutcome != null ? forcedOutcome : outcomeForParticipant(session, selfUuid)
        );
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    private static boolean isHudEnabled(ClashSession session) {
        ServerLevel level = resolveLevel(session);
        return level == null || AddonGameRules.domainClashHud(level);
    }

    private static int sampleInterval(ClashSession session) {
        ServerLevel level = resolveLevel(session);
        return AddonGameRules.positiveInt(level, AddonGameRules.DOMAIN_CLASH_SAMPLE_INTERVAL_TICKS, SAMPLING_INTERVAL_TICKS_DEFAULT);
    }

    private static ServerLevel resolveLevel(ClashSession session) {
        if (session == null) {
            return null;
        }
        LivingEntity a = session.casterA.get();
        if (a != null && a.level() instanceof ServerLevel level) {
            return level;
        }
        LivingEntity b = session.casterB.get();
        if (b != null && b.level() instanceof ServerLevel level) {
            return level;
        }
        return null;
    }

    private static ClashOutcome outcomeForParticipant(ClashSession session, UUID selfUuid) {
        ClashOutcome outcome = session.outcome();
        if (outcome == null || outcome == ClashOutcome.TIE || outcome == ClashOutcome.CANCELLED || selfUuid == null) {
            return outcome;
        }
        boolean selfIsOriginalA = selfUuid.equals(session.pair.a());
        if (outcome == ClashOutcome.WINNER_A) {
            return selfIsOriginalA ? ClashOutcome.WINNER_A : ClashOutcome.WINNER_B;
        }
        if (outcome == ClashOutcome.WINNER_B) {
            return selfIsOriginalA ? ClashOutcome.WINNER_B : ClashOutcome.WINNER_A;
        }
        return outcome;
    }

    private static String getDisplayName(LivingEntity entity) {
        if (entity == null) {
            return "Enemy";
        }
        String name = entity.getName().getString();
        return name == null || name.isBlank() ? "Enemy" : name;
    }

    private static int getFormId(LivingEntity entity) {
        if (entity == null) {
            return DomainForm.CLOSED.amplifierValue;
        }
        MobEffect domain = (MobEffect) JujutsucraftModMobEffects.DOMAIN_EXPANSION.get();
        if (domain == null) {
            return DomainForm.CLOSED.amplifierValue;
        }
        MobEffectInstance instance = entity.getEffect(domain);
        if (instance == null) {
            return DomainForm.CLOSED.amplifierValue;
        }
        return instance.getAmplifier();
    }

    private static int getDomainId(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        double runtime = entity.getPersistentData().getDouble("jjkbrp_domain_id_runtime");
        if (runtime != 0.0) {
            return (int) Math.round(runtime);
        }
        double skillDomain = entity.getPersistentData().getDouble("skill_domain");
        if (skillDomain != 0.0) {
            return (int) Math.round(skillDomain);
        }
        return (int) Math.round(entity.getPersistentData().getDouble("select"));
    }

    public void reset() {
        lastSendStates.clear();
    }
}



