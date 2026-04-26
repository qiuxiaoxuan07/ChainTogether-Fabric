package com.evailcodes.chaintogether.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class ChainPlayerHandler {
    public static boolean isChained(Player player) {
        return player instanceof ServerPlayer serverPlayer && ChainHandler.getPartner(serverPlayer) != null;
    }

    public static ServerPlayer getPartner(Player player) {
        return player instanceof ServerPlayer serverPlayer ? ChainHandler.getPartner(serverPlayer) : null;
    }

    public static UUID getPartnerId(Player player) {
        ServerPlayer partner = getPartner(player);
        return partner != null ? partner.getUUID() : null;
    }
}
