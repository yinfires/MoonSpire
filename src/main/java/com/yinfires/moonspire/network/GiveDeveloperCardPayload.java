package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleManager;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.card.PlayerCardData;
import com.yinfires.moonspire.registry.ModAttachments;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GiveDeveloperCardPayload(String cardId) implements CustomPacketPayload {
    public static final Type<GiveDeveloperCardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "give_developer_card"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GiveDeveloperCardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.cardId),
            buf -> new GiveDeveloperCardPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GiveDeveloperCardPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
            return;
        }
        var card = MoonSpireCardRegistry.cardInstance(payload.cardId);
        if (card.isEmpty()) {
            player.displayClientMessage(Component.translatable("command.moonspire.give_card.missing", payload.cardId), true);
            return;
        }
        PlayerCardData data = player.getData(ModAttachments.PLAYER_CARDS.get());
        data.addCard(card.get());
        player.setData(ModAttachments.PLAYER_CARDS.get(), data);
        player.syncData(ModAttachments.PLAYER_CARDS.get());
        BattleManager.syncCardData(player);
        player.displayClientMessage(Component.translatable("debug.moonspire.card_given", payload.cardId), true);
    }
}
