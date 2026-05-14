package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.client.ClientScreens;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenCardRewardScreenPayload(UUID rewardId, List<RewardPage> pages) implements CustomPacketPayload {
    public static final Type<OpenCardRewardScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "open_card_reward"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCardRewardScreenPayload> STREAM_CODEC = StreamCodec.of(
            OpenCardRewardScreenPayload::write,
            OpenCardRewardScreenPayload::read);

    public OpenCardRewardScreenPayload {
        rewardId = rewardId == null ? UUID.randomUUID() : rewardId;
        pages = List.copyOf(pages == null ? List.of() : pages);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCardRewardScreenPayload payload, IPayloadContext context) {
        ClientScreens.openCardReward(payload.rewardId, payload.pages);
    }

    private static void write(RegistryFriendlyByteBuf buf, OpenCardRewardScreenPayload payload) {
        buf.writeUUID(payload.rewardId);
        buf.writeVarInt(payload.pages.size());
        for (RewardPage page : payload.pages) {
            buf.writeUtf(page.entityTypeId());
            buf.writeVarInt(page.cards().size());
            for (CardInstance card : page.cards()) {
                CardInstance.STREAM_CODEC.encode(buf, card);
            }
        }
    }

    private static OpenCardRewardScreenPayload read(RegistryFriendlyByteBuf buf) {
        UUID rewardId = buf.readUUID();
        int pageCount = Math.min(64, buf.readVarInt());
        List<RewardPage> pages = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            String entityTypeId = buf.readUtf();
            int cardCount = Math.min(32, buf.readVarInt());
            List<CardInstance> cards = new ArrayList<>(cardCount);
            for (int j = 0; j < cardCount; j++) {
                cards.add(CardInstance.STREAM_CODEC.decode(buf));
            }
            pages.add(new RewardPage(entityTypeId, cards));
        }
        return new OpenCardRewardScreenPayload(rewardId, pages);
    }

    public record RewardPage(String entityTypeId, List<CardInstance> cards) {
        public RewardPage {
            entityTypeId = entityTypeId == null ? "" : entityTypeId;
            cards = List.copyOf(cards == null ? List.of() : cards);
        }
    }
}
