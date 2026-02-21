package com.roften.avilixeconomy.compat.ftbquests;

import com.roften.avilixeconomy.AvilixEconomy;
import dev.ftb.mods.ftblibrary.icon.ImageIcon;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional FTB Quests integration.
 *
 * Registers a native reward type "Nox Reward" with an editable Amount field in the FTB Quests UI.
 */
public final class FTBQuestsCompat {

    private static boolean inited = false;

    public static RewardType NOX_REWARD_TYPE;

    private FTBQuestsCompat() {
    }

    public static void init() {
        if (inited) return;
        inited = true;

        // Register our reward type. Namespace is our modid to avoid collisions.
        NOX_REWARD_TYPE = RewardTypes.register(
                ResourceLocation.fromNamespaceAndPath(AvilixEconomy.MODID, "nox"),
                NoxReward::new,
                () -> ImageIcon.getIcon(ResourceLocation.fromNamespaceAndPath(AvilixEconomy.MODID, "icons/nox_reward"))
        );
    }
}
