package com.yinfires.moonspire.registry;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.block.CardForgeBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MoonSpire.MOD_ID);

    public static final DeferredBlock<CardForgeBlock> CARD_FORGE = BLOCKS.registerBlock(
            "card_forge",
            CardForgeBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));

    private ModBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
