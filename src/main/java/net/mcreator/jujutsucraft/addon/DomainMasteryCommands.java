package net.mcreator.jujutsucraft.addon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.Consumer;
import net.mcreator.jujutsucraft.addon.DomainMasteryCapabilityProvider;
import net.mcreator.jujutsucraft.addon.DomainMasteryData;
import net.mcreator.jujutsucraft.addon.DomainMasteryProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Administrative command registration for inspecting and mutating addon domain mastery state. The commands let operators adjust XP, levels, properties, negative modifiers, and selected domain form for testing or moderation.
 */
public class DomainMasteryCommands {
    /**
     * Registers addon content with the appropriate Forge or client system.
     * @param dispatcher dispatcher used by this method.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"domainmastery").requires(src -> src.hasPermission(2))).then(Commands.literal((String)"addxp").then(Commands.argument((String)"amount", (ArgumentType)DoubleArgumentType.doubleArg()).executes(ctx -> DomainMasteryCommands.addXP((CommandContext<CommandSourceStack>)ctx, DoubleArgumentType.getDouble((CommandContext)ctx, (String)"amount")))))).then(Commands.literal((String)"setlevel").then(Commands.argument((String)"level", (ArgumentType)IntegerArgumentType.integer((int)0, (int)5)).executes(ctx -> DomainMasteryCommands.setLevel((CommandContext<CommandSourceStack>)ctx, IntegerArgumentType.getInteger((CommandContext)ctx, (String)"level")))))).then(Commands.literal((String)"addpoints").then(Commands.argument((String)"amount", (ArgumentType)IntegerArgumentType.integer((int)0)).executes(ctx -> DomainMasteryCommands.addPoints((CommandContext<CommandSourceStack>)ctx, IntegerArgumentType.getInteger((CommandContext)ctx, (String)"amount")))))).then(Commands.literal((String)"setform").then(Commands.argument((String)"form", (ArgumentType)IntegerArgumentType.integer((int)0, (int)2)).executes(ctx -> DomainMasteryCommands.setForm((CommandContext<CommandSourceStack>)ctx, IntegerArgumentType.getInteger((CommandContext)ctx, (String)"form")))))).then(Commands.literal((String)"giveprop").then(Commands.argument((String)"prop", (ArgumentType)StringArgumentType.word()).suggests((ctx, builder) -> {
            for (DomainMasteryProperties p : DomainMasteryProperties.values()) {
                builder.suggest(p.name().toLowerCase());
            }
            return builder.buildFuture();
        }).then(Commands.argument((String)"level", (ArgumentType)IntegerArgumentType.integer((int)0, (int)10)).executes(ctx -> DomainMasteryCommands.giveProp((CommandContext<CommandSourceStack>)ctx, StringArgumentType.getString((CommandContext)ctx, (String)"prop"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"level"))))))).then(Commands.literal((String)"setnegative").then(Commands.argument((String)"prop", (ArgumentType)StringArgumentType.word()).suggests((ctx, builder) -> {
            for (DomainMasteryProperties p : DomainMasteryProperties.values()) {
                if (!p.supportsNegativeModify()) continue;
                builder.suggest(p.name().toLowerCase());
            }
            return builder.buildFuture();
        }).then(Commands.argument((String)"level", (ArgumentType)IntegerArgumentType.integer((int)-5, (int)0)).executes(ctx -> DomainMasteryCommands.setNegative((CommandContext<CommandSourceStack>)ctx, StringArgumentType.getString((CommandContext)ctx, (String)"prop"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"level"))))))).then(Commands.literal((String)"reset").executes(ctx -> DomainMasteryCommands.resetAll((CommandContext<CommandSourceStack>)ctx)))).then(Commands.literal((String)"info").executes(ctx -> DomainMasteryCommands.showInfo((CommandContext<CommandSourceStack>)ctx))));
    }

    /**
     * Performs add xp for this addon component.
     * @param ctx context data supplied by the current callback or network pipeline.
     * @param amount amount used by this method.
     * @return the resulting add xp value.
     */
    private static int addXP(CommandContext<CommandSourceStack> ctx, double amount) {
        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (player == null) {
            return 0;
        }
        DomainMasteryCommands.withData(player, data -> {
            int prev = data.getDomainMasteryLevel();
            data.addDomainXP(amount);
            int next = data.getDomainMasteryLevel();
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.gold("\u2726 Added " + (amount >= 0.0 ? "+" : "") + (int)amount + " Domain XP  \u2502  Total: " + (int)data.getDomainXP() + " XP  \u2502  Level: " + next + (next > prev ? " \u2605 LEVEL UP!" : "")), true);
            if (next > prev) {
                ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.aqua("\u2605 +1 Property Point earned!"), true);
            }
        });
        return 1;
    }

    /**
     * Updates level for the current addon state.
     * @param ctx context data supplied by the current callback or network pipeline.
     * @param level level value used by this operation.
     * @return the resulting set level value.
     */
    private static int setLevel(CommandContext<CommandSourceStack> ctx, int level) {
        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (player == null) {
            return 0;
        }
        DomainMasteryCommands.withData(player, data -> {
            data.setDomainMasteryLevel(level);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.gold("\u2726 Domain Mastery level set to " + level), true);
        });
        return 1;
    }

    /**
     * Performs add points for this addon component.
     * @param ctx context data supplied by the current callback or network pipeline.
     * @param amount amount used by this method.
     * @return the resulting add points value.
     */
    private static int addPoints(CommandContext<CommandSourceStack> ctx, int amount) {
        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (player == null) {
            return 0;
        }
        DomainMasteryCommands.withData(player, data -> {
            data.setDomainPropertyPoints(data.getDomainPropertyPoints() + amount);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.gold("\u2726 +" + amount + " Property Points  \u2502  Total: " + data.getDomainPropertyPoints()), true);
        });
        return 1;
    }

    /**
     * Updates form for the current addon state.
     * @param ctx context data supplied by the current callback or network pipeline.
     * @param form form used by this method.
     * @return the resulting set form value.
     */
    private static int setForm(CommandContext<CommandSourceStack> ctx, int form) {
        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (player == null) {
            return 0;
        }
        String[] names = new String[]{"Incomplete", "Closed", "Open"};
        DomainMasteryCommands.withData(player, data -> {
            data.setDomainTypeSelected(form);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.gold("\u2726 Domain form set to " + names[form]), true);
        });
        return 1;
    }

    /**
     * Performs give prop for this addon component.
     * @param ctx context data supplied by the current callback or network pipeline.
     * @param propName property identifier involved in this operation.
     * @param level level value used by this operation.
     * @return the resulting give prop value.
     */
    private static int giveProp(CommandContext<CommandSourceStack> ctx, String propName, int level) {
        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (player == null) {
            return 0;
        }
        DomainMasteryProperties prop = null;
        for (DomainMasteryProperties p : DomainMasteryProperties.values()) {
            if (!p.name().equalsIgnoreCase(propName)) continue;
            prop = p;
            break;
        }
        if (prop == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure((Component)DomainMasteryCommands.text("\u00d7 Unknown property: " + propName + "  \u2502  Valid: ce_drain, bf_chance, rct_heal, blind, slow, duration, radius, clash_power", ChatFormatting.RED));
            return 0;
        }
        DomainMasteryProperties target = prop;
        DomainMasteryCommands.withData(player, data -> {
            data.setPropertyLevel(target, Math.max(0, Math.min(target.getMaxLevel(), level)));
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.green("\u2714 " + target.name() + " = Lv." + data.getPropertyLevel(target)), true);
        });
        return 1;
    }

    /**
     * Updates negative for the current addon state.
     * @param ctx context data supplied by the current callback or network pipeline.
     * @param propName property identifier involved in this operation.
     * @param level level value used by this operation.
     * @return the resulting set negative value.
     */
    private static int setNegative(CommandContext<CommandSourceStack> ctx, String propName, int level) {
        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (player == null) {
            return 0;
        }
        DomainMasteryProperties prop = null;
        for (DomainMasteryProperties p : DomainMasteryProperties.values()) {
            if (!p.name().equalsIgnoreCase(propName)) continue;
            prop = p;
            break;
        }
        if (prop == null || !prop.supportsNegativeModify()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure((Component)DomainMasteryCommands.text("\u00d7 Negative property must be duration, radius, or clash_power", ChatFormatting.RED));
            return 0;
        }
        DomainMasteryProperties target = prop;
        int targetLevel = level;
        DomainMasteryCommands.withData(player, data -> {
            data.refundAllProperties();
            if (targetLevel < 0) {
                data.setDomainMasteryLevel(Math.max(data.getDomainMasteryLevel(), 5));
            }
            while (data.getNegativeLevel() < targetLevel && data.increaseNegative(target)) {
            }
            while (data.getNegativeLevel() > targetLevel && data.decreaseNegative(target)) {
            }
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.aqua("\u25c8 " + target.name() + " negative level set to " + data.getNegativeLevel()), true);
        });
        return 1;
    }

    /**
     * Performs reset all for this addon component.
     * @param ctx context data supplied by the current callback or network pipeline.
     * @return the resulting reset all value.
     */
    private static int resetAll(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (player == null) {
            return 0;
        }
        DomainMasteryCommands.withData(player, data -> {
            int before = data.getDomainPropertyPoints();
            data.refundAllProperties();
            int gained = data.getDomainPropertyPoints() - before;
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.aqua("\u21ba All properties and negative modify reset. +" + gained + " Property Points restored  \u2502  Total: " + data.getDomainPropertyPoints()), true);
        });
        return 1;
    }

    /**
     * Performs show info for this addon component.
     * @param ctx context data supplied by the current callback or network pipeline.
     * @return the resulting show info value.
     */
    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (player == null) {
            return 0;
        }
        DomainMasteryCommands.withData(player, data -> {
            String[] formNames = new String[]{"Incomplete", "Closed", "Open"};
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.empty(), false);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.gold("  \u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2510"), false);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.gold("  \u2502   \u2726  DOMAIN MASTERY INFO  \u2726"), false);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.gold("  \u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2514"), false);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.white("  Level: " + data.getDomainMasteryLevel() + "/5  \u2502  XP: " + (int)data.getDomainXP() + " / " + data.getXPToNextLevel()), false);
            int formIdx = Math.max(0, Math.min(2, data.getDomainTypeSelected()));
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.white("  Form: " + formNames[formIdx] + "  \u2502  Property Points: " + data.getDomainPropertyPoints()), false);
            String negativeInfo = data.hasNegativeModify() ? data.getNegativeProperty() + " " + data.getNegativeLevel() : "None";
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.white("  Negative Modify: " + negativeInfo), false);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.white("  Clash Bonus: " + String.format("%+.1f", data.getClashPowerBonus())), false);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.empty(), false);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> DomainMasteryCommands.dim("  Properties:"), false);
            for (DomainMasteryProperties p : DomainMasteryProperties.values()) {
                int lvl = data.getEffectivePropertyLevel(p);
                boolean locked = p.isLocked(data.getDomainMasteryLevel());
                Object valueText = lvl < 0 ? "debuff " + p.formatNegativeValue(Math.abs(lvl)) : p.formatLevelValue(lvl);
                String row = "    " + (locked ? "\ud83d\udd12" : "\u2605") + " " + p.name().replace("_", " ") + " \u2502 " + String.valueOf(lvl < 0 ? Integer.valueOf(lvl) : lvl + "/" + p.getMaxLevel()) + " \u2502 " + (String)valueText + (String)(locked ? " \u2502 [Unlock at Lv." + p.unlockLevel() + "]" : "");
                ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> lvl > 0 ? DomainMasteryCommands.green(row) : (lvl < 0 ? DomainMasteryCommands.text(row, ChatFormatting.RED) : DomainMasteryCommands.dim(row)), false);
            }
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.empty(), false);
        });
        return 1;
    }

    /**
     * Performs with data for this addon component.
     * @param player player instance involved in this operation.
     * @param action action used by this method.
     */
    private static void withData(ServerPlayer player, Consumer<DomainMasteryData> action) {
        player.getCapability(DomainMasteryCapabilityProvider.DOMAIN_MASTERY_CAPABILITY, null).ifPresent(data -> {
            action.accept((DomainMasteryData)data);
            data.syncToClient(player);
        });
    }

    /**
     * Performs gold for this addon component.
     * @param msg msg used by this method.
     * @return the resulting gold value.
     */
    private static MutableComponent gold(String msg) {
        return Component.literal((String)msg).withStyle(ChatFormatting.GOLD);
    }

    /**
     * Performs green for this addon component.
     * @param msg msg used by this method.
     * @return the resulting green value.
     */
    private static MutableComponent green(String msg) {
        return Component.literal((String)msg).withStyle(ChatFormatting.GREEN);
    }

    /**
     * Performs aqua for this addon component.
     * @param msg msg used by this method.
     * @return the resulting aqua value.
     */
    private static MutableComponent aqua(String msg) {
        return Component.literal((String)msg).withStyle(ChatFormatting.AQUA);
    }

    /**
     * Performs white for this addon component.
     * @param msg msg used by this method.
     * @return the resulting white value.
     */
    private static MutableComponent white(String msg) {
        return Component.literal((String)msg).withStyle(ChatFormatting.WHITE);
    }

    /**
     * Performs dim for this addon component.
     * @param msg msg used by this method.
     * @return the resulting dim value.
     */
    private static MutableComponent dim(String msg) {
        return Component.literal((String)msg).withStyle(ChatFormatting.GRAY);
    }

    /**
     * Performs text for this addon component.
     * @param msg msg used by this method.
     * @param c c used by this method.
     * @return the resulting text value.
     */
    private static MutableComponent text(String msg, ChatFormatting c) {
        return Component.literal((String)msg).withStyle(c);
    }
}

