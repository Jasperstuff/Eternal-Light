package me.masstrix.eternallight.handle;

import me.masstrix.eternallight.EternalLightConfig;
import me.masstrix.eternallight.util.RGBParticle;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.*;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.*;

public class LightVisual {

    private final UUID PLAYER_UUID;
    private Projector projector;
    private Player player;
    private boolean enabled = false;
    private DisplayMethod method;
    private EternalLightConfig config;

    public LightVisual(Projector projector, UUID uuid) {
        this.projector = projector;
        PLAYER_UUID = uuid;
        player = Bukkit.getPlayer(uuid);
        config = projector.plugin.getPluginConfig();
        method = config.getDefaultMode();
    }

    /**
     * @return if the visual display is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable the and show the light levels as a visualized display.
     */
    public void show() {
        enabled = true;
        update();
    }

    /**
     * Disable the light level display.
     */
    public void hide() {
        if (!enabled) return;
        enabled = false;
        update();
    }

    /**
     * @return the state of the visual before it was toggled.
     */
    public boolean toggle() {
        enabled = !enabled;
        if (enabled) show();
        else hide();
        return !enabled;
    }

    /**
     * Send an update to the player of the surrounding systems.
     */
    public void update() {
        if (enabled) send();
    }

    /**
     * Sets the display type of the projector.
     *
     * @param method what display method will be used to render light levels.
     */
    public void setMethod(DisplayMethod method) {
        this.method = method;
    }

    /**
     * Toggle between display modes.
     */
    public void toggleType() {
        this.method = method.next();
    }

    /**
     * @return the current display method being used.
     */
    public DisplayMethod getMethod() {
        return method;
    }

    private void send() {
        if (this.player == null) {
            projector.remove(PLAYER_UUID);
            return;
        }
        Location loc = player.getLocation().clone();
        World world = loc.getWorld();
        int px = loc.getBlockX(), py = loc.getBlockY(), pz = loc.getBlockZ();
        int rad = config.getRadius();

        RGBParticle particle = new RGBParticle(0, 0, 0);

        for (int z = -rad; z <= rad; z++) {
            for (int x = -rad; x <= rad; x++) {
                for (int y = -rad; y <= rad; y++) {
                    if(Math.sqrt((x * x) + (y * y) + (z * z)) > rad) continue;
                    //if (x * x + y * y + z * z < rad * rad) continue;
                    assert world != null;
                    Block block = world.getBlockAt(px + x, py + y, pz + z);
                    SpawnValue spawnValue = SpawnValue.get(block.getType());
                    BlockData blockData = block.getBlockData();

                    if (block.isPassable()) continue;

                    // Update for stairs
                    if (blockData instanceof Stairs) {
                        if (isStairInSpawnRotation(block)) spawnValue = SpawnValue.ALWAYS;
                        else continue;
                    }
                    if (spawnValue != SpawnValue.ALWAYS) continue;

                    // Validate if there is a 2 block gap above for mobs to spawn.
                    boolean valid = true;
                    for (int yValid = 1; yValid <= 2; yValid++) {
                        Block above = world.getBlockAt(px + x, (py + y) + yValid, pz + z);
                        if (above.getType() == Material.AIR || above.getType() == Material.CAVE_AIR) continue;
                        if (above.isPassable()) continue;
                        SpawnValue sv = SpawnValue.get(above.getType());
                        if (sv != SpawnValue.TRANSPARENT) {
                            valid = false;
                            break;
                        }
                    }
                    if (!valid) continue;

                    Block onTop = world.getBlockAt(px + x, (py + y) + 1, pz + z);

                    if (this.method == DisplayMethod.SPAWNABLE) {
                        LightSpawnCase spawnCase = LightSpawnCase.getCase(onTop);
                        if (spawnCase == LightSpawnCase.NEVER) continue;
                        particle.setColor(spawnCase.color);
                    } else if (this.method == DisplayMethod.ALL) {
                        LightSpawnCase spawnCase = LightSpawnCase.getCase(onTop);
                        particle.setColor(spawnCase.color);
                    } else if (this.method == DisplayMethod.LIGHTLEVEL) {
                        float p = (float) onTop.getLightFromBlocks() / 14F;
                        Color c = new Color(255, 0, 6);

                        // Get saturation and brightness.
                        float[] hsbVals = new float[3];
                        Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsbVals);

                        // Shift the hue around by 25%
                        c = new Color(Color.HSBtoRGB((0.25f * p), hsbVals[1], hsbVals[2]));
                        particle.setColor(c);
                    }
                    particle.send(player, (px + x) + 0.5, (py + y) + getBlockHeight(block) + 0.2, (pz + z) + 0.5);
                }
            }
        }
    }

    /**
     * @param block block to get height of.
     * @return the height of the given block.
     */
    private double getBlockHeight(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Slab) {
            Slab slab = (Slab) data;
            return slab.getType() == Slab.Type.BOTTOM ? 0.5 : 1;
        }
        else if (data instanceof Snow) {
            Snow snow = (Snow) data;
            return (double) snow.getLayers() / (double) snow.getMaximumLayers();
        }
        return 1;
    }

    private boolean isStairInSpawnRotation(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Stairs) {
            Stairs stair = (Stairs) data;
            return stair.getHalf() == Bisected.Half.TOP;
        }
        return false;
    }

    private enum LightSpawnCase {
        NIGHT_SPAWN(Color.YELLOW), NEVER(Color.GREEN), ALWAYS(Color.RED);

        Color color;

        LightSpawnCase(Color color) {
            this.color = color;
        }

        public static LightSpawnCase getCase(Block block) {
            if (block.getLightFromBlocks() > 7) return NEVER;
            if (block.getLightFromSky() > 7) return NIGHT_SPAWN;
            else return ALWAYS;
        }
    }
}
