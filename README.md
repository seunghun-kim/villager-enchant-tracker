# Minecraft Villager Enchant Tracker

A Minecraft plugin for managing and tracking librarian villager enchantment trades.

https://github.com/user-attachments/assets/4098bd03-362b-4079-b558-4b7bf99ae9cc

## Features

- Register and manage librarian villager enchantment trades
- Search for specific enchantments and track their locations
- Visual location indicators using particle effects
- Region-based villager trade management
- Integration with EasyVillagerTrade mod
- Text User Interface (TUI) for enchantment management
- Multi-language support (English, Korean)

## Commands

### `/librariandb`
- `create <description>`: Register enchantment trades from a nearby librarian villager
- `search <enchantment>`: Search for villagers selling a specific enchantment
- `list`: Display all registered enchantment trades
- `delete <ID>`: Delete a specific trade entry
- `edit-description <ID> <description>`: Edit the description of a trade entry

### `/findvillager <enchantment>`
- Find nearby librarian villagers with specific enchantments and mark them with particles
- Searches both registered trades and nearby villagers within 50 blocks
- Shows clickable location messages with particle effects

### `/villagerregion`
- `create <name> [x1 y1 z1 x2 y2 z2]`: Create a new region for villager trade tracking (supports WorldEdit selection)
- `list`: List all registered regions
- `delete <ID>`: Delete a region
- `scan <regionName>`: Scan and register all librarian trades in the specified region

### `/evtintegration`
- [EVT (EasyVillagerTrade)](https://modrinth.com/mod/easyvillagertrade) integration with TUI interface
- `nearby <radius>`: Find and manage nearby librarian trades with TUI
- `region <regionName/*>`: Manage trades in specified region(s) with TUI
- Interactive TUI features:
  - Toggle between new and existing enchants
  - Multiple price options for each enchant
  - Pagination for large lists
  - Click-to-add functionality

## Permissions

- `villagerenchanttracker.use`: Basic permission for searching enchantments and using EVT integration
- `villagerenchanttracker.admin`: Admin permission for managing trades and regions
- `findvillager.use`: Permission for using findvillager command
- `region.admin`: Permission for managing regions
- `librariandb.use`: Permission for using librariandb command

## Installation

1. Copy the plugin JAR file to your server's `plugins` folder
2. Restart your server
3. Check the configuration files in the `plugins/VillagerEnchantTracker` folder

## Configuration

The plugin can be configured through the `config.yml` file:

### Particle Settings
```yaml
particles:
  # Particle type, height, count
  - type: "HAPPY_VILLAGER"
    height: 20
    count: 10
  - type: "ENCHANT"
    height: 20
    count: 10
  - type: "END_ROD"
    height: 20
    count: 5
  - type: "DRAGON_BREATH"
    height: 20
    count: 5
```

### Particle Duration and Interval
```yaml
particle-duration: 30    # Duration in seconds
particle-interval: 1     # Spawn interval in seconds
```

### Particle Effect Settings
```yaml
particle-effects:
  show-pillar: true      # Show particle pillar at villager location
  show-line: true        # Show particle line between player and villager
  line-points: 20        # Number of points in the particle line
  line-update-interval: 0.1  # Line update interval in seconds
```

## Changelog

### 0.3.0
- Added [EVT (EasyVillagerTrade)](https://modrinth.com/mod/easyvillagertrade) integration with TUI interface
- Added interactive Text User Interface (TUI) for enchantment management
- Added region scanning functionality with `/villagerregion scan`
- Enhanced search to include both registered and nearby villagers
- Added clickable chat messages for better interaction
- Improved permission system with more granular controls
- Removed language settings from config.yml (now uses client locale)
- Code refactoring and package reorganization
- Added support for new enchantments (Wind Burst, Density, Breach)

### 0.2.0
- Added region-based villager trade management with `/villagerregion` command
- Enhanced `/findvillager` command to search for specific enchantments
- Added WorldEdit integration for region selection
- Added basic user permission (`villagerenchanttracker.use`)

### 0.1.0
- Initial release
- Basic villager trade management
- Enchantment search functionality
- Particle effect visualization

## License

This project is licensed under the MIT License. 
