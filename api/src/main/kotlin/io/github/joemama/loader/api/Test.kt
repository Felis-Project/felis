package io.github.joemama.loader.api

import net.minecraft.world.item.Item
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour

class Test : CommonEntrypoint {
    private val testItem = Item(Item.Properties())
    private val testBlock = Block(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL))

    override fun onInit() {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation("mymod", "test_item"), testItem)
        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation("mymod", "test_block"), testBlock)
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation("mymod", "test_block"),
            BlockItem(testBlock, Item.Properties())
        )
    }
}
