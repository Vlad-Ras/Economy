package com.roften.avilixeconomy.compat.ftbquests;

import com.roften.avilixeconomy.EconomyData;
import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.net.NotifyRewardMessage;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * FTB Quests reward: grants Nox (AvilixEconomy balance) to the player.
 */
public class NoxReward extends Reward {

    private int amount;

    public NoxReward(long id, Quest quest) {
        this(id, quest, 100);
    }

    public NoxReward(long id, Quest quest, int amount) {
        super(id, quest);
        this.amount = Math.max(1, amount);
    }

    @Override
    public RewardType getType() {
        // This RewardType is registered by our compat init.
        return FTBQuestsCompat.NOX_REWARD_TYPE;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putInt("amount", amount);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        amount = Math.max(1, nbt.getInt("amount"));
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(amount);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        amount = Math.max(1, buffer.readVarInt());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // This adds an editable integer field in the quest editor UI.
        config.addInt("amount", amount, v -> amount = Math.max(1, v), 100, 1, Integer.MAX_VALUE)
                .setNameKey("ftbquests.reward.avilixeconomy.nox");
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        // Log into economy balance history as a quest reward (LP permissions not involved).
        String questTitle = null;
        // IMPORTANT: FTB Quests API changes across versions. Calling Quest#getTitle() directly can
        // throw AbstractMethodError at runtime (linkage error) which would block reward payout.
        // Title is only used for logging, so resolve it reflectively and swallow *all* failures.
        try {
            Quest q = getQuest();
            if (q != null) {
                try {
                    Object maybeTitle = q.getClass().getMethod("getTitle").invoke(q);
                    if (maybeTitle instanceof Component c) {
                        questTitle = c.getString();
                    }
                } catch (Throwable ignored) {
                    // ignore; quest title is optional
                }
            }
        } catch (Throwable ignored) {
            // ignore; quest title is optional
        }

        EconomyData.addBalance(player.getUUID(), amount,
                "QUEST_REWARD",
                null,
                questTitle,
                null);

        if (notify) {
            Component msg = Component.translatable("ftbquests.reward.avilixeconomy.nox")
                    .append(": ")
                    .append(Component.literal("+" + amount).withStyle(ChatFormatting.GREEN));
            NetworkManager.sendToPlayer(player, new NotifyRewardMessage(id, msg, Icons.MONEY, disableRewardScreenBlur));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.reward.avilixeconomy.nox")
                .append(": ")
                .append(Component.literal("+" + amount).withStyle(ChatFormatting.GREEN));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public String getButtonText() {
        return "+" + amount;
    }
}
