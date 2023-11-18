# WildSpawn
WildSpawn's sole purpose is to spread players far apart from eachother while retaining some sense of being fair to them.  

### Why
* Vanilla's `spawnRadius` gamerule is okay but generally unfair for players if set to a large value, say 10000 blocks.
* A little something called "NRMC manifesto", which is a (WIP) piece of media full of recomendations for setting up an SMP to be perfect from my point of view. This "manifesto" includes _survival first, multiplayer second_ entry.

# Usage examples
1. Randomizing your own spawn point - `/wsp randomize`:
   ![](2023-11-19_00-56.png)
   ![](2023-11-19_00-56_1.png)
2. Sharing your spawn point with others - `/wsp share %playername%`:
   ![](2023-11-19_00-53.png)
3. Accepting a shared spawn point - `/wsp accept`:
   ![](2023-11-19_00-54.png)
   ![](2023-11-19_00-55.png)
   ![](2023-11-19_00-55_1.png)
4. Or politely rejecting it - `/wsp deny`:
   ![](2023-11-19_00-54_1.png)

# Configuration
Example config:
```yaml
# plugins/WildSpawn/config.yml
global_spawn_radius: 10000
```

The value of `global_spawn_radius` defines the radius in which worldspawn point coordinates will be chosen randomly when required. These values (best case scenario) are chosen only ONCE, and then a value offset using the vanilla `spawnRadius` is used as a spawn location.
