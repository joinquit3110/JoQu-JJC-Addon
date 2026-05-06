package net.mcreator.jujutsucraft.addon.yuta;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

public class YutaFakePlayerEntity extends Villager {
    public static final String KEY_FAKE = "jjkaddon_yuta_fake_player";
    public static final String KEY_RCT = "jjkaddon_yuta_fake_rct";
    public static final String KEY_HIT_COUNT = "jjkaddon_yuta_fake_hit_count";

    public YutaFakePlayerEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    public void configure(double techniqueId, boolean rct) {
        this.getPersistentData().putBoolean(KEY_FAKE, true);
        this.getPersistentData().putBoolean(KEY_RCT, rct);
        this.getPersistentData().putDouble("PlayerCurseTechnique", techniqueId);
        this.getPersistentData().putDouble("PlayerCurseTechnique2", 0.0D);
        this.getPersistentData().putDouble("jjkaddon_source_PlayerCurseTechnique", techniqueId);
        this.getPersistentData().putDouble("jjkaddon_source_PlayerCurseTechnique2", 0.0D);
        this.setCustomName(Component.literal((rct ? "Yuta RCT Fake CT " : "Yuta Fake CT ") + (int)Math.round(techniqueId)));
        this.setCustomNameVisible(true);
    }
}
