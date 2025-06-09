package dev.mrduck;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record Quest(
    String id, 
    String name, 
    String description,
    int target,
    QuestType type,
    List<ItemStack> rewards,
    String punishment
) {
    public enum QuestType {
        BREAK_BLOCKS,
        COLLECT_ITEMS,
        CRAFT_ITEMS,
        TRAVEL_DISTANCE,
        FISH,
        KILL_MOBS,
        MINE_ORES,
        FARM_CROPS
    }
}