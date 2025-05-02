package org.teamck.villagerEnchantTracker.ui;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;
import org.teamck.villagerEnchantTracker.core.VillagerEnchantTracker;
import org.teamck.villagerEnchantTracker.manager.MessageManager;
import org.teamck.villagerEnchantTracker.manager.EnchantmentManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.util.*;

public class EnchantmentTUI {
    private static final int ITEMS_PER_PAGE = 5;
    private final VillagerEnchantTracker plugin;
    private final Player player;
    private final MessageManager messageManager;
    private final Set<EnchantmentManager.EnchantmentInfo> enchantments;
    private final Set<EnchantmentManager.EnchantmentInfo> existingEnchantments;
    private boolean showingNewEnchants = true;
    private int currentPage = 0;
    private String feedbackMessage = "";

    public EnchantmentTUI(VillagerEnchantTracker plugin, Player player, 
            Set<EnchantmentManager.EnchantmentInfo> newEnchants, 
            Set<EnchantmentManager.EnchantmentInfo> existingEnchants) {
        this.plugin = plugin;
        this.player = player;
        this.messageManager = MessageManager.getInstance();
        this.enchantments = newEnchants;
        this.existingEnchantments = existingEnchants;
    }

    public void render() {
        // Header
        player.sendMessage("");
        player.sendMessage(messageManager.getMessage("tui_header", player));
        
        // Tab buttons
        renderTabs();
        
        // Content area
        player.sendMessage(messageManager.getMessage("tui_divider", player));
        
        // Column headers
        player.sendMessage(String.format(messageManager.getMessage("tui_enchant_header", player), "Enchantment", "Lvl"));
        player.sendMessage(messageManager.getMessage("tui_divider", player));
        
        // Items
        renderItems();
        
        // Fill empty space
        int emptyLines = ITEMS_PER_PAGE - getCurrentPageItems().size();
        for (int i = 0; i < emptyLines; i++) {
            player.sendMessage(messageManager.getMessage("tui_empty_line", player));
        }
        
        // Navigation
        renderNavigation();
        
        // Feedback area
        player.sendMessage(messageManager.getMessage("tui_divider", player));
        if (!feedbackMessage.isEmpty()) {
            player.sendMessage(feedbackMessage);
        } else {
            player.sendMessage(messageManager.getMessage("tui_default_feedback", player));
        }
        
        // Close button
        renderCloseButton();
    }

    private void renderTabs() {
        TextComponent tabs = new TextComponent();
        
        // New enchants tab
        TextComponent newEnchantsTab = new TextComponent(
            showingNewEnchants ? 
                messageManager.getMessage("tui_new_enchants_tab", player) : 
                messageManager.getMessage("tui_new_enchants_tab_inactive", player)
        );
        newEnchantsTab.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            "/vet evt tui toggle"
        ));
        tabs.addExtra(newEnchantsTab);
        
        tabs.addExtra(" ");
        
        // Existing enchants tab
        TextComponent existingEnchantsTab = new TextComponent(
            !showingNewEnchants ? 
                messageManager.getMessage("tui_existing_enchants_tab", player) : 
                messageManager.getMessage("tui_existing_enchants_tab_inactive", player)
        );
        existingEnchantsTab.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            "/vet evt tui toggle"
        ));
        tabs.addExtra(existingEnchantsTab);
        
        player.spigot().sendMessage(tabs);
    }

    private void renderItems() {
        for (EnchantmentManager.EnchantmentInfo enchant : getCurrentPageItems()) {
            String enchantId = enchant.getId();
            int level = enchant.getLevel();
            int defaultPrice = enchant.getPrice() != null ? enchant.getPrice() : 64;
            
            String enchantName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
            TextComponent line = new TextComponent(
                String.format("%-20s %-3d ", enchantName, level)
            );
            
            // Price buttons
            int[] prices = showingNewEnchants ? 
                new int[]{64, 48, 32, 16, 1} :
                new int[]{defaultPrice, defaultPrice - 5, defaultPrice - 10, defaultPrice - 15, 1};
            
            for (int p : prices) {
                if (p < 1) continue;
                line.addExtra(createPriceComponent(p, enchantId, level));
                line.addExtra(" ");
            }
            
            player.spigot().sendMessage(line);
        }
    }

    private void renderNavigation() {
        TextComponent navigation = new TextComponent();
        
        if (hasPreviousPage()) {
            TextComponent prev = new TextComponent(
                messageManager.getMessage("tui_nav_prev_active", player)
            );
            prev.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/vet evt tui prev"
            ));
            navigation.addExtra(prev);
        } else {
            navigation.addExtra(messageManager.getMessage("tui_nav_prev_inactive", player));
        }
        
        navigation.addExtra(" ");
        navigation.addExtra(String.format(messageManager.getMessage("tui_nav_page", player), currentPage + 1, getTotalPages()));
        navigation.addExtra(" ");
        
        if (hasNextPage()) {
            TextComponent next = new TextComponent(
                messageManager.getMessage("tui_nav_next_active", player)
            );
            next.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/vet evt tui next"
            ));
            navigation.addExtra(next);
        } else {
            navigation.addExtra(messageManager.getMessage("tui_nav_next_inactive", player));
        }
        
        player.spigot().sendMessage(navigation);
    }

    private void renderCloseButton() {
        TextComponent close = new TextComponent(
            messageManager.getMessage("tui_close_button", player)
        );
        close.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            "/vet evt tui close"
        ));
        player.spigot().sendMessage(close);
    }

    private TextComponent createPriceComponent(int price, String enchantId, int level) {
        plugin.getLogger().info(String.format("Creating price component for enchant '%s' level %d price %d", enchantId, level, price));
        
        // Create the main visible component
        TextComponent priceComponent = new TextComponent(
            String.format(messageManager.getMessage("tui_price_button", player), price)
        );
        
        // Add minecraft: prefix if not present
        String fullEnchantId = enchantId.startsWith("minecraft:") ? enchantId : "minecraft:" + enchantId;
        plugin.getLogger().info(String.format("Using full enchant ID: %s", fullEnchantId));
        
        // Create the search command
        String searchCommand = String.format("/evt search add %d %s %d", price, fullEnchantId, level);
        plugin.getLogger().info(String.format("Generated search command: %s", searchCommand));
        
        // Create a hidden component that will be clicked after the search command
        TextComponent hiddenFeedback = new TextComponent(" ");
        hiddenFeedback.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            String.format("/vet evt tui feedback %s %d %d", enchantId, level, price)
        ));
        
        // Create the hover text that shows both commands
        String enchantName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
        BaseComponent[] hoverComponents = new BaseComponent[] {
            new TextComponent(
                String.format(messageManager.getMessage("tui_price_button_hover", player), enchantName, level, price)
            )
        };
        
        // Set up the click and hover events
        priceComponent.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            searchCommand
        ));
        priceComponent.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            hoverComponents
        ));
        
        // Combine the visible and hidden components
        TextComponent combined = new TextComponent();
        combined.addExtra(priceComponent);
        combined.addExtra(hiddenFeedback);
        
        return combined;
    }

    public boolean handleCommand(String command, String[] args) {
        plugin.getLogger().info(String.format("Handling TUI command: %s %s", command, String.join(" ", args)));
        
        switch (command.toLowerCase()) {
            case "toggle":
                plugin.getLogger().info("Toggling enchant type view");
                showingNewEnchants = !showingNewEnchants;
                currentPage = 0;
                break;
            case "next":
                plugin.getLogger().info("Moving to next page");
                if (hasNextPage()) {
                    currentPage++;
                }
                break;
            case "prev":
                plugin.getLogger().info("Moving to previous page");
                if (hasPreviousPage()) {
                    currentPage--;
                }
                break;
            case "close":
                plugin.getLogger().info("Closing TUI");
                player.sendMessage(messageManager.getMessage("tui_closed", player));
                return true;
            case "feedback":
                if (args.length >= 3) {
                    try {
                        String enchantId = args[0];
                        int level = Integer.parseInt(args[1]);
                        int price = Integer.parseInt(args[2]);
                        
                        plugin.getLogger().info(String.format("Setting feedback: enchant=%s level=%d price=%d", 
                            enchantId, level, price));
                            
                        String enchantName = messageManager.getEnchantName(
                            enchantId.replace("minecraft:", ""), 
                            messageManager.getBaseLanguageCode(player.getLocale())
                        );
                        feedbackMessage = String.format(messageManager.getMessage("tui_price_added_feedback", player), enchantName, level, price);
                        
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning(String.format("Invalid number in feedback command: %s", String.join(" ", args)));
                    }
                } else {
                    plugin.getLogger().warning(String.format("Invalid feedback command args: %s", String.join(" ", args)));
                }
                break;
            default:
                plugin.getLogger().warning(String.format("Unknown TUI command: %s", command));
                return false;
        }
        
        render();
        return true;
    }

    private List<EnchantmentManager.EnchantmentInfo> getCurrentPageItems() {
        List<EnchantmentManager.EnchantmentInfo> currentList = 
            new ArrayList<>(showingNewEnchants ? enchantments : existingEnchantments);
        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, currentList.size());
        return currentList.subList(start, end);
    }
    
    private boolean hasNextPage() {
        List<EnchantmentManager.EnchantmentInfo> currentList = 
            new ArrayList<>(showingNewEnchants ? enchantments : existingEnchantments);
        return (currentPage + 1) * ITEMS_PER_PAGE < currentList.size();
    }
    
    private boolean hasPreviousPage() {
        return currentPage > 0;
    }
    
    private int getTotalPages() {
        List<EnchantmentManager.EnchantmentInfo> currentList = 
            new ArrayList<>(showingNewEnchants ? enchantments : existingEnchantments);
        return (int) Math.ceil((double) currentList.size() / ITEMS_PER_PAGE);
    }

    public Inventory createInventory() {
        Inventory inventory = Bukkit.createInventory(null, 54, messageManager.getMessage("enchantment-tui-title", player));

        List<EnchantmentManager.EnchantmentInfo> currentPageItems = getCurrentPageItems();
        int slot = 0;

        for (EnchantmentManager.EnchantmentInfo enchant : currentPageItems) {
            ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                if (showingNewEnchants) {
                    meta.setDisplayName(ChatColor.GREEN + enchant.getId());
                    lore.add(ChatColor.GRAY + messageManager.getMessage("enchantment-tui-click-to-add", player));
                } else {
                    meta.setDisplayName(ChatColor.YELLOW + enchant.getId());
                    lore.add(ChatColor.GRAY + messageManager.getMessage("enchantment-tui-level", player) + ": " + enchant.getLevel());
                    if (enchant.getPrice() != null) {
                        lore.add(ChatColor.GRAY + messageManager.getMessage("enchantment-tui-price", player) + ": " + enchant.getPrice());
                    }
                    lore.add(ChatColor.GRAY + messageManager.getMessage("enchantment-tui-click-to-remove", player));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot++, item);
        }

        // Navigation buttons
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(ChatColor.YELLOW + messageManager.getMessage("enchantment-tui-previous-page", player));
                prevButton.setItemMeta(prevMeta);
            }
            inventory.setItem(45, prevButton);
        }

        if (hasNextPage()) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.YELLOW + messageManager.getMessage("enchantment-tui-next-page", player));
                nextButton.setItemMeta(nextMeta);
            }
            inventory.setItem(53, nextButton);
        }

        // Toggle button
        ItemStack toggleButton = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta toggleMeta = toggleButton.getItemMeta();
        if (toggleMeta != null) {
            toggleMeta.setDisplayName(ChatColor.YELLOW + (showingNewEnchants ? 
                messageManager.getMessage("enchantment-tui-show-existing", player) : 
                messageManager.getMessage("enchantment-tui-show-new", player)));
            toggleButton.setItemMeta(toggleMeta);
        }
        inventory.setItem(49, toggleButton);

        // Add feedback message if present
        if (!feedbackMessage.isEmpty()) {
            ItemStack feedbackItem = new ItemStack(Material.PAPER);
            ItemMeta feedbackMeta = feedbackItem.getItemMeta();
            if (feedbackMeta != null) {
                feedbackMeta.setDisplayName(feedbackMessage);
                feedbackItem.setItemMeta(feedbackMeta);
            }
            inventory.setItem(4, feedbackItem);
        }

        return inventory;
    }
} 