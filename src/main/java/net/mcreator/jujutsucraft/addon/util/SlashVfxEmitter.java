package net.mcreator.jujutsucraft.addon.util;

import net.mcreator.jujutsucraft.addon.logic.SlashVfxPolicy;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Shared command emitter for Malevolent Shrine's custom slash particle. */
public final class SlashVfxEmitter {

    private SlashVfxEmitter() {
    }

    public static void emitScaledSlash(ServerLevel world, Vec3 center, double range, double radiusMul) {
        if (world == null || center == null) {
            return;
        }
        double spread = SlashVfxPolicy.slashSpread(range);
        int count = SlashVfxPolicy.slashParticleCount(range);
        SlashVfxEmitter.broadcastSlash(world, center, spread, spread, spread, count);
    }

    public static void broadcastSlash(ServerLevel world, Vec3 center, double spread, int count) {
        SlashVfxEmitter.broadcastSlash(world, center, spread, spread, spread, count);
    }

    private static void broadcastSlash(ServerLevel world, Vec3 center, double spreadX, double spreadY, double spreadZ, int count) {
        if (world == null || center == null || count <= 0) {
            return;
        }
        String command = "particle jujutsucraft:particle_slash_large " + center.x + " " + center.y + " " + center.z + " " + spreadX + " " + spreadY + " " + spreadZ + " 0.01 " + count + " force";
        world.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, center, Vec2.ZERO, world, 4, "", Component.literal(""), world.getServer(), null).withSuppressedOutput(), command);
    }
}
