package com.yinfires.moonspire.registry;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.card.PlayerCardData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MoonSpire.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerCardData>> PLAYER_CARDS = ATTACHMENTS.register(
            "player_cards",
            () -> AttachmentType.serializable(PlayerCardData::new)
                    .copyOnDeath()
                    .sync(PlayerCardData.STREAM_CODEC)
                    .build());

    private ModAttachments() {
    }

    public static void register(IEventBus eventBus) {
        ATTACHMENTS.register(eventBus);
    }
}
