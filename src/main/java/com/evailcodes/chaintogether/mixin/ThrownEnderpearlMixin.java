package com.evailcodes.chaintogether.mixin;

import com.evailcodes.chaintogether.handler.ChainHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
    @Inject(method = "onHit", at = @At("RETURN"))
    private void chaintogether$afterEnderPearlHit(HitResult result, CallbackInfo ci) {
        if (((ThrownEnderpearl) (Object) this).getOwner() instanceof ServerPlayer player) {
            ChainHandler.onEnderPearlTeleported(player);
        }
    }
}
