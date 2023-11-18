package su.untode.nrmc.wildspawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.util.*;

@SuppressWarnings("unused")
public final class WildSpawn extends JavaPlugin implements CommandExecutor, Listener, TabCompleter
{
    private static final String[] CMD_VERBS = { "randomize", "share", "accept", "deny" };
    private static final String TEXT_PREFIX = "[WildSpawn] ";

    private static final class SpawnPoint
    {
        public int X;
        public int Z;

        public SpawnPoint(int x, int z)
        {
            X = x;
            Z = z;
        }
    }

    private int globalSpawnRadius = 10000;

    private final Random random = new Random();
    private final HashMap<UUID, SpawnPoint> offers = new HashMap<>();

    private @Nonnull SpawnPoint assignSpawnPoint(@Nonnull World world, @Nonnull Player player)
    {
        final NamespacedKey key = new NamespacedKey(this, "spawn-point");
        final PersistentDataContainer container = player.getPersistentDataContainer();

        final int xposGlobal = random.nextInt(-globalSpawnRadius, globalSpawnRadius);
        final int zposGlobal = random.nextInt(-globalSpawnRadius, globalSpawnRadius);
        container.set(key, PersistentDataType.INTEGER_ARRAY, new int[] { xposGlobal, zposGlobal });

        return new SpawnPoint(xposGlobal, zposGlobal);
    }

    private @Nonnull SpawnPoint assignSpawnPoint(@Nonnull World world, @Nonnull Player player, @Nonnull SpawnPoint point)
    {
        final NamespacedKey key = new NamespacedKey(this, "spawn-point");
        final PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(key, PersistentDataType.INTEGER_ARRAY, new int[] { point.X, point.Z });
        return point;
    }

    private @Nonnull SpawnPoint getSpawnPoint(@Nonnull World world, @Nonnull Player player)
    {
        final NamespacedKey key = new NamespacedKey(this, "spawn-point");
        final PersistentDataContainer container = player.getPersistentDataContainer();

        if(container.has(key, PersistentDataType.INTEGER_ARRAY)) {
            final int[] point = Objects.requireNonNull(container.get(key, PersistentDataType.INTEGER_ARRAY));
            return new SpawnPoint(point[0], point[1]);
        }

        if(player.hasPlayedBefore()) {
            final Location worldspawn = world.getSpawnLocation();
            final SpawnPoint point = new SpawnPoint(worldspawn.getBlockX(), worldspawn.getBlockZ());
            return assignSpawnPoint(world, player, point);
        }

        return assignSpawnPoint(world, player);
    }

    private @Nonnull Location getSpawnLocation(@Nonnull World world, @Nonnull Player player)
    {
        final WorldBorder border = world.getWorldBorder();
        final int localSpawnRadius = Objects.requireNonNull(world.getGameRuleValue(GameRule.SPAWN_RADIUS));
        final SpawnPoint spawnPoint = getSpawnPoint(world, player);

        final Location center = border.getCenter();
        final int offset = Math.max(Math.abs(center.getBlockX()), Math.abs(center.getBlockZ()));


        final int xpos = spawnPoint.X + random.nextInt(-localSpawnRadius, localSpawnRadius);
        final int zpos = spawnPoint.Z + random.nextInt(-localSpawnRadius, localSpawnRadius);
        final int ypos = world.getHighestBlockYAt(xpos, zpos);
        final float yaw = 360.0f * random.nextFloat() - 180.0f;

        return new Location(world, xpos, ypos, zpos, yaw, 0.0f);
    }

    @Override
    public void reloadConfig()
    {
        super.reloadConfig();

        FileConfiguration config = getConfig();
        config.addDefault("global_spawn_radius", 10000);
        config.options().copyDefaults(true);

        globalSpawnRadius = config.getInt("global_spawn_radius");

        saveConfig();
    }

    @Override
    public void onEnable()
    {
        reloadConfig();

        final PluginCommand command = Objects.requireNonNull(getCommand("wildspawn"));
        command.setTabCompleter(this);
        command.setExecutor(this);

        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        final Player player = event.getPlayer();

        if(!player.hasPlayedBefore()) {
            final World world = player.getWorld();
            final Location spawn = getSpawnLocation(world, player);

            spawn.getChunk().load();

            player.teleport(spawn);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event)
    {
        if(!event.isAnchorSpawn() && !event.isBedSpawn()) {
            final World world = event.getRespawnLocation().getWorld();
            final Player player = event.getPlayer();
            final Location spawn = getSpawnLocation(world, player);
            event.setRespawnLocation(spawn);
        }
    }

    @Override
    public @Nonnull List<String> onTabComplete(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String alias, @Nonnull String[] args)
    {
        final List<String> completions = new ArrayList<>();

        if(args.length == 1) {
            for(String verb : CMD_VERBS) {
                if(!verb.toLowerCase().startsWith(args[0].toLowerCase()))
                    continue;
                completions.add(verb);
            }
        }

        if(args.length == 2 && (args[0].equalsIgnoreCase("share"))) {
            for(Player player : Bukkit.getOnlinePlayers()) {
                if(!player.getName().startsWith(args[1]))
                    continue;
                completions.add(player.getName());
            }
        }

        return completions;
    }

    @Override
    public boolean onCommand(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String label, @Nonnull String[] args)
    {
        if(sender instanceof Player player) {
            if(args.length >= 1) {
                if(args[0].equalsIgnoreCase("randomize")) {
                    if((args.length == 1) || !args[1].equalsIgnoreCase("confirm")) {
                        final TextComponent.Builder text1 = Component.text();
                        text1.append(Component.text(TEXT_PREFIX, NamedTextColor.RED, TextDecoration.BOLD));
                        text1.append(Component.text("Randomizing will "));
                        text1.append(Component.text("OVERWRITE", NamedTextColor.WHITE, TextDecoration.BOLD));
                        text1.append(Component.text(" your current worldspawn point!"));
                        text1.append(Component.text(" This action is "));
                        text1.append(Component.text("IRREVERSIBLE!!!", NamedTextColor.WHITE, TextDecoration.BOLD));
                        player.sendMessage(text1.build());

                        final TextComponent.Builder text2 = Component.text();
                        text2.append(Component.text(TEXT_PREFIX, NamedTextColor.RED, TextDecoration.BOLD));
                        text2.append(Component.text("Use "));
                        text2.append(Component.text("/wsp randomize confirm", NamedTextColor.GOLD, TextDecoration.ITALIC));
                        text2.append(Component.text(" to proceed."));
                        player.sendMessage(text2.build());

                        return true;
                    }

                    assignSpawnPoint(player.getWorld(), player);

                    final TextComponent.Builder text3 = Component.text();
                    text3.append(Component.text(TEXT_PREFIX, NamedTextColor.GREEN, TextDecoration.BOLD));
                    text3.append(Component.text("Your worldspawn point has been randomized!"));
                    player.sendMessage(text3.build());

                    return true;
                }

                if(args[0].equalsIgnoreCase("accept")) {
                    final UUID uuid = player.getUniqueId();
                    final SpawnPoint spawn = offers.get(uuid);

                    if(spawn == null) {
                        final TextComponent.Builder text1 = Component.text();
                        text1.append(Component.text(TEXT_PREFIX, NamedTextColor.RED, TextDecoration.BOLD));
                        text1.append(Component.text("Nothing to accept!"));
                        player.sendMessage(text1.build());

                        return true;
                    }

                    if((args.length == 1) || !args[1].equalsIgnoreCase("confirm")) {
                        final TextComponent.Builder text2 = Component.text();
                        text2.append(Component.text(TEXT_PREFIX, NamedTextColor.RED, TextDecoration.BOLD));
                        text2.append(Component.text("Accepting will "));
                        text2.append(Component.text("OVERWRITE", NamedTextColor.WHITE, TextDecoration.BOLD));
                        text2.append(Component.text(" your current worldspawn point!"));
                        text2.append(Component.text(" This action is "));
                        text2.append(Component.text("IRREVERSIBLE!!!", NamedTextColor.WHITE, TextDecoration.BOLD));
                        player.sendMessage(text2.build());

                        final TextComponent.Builder text3 = Component.text();
                        text3.append(Component.text(TEXT_PREFIX, NamedTextColor.RED, TextDecoration.BOLD));
                        text3.append(Component.text("Use "));
                        text3.append(Component.text("/wsp accept confirm", NamedTextColor.GOLD, TextDecoration.ITALIC));
                        text3.append(Component.text(" proceed."));
                        player.sendMessage(text3.build());

                        return true;
                    }

                    offers.remove(uuid);

                    assignSpawnPoint(player.getWorld(), player, spawn);

                    final TextComponent.Builder text4 = Component.text();
                    text4.append(Component.text(TEXT_PREFIX, NamedTextColor.GREEN, TextDecoration.BOLD));
                    text4.append(Component.text("Your worldspawn point has been updated!"));
                    player.sendMessage(text4.build());

                    return true;
                }

                if(args[0].equalsIgnoreCase("deny")) {
                    final UUID uuid = player.getUniqueId();
                    final SpawnPoint spawn = offers.get(uuid);

                    if(spawn == null) {
                        final TextComponent.Builder text1 = Component.text();
                        text1.append(Component.text(TEXT_PREFIX, NamedTextColor.RED, TextDecoration.BOLD));
                        text1.append(Component.text("Nothing to deny!"));
                        player.sendMessage(text1.build());

                        return true;
                    }

                    offers.remove(uuid);

                    final TextComponent.Builder text2 = Component.text();
                    text2.append(Component.text(TEXT_PREFIX, NamedTextColor.GREEN, TextDecoration.BOLD));
                    text2.append(Component.text("Worldspawn offer has been politely rejected!"));
                    player.sendMessage(text2.build());

                    return true;
                }
            }

            if(args.length >= 2) {
                if(args[0].equalsIgnoreCase("share")) {
                    final Player target = Bukkit.getPlayer(args[1]);

                    if((target != null) && (target != player)) {
                        final UUID uuid = target.getUniqueId();
                        final SpawnPoint spawn = getSpawnPoint(player.getWorld(), player);

                        offers.put(uuid, spawn);

                        final TextComponent.Builder text1 = Component.text();
                        text1.append(Component.text(TEXT_PREFIX, NamedTextColor.BLUE, TextDecoration.BOLD));
                        text1.append(Component.text(String.format("%s shared their worldspawn point with you!", player.getName())));
                        target.sendMessage(text1.build());

                        final TextComponent.Builder text2 = Component.text();
                        text2.append(Component.text(TEXT_PREFIX, NamedTextColor.BLUE, TextDecoration.BOLD));
                        text2.append(Component.text("Use "));
                        text2.append(Component.text("/wsp accept", NamedTextColor.GOLD, TextDecoration.ITALIC));
                        text2.append(Component.text(" to accept the offer."));
                        target.sendMessage(text2.build());

                        final TextComponent.Builder text3 = Component.text();
                        text3.append(Component.text(TEXT_PREFIX, NamedTextColor.GREEN, TextDecoration.BOLD));
                        text3.append(Component.text(String.format("Your worldspawn point has been shared with %s!", target.getName())));
                        player.sendMessage(text3.build());

                        return true;
                    }

                    final TextComponent.Builder text4 = Component.text();
                    text4.append(Component.text(TEXT_PREFIX, NamedTextColor.RED, TextDecoration.BOLD));
                    text4.append(Component.text("No valid player found!"));
                    player.sendMessage(text4.build());

                    return true;
                }
            }
        }

        return true;
    }
}
