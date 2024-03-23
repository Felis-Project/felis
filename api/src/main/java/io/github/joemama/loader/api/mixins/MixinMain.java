package io.github.joemama.loader.api.mixins;

import io.github.joemama.loader.api.ApiInit;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class MixinMain {
    @Inject(method = "main", at = @At(value = "INVOKE", target = "Ljava/lang/Runtime;getRuntime()Ljava/lang/Runtime;"))
    private static void testmod$onMain(String[] args, CallbackInfo ci) {
        ApiInit.getLogger().info("Hello from mixins!");
    }
}
