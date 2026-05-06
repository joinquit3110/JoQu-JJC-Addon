package net.mcreator.jujutsucraft.addon.yuta;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.mcreator.jujutsucraft.addon.limb.LimbEntityRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class YutaFakePlayerCommands {
    private static final int[] TECHNIQUE_IDS = new int[]{1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};

    private YutaFakePlayerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jjkaddon_yuta_fake").requires(src -> src.hasPermission(2))
            .then(Commands.argument("techniqueId", DoubleArgumentType.doubleArg(1.0D, 19.0D)).suggests((ctx, builder) -> {
                for (int id : TECHNIQUE_IDS) {
                    builder.suggest(String.valueOf(id));
                }
                return builder.buildFuture();
            }).executes(ctx -> spawn(ctx, false))));
        dispatcher.register(Commands.literal("jjkaddon_yuta_fake_rct").requires(src -> src.hasPermission(2))
            .then(Commands.argument("techniqueId", DoubleArgumentType.doubleArg(1.0D, 19.0D)).suggests((ctx, builder) -> {
                for (int id : TECHNIQUE_IDS) {
                    builder.suggest(String.valueOf(id));
                }
                return builder.buildFuture();
            }).executes(ctx -> spawn(ctx, true))));
        dispatcher.register(Commands.literal("jjkaddon_yuta_copy_reset").requires(src -> src.hasPermission(2))
            .executes(YutaFakePlayerCommands::resetCopies));
    }

    private static int resetCopies(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int count = YutaCopyStore.resetCopies(player);
        ctx.getSource().sendSuccess(() -> Component.literal("§d[Rika] Reset " + count + " copied technique record" + (count == 1 ? "" : "s") + " and related cooldowns."), true);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, boolean rct) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        double techniqueId = DoubleArgumentType.getDouble(ctx, "techniqueId");
        YutaFakePlayerEntity fake = new YutaFakePlayerEntity(LimbEntityRegistry.YUTA_FAKE_PLAYER.get(), level);
        Vec3 pos = source.getPosition();
        fake.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0F);
        fake.configure(techniqueId, rct);
        fake.setCustomName(Component.literal("Yuta fake copy CT: " + YutaCopyStore.techniqueName(techniqueId)));
        fake.setCustomNameVisible(true);
        fake.setHealth(fake.getMaxHealth());
        level.addFreshEntity(fake);
        source.sendSuccess(() -> Component.literal("Spawned " + (rct ? "RCT " : "") + "Yuta fake player with CT " + (int)Math.round(techniqueId)), true);
        return 1;
    }
}
