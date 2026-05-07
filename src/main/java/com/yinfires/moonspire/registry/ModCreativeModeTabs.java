package com.yinfires.moonspire.registry;

import com.yinfires.moonspire.MoonSpire;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeModeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MoonSpire.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOON_SPIRE = TABS.register(
            "moon_spire",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.moonspire.moon_spire"))
                    .icon(() -> ModItems.CARD_FORGE.get().getDefaultInstance())
                    .build());

    private ModCreativeModeTabs() {
    }

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
        eventBus.addListener(ModCreativeModeTabs::buildContents);
    }

    private static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(MOON_SPIRE.getKey())) {
            return;
        }
        for (DeferredHolder<Item, ? extends Item> item : ModItems.creativeTabItems()) {
            event.accept(item.get());
        }
    }
}
