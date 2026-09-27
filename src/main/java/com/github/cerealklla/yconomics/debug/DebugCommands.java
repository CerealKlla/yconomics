package com.github.cerealklla.yconomics.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.github.cerealklla.yconomics.api.Yconomics;
import com.github.cerealklla.yconomics.currency.CoinPurseContents;
import com.github.cerealklla.yconomics.registration.ModItems;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * DEBUG ONLY -- lets an operator jump the Coin Purse straight to a specific tier and/or nugget
 * count for testing (e.g. checking how the tier/count decorator text looks with several hundred or
 * thousand nuggets) instead of grinding out real trades. Same "deliberately no permission gate,
 * remove or gate more strictly before any real release" precedent as Lyfe's own {@code
 * debug.DebugCommands} -- see that class's Javadoc for the full reasoning.
 *
 * <p>{@code /yconomics purse tier <n> [target]} sets the tier directly (bypassing {@link
 * Yconomics#increaseCoinPurseTierTo}'s never-lowers guarantee, since this is meant to freely move
 * the tier in either direction for testing). {@code /yconomics purse add <count> [target]} adds
 * nuggets directly into the purse's contents, up to the current tier's capacity.
 */
public final class DebugCommands {

    private DebugCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var tierWithTarget = Commands.argument("target", EntityArgument.player())
                .executes(ctx -> setTier(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "tier"), EntityArgument.getPlayer(ctx, "target")));
        var tier = Commands.literal("tier")
                .then(Commands.argument("tier", IntegerArgumentType.integer(0, Yconomics.MAX_COIN_PURSE_TIER))
                        .executes(ctx -> setTier(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "tier"), playerOrSelf(ctx.getSource())))
                        .then(tierWithTarget));

        var addWithTarget = Commands.argument("target", EntityArgument.player())
                .executes(ctx -> addNuggets(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "count"), EntityArgument.getPlayer(ctx, "target")));
        var add = Commands.literal("add")
                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .executes(ctx -> addNuggets(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "count"), playerOrSelf(ctx.getSource())))
                        .then(addWithTarget));

        dispatcher.register(Commands.literal("yconomics")
                .requires(source -> true) // deliberately no permission gate -- see class doc
                .then(Commands.literal("purse").then(tier).then(add)));
    }

    private static ServerPlayer playerOrSelf(CommandSourceStack source) {
        return source.getPlayer();
    }

    private static int setTier(CommandSourceStack source, int tier, ServerPlayer target) {
        Yconomics.setCoinPurseTier(target, tier);
        source.sendSuccess(() -> Component.literal(
                "Set " + target.getName().getString() + "'s Coin Purse tier to T" + tier), true);
        return 1;
    }

    private static int addNuggets(CommandSourceStack source, int count, ServerPlayer target) {
        ItemStack purse = findPurse(target);
        if (purse == null) {
            source.sendFailure(Component.literal(target.getName().getString() + " doesn't have a Coin Purse."));
            return 0;
        }

        int capacity = Yconomics.coinPurseCapacity(Yconomics.getCoinPurseTier(target));
        CoinPurseContents contents = purse.getOrDefault(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.EMPTY);
        ItemStack incoming = new ItemStack(Items.GOLD_NUGGET, count);
        CoinPurseContents.InsertResult result = contents.insert(incoming, capacity);
        purse.set(ModItems.COIN_PURSE_CONTENTS, result.contents());

        int added = result.inserted();
        source.sendSuccess(() -> Component.literal(
                "Added " + added + " nugget(s) to " + target.getName().getString() + "'s Coin Purse (now "
                        + result.contents().totalCount() + "/" + capacity + ")."), true);
        return added;
    }

    /** Mirrors {@code currency.CoinPurseListener}'s own private lookup -- always the one purse each player carries. */
    private static ItemStack findPurse(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.COIN_PURSE.get())) {
                return stack;
            }
        }
        return null;
    }
}
