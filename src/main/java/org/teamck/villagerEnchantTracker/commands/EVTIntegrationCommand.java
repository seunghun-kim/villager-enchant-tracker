package org.teamck.villagerEnchantTracker.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.teamck.villagerEnchantTracker.core.VillagerEnchantTracker;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.EnchantmentManager;
import org.teamck.villagerEnchantTracker.manager.MessageManager;

import java.util.*;

public class EVTIntegrationCommand implements CommandExecutor, TabCompleter {
    private final VillagerEnchantTracker plugin;
    private final Database database;
    private final MessageManager messageManager;
    private List<String> pendingCommands = new ArrayList<>();

    public EVTIntegrationCommand(VillagerEnchantTracker plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.messageManager = MessageManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getMessage("player_only", "en"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("villagerenchanttracker.use")) {
            player.sendMessage(messageManager.getChatMessage("no_permission", player));
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        // Handle TUI commands
        if (args[0].equalsIgnoreCase("tui")) {
            return handleTUICommand(player, args);
        }

        plugin.getLogger().info(String.format("Player %s executed EVT integration command: /%s %s",
                player.getName(), label, String.join(" ", args)));

        switch (args[0].toLowerCase()) {
            case "nearby":
                return handleNearbyCommand(player, args);
            case "region":
                return handleRegionCommand(player, args);
            default:
                sendUsage(player);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return completions;
        }

        if (args.length == 1) {
            // First argument: subcommands
            String input = args[0].toLowerCase();
            List<String> subcommands = Arrays.asList("nearby", "region");
            
            for (String subcommand : subcommands) {
                if (subcommand.startsWith(input)) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2) {
            // Second argument: region name for region subcommand or radius for nearby
            String input = args[1].toLowerCase();
            if (args[0].equalsIgnoreCase("region")) {
                // Add "*" for all regions
                if ("*".startsWith(input)) {
                    completions.add("*");
                }
                
                // Add region names
                database.listRegions().stream()
                        .map(VillagerRegion::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .forEach(completions::add);
            } else if (args[0].equalsIgnoreCase("nearby")) {
                // Suggest some common radius values
                List<String> radii = Arrays.asList("5", "10", "15", "20", "25", "30");
                for (String radius : radii) {
                    if (radius.startsWith(input)) {
                        completions.add(radius);
                    }
                }
            }
        }
        
        return completions;
    }

    private boolean handleNearbyCommand(Player player, String[] args) {
        plugin.getLogger().info(String.format("Player %s executing nearby command with args: %s", 
            player.getName(), String.join(" ", args)));
            
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("nearby_usage", player));
            return true;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[1]);
            plugin.getLogger().info(String.format("Searching with radius: %d", radius));
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("invalid_radius", player));
            plugin.getLogger().warning(String.format("Invalid radius provided: %s", args[1]));
            return true;
        }

        // Find nearby librarians
        List<Villager> librarians = new ArrayList<>();
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Villager villager && villager.getProfession() == Villager.Profession.LIBRARIAN) {
                librarians.add(villager);
            }
        }

        plugin.getLogger().info(String.format("Found %d librarians near player %s within radius %d", 
            librarians.size(), player.getName(), radius));

        if (librarians.isEmpty()) {
            plugin.getLogger().info(String.format("No librarians found for player %s within %d blocks",
                    player.getName(), radius));
            player.sendMessage(messageManager.format("no_librarians_nearby", player, radius));
            return true;
        }

        // Get all existing trades from nearby librarians
        Set<String> existingTrades = new HashSet<>();
        for (Villager librarian : librarians) {
            Set<String> trades = EnchantmentManager.getVillagerEnchantments(librarian);
            existingTrades.addAll(trades);
            plugin.getLogger().info(String.format("Librarian at %s has enchantments: %s",
                    formatLocation(librarian.getLocation()), String.join(", ", trades)));
        }

        // Get new enchantments
        Set<String> allEnchants = new HashSet<>(EnchantmentManager.getAllMaxLevelEnchantments());
        Set<String> newEnchants = new HashSet<>(allEnchants);
        newEnchants.removeAll(existingTrades);
        
        plugin.getLogger().info(String.format("Total enchants: %d, Existing: %d, New: %d", 
            allEnchants.size(), existingTrades.size(), newEnchants.size()));

        // Create and show TUI
        TUIState state = new TUIState(newEnchants, existingTrades);
        activeTUIs.put(player.getUniqueId(), state);
        renderTUI(player, state);
        
        return true;
    }

    private boolean handleRegionCommand(Player player, String[] args) {
        plugin.getLogger().info(String.format("Player %s executing region command with args: %s", 
            player.getName(), String.join(" ", args)));
            
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("region_usage", player));
            return true;
        }

        // Get selected regions
        List<VillagerRegion> regions = new ArrayList<>();
        if (args[1].equalsIgnoreCase("all") || args[1].equals("*")) {
            regions.addAll(database.listRegions());
            plugin.getLogger().info("Searching in all regions");
        } else {
            VillagerRegion region = database.getRegionByName(args[1]);
            if (region == null) {
                player.sendMessage(messageManager.getChatMessage("region_not_found", player));
                plugin.getLogger().warning(String.format("Region not found: %s", args[1]));
                return true;
            }
            regions.add(region);
            plugin.getLogger().info(String.format("Searching in region: %s", region.getName()));
        }

        if (regions.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_regions", player));
            plugin.getLogger().warning("No regions found in database");
            return true;
        }

        // Get all existing trades from librarians in regions
        Set<String> existingTrades = new HashSet<>();
        int totalLibrarians = 0;

        for (VillagerRegion region : regions) {
            List<Villager> librarians = region.getLibrariansInRegion();
            totalLibrarians += librarians.size();
            plugin.getLogger().info(String.format("Region '%s': Found %d librarians",
                    region.getName(), librarians.size()));

            for (Villager librarian : librarians) {
                Set<String> trades = EnchantmentManager.getVillagerEnchantments(librarian);
                existingTrades.addAll(trades);
                plugin.getLogger().info(String.format("Librarian at %s in region '%s' has enchantments: %s",
                        formatLocation(librarian.getLocation()), region.getName(), String.join(", ", trades)));
            }
        }

        if (totalLibrarians == 0) {
            plugin.getLogger().info(String.format("No librarians found in any selected region for player %s",
                    player.getName()));
            player.sendMessage(messageManager.getChatMessage("no_librarians_in_region", player));
            return true;
        }

        // Get new enchantments
        Set<String> allEnchants = new HashSet<>(EnchantmentManager.getAllMaxLevelEnchantments());
        Set<String> newEnchants = new HashSet<>(allEnchants);
        newEnchants.removeAll(existingTrades);
        
        plugin.getLogger().info(String.format("Total enchants: %d, Existing: %d, New: %d", 
            allEnchants.size(), existingTrades.size(), newEnchants.size()));

        // Create and show TUI
        TUIState state = new TUIState(newEnchants, existingTrades);
        activeTUIs.put(player.getUniqueId(), state);
        renderTUI(player, state);
        
        return true;
    }

    private net.md_5.bungee.api.chat.TextComponent createPriceComponent(int price, String enchantId, int level, Player player) {
        plugin.getLogger().info(String.format("Creating price component for enchant '%s' level %d price %d", enchantId, level, price));
        
        // Create the main visible component
        net.md_5.bungee.api.chat.TextComponent priceComponent = new net.md_5.bungee.api.chat.TextComponent(
            messageManager.format("tui_price_button", player, price)
        );
        
        // Add minecraft: prefix if not present
        String fullEnchantId = enchantId.startsWith("minecraft:") ? enchantId : "minecraft:" + enchantId;
        plugin.getLogger().info(String.format("Using full enchant ID: %s", fullEnchantId));
        
        // Create the search command
        String searchCommand = String.format("/evt search add %d %s %d", price, fullEnchantId, level);
        plugin.getLogger().info(String.format("Generated search command: %s", searchCommand));
        
        // Create a hidden component that will be clicked after the search command
        net.md_5.bungee.api.chat.TextComponent hiddenFeedback = new net.md_5.bungee.api.chat.TextComponent(" ");
        hiddenFeedback.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
            String.format("/evtintegration tui feedback %s %d %d", enchantId, level, price)
        ));
        
        // Create the hover text that shows both commands
        String enchantName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
        net.md_5.bungee.api.chat.BaseComponent[] hoverComponents = new net.md_5.bungee.api.chat.BaseComponent[] {
            new net.md_5.bungee.api.chat.TextComponent(
                messageManager.format("tui_price_button_hover", player, enchantName, level, price)
            )
        };
        
        // Set up the click and hover events
        priceComponent.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
            searchCommand
        ));
        priceComponent.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            hoverComponents
        ));
        
        // Combine the visible and hidden components
        net.md_5.bungee.api.chat.TextComponent combined = new net.md_5.bungee.api.chat.TextComponent();
        combined.addExtra(priceComponent);
        combined.addExtra(hiddenFeedback);
        
        return combined;
    }

    private String formatLocation(org.bukkit.Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    private void sendUsage(Player player) {
        player.sendMessage(messageManager.getChatMessage("evtintegration_header", player));
        player.sendMessage(messageManager.getChatMessage("evtintegration_nearby_usage", player));
        player.sendMessage(messageManager.getChatMessage("evtintegration_region_usage", player));
    }

    class TUIState {
        private static final int ITEMS_PER_PAGE = 5;
        private final List<String> newEnchants;
        private final List<String> existingEnchants;
        private boolean showingNewEnchants = true;
        private int currentPage = 0;
        private String feedbackMessage = "";
        
        public TUIState(Set<String> newEnchants, Set<String> existingEnchants) {
            this.newEnchants = new ArrayList<>(newEnchants);
            this.existingEnchants = new ArrayList<>(existingEnchants);
        }
        
        public List<String> getCurrentPageItems() {
            List<String> currentList = showingNewEnchants ? newEnchants : existingEnchants;
            int start = currentPage * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, currentList.size());
            return currentList.subList(start, end);
        }
        
        public boolean hasNextPage() {
            List<String> currentList = showingNewEnchants ? newEnchants : existingEnchants;
            return (currentPage + 1) * ITEMS_PER_PAGE < currentList.size();
        }
        
        public boolean hasPreviousPage() {
            return currentPage > 0;
        }
        
        public void nextPage() {
            if (hasNextPage()) {
                currentPage++;
            }
        }
        
        public void previousPage() {
            if (hasPreviousPage()) {
                currentPage--;
            }
        }
        
        public void toggleEnchantType() {
            showingNewEnchants = !showingNewEnchants;
            currentPage = 0;
        }
        
        public void setFeedbackMessage(String message) {
            this.feedbackMessage = message;
        }
        
        public String getFeedbackMessage() {
            return feedbackMessage;
        }
        
        public boolean isShowingNewEnchants() {
            return showingNewEnchants;
        }
        
        public int getTotalPages() {
            List<String> currentList = showingNewEnchants ? newEnchants : existingEnchants;
            return (int) Math.ceil((double) currentList.size() / ITEMS_PER_PAGE);
        }
        
        public int getCurrentPage() {
            return currentPage;
        }
    }

    private void renderTUI(Player player, TUIState state) {
        // Header
        player.sendMessage("");
        player.sendMessage(messageManager.getChatMessage("tui_header", player));
        
        // Tab buttons
        net.md_5.bungee.api.chat.TextComponent tabs = new net.md_5.bungee.api.chat.TextComponent();
        
        // New enchants tab
        net.md_5.bungee.api.chat.TextComponent newEnchantsTab = new net.md_5.bungee.api.chat.TextComponent(
            state.isShowingNewEnchants() ? 
                messageManager.getChatMessage("tui_new_enchants_tab", player) : 
                messageManager.getChatMessage("tui_new_enchants_tab_inactive", player)
        );
        newEnchantsTab.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
            "/evtintegration tui toggle"
        ));
        tabs.addExtra(newEnchantsTab);
        
        tabs.addExtra(" ");
        
        // Existing enchants tab
        net.md_5.bungee.api.chat.TextComponent existingEnchantsTab = new net.md_5.bungee.api.chat.TextComponent(
            !state.isShowingNewEnchants() ? 
                messageManager.getChatMessage("tui_existing_enchants_tab", player) : 
                messageManager.getChatMessage("tui_existing_enchants_tab_inactive", player)
        );
        existingEnchantsTab.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
            "/evtintegration tui toggle"
        ));
        tabs.addExtra(existingEnchantsTab);
        
        player.spigot().sendMessage(tabs);
        
        // Content area
        player.sendMessage(messageManager.getChatMessage("tui_divider", player));
        
        // Column headers
        player.sendMessage(messageManager.format("tui_enchant_header", player, "Enchantment", "Lvl"));
        player.sendMessage(messageManager.getChatMessage("tui_divider", player));
        
        // Items
        for (String enchant : state.getCurrentPageItems()) {
            String[] parts = enchant.split(" ");
            String enchantId = parts[0];
            int level = Integer.parseInt(parts[1]);
            int price = parts.length > 2 ? Integer.parseInt(parts[2]) : 64;
            
            String enchantName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
            net.md_5.bungee.api.chat.TextComponent line = new net.md_5.bungee.api.chat.TextComponent(
                String.format("%-20s %-3d ", enchantName, level)
            );
            
            // Price buttons
            int[] prices = state.isShowingNewEnchants() ? 
                new int[]{64, 48, 32, 16, 1} :
                new int[]{price, price - 5, price - 10, price - 15, 1};
            
            for (int p : prices) {
                if (p < 1) continue;
                line.addExtra(createPriceComponent(p, enchantId, level, player));
                line.addExtra(" ");
            }
            
            player.spigot().sendMessage(line);
        }
        
        // Fill empty space
        int emptyLines = TUIState.ITEMS_PER_PAGE - state.getCurrentPageItems().size();
        for (int i = 0; i < emptyLines; i++) {
            player.sendMessage(messageManager.getChatMessage("tui_empty_line", player));
        }
        
        // Navigation
        net.md_5.bungee.api.chat.TextComponent navigation = new net.md_5.bungee.api.chat.TextComponent();
        
        if (state.hasPreviousPage()) {
            net.md_5.bungee.api.chat.TextComponent prev = new net.md_5.bungee.api.chat.TextComponent(
                messageManager.getChatMessage("tui_nav_prev_active", player)
            );
            prev.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/evtintegration tui prev"
            ));
            navigation.addExtra(prev);
        } else {
            navigation.addExtra(messageManager.getChatMessage("tui_nav_prev_inactive", player));
        }
        
        navigation.addExtra(" ");
        navigation.addExtra(messageManager.format("tui_nav_page", player, state.getCurrentPage() + 1, state.getTotalPages()));
        navigation.addExtra(" ");
        
        if (state.hasNextPage()) {
            net.md_5.bungee.api.chat.TextComponent next = new net.md_5.bungee.api.chat.TextComponent(
                messageManager.getChatMessage("tui_nav_next_active", player)
            );
            next.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/evtintegration tui next"
            ));
            navigation.addExtra(next);
        } else {
            navigation.addExtra(messageManager.getChatMessage("tui_nav_next_inactive", player));
        }
        
        player.spigot().sendMessage(navigation);
        
        // Feedback area
        player.sendMessage(messageManager.getChatMessage("tui_divider", player));
        if (!state.getFeedbackMessage().isEmpty()) {
            player.sendMessage(state.getFeedbackMessage());
        } else {
            player.sendMessage(messageManager.getChatMessage("tui_default_feedback", player));
        }
        
        // Close button
        net.md_5.bungee.api.chat.TextComponent close = new net.md_5.bungee.api.chat.TextComponent(
            messageManager.getChatMessage("tui_close_button", player)
        );
        close.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
            "/evtintegration tui close"
        ));
        player.spigot().sendMessage(close);
    }

    private Map<UUID, TUIState> activeTUIs = new HashMap<>();

    private boolean handleTUICommand(Player player, String[] args) {
        plugin.getLogger().info(String.format("Player %s executing TUI command with args: %s", 
            player.getName(), String.join(" ", args)));
            
        if (args.length < 2) return false;
        
        TUIState state = activeTUIs.get(player.getUniqueId());
        if (state == null) {
            plugin.getLogger().warning(String.format("No active TUI found for player %s", player.getName()));
            return false;
        }
        
        switch (args[1].toLowerCase()) {
            case "toggle":
                plugin.getLogger().info(String.format("Player %s toggled enchant type view", player.getName()));
                state.toggleEnchantType();
                break;
            case "next":
                plugin.getLogger().info(String.format("Player %s moved to next page", player.getName()));
                state.nextPage();
                break;
            case "prev":
                plugin.getLogger().info(String.format("Player %s moved to previous page", player.getName()));
                state.previousPage();
                break;
            case "close":
                plugin.getLogger().info(String.format("Player %s closed TUI", player.getName()));
                activeTUIs.remove(player.getUniqueId());
                player.sendMessage(messageManager.getChatMessage("tui_closed", player));
                return true;
            case "feedback":
                if (args.length >= 5) {
                    try {
                        String enchantId = args[2];
                        int level = Integer.parseInt(args[3]);
                        int price = Integer.parseInt(args[4]);
                        
                        plugin.getLogger().info(String.format("Setting feedback for player %s: enchant=%s level=%d price=%d", 
                            player.getName(), enchantId, level, price));
                            
                        String enchantName = messageManager.getEnchantName(
                            enchantId.replace("minecraft:", ""), 
                            messageManager.getBaseLanguageCode(player.getLocale())
                        );
                        state.setFeedbackMessage(messageManager.format("tui_price_added_feedback", player, enchantName, level, price));
                        
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning(String.format("Invalid number in feedback command: %s", String.join(" ", args)));
                    }
                } else {
                    plugin.getLogger().warning(String.format("Invalid feedback command args: %s", String.join(" ", args)));
                }
                break;
            default:
                plugin.getLogger().warning(String.format("Unknown TUI command: %s", args[1]));
                return false;
        }
        
        renderTUI(player, state);
        return true;
    }
} 