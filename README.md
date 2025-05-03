# Villager Enchant Tracker

A Minecraft plugin for tracking villager enchantment trades across your server.

https://github.com/user-attachments/assets/4098bd03-362b-4079-b558-4b7bf99ae9cc

## Features

- **Track Villager Trades**: Register and manage enchantment trades from villagers.
- **Search Enchantments**: Find specific enchantments offered by villagers nearby or in your database.
- **Region Management**: Define regions to organize villager trades by location.
- **Particle Guidance**: Visual indicators to guide you to villager locations.
- **Enchantment TUI**: Text-based user interface for easy enchantment management.
- **EasyVillagerTrade Integration**: Scan and manage enchantment trades with the [EasyVillagerTrade](https://modrinth.com/mod/easyvillagertrade) mod.

## Installation

1. Download the latest version of the plugin from the releases page.
2. Place the `.jar` file in your server's `plugins` folder.
3. Restart your server to load the plugin.

## Core Functionality: Finding Villagers with Specific Enchantments

If you're looking for a quick and simple way to locate villagers offering specific enchantments without the need for region management or trade registration, the `/findvillager` command is all you need. This core feature allows you to:
- Search for nearby villagers with a specific enchantment.
- View the region name if the villager is located within a defined region.
- Use particle effects to visually guide you to the villager's location.

This command is perfect for players who want to focus on finding enchantment trades without managing a database of trades or regions.

## Commands

- **`/findvillager <enchantment>`**: Find nearby villagers offering a specific enchantment. If the villager is in a region, the region name will be displayed.
- **`/vet trade`**: Manage villager trades.
  - `/vet trade create` - Register trades from the closest villager.
  - `/vet trade search <enchantment>` - Search for specific enchantment trades.
  - `/vet trade list` - List all registered trades.
  - `/vet trade delete <id>` - Delete a specific trade.
- **`/vet region`**: Manage regions for organizing trades.
  - `/vet region create <name> [x1 y1 z1 x2 y2 z2]` - Create a region with a name and optional coordinates. (supports WorldEdit selection)
  - `/vet region list` - List all defined regions.
  - `/vet region delete <id>` - Delete a specific region.
  - `/vet region edit <id> <newName>` - Edit the name of a specific region.
- **`/vet evt`**: Integration commands for [EasyVillagerTrade](https://modrinth.com/mod/easyvillagertrade).
  - `/vet evt nearby <radius>` - Scan enchantment trades of nearby villagers within the specified radius.
  - `/vet evt region <regionName/*>` - Scan enchantment trades of villagers in a specific region or all regions.

**EasyVillagerTrade Integration Features**:
- Scan and display enchantment trades from nearby villagers or within specific regions.
- Results are categorized into enchantments not sold by villagers (highest level only) and enchantments currently sold by villagers.
- Click on the displayed [New Price] in the chat to automatically input the `/evt search add <price> <enchantment> [<level>]` command.
- Allows users to add missing enchantments to EVT or find lower prices for existing enchantments.

## Permissions

- `villagerenchanttracker.use` - Allows use of read-only features (search, list, etc). Default: true
- `villagerenchanttracker.write` - Allows use of write features (create, delete, edit, etc). Default: op

## Configuration

The plugin configuration file is located at `plugins/VillagerEnchantTracker/config.yml`. You can customize particle effects and other settings here.

Language files are located in `plugins/VillagerEnchantTracker/localization/`. You can modify existing translations or add new ones by copying the format of existing language files.

## Dependencies

- **WorldEdit** (optional) - For region selection support.
- **EasyVillagerTrade** (optional) - For enhanced villager trading integration on the client-side.

## License

This project is licensed under the MIT License. 
