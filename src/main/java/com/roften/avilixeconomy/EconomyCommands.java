package com.roften.avilixeconomy;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.roften.avilixeconomy.config.AvilixEconomyCommonConfig;
import com.roften.avilixeconomy.database.DatabaseManager;
import com.roften.avilixeconomy.pricing.MinPriceManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.roften.avilixeconomy.trade.TradeManager;
import com.roften.avilixeconomy.commission.CommissionManager;
import com.roften.avilixeconomy.util.MoneyUtils;

public class EconomyCommands {

    // === Подсказки ников игроков ===
    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (ctx, builder) -> {
        ctx.getSource().getServer().getPlayerList().getPlayers()
                .forEach(p -> builder.suggest(p.getName().getString()));
        return builder.buildFuture();
    };

    private static java.util.UUID getServerAccountUuid() {
        try {
            return java.util.UUID.fromString(AvilixEconomyCommonConfig.ECONOMY.serverAccountUuid.get());
        } catch (Exception e) {
            return java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
    }

    private static String getServerAccountName() {
        try {
            return AvilixEconomyCommonConfig.ECONOMY.serverAccountName.get();
        } catch (Exception e) {
            return "SERVER";
        }
    }

    /** Resolves online player UUID by name, otherwise tries DB lookup (offline). */
    private static java.util.UUID resolveUuidByName(CommandSourceStack src, String name) {
        if (name == null || name.isBlank()) return null;
        ServerPlayer online = src.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        return DatabaseManager.getUuidByName(name);
    }

    
public static void register(RegisterCommandsEvent event) {

    event.getDispatcher().register(
            Commands.literal("eco")

                    // ===== /eco help =====
                    .then(Commands.literal("help")
                            .executes(ctx -> {
                                ctx.getSource().sendSystemMessage(Component.translatable("msg.avilixeconomy.eco.help"));
                                return Command.SINGLE_SUCCESS;
                            })
                    )

                    // ===== /eco balance =====
                    .then(Commands.literal("balance")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
                                double bal = EconomyData.getBalance(player.getUUID());
                                player.sendSystemMessage(Component.literal("Ваш баланс: " + MoneyUtils.formatSmart(bal)));
                                return Command.SINGLE_SUCCESS;
                            })
                    )

                    // ===== /eco history [page] =====
                    .then(Commands.literal("history")
                            .executes(ctx -> showHistory(ctx.getSource(), null, 1))
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                    .executes(ctx -> showHistory(ctx.getSource(), null, IntegerArgumentType.getInteger(ctx, "page")))
                            )
                    )

                    // ===== /eco top [page] =====
                    .then(Commands.literal("top")
                            .executes(ctx -> showTop(ctx.getSource(), 1))
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                    .executes(ctx -> showTop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))
                            )
                    )

                    // ===== /eco admin ... (all admin commands) =====
                    .then(Commands.literal("admin")
                            .requires(src -> src.hasPermission(2))

                            // /eco admin balance <player>
                            .then(Commands.literal("balance")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(PLAYER_SUGGESTIONS)
                                            .executes(ctx -> {
                                                String name = StringArgumentType.getString(ctx, "player");
                                                java.util.UUID uuid = resolveUuidByName(ctx.getSource(), name);
                                                if (uuid == null) {
                                                    ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден (нет в онлайне и нет записи в БД)"));
                                                    return 0;
                                                }
                                                double bal = EconomyData.getBalance(uuid);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("Баланс " + name + ": " + MoneyUtils.formatSmart(bal)),
                                                        false
                                                );
                                                return Command.SINGLE_SUCCESS;
                                            })
                                    )
                            )

                            // /eco admin history <player> [page]
                            .then(Commands.literal("history")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(PLAYER_SUGGESTIONS)
                                            .executes(ctx -> showHistory(ctx.getSource(), StringArgumentType.getString(ctx, "player"), 1))
                                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> showHistory(
                                                            ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "player"),
                                                            IntegerArgumentType.getInteger(ctx, "page")
                                                    ))
                                            )
                                    )
                            )

                            // /eco admin server ...
                            .then(Commands.literal("server")
                                    .then(Commands.literal("balance")
                                            .executes(ctx -> {
                                                java.util.UUID su = getServerAccountUuid();
                                                double bal = EconomyData.getBalance(su);
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("Серверный баланс (" + getServerAccountName() + "): " + MoneyUtils.formatSmart(bal)), false);
                                                return Command.SINGLE_SUCCESS;
                                            })
                                    )
                                    .then(Commands.literal("set")
                                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                                    .executes(ctx -> {
                                                        double amount = MoneyUtils.round2(DoubleArgumentType.getDouble(ctx, "amount"));
                                                        java.util.UUID su = getServerAccountUuid();
                                                        EconomyData.setBalance(su, amount, getServerAccountName());
                                                        ctx.getSource().sendSuccess(() -> Component.literal("Серверный баланс установлен: " + MoneyUtils.formatSmart(amount)), true);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                                    .then(Commands.literal("add")
                                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                                    .executes(ctx -> {
                                                        double amount = MoneyUtils.round2(DoubleArgumentType.getDouble(ctx, "amount"));
                                                        java.util.UUID su = getServerAccountUuid();
                                                        EconomyData.addBalance(su, amount);
                                                        ctx.getSource().sendSuccess(() -> Component.literal("Добавлено серверу: " + MoneyUtils.formatSmart(amount)), true);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                                    .then(Commands.literal("remove")
                                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                                    .executes(ctx -> {
                                                        double amount = MoneyUtils.round2(DoubleArgumentType.getDouble(ctx, "amount"));
                                                        java.util.UUID su = getServerAccountUuid();
                                                        EconomyData.removeBalance(su, amount);
                                                        ctx.getSource().sendSuccess(() -> Component.literal("Снято с сервера: " + MoneyUtils.formatSmart(amount)), true);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                            )

                            // /eco admin set|add|remove ...
                            .then(Commands.literal("set")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(PLAYER_SUGGESTIONS)
                                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                                    .executes(ctx -> {
                                                        String name = StringArgumentType.getString(ctx, "player");
                                                        double amount = MoneyUtils.round2(DoubleArgumentType.getDouble(ctx, "amount"));

                                                        java.util.UUID uuid = resolveUuidByName(ctx.getSource(), name);
                                                        if (uuid == null) {
                                                            ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден (нет в онлайне и нет записи в БД)"));
                                                            return 0;
                                                        }

                                                        EconomyData.setBalance(uuid, amount, name);
                                                        ctx.getSource().sendSystemMessage(Component.literal("Баланс изменён на " + MoneyUtils.formatSmart(amount)));
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                            )

                            .then(Commands.literal("add")
                                    // /eco admin add all <amount>
                                    .then(Commands.literal("all")
                                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                                    .executes(ctx -> {
                                                        double amount = MoneyUtils.round2(DoubleArgumentType.getDouble(ctx, "amount"));
                                                        java.util.UUID serverUuid = getServerAccountUuid();
                                                        int changed = DatabaseManager.addBalanceToAll(amount, serverUuid);

                                                        // refresh online players cache + send packets
                                                        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
                                                            try {
                                                                EconomyData.getBalance(p.getUUID());
                                                                EconomyData.sendBalanceUpdateToPlayer(p);
                                                            } catch (Exception ignored) {}
                                                        });

                                                        ctx.getSource().sendSuccess(() ->
                                                                Component.literal("Добавлено всем игрокам: " + MoneyUtils.formatSmart(amount) + " (изменено записей: " + changed + ")"),
                                                                true);

                                                        // notify online players
                                                        String actorName = ctx.getSource().getTextName();
                                                        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
                                                            p.sendSystemMessage(Component.translatable("msg.avilixeconomy.eco.added_to_you", MoneyUtils.formatSmart(amount), actorName));
                                                        });
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                                    // /eco admin add <player> <amount>
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(PLAYER_SUGGESTIONS)
                                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                                    .executes(ctx -> {
                                                        String name = StringArgumentType.getString(ctx, "player");
                                                        double amount = MoneyUtils.round2(DoubleArgumentType.getDouble(ctx, "amount"));

                                                        java.util.UUID uuid = resolveUuidByName(ctx.getSource(), name);
                                                        if (uuid == null) {
                                                            ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден (нет в онлайне и нет записи в БД)"));
                                                            return 0;
                                                        }

                                                        EconomyData.addBalance(uuid, amount);
                                                        ctx.getSource().sendSystemMessage(Component.literal("Добавлено: " + MoneyUtils.formatSmart(amount)));

                                                        // notify target player if online
                                                        var target = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
                                                        if (target != null) {
                                                            String actorName = ctx.getSource().getTextName();
                                                            target.sendSystemMessage(Component.translatable("msg.avilixeconomy.eco.added_to_you", MoneyUtils.formatSmart(amount), actorName));
                                                        }
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                            )

                            .then(Commands.literal("remove")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(PLAYER_SUGGESTIONS)
                                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                                    .executes(ctx -> {
                                                        String name = StringArgumentType.getString(ctx, "player");
                                                        double amount = MoneyUtils.round2(DoubleArgumentType.getDouble(ctx, "amount"));

                                                        java.util.UUID uuid = resolveUuidByName(ctx.getSource(), name);
                                                        if (uuid == null) {
                                                            ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден (нет в онлайне и нет записи в БД)"));
                                                            return 0;
                                                        }

                                                        EconomyData.removeBalance(uuid, amount);
                                                        ctx.getSource().sendSystemMessage(Component.literal("Снято: " + MoneyUtils.formatSmart(amount)));
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                            )

                            // /eco admin commission ...
                            .then(Commands.literal("commission")
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
                                    .then(Commands.literal("owner")
                                            .then(Commands.argument("player", StringArgumentType.word())
                                                    .suggests(PLAYER_SUGGESTIONS)
                                                    .then(Commands.literal("sell")
                                                            .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                                    .executes(ctx -> {
                                                                        String name = StringArgumentType.getString(ctx, "player");
                                                                        java.util.UUID uuid = resolveUuidByName(ctx.getSource(), name);
                                                                        if (uuid == null) {
                                                                            ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден (нет в онлайне и нет записи в БД)"));
                                                                            return 0;
                                                                        }
                                                                        double pct = DoubleArgumentType.getDouble(ctx, "percent");
                                                                        int bps = (int) Math.round(pct * 100.0);
                                                                        CommissionManager.setOwnerOverride(uuid, bps, null);
                                                                        ctx.getSource().sendSuccess(() ->
                                                                                Component.literal("Комиссия SELL для " + name + " установлена: " + pct + "%"),
                                                                                true);
                                                                        return Command.SINGLE_SUCCESS;
                                                                    })
                                                            )
                                                    )
                                                    .then(Commands.literal("buy")
                                                            .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                                    .executes(ctx -> {
                                                                        String name = StringArgumentType.getString(ctx, "player");
                                                                        java.util.UUID uuid = resolveUuidByName(ctx.getSource(), name);
                                                                        if (uuid == null) {
                                                                            ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден (нет в онлайне и нет записи в БД)"));
                                                                            return 0;
                                                                        }
                                                                        double pct = DoubleArgumentType.getDouble(ctx, "percent");
                                                                        int bps = (int) Math.round(pct * 100.0);
                                                                        CommissionManager.setOwnerOverride(uuid, null, bps);
                                                                        ctx.getSource().sendSuccess(() ->
                                                                                Component.literal("Комиссия BUY для " + name + " установлена: " + pct + "%"),
                                                                                true);
                                                                        return Command.SINGLE_SUCCESS;
                                                                    })
                                                            )
                                                    )
                                            )
                                    )
                                    .then(Commands.literal("clear")
                                            .then(Commands.argument("player", StringArgumentType.word())
                                                    .suggests(PLAYER_SUGGESTIONS)
                                                    .executes(ctx -> {
                                                        String name = StringArgumentType.getString(ctx, "player");
                                                        java.util.UUID uuid = resolveUuidByName(ctx.getSource(), name);
                                                        if (uuid == null) {
                                                            ctx.getSource().sendSystemMessage(Component.literal("Игрок не найден (нет в онлайне и нет записи в БД)"));
                                                            return 0;
                                                        }
                                                        boolean removed = CommissionManager.clearOwnerOverride(uuid);
                                                        ctx.getSource().sendSuccess(() ->
                                                                Component.literal(removed
                                                                        ? ("Оверрайд комиссии для " + name + " удалён.")
                                                                        : ("Оверрайда комиссии для " + name + " не было.")),
                                                                true);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                            )

                            // /eco admin minprice ...
                            .then(Commands.literal("minprice")
                                    .then(Commands.literal("reload")
                                            .executes(ctx -> {
                                                MinPriceManager.reload();
                                                String err = MinPriceManager.getLastError();
                                                if (err != null) {
                                                    ctx.getSource().sendFailure(Component.translatable("msg.avilixeconomy.minprice.reload_fail", err));
                                                    return 0;
                                                }
                                                ctx.getSource().sendSuccess(() -> Component.translatable("msg.avilixeconomy.minprice.reloaded"), false);
                                                return Command.SINGLE_SUCCESS;
                                            })
                                    )
                                    .then(Commands.literal("set")
                                            .then(Commands.argument("item", StringArgumentType.word())
                                                    .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                                                            .executes(ctx -> {
                                                                String itemStr = StringArgumentType.getString(ctx, "item");
                                                                ResourceLocation id = ResourceLocation.tryParse(itemStr);
                                                                if (id == null) {
                                                                    ctx.getSource().sendFailure(Component.translatable("msg.avilixeconomy.minprice.bad_item", itemStr));
                                                                    return 0;
                                                                }
                                                                double price = DoubleArgumentType.getDouble(ctx, "price");
                                                                try {
                                                                    MinPriceManager.setMin(id, price);
                                                                } catch (Exception ex) {
                                                                    ctx.getSource().sendFailure(Component.translatable(
                                                                            "msg.avilixeconomy.minprice.save_fail",
                                                                            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                                                                    ));
                                                                    return 0;
                                                                }
                                                                ctx.getSource().sendSuccess(() ->
                                                                        Component.translatable("msg.avilixeconomy.minprice.set_ok", id.toString(), MoneyUtils.formatSmart(price)),
                                                                        false);
                                                                return Command.SINGLE_SUCCESS;
                                                            })
                                                    )
                                            )
                                    )
                                    // alias: add
                                    .then(Commands.literal("add")
                                            .then(Commands.argument("item", StringArgumentType.word())
                                                    .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                                                            .executes(ctx -> {
                                                                String itemStr = StringArgumentType.getString(ctx, "item");
                                                                ResourceLocation id = ResourceLocation.tryParse(itemStr);
                                                                if (id == null) {
                                                                    ctx.getSource().sendFailure(Component.translatable("msg.avilixeconomy.minprice.bad_item", itemStr));
                                                                    return 0;
                                                                }
                                                                double price = DoubleArgumentType.getDouble(ctx, "price");
                                                                try {
                                                                    MinPriceManager.setMin(id, price);
                                                                } catch (Exception ex) {
                                                                    ctx.getSource().sendFailure(Component.translatable(
                                                                            "msg.avilixeconomy.minprice.save_fail",
                                                                            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                                                                    ));
                                                                    return 0;
                                                                }
                                                                ctx.getSource().sendSuccess(() ->
                                                                        Component.translatable("msg.avilixeconomy.minprice.set_ok", id.toString(), MoneyUtils.formatSmart(price)),
                                                                        false);
                                                                return Command.SINGLE_SUCCESS;
                                                            })
                                                    )
                                            )
                                    )
                                    .then(Commands.literal("remove")
                                            .then(Commands.argument("item", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        String itemStr = StringArgumentType.getString(ctx, "item");
                                                        ResourceLocation id = ResourceLocation.tryParse(itemStr);
                                                        if (id == null) {
                                                            ctx.getSource().sendFailure(Component.translatable("msg.avilixeconomy.minprice.bad_item", itemStr));
                                                            return 0;
                                                        }
                                                        try {
                                                            MinPriceManager.removeMin(id);
                                                        } catch (Exception ex) {
                                                            ctx.getSource().sendFailure(Component.translatable(
                                                                    "msg.avilixeconomy.minprice.save_fail",
                                                                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                                                            ));
                                                            return 0;
                                                        }
                                                        ctx.getSource().sendSuccess(() -> Component.translatable("msg.avilixeconomy.minprice.remove_ok", id.toString()), false);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                                    .then(Commands.literal("get")
                                            .then(Commands.argument("item", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        String itemStr = StringArgumentType.getString(ctx, "item");
                                                        ResourceLocation id = ResourceLocation.tryParse(itemStr);
                                                        if (id == null) {
                                                            ctx.getSource().sendFailure(Component.translatable("msg.avilixeconomy.minprice.bad_item", itemStr));
                                                            return 0;
                                                        }
                                                        double v = MinPriceManager.getMinPerItem(id);
                                                        ctx.getSource().sendSuccess(() -> Component.translatable("msg.avilixeconomy.minprice.get_ok", id.toString(), MoneyUtils.formatSmart(v)), false);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                            )
                                    )
                                    .then(Commands.literal("list")
                                            .executes(ctx -> showMinPriceList(ctx.getSource(), 1))
                                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> showMinPriceList(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))
                                            )
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

    private static int showTop(CommandSourceStack src, int page) {
        final int perPage = 10;
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * perPage;

        java.util.UUID serverUuid = getServerAccountUuid();
        DatabaseManager.BalancesPage top = DatabaseManager.getBalancesTopPage(perPage, offset, serverUuid);

        if (top.rows().isEmpty()) {
            src.sendSystemMessage(Component.literal("Топ пуст."));
            return Command.SINGLE_SUCCESS;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Топ по балансу (страница ").append(safePage).append("):\n");
        for (int i = 0; i < top.rows().size(); i++) {
            var row = top.rows().get(i);
            int place = offset + i + 1;
            sb.append(place)
                    .append(". ")
                    .append(row.name())
                    .append(" — ")
                    .append(MoneyUtils.formatSmart(row.balance()))
                    .append("\n");
        }
        if (top.hasMore()) {
            sb.append("\nЕсть следующая страница: /eco top ").append(safePage + 1);
        }

        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return Command.SINGLE_SUCCESS;
    }

    
private static int showMinPriceList(CommandSourceStack src, int page) {
    final int perPage = 10;
    int safePage = Math.max(1, page);
    int offset = (safePage - 1) * perPage;

    java.util.Map<ResourceLocation, Double> map = MinPriceManager.snapshot();
    if (map.isEmpty()) {
        src.sendSystemMessage(Component.translatable("msg.avilixeconomy.minprice.list_empty"));
        return Command.SINGLE_SUCCESS;
    }

    java.util.List<java.util.Map.Entry<ResourceLocation, Double>> entries = new java.util.ArrayList<>(map.entrySet());
    entries.sort(java.util.Comparator.comparing(e -> e.getKey().toString()));

    int from = Math.min(offset, entries.size());
    int to = Math.min(offset + perPage, entries.size());
    java.util.List<java.util.Map.Entry<ResourceLocation, Double>> pageEntries = entries.subList(from, to);
    boolean hasMore = to < entries.size();

    StringBuilder sb = new StringBuilder();
    sb.append(Component.translatable("msg.avilixeconomy.minprice.list_header", safePage).getString()).append("\n");
    for (java.util.Map.Entry<ResourceLocation, Double> e : pageEntries) {
        sb.append(" - ").append(e.getKey()).append(" = ").append(MoneyUtils.formatSmart(e.getValue())).append("\n");
    }
    if (hasMore) {
        sb.append(Component.translatable("msg.avilixeconomy.minprice.list_more", safePage + 1).getString());
    }
    src.sendSystemMessage(Component.literal(sb.toString()));
    return Command.SINGLE_SUCCESS;
}


private static int showHistory(CommandSourceStack src, String playerName, int page) {
        final int perPage = 10;
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * perPage;

        java.util.UUID uuid;
        String displayName;
        try {
            if (playerName == null) {
                ServerPlayer p = src.getPlayer();
                uuid = p.getUUID();
                displayName = p.getName().getString();
            } else {
                uuid = resolveUuidByName(src, playerName);
                displayName = playerName;
            }
        } catch (Exception e) {
            src.sendSystemMessage(Component.literal("Эта команда доступна только игроку: /eco history"));
            return 0;
        }

        if (uuid == null) {
            src.sendSystemMessage(Component.literal("Игрок не найден (нет в онлайне и нет записи в БД)"));
            return 0;
        }

        // limit+1 to detect next page
        java.util.List<DatabaseManager.BalanceHistoryRow> rows = DatabaseManager.getBalanceHistory(uuid, perPage + 1, offset);
        boolean hasMore = rows.size() > perPage;
        if (hasMore) rows = rows.subList(0, perPage);

        if (rows.isEmpty()) {
            src.sendSystemMessage(Component.translatable("msg.avilixeconomy.eco.history_empty", displayName));
            return Command.SINGLE_SUCCESS;
        }

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("История баланса ").append(displayName).append(" (стр. ").append(safePage).append("):\n");
        for (var r : rows) {
            String time = "?";
            try {
                time = r.createdAt().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(fmt);
            } catch (Exception ignored) {}

            String sign = r.delta() >= 0 ? "+" : "";
            sb.append("[").append(time).append("] ")
                    .append(sign).append(MoneyUtils.formatSmart(r.delta()))
                    .append(" | ")
                    .append(MoneyUtils.formatSmart(r.balanceBefore()))
                    .append(" -> ")
                    .append(MoneyUtils.formatSmart(r.balanceAfter()))
                    .append(" | ")
                    .append(r.reason());
            if (r.actorName() != null && !r.actorName().isBlank()) {
                sb.append(" (").append(r.actorName()).append(")");
            }
            sb.append("\n");
        }
        if (hasMore) {
            sb.append("\nЕсть следующая страница: /eco history ");
            if (playerName != null) sb.append(playerName).append(" ");
            sb.append(safePage + 1);
        }

        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return Command.SINGLE_SUCCESS;
    }
}
