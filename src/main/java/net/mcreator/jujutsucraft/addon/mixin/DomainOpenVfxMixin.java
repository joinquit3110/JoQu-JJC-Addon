package net.mcreator.jujutsucraft.addon.mixin;

import java.lang.reflect.Method;
import net.mcreator.jujutsucraft.addon.mixin.DomainMasteryMixin;
import net.mcreator.jujutsucraft.procedures.DomainExpansionCreateBarrierProcedure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Open-domain startup mixin for `DomainExpansionCreateBarrierProcedure.execute()` that bridges to the main opening-burst helper through reflection when open-form VFX must be fired from a different startup path.
 */
// Binds this addon mixin to the original target class so only the selected procedure or entity behavior is altered.
@Mixin(value={DomainExpansionCreateBarrierProcedure.class}, remap=false)
public class DomainOpenVfxMixin {
    // Name of the private helper inside the main domain mastery mixin that is invoked reflectively for open-domain startup bursts.
    private static final String JJKBRP$OPENING_METHOD = "jjkbrp$fireOpeningVFX";

    /**
     * Injects after barrier startup and triggers the open-domain opening burst when the caster is entering open form from this startup path.
     * @param world world access used by the current mixin callback.
     * @param x world coordinate value used by this callback.
     * @param y world coordinate value used by this callback.
     * @param z world coordinate value used by this callback.
     * @param entity entity involved in the current mixin operation.
     * @param ci callback handle used to cancel or override the original procedure.
     */
    // Injects on method return so temporary runtime state can be restored or post-processing can run after the original logic completes.
    @Inject(method={"execute"}, at={@At(value="RETURN")}, remap=false)
    private static void jjkbrp$domainOpeningVfx(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        boolean openDomainSelected;
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)world;
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        CompoundTag nbt = player.getPersistentData();
        // Treat explicit runtime flags as authoritative so stale startup markers cannot re-trigger open-domain VFX.
        boolean bl = openDomainSelected = nbt.getBoolean("jjkbrp_open_form_active") || nbt.getInt("jjkbrp_domain_form_effective") == 2;
        if (!openDomainSelected) {
            return;
        }
        if (nbt.getBoolean("jjkbrp_opening_vfx_fired")) {
            return;
        }
        if (nbt.getDouble("cnt1") < 1.0) {
            return;
        }
        if (!nbt.contains("x_pos_doma")) {
            return;
        }
        nbt.putDouble("jjkbrp_open_domain_cx", nbt.getDouble("x_pos_doma"));
        nbt.putDouble("jjkbrp_open_domain_cy", nbt.getDouble("y_pos_doma"));
        nbt.putDouble("jjkbrp_open_domain_cz", nbt.getDouble("z_pos_doma"));
        nbt.putBoolean("jjkbrp_open_center_locked", true);
        if (!nbt.contains("jjkbrp_open_cast_game_time")) {
            nbt.putLong("jjkbrp_open_cast_game_time", serverLevel.getGameTime());
        }
        // Delegate to the shared private VFX builder so this alternate startup path stays visually consistent with the main active-tick mixin.
        DomainOpenVfxMixin.jjkbrp$invokeOpeningBurst(serverLevel, player);
    }

    /**
     * Reflectively invokes the private opening-burst helper owned by the main domain mastery mixin.
     * @param world world access used by the current mixin callback.
     * @param player entity involved in the current mixin operation.
     */
    private static void jjkbrp$invokeOpeningBurst(ServerLevel world, Player player) {
        try {
            // Reflection keeps this helper decoupled from the private implementation details of the main mastery mixin.
            Method method = DomainMasteryMixin.class.getDeclaredMethod(JJKBRP$OPENING_METHOD, ServerLevel.class, Player.class);
            method.setAccessible(true);
            method.invoke(null, world, player);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}
