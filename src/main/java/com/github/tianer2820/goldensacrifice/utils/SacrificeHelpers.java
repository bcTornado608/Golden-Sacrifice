package com.github.tianer2820.goldensacrifice.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.github.tianer2820.goldensacrifice.GoldenSacrifice;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class SacrificeHelpers {
    private static final Map<Material, Integer> MATERIAL_TO_ENERGY_MAP = new ImmutableMap.Builder<Material, Integer>()
        // Leaves
        .put(Material.OAK_LEAVES, 1)
        .put(Material.SPRUCE_LEAVES, 1)
        .put(Material.BIRCH_LEAVES, 1)
        .put(Material.JUNGLE_LEAVES, 1)
        .put(Material.ACACIA_LEAVES, 1)
        .put(Material.CHERRY_LEAVES, 1)
        .put(Material.DARK_OAK_LEAVES, 1)
        .put(Material.MANGROVE_LEAVES, 1)
        .put(Material.AZALEA_LEAVES, 1)
        .put(Material.FLOWERING_AZALEA_LEAVES, 1)
        // Crops
        .put(Material.WHEAT, 2)
        .put(Material.CARROT, 2)
        .put(Material.POTATO, 2)
        // Special blocks
        .put(Material.HAY_BLOCK, 20)
        // Add more
        .build();

    private static final Set<Block> protectedAltarBlocks = new HashSet<>();

    public static boolean isValidAltar(Block headBlock){
        return !detectValidAltar(headBlock).isEmpty();
    }

    public static Set<Block> detectValidAltar(Block headBlock){
        if(!ImmutableSet.of(Material.PLAYER_HEAD, Material.PLAYER_WALL_HEAD).contains(headBlock.getType())){
            return Collections.emptySet();
        }
        if(protectedAltarBlocks.contains(headBlock)){
            // already has an ongoing sacrifice, ignore
            return Collections.emptySet();
        }

        Set<Block> detectedBlocks = new HashSet<>();
        detectedBlocks.add(headBlock);

        // check if the blocks under are all stones
        Block under = headBlock.getRelative(BlockFace.DOWN);
        for (int h = 0; h <= 1; h++) {
            Set<Block> layer = detectValidSquare(under.getRelative(0, -h, 0), h, Material.COBBLESTONE);
            if(layer.isEmpty()){
                return Collections.emptySet();
            }
            detectedBlocks.addAll(layer);
        }
        return detectedBlocks;
    }

    public static boolean tryBeginSacrifice(Block headBlock){
        // detect valid altar
        Set<Block> altarBlocks = detectValidAltar(headBlock);
        if(altarBlocks.isEmpty()){
            GoldenSacrifice.getInstance().getLogger().info("not altar");
            return false;
        }
        GoldenSacrifice.getInstance().getLogger().info("Is valid altar! ");
        
        // detect valid player
        Skull skull = (Skull)headBlock.getState();
        OfflinePlayer offlinePlayer = skull.getOwningPlayer();
        if(offlinePlayer == null){
            GoldenSacrifice.getInstance().getLogger().info("no player");
            return false;
        }
        Player player = offlinePlayer.getPlayer();
        if(player == null){
            return false;
        }
        // if(player.getGameMode() != GameMode.SPECTATOR){
        //     return;
        // }

        // begin the sacrifice process
        GoldenSacrifice.getInstance().getLogger().info("tasks running");
        protectedAltarBlocks.addAll(altarBlocks);
        new SacrificeRunnable(headBlock, player, altarBlocks).runTaskTimer(GoldenSacrifice.getInstance(), 0, 20);
        return true;
    }

    public static boolean isProtectedBlock(Block block){
        return protectedAltarBlocks.contains(block);
    }

    /**
     * Detect if a NxN square are all made by the same block type
     * radius=1 means 3x3, radius=2 means 5x5, etc.
     */
    private static Set<Block> detectValidSquare(Block center, int radius, Material blockType){
        Set<Block> blocks = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block block = center.getRelative(dx, 0, dz);
                if(block.getType() != blockType){
                    return Collections.emptySet();
                }
                blocks.add(block);
            }
        }
        return blocks;
    }


    /**
     * The background sacrifice process
     */
    private static class SacrificeRunnable extends BukkitRunnable{
        private Block headBlock;
        private Player player;
        Set<Block> altarBlocks;

        private int progress = 0;
        private int energyCollected = 0;

        private static final int ENERGY_NEEDED = 100;
        private static final int RANGE_LIMIT = 32;

        public SacrificeRunnable(Block headBlock, Player player, Set<Block> altarBlocks){
            this.headBlock = headBlock;
            this.player = player;
            this.altarBlocks = altarBlocks;
        }

        @Override
        public void run() {
            // limit the range
            progress += 1;
            if(progress > RANGE_LIMIT){
                cancel();
                protectedAltarBlocks.removeAll(altarBlocks);
            }
            
            // check each block
            getCubeShellLocations(headBlock.getLocation(), progress).forEach(location -> {
                Block block = location.getBlock();
                Material blockType = block.getType();
                int energy = MATERIAL_TO_ENERGY_MAP.getOrDefault(blockType, 0);
                if(energy > 0){
                    block.setType(Material.AIR);
                    energyCollected += energy;
                }
            });

            // respawn if energy is full
            if(energyCollected >= ENERGY_NEEDED && player.isValid()){
                // do respawn player
                headBlock.setType(Material.AIR);
                player.teleport(headBlock.getLocation());
                player.setGameMode(GameMode.SURVIVAL);
                cancel();
                protectedAltarBlocks.removeAll(altarBlocks);
            }
        }

        private Set<Location> getCubeShellLocations(Location center, int radius){
            Set<Location> locations = new HashSet<>();
            int cx = center.getBlockX();
            int cz = center.getBlockZ();

            for (int h = -radius; h <= radius; h++) {
                int layer = center.getBlockY() + h;

                for (int i = -radius; i < radius; i++) {
                    locations.add(new Location(center.getWorld(), cx+i, layer, cz-radius));
                }
                for (int i = -radius; i < radius; i++) {
                    locations.add(new Location(center.getWorld(), cx+radius, layer, cz+i));
                }
                for (int i = -radius; i < radius; i++) {
                    locations.add(new Location(center.getWorld(), cx-i, layer, cz+radius));
                }
                for (int i = -radius; i < radius; i++) {
                    locations.add(new Location(center.getWorld(), cx-radius, layer, cz-i));
                }
            }
            return locations;
        }

    }
}
