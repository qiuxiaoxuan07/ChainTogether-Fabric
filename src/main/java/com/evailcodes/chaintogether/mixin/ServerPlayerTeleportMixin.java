package com.evailcodes.chaintogether.mixin;

import com.evailcodes.chaintogether.handler.ChainHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {
    @Inject(method = "teleportTo(DDD)V", at = @At("RETURN"))
    private void chaintogether$afterSameLevelTeleport(double x, double y, double z, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ChainHandler.onPlayerTeleported(player, (ServerLevel) player.level(), x, y, z);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("RETURN"))
    private void chaintogether$afterSimpleTeleport(ServerLevel targetLevel, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        ChainHandler.onPlayerTeleported((ServerPlayer) (Object) this, targetLevel, x, y, z);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("RETURN"))
    private void chaintogether$afterRelativeTeleport(ServerLevel targetLevel, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            ChainHandler.onPlayerTeleported((ServerPlayer) (Object) this, targetLevel, x, y, z);
        }
    }
}
