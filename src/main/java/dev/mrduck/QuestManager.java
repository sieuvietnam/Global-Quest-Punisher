package dev.mrduck;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class QuestManager implements Listener {

    private final GlobalQuestPunisher plugin;
    private final File dataFile;
    private final YamlConfiguration dataConfig;

    // Global quest state - synchronized across all players
    private final Map<UUID, Integer> globalProgress = new HashMap<>();
    private final Set<UUID> completedPlayers = new HashSet<>();
    private final Set<UUID> punishedPlayers = new HashSet<>();
    
    private final Map<UUID, Long> lastSaveTime = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    
    private static final long SAVE_INTERVAL = 2 * 60 * 1000; // Save every 2 minutes
    private static final long ONE_WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000; // 7 days
    // private static final long ONE_WEEK_MILLIS = 5 * 60 * 1000; // 5 minutes for testing
    
    private Quest currentGlobalQuest;
    private long questStartTime;
    private int pendingUpdates = 0;

    // Harder quest pool with better rewards and worse punishments
    private final List<Quest> hardQuestPool = Arrays.asList(
        new Quest("mine_500_diamond_ore", "Khai thác 500 quặng kim cương", 
                "Đào sâu và tìm kiếm kim cương trong lòng đất!", 500,
                Quest.QuestType.MINE_ORES, createRewards("diamond_mining"), 
                "Bị nguyền rủa không thể đào được kim cương trong 3 ngày!"),
                
        new Quest("kill_1000_hostile_mobs", "Tiêu diệt 1000 quái vật", 
                "Bảo vệ thế giới khỏi những sinh vật nguy hiểm!", 1000,
                Quest.QuestType.KILL_MOBS, createRewards("mob_slayer"), 
                "Bị quái vật truy sát, nhận double damage từ mobs trong 7 ngày!"),
                
        new Quest("collect_2000_ancient_debris", "Thu thập 100 Ancient Debris",
                "Khám phá Nether và tìm kiếm kho báu cổ đại!", 100,
                Quest.QuestType.COLLECT_ITEMS, createRewards("netherite_master"), 
                "Bị cấm vào Nether và mất toàn bộ Netherite items!"),
                
        new Quest("craft_500_enchanted_books", "Chế tạo 200 sách enchant",
                "Trở thành pháp sư mạnh mẽ nhất server!", 200,
                Quest.QuestType.CRAFT_ITEMS, createRewards("enchanter"), 
                "Mất toàn bộ exp và không thể enchant trong 7 ngày!"),
                
        new Quest("travel_50000_blocks", "Du lịch 5,000 blocks",
                "Khám phá mọi ngóc ngách của thế giới!", 5000,
                Quest.QuestType.TRAVEL_DISTANCE, createRewards("explorer"), 
                "Bị slowness vĩnh viễn và không thể dùng phương tiện di chuyển!"),
                
        new Quest("farm_10000_crops", "Thu hoạch 1,000 cây trồng",
                "Trở thành nông dân xuất sắc nhất server!", 1000,
                Quest.QuestType.FARM_CROPS, createRewards("farmer"), 
                "Cây trồng sẽ chết ngay khi bạn trồng trong 7 ngày!"),
                
        new Quest("break_5000_obsidian", "Phá 500 khối obsidian",
                "Thử thách sức bền và kiên nhẫn của bạn!", 500,
                Quest.QuestType.BREAK_BLOCKS, createRewards("obsidian_breaker"), 
                "Pickaxe sẽ bị phá hủy ngay lập tức khi sử dụng!"),
                
        new Quest("fish_1000_treasure", "Câu được 1000 kho báu",
                "Trở thành ngư dân may mắn nhất thế giới!", 1000,
                Quest.QuestType.FISH, createRewards("treasure_hunter"), 
                "Chỉ câu được rác thải và không bao giờ câu được cá!")
    );

    public QuestManager(@NotNull GlobalQuestPunisher plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "GlobalQuestData.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadGlobalQuestData();
        
        if (currentGlobalQuest == null) {
            generateNewGlobalQuest();
        }
    }

    private List<ItemStack> createRewards(String rewardType) {
        List<ItemStack> rewards = new ArrayList<>();
        
        switch (rewardType) {
            case "diamond_mining" -> {
                rewards.add(createEnchantedItem(Material.DIAMOND_PICKAXE, "§6Siêu Cuốc Kim Cương", 
                    List.of("§7Hiệu quả V", "§7Bền vững III", "§7May mắn III"), 
                    Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 3)));
                rewards.add(new ItemStack(Material.DIAMOND, 64));
                rewards.add(new ItemStack(Material.EMERALD, 32));
            }
            case "mob_slayer" -> {
                rewards.add(createEnchantedItem(Material.NETHERITE_SWORD, "§4Thanh Kiếm Diệt Quái", 
                    List.of("§7Sắc bén V", "§7Đánh úp II", "§7Bền vững III"), 
                    Map.of(Enchantment.SHARPNESS, 5, Enchantment.SMITE, 2, Enchantment.UNBREAKING, 3)));
                rewards.add(new ItemStack(Material.TOTEM_OF_UNDYING, 5));
                rewards.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
            }
            case "netherite_master" -> {
                rewards.add(createEnchantedItem(Material.NETHERITE_CHESTPLATE, "§5Áo Giáp Cổ Đại", 
                    List.of("§7Bảo vệ IV", "§7Bền vững III", "§7Gai III"), 
                    Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.THORNS, 3)));
                rewards.add(new ItemStack(Material.NETHERITE_INGOT, 16));
                rewards.add(new ItemStack(Material.ANCIENT_DEBRIS, 32));
            }
            case "enchanter" -> {
                rewards.add(new ItemStack(Material.ENCHANTED_BOOK, 20));
                rewards.add(new ItemStack(Material.LAPIS_LAZULI, 256));
                rewards.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 128));
            }
            case "explorer" -> {
                rewards.add(createEnchantedItem(Material.ELYTRA, "§bCánh Thiên Thần", 
                    List.of("§7Bền vững III", "§7Tốc độ bay tối đa"), 
                    Map.of(Enchantment.UNBREAKING, 3)));
                rewards.add(new ItemStack(Material.FIREWORK_ROCKET, 256));
                rewards.add(new ItemStack(Material.ENDER_PEARL, 64));
            }
            case "farmer" -> {
                rewards.add(createEnchantedItem(Material.NETHERITE_HOE, "§2Cuốc Thần Thánh", 
                    List.of("§7Hiệu quả V", "§7Bền vững III", "§7May mắn III"), 
                    Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 3)));
                rewards.add(new ItemStack(Material.GOLDEN_CARROT, 64));
                rewards.add(new ItemStack(Material.GOLDEN_APPLE, 16));
            }
            case "obsidian_breaker" -> {
                rewards.add(createEnchantedItem(Material.NETHERITE_PICKAXE, "§8Cuốc Phá Hủy", 
                    List.of("§7Hiệu quả V", "§7Bền vững III", "§7Silk Touch"), 
                    Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.SILK_TOUCH, 1)));
                rewards.add(new ItemStack(Material.OBSIDIAN, 128));
                rewards.add(new ItemStack(Material.CRYING_OBSIDIAN, 64));
            }
            case "treasure_hunter" -> {
                rewards.add(createEnchantedItem(Material.FISHING_ROD, "§3Cần Câu Vàng", 
                    List.of("§7Vận may biển cả III", "§7Mồi III", "§7Bền vững III"), 
                    Map.of(Enchantment.LUCK_OF_THE_SEA, 3, Enchantment.LURE, 3, Enchantment.UNBREAKING, 3)));
                rewards.add(new ItemStack(Material.HEART_OF_THE_SEA, 3));
                rewards.add(new ItemStack(Material.NAUTILUS_SHELL, 32));
            }
        }
        
        return rewards;
    }

    private ItemStack createEnchantedItem(Material material, String name, List<String> lore, Map<Enchantment, Integer> enchants) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
        }
        
        return item;
    }

    public void startQuestTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long timeLeft = (questStartTime + ONE_WEEK_MILLIS) - now;
                
                if (timeLeft <= 0) {
                    // Time's up! Punish non-completers and start new quest
                    punishFailedPlayers();
                    generateNewGlobalQuest();
                } else {
                    // Send periodic warnings
                    sendTimeWarnings(timeLeft);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 20L * 60); // Check every minute
    }

    private void sendTimeWarnings(long timeLeft) {
        long hoursLeft = timeLeft / (60 * 60 * 1000);
        
        // Send warnings at specific intervals
        if (hoursLeft == 24 || hoursLeft == 12 || hoursLeft == 6 || hoursLeft == 1) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String timeStr = hoursLeft == 1 ? "1 giờ" : hoursLeft + " giờ";
                for (Player aplayer : Bukkit.getOnlinePlayers()) {
                    aplayer.sendMessage("§c⚠ CHỈ CÒN " + timeStr.toUpperCase() + " ĐỂ HOÀN THÀNH QUEST!");
                    aplayer.sendMessage("§e" + completedPlayers.size() + "/" + Bukkit.getOnlinePlayers().size() + " người chơi đã hoàn thành.");    
                }
                // Play warning sound
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                }
            });
        }
    }

    public void generateNewGlobalQuest() {
        // Select random quest
        currentGlobalQuest = hardQuestPool.get(ThreadLocalRandom.current().nextInt(hardQuestPool.size()));
        questStartTime = System.currentTimeMillis();
        
        // Reset all progress
        globalProgress.clear();
        completedPlayers.clear();
        punishedPlayers.clear();

        // Announce new quest to all players
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player aplayer : Bukkit.getOnlinePlayers()) {
                aplayer.sendMessage("§4§l=== NHIỆM VỤ HÀNG TUẦN MỚI! ===");
                aplayer.sendMessage("§6" + currentGlobalQuest.name());
                aplayer.sendMessage("§7" + currentGlobalQuest.description());
                aplayer.sendMessage("§eMục tiêu: §f" + currentGlobalQuest.target());
                aplayer.sendMessage("§aPhần thưởng: §fVật phẩm huyền thoại!");
                aplayer.sendMessage("§cHình phạt: §f" + currentGlobalQuest.punishment());
                aplayer.sendMessage("§4Thời hạn: §f7 NGÀY!");
                aplayer.sendMessage("§4§l========================");
            }
            
            // Play dramatic sound
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                plugin.getScoreboard().update(player);
            }
        });

        saveGlobalQuestData();
        plugin.getLogger().info("Generated new global quest: " + currentGlobalQuest.name());
    }

    public void updateGlobalProgress(Player player, int amount) {
        if (currentGlobalQuest == null || amount <= 0) {
            plugin.getLogger().info("Progress update failed: quest=" + (currentGlobalQuest != null ? currentGlobalQuest.id() : "null") + ", amount=" + amount);
            return;
        }
        
        UUID uuid = player.getUniqueId();
        
        if (completedPlayers.contains(uuid)) {
            plugin.getLogger().info("Player " + player.getName() + " already completed the quest.");
            return;
        }
        
        int currentProgress = globalProgress.getOrDefault(uuid, 0);
        int newProgress = Math.min(currentProgress + amount, currentGlobalQuest.target());
        
        plugin.getLogger().info("Updating progress for " + player.getName() + ": " + currentProgress + " -> " + newProgress + " (+" + amount + ")");
        
        if (newProgress > currentProgress) {
            globalProgress.put(uuid, newProgress);
            pendingUpdates++;
            
            // Check for completion
            if (newProgress >= currentGlobalQuest.target()) {
                completeQuest(player);
            } else {
                // Show progress update with percentage
                double percentage = (double) newProgress / currentGlobalQuest.target() * 100;
                player.sendMessage("§7[§6" + currentGlobalQuest.name() + "§7] §a+" + amount + " §7(" + newProgress + "/" + currentGlobalQuest.target() + " - " + String.format("%.1f", percentage) + "%)");
                
                // Send milestone messages
                if (percentage >= 25 && (double) currentProgress / currentGlobalQuest.target() * 100 < 25) {
                    player.sendMessage("§e🎯 Bạn đã hoàn thành 25% quest!");
                } else if (percentage >= 50 && (double) currentProgress / currentGlobalQuest.target() * 100 < 50) {
                    player.sendMessage("§e🎯 Bạn đã hoàn thành 50% quest!");
                } else if (percentage >= 75 && (double) currentProgress / currentGlobalQuest.target() * 100 < 75) {
                    player.sendMessage("§e🎯 Bạn đã hoàn thành 75% quest!");
                } else if (percentage >= 90 && (double) currentProgress / currentGlobalQuest.target() * 100 < 90) {
                    player.sendMessage("§a🎯 Sắp xong rồi! 90% quest đã hoàn thành!");
                }
            }
            
            // Update scoreboard
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.getScoreboard() != null) {
                    plugin.getScoreboard().update(player);
                }
            });
            
            // Schedule save
            scheduleSave();
        }
    }

    public Quest getCurrentGlobalQuest() {
        return currentGlobalQuest;
    }
    
    public int getPlayerProgress(UUID uuid) {
        return globalProgress.getOrDefault(uuid, 0);
    }
    
    public boolean hasPlayerCompleted(UUID uuid) {
        return completedPlayers.contains(uuid);
    }
    
    public boolean isPlayerPunished(UUID uuid) {
        return punishedPlayers.contains(uuid);
    }
    
    public long getTimeLeft() {
        return (questStartTime + ONE_WEEK_MILLIS) - System.currentTimeMillis();
    }
    
    public int getCompletedCount() {
        return completedPlayers.size();
    }

    private void completeQuest(Player player) {
        if (completedPlayers.add(player.getUniqueId())) {
            // Dramatic completion announcement
            for (Player aplayer : Bukkit.getOnlinePlayers()) {
                aplayer.sendMessage("§a§l🎉 " + player.getName() + " đã hoàn thành quest! 🎉");
            }
            
            // Give rewards
            giveRewards(player);
            
            // Play celebration sound
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
            
            saveGlobalQuestData();
        }
    }

    private void giveRewards(Player player) {
        player.sendMessage("§a§l=== PHẦN THƯỞNG QUEST ===");
        
        for (ItemStack reward : currentGlobalQuest.rewards()) {
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(reward);
                player.sendMessage("§a+ " + reward.getAmount() + "x " + reward.getType().name());
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), reward);
                player.sendMessage("§e(Rơi xuống đất) §a+ " + reward.getAmount() + "x " + reward.getType().name());
            }
        }
        
        // Bonus exp
        player.giveExp(5000);
        player.sendMessage("§a+ 5000 EXP");
        
        // Special effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 4));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 1));
        
        player.sendMessage("§a§l=====================");
    }

    private void punishFailedPlayers() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<Player> failedPlayers = new ArrayList<>();
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!completedPlayers.contains(player.getUniqueId())) {
                    failedPlayers.add(player);
                }
            }
            
            if (!failedPlayers.isEmpty()) {
                // Dramatic punishment announcement
                for (Player aplayer : Bukkit.getOnlinePlayers()) {
                    aplayer.sendMessage("§4§l=== THỜI GIAN ĐÃ HẾT! ===");
                    aplayer.sendMessage("§cCác người chơi sau đây sẽ bị trừng phạt:"); 
                    
                    for (Player player : failedPlayers) {
                        aplayer.sendMessage("§7- " + player.getName());
                        applyPunishment(player);
                    }

                    aplayer.sendMessage("§4§l===================");
                }
            }
        });
    }

    private void applyPunishment(Player player) {
        UUID uuid = player.getUniqueId();
        punishedPlayers.add(uuid);
        
        // Apply quest-specific punishment
        switch (currentGlobalQuest.id()) {
            case "mine_500_diamond_ore" -> {
                // Can't mine diamonds for 3 days
                player.sendMessage("§4§l" + currentGlobalQuest.punishment());
                // This would be handled in block break event
            }
            case "kill_1000_hostile_mobs" -> {
                // Double damage from mobs
                player.sendMessage("§4§l" + currentGlobalQuest.punishment());
                // This would be handled in damage event
            }
            case "travel_50000_blocks" -> {
                // Permanent slowness
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 2));
                player.sendMessage("§4§l" + currentGlobalQuest.punishment());
            }
            default -> {
                // Generic harsh punishment
                player.setHealth(1);
                player.setFoodLevel(1);
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 60 * 60, 1)); // 1 hour
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 60 * 60, 1)); // 1 hour
                player.sendMessage("§4§lBạn đã bị trừng phạt vì không hoàn thành quest!");
            }
        }
        
        // Play punishment sound
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.5f);
    }

    @EventHandler
    public void onBlockBreakForFarming(BlockBreakEvent event) {
        if (currentGlobalQuest == null || currentGlobalQuest.type() != Quest.QuestType.FARM_CROPS) return;
        if (!currentGlobalQuest.id().equals("farm_10000_crops")) return;
        
        Material blockType = event.getBlock().getType();
        Player player = event.getPlayer();
        
        // Check if player is punished
        if (punishedPlayers.contains(player.getUniqueId())) {
            // Punished players can't farm successfully
            if (isCrop(blockType)) {
                event.setCancelled(true);
                player.sendMessage("§c§lCây trồng của bạn bị nguyền rủa!");
                return;
            }
        }
        
        // Count crop harvesting
        if (isCrop(blockType)) {
            updateGlobalProgress(player, 1);
            plugin.getLogger().info(player.getName() + " harvested crop. Progress updated.");
        }
    }

    @EventHandler
    public void onBlockBreakForCollection(BlockBreakEvent event) {
        if (currentGlobalQuest == null || currentGlobalQuest.type() != Quest.QuestType.COLLECT_ITEMS) return;
        if (!currentGlobalQuest.id().equals("collect_2000_ancient_debris")) return;
        
        if (event.getBlock().getType() == Material.ANCIENT_DEBRIS) {
            updateGlobalProgress(event.getPlayer(), 1);
            plugin.getLogger().info(event.getPlayer().getName() + " collected ancient debris. Progress updated.");
        }
    }

    private boolean isCrop(Material material) {
        return material == Material.WHEAT || material == Material.CARROTS || 
               material == Material.POTATOES || material == Material.BEETROOTS ||
               material == Material.PUMPKIN || material == Material.MELON ||
               material == Material.SUGAR_CANE || material == Material.COCOA ||
               material == Material.NETHER_WART || material == Material.SWEET_BERRY_BUSH;
    }

    // Event handlers for different quest types
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (currentGlobalQuest == null) return;
        
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        // Check if player is punished and can't mine diamonds
        if (punishedPlayers.contains(player.getUniqueId()) && blockType == Material.DIAMOND_ORE) {
            event.setCancelled(true);
            player.sendMessage("§c§lBạn bị nguyền rủa không thể đào kim cương!");
            return;
        }
        
        // Handle different quest types
        if (currentGlobalQuest.type() == Quest.QuestType.BREAK_BLOCKS) {
            if (currentGlobalQuest.id().equals("break_5000_obsidian") && blockType == Material.OBSIDIAN) {
                updateGlobalProgress(player, 1);
                plugin.getLogger().info(player.getName() + " broke obsidian. Progress updated.");
            }
        } else if (currentGlobalQuest.type() == Quest.QuestType.MINE_ORES) {
            if (currentGlobalQuest.id().equals("mine_500_diamond_ore") && blockType == Material.DIAMOND_ORE) {
                updateGlobalProgress(player, 1);
                plugin.getLogger().info(player.getName() + " mined diamond ore. Progress updated.");
            }
        }
    }

    @EventHandler
    public void onMineOre(BlockBreakEvent event) {
        if (currentGlobalQuest == null || currentGlobalQuest.type() != Quest.QuestType.MINE_ORES) return;
        
        Material blockType = event.getBlock().getType();
        
        if (currentGlobalQuest.id().equals("mine_500_diamond_ore") && blockType == Material.DIAMOND_ORE) {
            updateGlobalProgress(event.getPlayer(), 1);
        }
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (currentGlobalQuest == null || currentGlobalQuest.type() != Quest.QuestType.KILL_MOBS) return;
        
        if (event.getEntity().getKiller() instanceof Player player) {
            Entity entity = event.getEntity();
            
            // Only count hostile mobs for kill quest
            if (currentGlobalQuest.id().equals("kill_1000_hostile_mobs")) {
                if (entity instanceof Monster || entity instanceof Slime || entity instanceof Ghast || 
                    entity instanceof Phantom || entity instanceof Shulker) {
                    updateGlobalProgress(player, 1);
                    plugin.getLogger().info(player.getName() + " killed hostile mob. Progress updated.");
                }
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (currentGlobalQuest == null || currentGlobalQuest.type() != Quest.QuestType.CRAFT_ITEMS) return;
        
        if (event.getWhoClicked() instanceof Player player) {
            ItemStack result = event.getInventory().getResult();
            if (result != null && currentGlobalQuest.id().equals("craft_500_enchanted_books")) {
                if (result.getType() == Material.ENCHANTED_BOOK) {
                    updateGlobalProgress(player, result.getAmount());
                    plugin.getLogger().info(player.getName() + " crafted enchanted book. Progress updated.");
                }
            }
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (currentGlobalQuest == null || currentGlobalQuest.type() != Quest.QuestType.FISH) return;
        
        if (currentGlobalQuest.id().equals("fish_1000_treasure") && 
            event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            
            if (event.getCaught() instanceof Item item) {
                ItemStack caught = item.getItemStack();
                if (isTreasure(caught.getType())) {
                    updateGlobalProgress(event.getPlayer(), 1);
                    plugin.getLogger().info(event.getPlayer().getName() + " caught treasure. Progress updated.");
                }
            }
        }
    }

    private boolean isTreasure(Material material) {
        return material == Material.ENCHANTED_BOOK || material == Material.NAME_TAG || 
               material == Material.SADDLE || material == Material.NAUTILUS_SHELL ||
               material == Material.BOW || material == Material.FISHING_ROD;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (currentGlobalQuest == null || currentGlobalQuest.type() != Quest.QuestType.TRAVEL_DISTANCE) return;
        if (!currentGlobalQuest.id().equals("travel_50000_blocks")) return;
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || from.getWorld() != to.getWorld()) return;
        
        // Only count significant movement to avoid spam
        double distance = from.distance(to);
        if (distance < 0.5) return; // Minimum movement threshold
        
        // Check if player is punished with slowness
        if (punishedPlayers.contains(player.getUniqueId())) {
            // Apply slowness if not already applied
            if (!player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 2));
            }
        }

        updateGlobalProgress(player, (int) Math.floor(distance));
        
        // Debug logging (remove in production)
        if (distance > 1.0) {
            plugin.getLogger().info(player.getName() + " moved " + distance + " blocks. Progress updated.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lastLocations.put(player.getUniqueId(), player.getLocation());
        
        if (currentGlobalQuest != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                showQuestInfo(player);
                plugin.getScoreboard().update(player);
            }, 40L); // 2 second delay
        }
    }

    public void showQuestInfo(Player player) {
        if (currentGlobalQuest == null) {
            player.sendMessage("§cKhông có quest toàn cầu nào đang hoạt động.");
            return;
        }
        
        player.sendMessage("§6§l=== QUEST TOÀN CẦU ===");
        player.sendMessage("§e" + currentGlobalQuest.name());
        player.sendMessage("§7" + currentGlobalQuest.description());
        
        int progress = globalProgress.getOrDefault(player.getUniqueId(), 0);
        player.sendMessage("§fTiến độ: §a" + progress + "/" + currentGlobalQuest.target());
        
        if (completedPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§a✅ Bạn đã hoàn thành quest này!");
        } else {
            long timeLeft = (questStartTime + ONE_WEEK_MILLIS) - System.currentTimeMillis();
            long hoursLeft = timeLeft / (60 * 60 * 1000);
            player.sendMessage("§cThời gian còn lại: §f" + hoursLeft + " giờ");
            player.sendMessage("§4Hình phạt: §f" + currentGlobalQuest.punishment());
        }
        
        player.sendMessage("§6§l===================");
    }

    private void scheduleSave() {
        if (pendingUpdates > 50) { // Save more frequently for global quest
            saveGlobalQuestData();
            pendingUpdates = 0;
        }
    }

    public void saveGlobalQuestData() {
        FileConfiguration config = dataConfig;
        
        // Save current quest info
        if (currentGlobalQuest != null) {
            config.set("global.questId", currentGlobalQuest.id());
            config.set("global.startTime", questStartTime);
            
            // Save completed players
            List<String> completed = new ArrayList<>();
            for (UUID uuid : completedPlayers) {
                completed.add(uuid.toString());
            }
            config.set("global.completed", completed);
            
            // Save punished players
            List<String> punished = new ArrayList<>();
            for (UUID uuid : punishedPlayers) {
                punished.add(uuid.toString());
            }
            config.set("global.punished", punished);
            
            // Save progress
            for (Map.Entry<UUID, Integer> entry : globalProgress.entrySet()) {
                config.set("progress." + entry.getKey().toString(), entry.getValue());
            }
        }
        
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save global quest data!", e);
        }
        
        pendingUpdates = 0;
    }

    public void loadGlobalQuestData() {
        String questId = dataConfig.getString("global.questId");
        long startTime = dataConfig.getLong("global.startTime", System.currentTimeMillis());
        
        if (questId != null) {
            // Find the quest by ID
            for (Quest quest : hardQuestPool) {
                if (quest.id().equals(questId)) {
                    currentGlobalQuest = quest;
                    questStartTime = startTime;
                    break;
                }
            }
            
            // Load completed players
            List<String> completed = dataConfig.getStringList("global.completed");
            for (String uuidStr : completed) {
                try {
                    completedPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in completed players: " + uuidStr);
                }
            }
            
            // Load punished players
            List<String> punished = dataConfig.getStringList("global.punished");
            for (String uuidStr : punished) {
                try {
                    punishedPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in punished players: " + uuidStr);
                }
            }
            
            // Load progress
            ConfigurationSection progressSection = dataConfig.getConfigurationSection("progress");
            if (progressSection != null) {
                for (String uuidStr : progressSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        int progress = progressSection.getInt(uuidStr, 0);
                        globalProgress.put(uuid, progress);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in progress data: " + uuidStr);
                    }
                }
            }
        }
        
        plugin.getLogger().info("Loaded global quest data. Current quest: " + 
            (currentGlobalQuest != null ? currentGlobalQuest.name() : "None"));
    }

    public void forceCompleteQuest(Player player) {
        if (currentGlobalQuest != null) {
            globalProgress.put(player.getUniqueId(), currentGlobalQuest.target());
            completeQuest(player);
            plugin.getLogger().info("Force completed quest for " + player.getName());
        }
    }
    
    public void addProgress(Player player, int amount) {
        updateGlobalProgress(player, amount);
        plugin.getLogger().info("Added " + amount + " progress to " + player.getName());
    }
    
    public void resetPlayerProgress(Player player) {
        UUID uuid = player.getUniqueId();
        globalProgress.remove(uuid);
        completedPlayers.remove(uuid);
        punishedPlayers.remove(uuid);
        saveGlobalQuestData();
        plugin.getLogger().info("Reset progress for " + player.getName());
    }

    public void shutdown() {
        saveGlobalQuestData();
        plugin.getLogger().info("QuestManager shutting down. Data saved.");
    }
}