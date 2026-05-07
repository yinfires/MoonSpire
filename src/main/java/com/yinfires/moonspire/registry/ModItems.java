package com.yinfires.moonspire.registry;

import com.yinfires.moonspire.MoonSpire;
import java.util.Collection;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoonSpire.MOD_ID);

    public static final DeferredItem<BlockItem> CARD_FORGE = ITEMS.register(
            "card_forge",
            () -> new BlockItem(ModBlocks.CARD_FORGE.get(), new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static Collection<DeferredHolder<Item, ? extends Item>> creativeTabItems() {
        return ITEMS.getEntries();
    }
}
