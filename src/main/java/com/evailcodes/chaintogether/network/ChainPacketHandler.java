package com.evailcodes.chaintogether.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public class ChainPacketHandler {
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(UpdateChainLengthPacket.TYPE, UpdateChainLengthPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncBoundStatusPacket.TYPE, SyncBoundStatusPacket.STREAM_CODEC);
    }

    public static void registerClient() {
        // Client receivers are registered from the client entrypoint.
    }

    public static <MSG extends CustomPacketPayload> void sendToPlayer(MSG message, ServerPlayer player) {
        ServerPlayNetworking.send(player, message);
    }
}
