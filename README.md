# FarmControl
[Hangar](https://hangar.papermc.io/froobynooby/FarmControl) | [Modrinth](https://modrinth.com/plugin/farmcontrol) | [Spigot](https://www.spigotmc.org/resources/86923/)

## About
FarmControl is a Bukkit plugin that allows you to control certain properties of farms on your server.

**Features**:
* Disable breeding in oversized animal farms and villager breeders.
* Reduce unnecessary random movement within mob farms.
* Disable the AI of mobs in farms.
* Limit the number of entities allowed in an area.
* Highly configurable - allowing you to tailor the plugin to your needs.
* Low impact - with the brunt of the plugin's processing performed asynchronously.

This fork enforces independent, code-owned per-chunk limits: 20 hostile mobs, 20 non-hostile mobs other than
villagers, and 15 villagers. An entity can contribute to only one of these three limits. These limit profiles
cannot be configured: obsolete copies are removed from `profiles.yml` and profile assignments in `config.yml`
when the plugin starts or reloads.

## Building
If you would like to build the plugin yourself you can follow these steps.

1\. Clone FarmControl and build
```bash
git clone https://github.com/froobynooby/FarmControl
cd FarmControl
./gradlew clean build
```

2\. Find jar in `FarmControl/build/libs`

## Death watch (fork addition)
The death watch answers the question "what is killing the mobs in my farm?".

For every mob that dies or vanishes inside a configured zone it records:
* what died — entity type, custom name, UUID and exact coordinates,
* what it died of — the damage cause (`LAVA`, `FALL`, `ENTITY_ATTACK`, `CRAMMING`, …) or the removal reason
  (`REMOVE_DESPAWN`, `REMOVE_TRANSFORMATION`, `REMOVE_MERGE`, `REMOVE_PLUGIN`, …),
* which mob did it — projectiles, area effect clouds, TNT and evoker fangs are followed back to the mob that
  fired them, along with its distance from the victim and the item it was holding,
* which plugin did it, when a mob was removed by plugin code,
* FarmControl's own culls, logged as `FARMCONTROL_KILL` / `FARMCONTROL_REMOVE` so they can never be mistaken
  for something else.

Zones are configured in `plugins/FarmControl/debug.yml` as cylinders (`radius` horizontally, `vertical-radius`
above and below `y`). Records go to `plugins/FarmControl/logs/YYYY-MM-DD.log` and are kept in memory for:

```
/fc deaths                      # last 60 minutes, all zones
/fc deaths nether-farm 180      # one zone, last 3 hours
/fc deaths world 60 list        # one world, plus the latest individual entries
/fc deaths zones                # show the configured zones
```

The summary breaks the losses down by victim type, by cause, by killer mob, and as `victim <- culprit` pairs.
Requires the `farmcontrol.command.deaths` permission (op by default). Removal tracking needs Paper's
`EntityRemoveEvent`; deaths are logged on any Spigot-compatible server. Folia-safe.
