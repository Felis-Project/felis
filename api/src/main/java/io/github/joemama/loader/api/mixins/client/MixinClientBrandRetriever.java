package io.github.joemama.loader.api.mixins.client;

import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.obfuscate.DontObfuscate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ClientBrandRetriever.class)
public class MixinClientBrandRetriever {
    /**
     * Nobody else gets the right to change our branding
     * @author 0xJoeMama
     */
    @DontObfuscate
    @Overwrite
    public static String getClientModName(){
        return "Felis";
    }
}
