# Minecraft Librarian Database

A Minecraft plugin for managing and tracking librarian villager enchantment trades.

## Features

- Register and manage librarian villager enchantment trades
- Search for specific enchantments and track their locations
- Visual location indicators using particle effects

## Commands

### `/librariandb`
- `create <description>`: Register enchantment trades from a nearby librarian villager.
- `search <enchantment>`: Search for villagers selling a specific enchantment.
- `list`: Display all registered enchantment trades.
- `delete <ID>`: Delete a specific trade entry.
- `edit-description <ID> <description>`: Edit the description of a trade entry.

### `/findvillager`
- Find nearby librarian villagers and mark them with particles.

## Permissions

- `villagerenchanttracker.admin`: Admin permission

## Installation

1. Copy the plugin JAR file to your server's `plugins` folder.
2. Restart your server.
3. Check the configuration files in the `plugins/VillagerEnchantTracker` folder.

## Configuration

The plugin can be configured through the `config.yml` file:

### Language Settings
```yaml
language:
  chat: "en"   # Chat output language (en, ko)
```

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

## License

This project is licensed under the MIT License. 