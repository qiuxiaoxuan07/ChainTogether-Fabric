package com.evailcodes.chaintogether.client;

import com.evailcodes.chaintogether.config.ChainConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChainRenderer {
    private static final Map<UUID, UUID> CLIENT_BOUND_PLAYERS = new HashMap<>();

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.matrixStack() == null) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            LocalPlayer localPlayer = mc.player;
            if (localPlayer == null || mc.level == null) {
                return;
            }

            renderWires(context.matrixStack().last().pose(), localPlayer,
                    context.tickCounter().getGameTimeDeltaPartialTick(true));
        });
    }

    public static void syncBoundStatus(UUID player1, UUID player2, boolean bound) {
        if (bound) {
            CLIENT_BOUND_PLAYERS.put(player1, player2);
            CLIENT_BOUND_PLAYERS.put(player2, player1);
        } else {
            CLIENT_BOUND_PLAYERS.remove(player1);
            CLIENT_BOUND_PLAYERS.remove(player2);
        }
    }

    private static boolean isPlayersBound(Player player1, Player player2) {
        return CLIENT_BOUND_PLAYERS.containsKey(player1.getUUID())
                && CLIENT_BOUND_PLAYERS.get(player1.getUUID()).equals(player2.getUUID());
    }

    private static void renderWires(Matrix4f pose, LocalPlayer localPlayer, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (localPlayer == null || mc.level == null) {
            return;
        }

        List<PlayerWireData> wires = new ArrayList<>();
        for (Player otherPlayer : mc.level.players()) {
            if (otherPlayer != localPlayer && isPlayersBound(localPlayer, otherPlayer)) {
                double maxDistance = ChainConfig.CHAIN_LENGTH.get() * 10.0;
                wires.add(new PlayerWireData(localPlayer.getUUID(), otherPlayer.getUUID(), maxDistance, true));
            }
        }

        if (wires.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.LINES);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        for (PlayerWireData wire : wires) {
            if (!wire.active()) {
                continue;
            }

            Player player1 = mc.level.getPlayerByUUID(wire.player1());
            Player player2 = mc.level.getPlayerByUUID(wire.player2());

            if (player1 == null || player2 == null) {
                continue;
            }

            Vec3 pos1 = player1.getPosition(partialTicks);
            Vec3 pos2 = player2.getPosition(partialTicks);
            double distance = pos1.distanceTo(pos2);
            double maxDistance = wire.maxDistance();

            if (distance > maxDistance * 2.0) {
                continue;
            }

            renderLineMatrix(pose, vertexConsumer, cameraPos, pos1, pos2, calculateColor(distance, maxDistance));
        }

        bufferSource.endBatch(RenderType.LINES);
    }

    private static void renderLineMatrix(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 cameraPos, Vec3 start, Vec3 end, Color color) {
        int alpha = (int) (ChainConfig.getTransparencyAsFloat() * 255);
        Vec3 startRelative = start.subtract(cameraPos);
        Vec3 endRelative = end.subtract(cameraPos);

        vertexConsumer.addVertex(pose, (float) startRelative.x, (float) startRelative.y + 1.0f, (float) startRelative.z)
                .setColor(color.getRed(), color.getGreen(), color.getBlue(), alpha)
                .setNormal(0, 1, 0);

        vertexConsumer.addVertex(pose, (float) endRelative.x, (float) endRelative.y + 1.0f, (float) endRelative.z)
                .setColor(color.getRed(), color.getGreen(), color.getBlue(), alpha)
                .setNormal(0, 1, 0);
    }

    private static Color calculateColor(double distance, double maxDistance) {
        double percentage = distance / maxDistance;

        if (percentage <= 0.5) {
            return Color.GREEN;
        }
        if (percentage <= 1.0) {
            float factor = (float) ((percentage - 0.5) / 0.5);
            int red = Math.min(255, (int) (255 * factor));
            int green = Math.min(255, 255 - red);
            return new Color(red, green, 0);
        }
        return Color.RED;
    }

    private record PlayerWireData(UUID player1, UUID player2, double maxDistance, boolean active) {
    }
}
