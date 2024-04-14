package io.github.joemama.loader.api.mixins;

import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {
    /**
     * @author 0xJoeMama
     * @reason No one can change our branding
     */
    @DontObfuscate
    @Overwrite
    public String getServerModName() {
        return "joeloader";
    }
}
