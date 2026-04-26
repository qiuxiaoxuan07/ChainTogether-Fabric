package com.evailcodes.chaintogether.handler;

import com.evailcodes.chaintogether.ChainCommand;
import com.evailcodes.chaintogether.ChainTogether;
import com.evailcodes.chaintogether.config.ChainConfig;
import com.evailcodes.chaintogether.network.ChainPacketHandler;
import com.evailcodes.chaintogether.network.SyncBoundStatusPacket;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ChainHandler {
    private static final Map<UUID, UUID> CHAINED_PLAYERS = new HashMap<>();
    private static final Set<UUID> DEAD_PLAYERS = new HashSet<>();
    private static final Set<UUID> FOLLOW_TELEPORTING = new HashSet<>();

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                ChainTogether.LOGGER.info("ChainTogether game rules configured!"));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ChainCommand.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onPlayerTick(player);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                DEAD_PLAYERS.add(player.getUUID());
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onPlayerRespawn(newPlayer));

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof ServerPlayer targetPlayer)) {
                return InteractionResult.PASS;
            }

            ItemStack stack = serverPlayer.getItemInHand(hand);
            if (stack.getItem() == Items.LEAD && !isChained(serverPlayer) && !isChained(targetPlayer)) {
                bindPlayers(serverPlayer, targetPlayer);
                if (!serverPlayer.isCreative()) {
                    stack.shrink(1);
                }
                return InteractionResult.SUCCESS;
            }

            if (arePlayersChained(serverPlayer, targetPlayer) || isChained(serverPlayer) || isChained(targetPlayer)) {
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && stack.getItem() == Items.LEAD && isChained(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (source.getEntity() instanceof ServerPlayer attacker && entity instanceof ServerPlayer victim) {
                return !isChained(attacker) && !isChained(victim);
            }
            return true;
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                onPlayerChangedDimension(player, destination));
    }

    private static void onPlayerTick(ServerPlayer player) {
        ServerPlayer partner = getPartner(player);
        if (partner != null && player.level() == partner.level()) {
            updateChain(player, partner);
            enforceChainRules(player, partner);
        }
    }

    private static void onPlayerRespawn(ServerPlayer player) {
        DEAD_PLAYERS.remove(player.getUUID());

        ServerPlayer partner = getPartner(player);
        if (partner == null || DEAD_PLAYERS.contains(partner.getUUID()) || !partner.isAlive()) {
            return;
        }

        teleportPlayerToPartner(player, partner, (ServerLevel) partner.level());
    }

    private static void onPlayerChangedDimension(ServerPlayer player, ServerLevel destination) {
        ServerPlayer partner = getPartner(player);
        if (partner == null || !partner.isAlive() || partner.level() == destination) {
            return;
        }

        player.getServer().execute(() -> {
            if (partner.isAlive() && partner.level() != player.level()) {
                teleportPlayerToPartner(partner, player, (ServerLevel) player.level());
            }
        });
    }

    public static void onPlayerTeleported(ServerPlayer player, ServerLevel targetLevel, double x, double y, double z) {
        if (FOLLOW_TELEPORTING.remove(player.getUUID())) {
            return;
        }

        ServerPlayer partner = getPartner(player);
        if (partner == null || !partner.isAlive()) {
            return;
        }

        if (partner.isAlive()) {
            teleportPlayerToLocation(partner, targetLevel, x, y, z);
        }
    }

    public static void onEnderPearlTeleported(ServerPlayer player) {
        onPlayerTeleported(player, (ServerLevel) player.level(), player.getX(), player.getY(), player.getZ());
    }

    public static boolean isChained(ServerPlayer player) {
        return getPartner(player) != null;
    }

    public static void bindPlayers(ServerPlayer player1, ServerPlayer player2) {
        if (Objects.equals(player1.getUUID(), player2.getUUID())) {
            return;
        }

        unbindPlayer(player1);
        unbindPlayer(player2);

        CHAINED_PLAYERS.put(player1.getUUID(), player2.getUUID());
        CHAINED_PLAYERS.put(player2.getUUID(), player1.getUUID());

        SyncBoundStatusPacket packet = new SyncBoundStatusPacket(player1.getUUID(), player2.getUUID(), true);
        ChainPacketHandler.sendToPlayer(packet, player1);
        ChainPacketHandler.sendToPlayer(packet, player2);

        player1.sendSystemMessage(Component.translatable("chaintogether.bound.success", player2.getName().getString()));
        player2.sendSystemMessage(Component.translatable("chaintogether.bound.success", player1.getName().getString()));

        ChainTogether.LOGGER.info("Bound players: {} and {}",
                player1.getName().getString(), player2.getName().getString());
    }

    public static void unbindPlayer(ServerPlayer player) {
        UUID partnerId = CHAINED_PLAYERS.remove(player.getUUID());
        if (partnerId == null) {
            return;
        }

        CHAINED_PLAYERS.remove(partnerId);

        ServerPlayer partner = player.getServer().getPlayerList().getPlayer(partnerId);
        SyncBoundStatusPacket packet = new SyncBoundStatusPacket(player.getUUID(), partnerId, false);
        ChainPacketHandler.sendToPlayer(packet, player);

        if (partner != null) {
            ChainPacketHandler.sendToPlayer(packet, partner);
            player.sendSystemMessage(Component.translatable("chaintogether.unbound.success", partner.getName().getString()));
            partner.sendSystemMessage(Component.translatable("chaintogether.unbound.success", player.getName().getString()));
        } else {
            player.sendSystemMessage(Component.translatable("chaintogether.unbound.success.alone"));
        }

        ChainTogether.LOGGER.info("Unbound player: {}", player.getName().getString());
    }

    public static ServerPlayer getPartner(ServerPlayer player) {
        UUID partnerId = CHAINED_PLAYERS.get(player.getUUID());
        return partnerId != null ? player.getServer().getPlayerList().getPlayer(partnerId) : null;
    }

    public static boolean arePlayersChained(ServerPlayer player1, ServerPlayer player2) {
        return Objects.equals(CHAINED_PLAYERS.get(player1.getUUID()), player2.getUUID());
    }

    public static Collection<ServerPlayer> getChainedPlayers(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        Set<UUID> processedPlayers = new HashSet<>();

        for (Map.Entry<UUID, UUID> entry : new HashSet<>(CHAINED_PLAYERS.entrySet())) {
            UUID playerId = entry.getKey();
            UUID partnerId = entry.getValue();
            if (processedPlayers.add(playerId)) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    players.add(player);
                }
                processedPlayers.add(partnerId);
            }
        }

        return players;
    }

    public static List<String> getChainedPlayerPairs(MinecraftServer server) {
        List<String> pairs = new ArrayList<>();
        Set<UUID> processedPairs = new HashSet<>();

        for (Map.Entry<UUID, UUID> entry : new HashSet<>(CHAINED_PLAYERS.entrySet())) {
            UUID playerId = entry.getKey();
            UUID partnerId = entry.getValue();

            if (processedPairs.contains(playerId) || processedPairs.contains(partnerId)) {
                continue;
            }

            processedPairs.add(playerId);
            processedPairs.add(partnerId);

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);

            if (player != null && partner != null) {
                pairs.add(player.getName().getString() + " 与 " + partner.getName().getString() + " 已绑定");
            }
        }

        return pairs;
    }

    private static void updateChain(ServerPlayer player1, ServerPlayer player2) {
        double maxDistance = 10.0 * ChainConfig.CHAIN_LENGTH.get();
        double minDistance = 4.0;
        double distance = player1.distanceTo(player2);

        if (distance > maxDistance + 0.5) {
            Vec3 direction = player2.position().subtract(player1.position()).normalize();
            double pullStrength = 0.3;

            if (player1.getDeltaMovement().lengthSqr() < 0.001) {
                player1.setDeltaMovement(direction.scale(pullStrength * 0.5));
            } else {
                player1.setDeltaMovement(player1.getDeltaMovement().add(direction.scale(pullStrength * 0.25)));
            }

            if (player2.getDeltaMovement().lengthSqr() < 0.001) {
                player2.setDeltaMovement(direction.scale(-pullStrength * 0.5));
            } else {
                player2.setDeltaMovement(player2.getDeltaMovement().subtract(direction.scale(pullStrength * 0.25)));
            }

            player1.hurtMarked = true;
            player2.hurtMarked = true;
        } else if (distance < minDistance - 1.5) {
            Vec3 direction = player2.position().subtract(player1.position()).normalize();
            double pushStrength = 0.05;

            if (player1.getDeltaMovement().lengthSqr() < 0.0001) {
                player1.setDeltaMovement(direction.scale(-pushStrength));
            }

            if (player2.getDeltaMovement().lengthSqr() < 0.0001) {
                player2.setDeltaMovement(direction.scale(pushStrength));
            }
        }
    }

    private static void enforceChainRules(ServerPlayer player1, ServerPlayer player2) {
        if (ChainConfig.PREVENT_TELEPORT.get()) {
            if (player1.getLastDeathLocation().isPresent() || player2.getLastDeathLocation().isPresent()) {
                player1.setLastDeathLocation(Optional.empty());
                player2.setLastDeathLocation(Optional.empty());
            }
        }
    }

    private static void teleportPlayerToPartner(ServerPlayer player, ServerPlayer partner, ServerLevel targetLevel) {
        BlockPos partnerPos = partner.blockPosition();
        teleportPlayerToLocation(player, targetLevel, partnerPos.getX() + 0.5, partnerPos.getY(), partnerPos.getZ() + 0.5);

        ChainTogether.LOGGER.info("Player {} teleported to {} near partner {}",
                player.getName().getString(),
                targetLevel.dimension().location(),
                partner.getName().getString());
    }

    private static void teleportPlayerToLocation(ServerPlayer player, ServerLevel targetLevel, double x, double y, double z) {
        FOLLOW_TELEPORTING.add(player.getUUID());
        player.teleportTo(targetLevel, x, y, z, player.getYRot(), player.getXRot());
    }
}
