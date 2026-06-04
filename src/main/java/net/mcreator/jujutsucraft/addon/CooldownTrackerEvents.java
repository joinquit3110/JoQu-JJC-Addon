package net.mcreator.jujutsucraft.addon;

import net.mcreator.jujutsucraft.addon.ModNetworking;
import net.mcreator.jujutsucraft.addon.limb.RCTLevel3Handler;
import net.mcreator.jujutsucraft.addon.util.DomainAddonUtils;
import net.mcreator.jujutsucraft.addon.util.SureHitShrineFx;
import net.mcreator.jujutsucraft.init.JujutsucraftModMobEffects;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="jjkblueredpurple", bus=Mod.EventBusSubscriber.Bus.FORGE)
/**
 * Server-side combat tracker for Black Flash systems. It calculates live proc chance, unlocks Black Flash mastery milestones, synchronizes HUD values, and applies near-death cooldown reductions after successful procs.
 */
public class CooldownTrackerEvents {
    // Total Black Flash hit threshold required to unlock mastery for most characters.
    private static final int BF_MASTERY_THRESHOLD = 100;
    // Reduced mastery threshold used for Yuji because his Black Flash identity is stronger.
    private static final int BF_MASTERY_THRESHOLD_YUJI = 50;
    // Legacy probability constant retained alongside the newer percentage-based Black Flash calculation.
    private static final double BF_ROLL_CHANCE = 0.0012;
    // Combat tag timeout in ticks before accumulated Black Flash combat bonus starts decaying.
    private static final int BF_COMBAT_TIMEOUT = 100;
    // Base amount of Black Flash combat bonus gained per tick while actively fighting.
    private static final double BF_COMBAT_RATE = 0.004;
    // Amount of stored combat bonus removed per tick once the player leaves combat.
    private static final double BF_COMBAT_DECAY = 0.06;
    // Maximum combat-generated bonus percentage for standard characters.
    private static final double BF_COMBAT_MAX_NORMAL = 15.0;
    // Maximum combat-generated bonus percentage for Yuji.
    private static final double BF_COMBAT_MAX_YUJI = 28.0;
    // Final Black Flash percentage cap applied to standard characters.
    private static final double BF_PERCENT_CAP_NORMAL = 15.0;
    // Final Black Flash percentage cap applied to Yuji.
    private static final double BF_PERCENT_CAP_YUJI = 30.0;
    // Near-death cooldown reduction, in ticks, granted when Black Flash procs in the supported path.
    private static final int ND_COOLDOWN_REDUCTION = 120;
    // How many CONSECUTIVE server ticks the live DOMAIN_EXPANSION effect must read absent
    // before the per-tick handler concludes the Incomplete Domain Shrine genuinely ended.
    // The base-mod active-tick procedure briefly removes and re-applies the domain effect
    // during its clash/neutralization checks, so a single absent read is a normal flicker;
    // requiring several consecutive misses prevents a premature domain-end that would clear
    // the surehit latch mid-domain (Issue 2).
    private static final int SUREHIT_END_GRACE_TICKS = 10;

    @SubscribeEvent
    /**
     * Updates live Black Flash data for each server player, including chance calculation, mastery unlock checks, and HUD sync packets.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        boolean isYuji;
        int amp;
        boolean hasBfExperience;
        int amp2;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        CompoundTag data = player2.getPersistentData();
        CooldownTrackerEvents.handleSukunaIncompleteSureHitReward(player2, data);
        CooldownTrackerEvents.tickStaleStateReconcile(player2, data);
        int bfCd = data.getInt("jjkbrp_bf_cd");
        if (bfCd > 0) {
            data.putInt("jjkbrp_bf_cd", bfCd - 1);
        }
        // Build the live Black Flash chance multiplier from advancements, effects, character bonuses, and current risk state.
        double bonusMultiplier = 1.0;
        if (player2.hasEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get())) {
            amp2 = player2.getEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get()).getAmplifier();
            bonusMultiplier += 2.0 + (double)amp2;
        }
        if (player2.hasEffect((MobEffect)JujutsucraftModMobEffects.DEEP_CONCENTRATION.get())) {
            amp2 = player2.getEffect((MobEffect)JujutsucraftModMobEffects.DEEP_CONCENTRATION.get()).getAmplifier();
            bonusMultiplier += 75.0 + 5.0 * (double)(amp2 + 1);
        }
        if (hasBfExperience = CooldownTrackerEvents.hasAdvancement(player2, "jujutsucraft:black_flash")) {
            bonusMultiplier += 1.0;
        }
        if (player2.hasEffect((MobEffect)JujutsucraftModMobEffects.SPECIAL.get()) && (amp = player2.getEffect((MobEffect)JujutsucraftModMobEffects.SPECIAL.get()).getAmplifier()) > 0) {
            bonusMultiplier += 5.0;
        }
        JujutsucraftModVariables.PlayerVariables vars = player2.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(new JujutsucraftModVariables.PlayerVariables());
        double activeTech = vars.SecondTechnique ? vars.PlayerCurseTechnique2 : vars.PlayerCurseTechnique;
        boolean bl = isYuji = (int)Math.round(activeTech) == 21;
        if (isYuji) {
            bonusMultiplier += 3.0;
        }
        float hp = player2.getHealth();
        float maxHp = player2.getMaxHealth();
        double hpFactor = 1.0 - (double)Math.max(hp, 1.0f) / (double)Math.max(maxHp, 1.0f);
        // Convert the accumulated multiplier into a bounded percentage curve so bonuses scale smoothly instead of linearly exploding.
        double basePercent = (1.0 - Math.pow(0.9988, bonusMultiplier * (1.0 + hpFactor * 2.0))) * 100.0;
        boolean hasMastery = data.getBoolean("addon_bf_mastery");
        long lastCombatTick = data.getLong("addon_bf_last_combat_tick");
        boolean inCombat = (long)player2.tickCount - lastCombatTick < 100L;
        double combatBonus = data.getDouble("addon_bf_combat_bonus");
        double rate = 0.004;
        if (hasBfExperience) {
            rate *= 1.2;
        }
        if (hasMastery) {
            rate *= 1.4;
        }
        if (isYuji) {
            rate *= 1.6;
        }
        double combatMax = isYuji ? 28.0 : 15.0;
        double percentCap = isYuji ? 30.0 : 15.0;
        // Combat gradually ramps the proc rate up, then decays it once the player falls out of the recent-combat window.
        combatBonus = inCombat ? Math.min(combatMax, combatBonus + rate) : Math.max(0.0, combatBonus - 0.06);
        data.putDouble("addon_bf_combat_bonus", combatBonus);
        double domainBonus = Math.max(0.0, data.getDouble("jjkbrp_domain_bf_bonus"));
        double bfPercent = basePercent + combatBonus + domainBonus;
        if (bfPercent > percentCap) {
            bfPercent = percentCap;
        }
        data.putDouble("addon_bf_chance", bfPercent);
        if (!hasMastery) {
            if (CooldownTrackerEvents.hasAdvancement(player2, "jjkblueredpurple:black_flash_mastery")) {
                data.putBoolean("addon_bf_mastery", true);
                hasMastery = true;
            } else {
                int threshold;
                int totalHits = data.getInt("addon_bf_total_hits");
                threshold = isYuji ? BF_MASTERY_THRESHOLD_YUJI : BF_MASTERY_THRESHOLD;
                // Mastery unlocks automatically after enough successful Black Flash-related combat milestones, with a lower threshold for Yuji.
                if (totalHits >= threshold) {
                    data.putBoolean("addon_bf_mastery", true);
                    CooldownTrackerEvents.grantAdvancement(player2, "jjkblueredpurple:black_flash_mastery");
                    player2.displayClientMessage((Component)Component.literal((String)"\u00a7d\u00a7l\u2605 Black Flash Mastery Achieved! \u2605"), false);
                }
            }
        }
        if (player2.tickCount % 10 == 0) {
            ModNetworking.sendBlackFlashSync(player2);
        }
        if (player2.tickCount % 20 == 0) {
            ModNetworking.sendNearDeathCdSync(player2);
        }
        if (player2.tickCount % 40 == 0) {
            RCTLevel3Handler.checkAndGrantRCTLevel3(player2);
        }
    }

    private static void handleSukunaIncompleteSureHitReward(ServerPlayer player, CompoundTag data) {
        boolean sessionActive = data.getBoolean("jjkbrp_sukuna_incomplete_surehit_session");
        // The live DOMAIN_EXPANSION effect is the ground truth for "the shrine is still up".
        // It is far more stable than the full composite isActiveSukunaIncompleteShrine
        // predicate, which also reads the incomplete-form / runtime-domain-id NBT that other
        // base/addon paths can momentarily rewrite during a tick.
        boolean liveDomain = DomainAddonUtils.hasLiveDomainEffect(player);
        boolean domainActive = DomainAddonUtils.isActiveSukunaIncompleteShrine(player);
        boolean rewardFlag = data.getBoolean("jjkbrp_sukuna_fuga_dust_locked_full");

        // Req 2.1, 2.2, 2.4: LATCH surehit while the shrine session is established AND a live
        // domain effect is still present. Re-assert all three flags every tick so a transient
        // flicker in the composite predicate (a momentary incomplete-form / runtime-domain-id
        // read, or an incidental cleanup that strips a flag) cannot deactivate surehit
        // mid-domain. This is the core Issue 2 fix: previously the handler treated
        // "session set but isActiveSukunaIncompleteShrine() == false" as a domain end and
        // cleared the flags, which was self-reinforcing (once cleared, the predicate stayed
        // false forever), so surehit vanished a few ticks after activation.
        if (domainActive || (sessionActive && liveDomain)) {
            data.putBoolean("jjkbrp_sukuna_incomplete_surehit_session", true);
            data.putBoolean("jjkbrp_sukuna_incomplete_surehit_active", true);
            data.putBoolean("jjkbrp_sukuna_incomplete_surehit_had_domain", true);
            // The domain is confirmed live this tick: reset the end-grace counter.
            data.remove("jjkbrp_sukuna_incomplete_end_grace");
            // Drive the sure-hit shrine VFX + radius-bounded terrain pulverization here, on the
            // reliable per-tick path. Doing it here (rather than at the base
            // MalevolentShrineActiveProcedure HEAD, which the addon masks/redirects and which only
            // runs while skill_domain==1) guarantees the dramatic shrine effects appear for the
            // whole domain, mirroring how Closed/Open domains render their barrier/VFX.
            SureHitShrineFx.tick(player, data);
            // Req 4.3, 4.4: keep Fuga's cooldown effects absent whenever the reward flag is set.
            if (rewardFlag) {
                ModNetworking.clearSukunaFugaCooldown(player);
            }
            // Req 5.7, 6.5: re-sync the overlay from the REAL dust_amount (no refill to full),
            // independent of base-mod domain-end overlay clearing.
            ModNetworking.syncDustOverlayFromAmount(player);
            return;
        }

        if (sessionActive) {
            // Session is set but the live domain effect read false THIS tick. That is NOT
            // sufficient to conclude the domain ended: the base-mod active-tick procedure
            // briefly REMOVES and re-applies the DOMAIN_EXPANSION effect during its clash /
            // neutralization checks, so a single absent read is a normal mid-domain flicker.
            // Concluding "domain ended" here was the real Issue 2 killer — it cleared the
            // session latch and prematurely granted the reward while the domain was still
            // running, after which surehit could never recover.
            //
            // Require the effect to be gone for a few CONSECUTIVE ticks before treating it as
            // a genuine end. The grace counter is reset every tick the effect is seen (above).
            int endGrace = data.getInt("jjkbrp_sukuna_incomplete_end_grace") + 1;
            if (endGrace < SUREHIT_END_GRACE_TICKS) {
                data.putInt("jjkbrp_sukuna_incomplete_end_grace", endGrace);
                // Still within the grace window: keep the latch alive, do not grant/clear.
                data.putBoolean("jjkbrp_sukuna_incomplete_surehit_session", true);
                data.putBoolean("jjkbrp_sukuna_incomplete_surehit_active", true);
                data.putBoolean("jjkbrp_sukuna_incomplete_surehit_had_domain", true);
                return;
            }
            // Grace exhausted: the domain has genuinely ended.
            data.remove("jjkbrp_sukuna_incomplete_end_grace");
            boolean usedFugaDuringDomain = data.getBoolean("jjkbrp_sukuna_incomplete_fuga_used");
            if (data.getBoolean("jjkbrp_sukuna_incomplete_surehit_had_domain") && !usedFugaDuringDomain) {
                // Domain just ended with surehit and Fuga unused: grant the one-time reward.
                // This is a GRANT moment, so dust is filled once here (consistent with the
                // DomainExpireBarrierFixMixin domain-end path); steady-state ticks only re-sync.
                ModNetworking.clearSukunaFugaCooldown(player);
                data.putBoolean("jjkbrp_sukuna_fuga_dust_locked_full", true);
                ModNetworking.fillSukunaFugaDust(player);
            }
            // Req 2.5: clear the surehit session state so surehit cannot bleed into a later domain.
            data.remove("jjkbrp_sukuna_incomplete_surehit_session");
            data.remove("jjkbrp_sukuna_incomplete_surehit_had_domain");
            data.remove("jjkbrp_sukuna_incomplete_surehit_active");
            data.remove("jjkbrp_sukuna_incomplete_fuga_used");
        } else if (rewardFlag) {
            // Req 4.3, 4.4: persisted reward past domain end — re-clear the Fuga cooldown effects
            // every tick so they stay absent for the full Unstable duration.
            ModNetworking.clearSukunaFugaCooldown(player);
            // Req 5.7, 6.5: keep the overlay synced from the REAL dust (no refill), independent of
            // the base-mod domain-end overlay clearing.
            ModNetworking.syncDustOverlayFromAmount(player);
        }
    }

    /**
     * Req 7.1, 7.5: the 20-tick post-login safety re-check of the Issue 4 stale-state
     * reconciliation. The login handler ({@code BlueRedPurpleNukeMod.onPlayerLoggedIn}) seeds
     * {@code jjkbrp_stale_reconcile_ticks = 20} for Sukuna players; while that countdown is
     * positive this re-runs the shared reconcile + write-back + sync each server tick so a login
     * that lands a few ticks before the capability/world is fully ready still converges to the
     * Clean_Baseline within 20 server ticks (1 second).
     *
     * <p>The actual reconcile decision is delegated to the pure
     * {@code StaleStateReconciler} via the shared
     * {@code BlueRedPurpleNukeMod.reconcileSukunaStaleState} helper, so the login path and this
     * re-check stay identical and DRY. Only writes back when stale state is present and no
     * genuinely-active shrine exists; an active shrine is preserved (Req 7.7).</p>
     *
     * @param player the server player being ticked.
     * @param data   the player's persistent data (holds the countdown).
     */
    private static void tickStaleStateReconcile(ServerPlayer player, CompoundTag data) {
        int ticksLeft = data.getInt("jjkbrp_stale_reconcile_ticks");
        if (ticksLeft <= 0) {
            return;
        }
        // Re-run the same reconcile + write-back + sync the login handler uses. This converges to
        // the Clean_Baseline even if the capability/world was not ready at the exact login instant.
        BlueRedPurpleNukeMod.reconcileSukunaStaleState(player);
        ticksLeft--;
        if (ticksLeft <= 0) {
            // Window elapsed: drop the transient countdown key entirely.
            data.remove("jjkbrp_stale_reconcile_ticks");
        } else {
            data.putInt("jjkbrp_stale_reconcile_ticks", ticksLeft);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData().remove("jjkbrp_sukuna_fuga_dust_locked_full");
            player.getPersistentData().remove("jjkbrp_sukuna_incomplete_fuga_used");
            player.getPersistentData().remove("jjkbrp_sukuna_incomplete_surehit_session");
            player.getPersistentData().remove("jjkbrp_sukuna_incomplete_surehit_had_domain");
            player.getPersistentData().remove("jjkbrp_sukuna_incomplete_surehit_active");
            player.getPersistentData().remove("jjkbrp_sukuna_fuga_reward_casting");
            ModNetworking.clearSukunaFugaDustOverlay(player);
        }
    }

    /**
     * Req 6.7 / 3.6: scrub the Fuga dust reward and surehit state from the NEW (post-respawn)
     * player on a death-respawn clone. Forge/base-mod may copy persistent data and overlay fields
     * forward across the clone, so the post-respawn session could otherwise carry a stale reward
     * flag, surehit flags, dust amount, or dust overlay. We clear the new player's keys directly so
     * the post-respawn session starts with dust_amount = 0, empty OVERLAY1/OVERLAY2, the three
     * surehit flags cleared, and the reward flag removed. All writes are server-side.
     * @param event Forge clone event fired on respawn (and dimension change).
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();
            data.remove("jjkbrp_sukuna_fuga_dust_locked_full");
            data.remove("jjkbrp_sukuna_incomplete_fuga_used");
            data.remove("jjkbrp_sukuna_incomplete_surehit_session");
            data.remove("jjkbrp_sukuna_incomplete_surehit_had_domain");
            data.remove("jjkbrp_sukuna_incomplete_surehit_active");
            data.remove("jjkbrp_sukuna_fuga_reward_casting");
            // Sets dust_amount = 0 and clears OVERLAY1/OVERLAY2 on the new player.
            ModNetworking.clearSukunaFugaDustOverlay(player);
        }
    }

    @SubscribeEvent
    /**
     * Tracks recent combat activity and applies Black Flash near-death cooldown interactions when valid hits land.
     * @param event context data supplied by the current callback or network pipeline.
     */
    public static void onLivingHurt(LivingHurtEvent event) {
        ServerPlayer sp;
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Entity attacker = event.getSource().getDirectEntity();
        if (attacker instanceof ServerPlayer) {
            sp = (ServerPlayer)attacker;
            sp.getPersistentData().putLong("addon_bf_last_combat_tick", (long)sp.tickCount);
            MobEffectInstance zone = sp.getEffect((MobEffect)JujutsucraftModMobEffects.ZONE.get());
            boolean bfJustProcced = zone != null && zone.getDuration() >= 5990;
            boolean rangedHandled = sp.getPersistentData().getBoolean("jjkbrp_bf_nd_handled");
            if (bfJustProcced) {
                int currentNdCd;
                // A confirmed Black Flash proc shaves time off the near-death cooldown so clutch plays feed back into survival tools.
                if (!rangedHandled && (currentNdCd = sp.getPersistentData().getInt("jjkbrp_near_death_cd")) > 0) {
                    int newCd = Math.max(0, currentNdCd - 120);
                    sp.getPersistentData().putInt("jjkbrp_near_death_cd", newCd);
                }
                sp.getPersistentData().putBoolean("jjkbrp_bf_regen_boost", true);
                sp.getPersistentData().putBoolean("jjkbrp_bf_nd_handled", false);
            }
        }
        if (event.getEntity() instanceof ServerPlayer) {
            sp = (ServerPlayer)event.getEntity();
            sp.getPersistentData().putLong("addon_bf_last_combat_tick", (long)sp.tickCount);
        }
    }

    static boolean hasAdvancement(ServerPlayer player, String advancementId) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(advancementId));
            if (adv == null) {
                return false;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            return progress.isDone();
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Awards every remaining criterion on the requested advancement when the player qualifies for mastery unlocks.
     * @param player player instance involved in this operation.
     * @param advancementId identifier used to resolve the requested entry or state.
     */
    private static void grantAdvancement(ServerPlayer player, String advancementId) {
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(advancementId));
            if (adv == null) {
                return;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            if (!progress.isDone()) {
                for (String criteria : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(adv, criteria);
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

