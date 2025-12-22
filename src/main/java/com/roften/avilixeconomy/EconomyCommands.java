package com.roften.avilixeconomy;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.roften.avilixeconomy.trade.TradeManager;
import com.roften.avilixeconomy.commission.CommissionManager;

public class EconomyCommands {

    // === Подсказки ников игроков ===
    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (ctx, builder) -> {
        ctx.getSource().getServer().getPlayerList().getPlayers()
                .forEach(p -> builder.suggest(p.getName().getString()));
        return builder.buildFuture();
    };

    public static void register(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("eco")

                        // ===== /eco balance =====
                        .then(Commands.literal("balance")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    long bal = EconomyData.getBalance(player.getUUID());
                                    player.sendSystemMessage(Component.literal("Ваш баланс: " + bal));
                                    return Command.SINGLE_SUCCESS;
                                })
                                // ===== /eco balance <player> (admin) =====
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            long bal = EconomyData.getBalance(target.getUUID());
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Баланс " + target.getGameProfile().getName() + ": " + bal),
                                                    false
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )

                        // ===== /eco set <player> <amount> =====
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "player");
                                                    long amount = LongArgumentType.getLong(ctx, "amount");

                                                    ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                                    if (target == null) {
                                                        ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден"));
                                                        return 0;
                                                    }

                                                    EconomyData.setBalance(target.getUUID(), amount, name);
                                                    ctx.getSource().sendSystemMessage(Component.literal("Баланс изменён на " + amount));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )

                        // ===== /eco add <player> <amount> =====
                        .then(Commands.literal("add")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "player");
                                                    long amount = LongArgumentType.getLong(ctx, "amount");

                                                    ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                                    if (target == null) {
                                                        ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден"));
                                                        return 0;
                                                    }

                                                    EconomyData.addBalance(target.getUUID(), amount);
                                                    ctx.getSource().sendSystemMessage(Component.literal("Добавлено: " + amount));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )

                        // ===== /eco remove <player> <amount> =====
                        .then(Commands.literal("remove")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "player");
                                                    long amount = LongArgumentType.getLong(ctx, "amount");

                                                    ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                                    if (target == null) {
                                                        ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден"));
                                                        return 0;
                                                    }

                                                    EconomyData.removeBalance(target.getUUID(), amount);
                                                    ctx.getSource().sendSystemMessage(Component.literal("Снято: " + amount));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )

                        // ===== /eco pay <player> <amount> =====
                        .then(Commands.literal("pay")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(ctx -> {
                                                    ServerPlayer sender = ctx.getSource().getPlayer();
                                                    String name = StringArgumentType.getString(ctx, "player");
                                                    long amount = LongArgumentType.getLong(ctx, "amount");

                                                    ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                                    if (target == null) {
                                                        sender.sendSystemMessage(Component.literal("Игрок не найден"));
                                                        return 0;
                                                    }

                                                    // Сам себе платить нельзя
                                                    if (sender.getUUID().equals(target.getUUID())) {
                                                        sender.sendSystemMessage(Component.literal("Нельзя переводить деньги самому себе!"));
                                                        return 0;
                                                    }

                                                    long bal = EconomyData.getBalance(sender.getUUID());
                                                    if (bal < amount) {
                                                        sender.sendSystemMessage(Component.literal("Недостаточно средств!"));
                                                        return 0;
                                                    }

                                                    EconomyData.removeBalance(sender.getUUID(), amount);
                                                    EconomyData.addBalance(target.getUUID(), amount);

                                                    sender.sendSystemMessage(Component.literal(
                                                            "Вы отправили " + amount + " игроку " + name));

                                                    target.sendSystemMessage(Component.literal(
                                                            "Вы получили " + amount + " от " + sender.getName().getString()));

                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        // ===== /eco commission ... =====
                        .then(Commands.literal("commission")
                                .requires(src -> src.hasPermission(2))

                                // /eco commission get
                                .then(Commands.literal("get")
                                        .executes(ctx -> {
                                            int sellBps = com.roften.avilixeconomy.config.AvilixEconomyCommonConfig.COMMISSION.defaultSellBps.get();
                                            int buyBps = com.roften.avilixeconomy.config.AvilixEconomyCommonConfig.COMMISSION.defaultBuyBps.get();
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Комиссия (глобальная): SELL=" + (sellBps / 100.0) + "%, BUY=" + (buyBps / 100.0) + "%"),
                                                    false);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )

                                // /eco commission info <player>
                                .then(Commands.literal("info")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    int sellBps = CommissionManager.getSellBpsForOwner(p.getUUID());
                                                    int buyBps = CommissionManager.getBuyBpsForOwner(p.getUUID());
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("Комиссия для " + p.getGameProfile().getName() + ": SELL=" + (sellBps / 100.0) + "%, BUY=" + (buyBps / 100.0) + "%"),
                                                            false);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )

                                // /eco commission global ...
                                .then(Commands.literal("global")
                                        .then(Commands.literal("sell")
                                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                        .executes(ctx -> {
                                                            double pct = DoubleArgumentType.getDouble(ctx, "percent");
                                                            int bps = (int) Math.round(pct * 100.0);
                                                            CommissionManager.setGlobalSellBps(bps);
                                                            ctx.getSource().sendSuccess(() ->
                                                                    Component.literal("Глобальная комиссия SELL установлена: " + pct + "%"),
                                                                    true);
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )
                                        )
                                        .then(Commands.literal("buy")
                                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                        .executes(ctx -> {
                                                            double pct = DoubleArgumentType.getDouble(ctx, "percent");
                                                            int bps = (int) Math.round(pct * 100.0);
                                                            CommissionManager.setGlobalBuyBps(bps);
                                                            ctx.getSource().sendSuccess(() ->
                                                                    Component.literal("Глобальная комиссия BUY установлена: " + pct + "%"),
                                                                    true);
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )
                                        )
                                )

                                // /eco commission owner <player> ...
                                .then(Commands.literal("owner")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.literal("sell")
                                                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                                .executes(ctx -> {
                                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                                    double pct = DoubleArgumentType.getDouble(ctx, "percent");
                                                                    int bps = (int) Math.round(pct * 100.0);
                                                                    CommissionManager.setOwnerOverride(p.getUUID(), bps, null);
                                                                    ctx.getSource().sendSuccess(() ->
                                                                            Component.literal("Комиссия SELL для " + p.getGameProfile().getName() + " установлена: " + pct + "%"),
                                                                            true);
                                                                    return Command.SINGLE_SUCCESS;
                                                                })
                                                        )
                                                )
                                                .then(Commands.literal("buy")
                                                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                                .executes(ctx -> {
                                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                                    double pct = DoubleArgumentType.getDouble(ctx, "percent");
                                                                    int bps = (int) Math.round(pct * 100.0);
                                                                    CommissionManager.setOwnerOverride(p.getUUID(), null, bps);
                                                                    ctx.getSource().sendSuccess(() ->
                                                                            Component.literal("Комиссия BUY для " + p.getGameProfile().getName() + " установлена: " + pct + "%"),
                                                                            true);
                                                                    return Command.SINGLE_SUCCESS;
                                                                })
                                                        )
                                                )
                                        )
                                )

                                // /eco commission clear <player>
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> {
                                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                                    boolean removed = CommissionManager.clearOwnerOverride(p.getUUID());
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal(removed
                                                                    ? ("Оверрайд комиссии для " + p.getGameProfile().getName() + " удалён.")
                                                                    : ("Оверрайда комиссии для " + p.getGameProfile().getName() + " не было.")),
                                                            true);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )

        );

        // =============================
        // /trade - обмен предметами и валютой
        // =============================
        event.getDispatcher().register(
                Commands.literal("trade")
                        // /trade <player>
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_SUGGESTIONS)
                                .executes(ctx -> {
                                    ServerPlayer sender = ctx.getSource().getPlayer();
                                    String name = StringArgumentType.getString(ctx, "player");

                                    ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                    if (target == null) {
                                        sender.sendSystemMessage(Component.literal("Игрок не найден"));
                                        return 0;
                                    }

                                    TradeManager.sendRequest(sender, target);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )

                        // /trade accept <player>
                        .then(Commands.literal("accept")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .executes(ctx -> {
                                            ServerPlayer receiver = ctx.getSource().getPlayer();
                                            String name = StringArgumentType.getString(ctx, "player");
                                            ServerPlayer sender = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            if (sender == null) {
                                                receiver.sendSystemMessage(Component.literal("Игрок не найден"));
                                                return 0;
                                            }

                                            TradeManager.acceptRequest(receiver, sender);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )

                        // /trade deny <player>
                        .then(Commands.literal("deny")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .executes(ctx -> {
                                            ServerPlayer receiver = ctx.getSource().getPlayer();
                                            String name = StringArgumentType.getString(ctx, "player");
                                            ServerPlayer sender = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            if (sender == null) {
                                                receiver.sendSystemMessage(Component.literal("Игрок не найден"));
                                                return 0;
                                            }

                                            TradeManager.denyRequest(receiver, sender);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )

                        // /trade cancel - отменить активный трейд (если открыт)
                        .then(Commands.literal("cancel")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    TradeManager.cancelActiveTrade(player);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }
}
