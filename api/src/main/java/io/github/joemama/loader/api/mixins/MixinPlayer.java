package io.github.joemama.loader.api.mixins;

import io.github.joemama.loader.api.event.GameEvents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class MixinPlayer {
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickEnd(CallbackInfo ci) {
        GameEvents.PlayerTick.getEnd().fire(((Player) (Object) this));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickStart(CallbackInfo ci) {
        GameEvents.PlayerTick.getStart().fire(((Player) (Object) this));
    }
}
