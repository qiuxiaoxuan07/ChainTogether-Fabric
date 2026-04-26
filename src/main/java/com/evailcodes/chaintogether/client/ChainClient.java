package com.evailcodes.chaintogether.client;

import com.evailcodes.chaintogether.network.ChainPacketHandler;
import com.evailcodes.chaintogether.network.SyncBoundStatusPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ChainClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ChainRenderer.register();

        ClientPlayNetworking.registerGlobalReceiver(SyncBoundStatusPacket.TYPE, (packet, context) ->
                context.client().execute(() ->
                        ChainRenderer.syncBoundStatus(packet.player1(), packet.player2(), packet.bound())));

        ChainPacketHandler.registerClient();
    }
}
