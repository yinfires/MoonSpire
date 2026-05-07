package com.yinfires.moonspire;

import com.mojang.logging.LogUtils;
import com.yinfires.moonspire.client.ClientEvents;
import com.yinfires.moonspire.developer.DeveloperPaths;
import com.yinfires.moonspire.network.ModNetworking;
import com.yinfires.moonspire.registry.ModAttachments;
import com.yinfires.moonspire.registry.ModBlocks;
import com.yinfires.moonspire.registry.ModCreativeModeTabs;
import com.yinfires.moonspire.registry.ModItems;
import java.io.IOException;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(MoonSpire.MOD_ID)
public class MoonSpire {
    public static final String MOD_ID = "moonspire";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MoonSpire(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModAttachments.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.registerModBus(modEventBus);
        }
        modEventBus.addListener(ModNetworking::register);
        modEventBus.addListener(this::createDeveloperDirectories);
        modEventBus.addListener(this::commonSetup);
    }

    private void createDeveloperDirectories(FMLCommonSetupEvent event) {
        try {
            DeveloperPaths.ensureDirectories();
        } catch (IOException e) {
            LOGGER.warn("Failed to create Moon Spire developer directories.", e);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Moon Spire initialized.");
    }
}
