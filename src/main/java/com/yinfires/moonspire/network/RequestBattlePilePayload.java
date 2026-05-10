package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleManager;
import com.yinfires.moonspire.battle.BattlePileSource;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestBattlePilePayload(UUID battleId, BattlePileSource source, long deckVersion) implements CustomPacketPayload {
    public static final Type<RequestBattlePilePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "request_battle_pile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestBattlePilePayload> STREAM_CODEC = StreamCodec.of(
            RequestBattlePilePayload::write,
            RequestBattlePilePayload::read);

    public RequestBattlePilePayload {
        battleId = battleId == null ? com.yinfires.moonspire.battle.BattleSnapshot.INACTIVE_BATTLE_ID : battleId;
        source = source == null ? BattlePileSource.BATTLE_DECK : source;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestBattlePilePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> BattleManager.requestPile(player, payload.battleId, payload.source, payload.deckVersion));
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, RequestBattlePilePayload payload) {
        buf.writeUUID(payload.battleId);
        buf.writeEnum(payload.source);
        buf.writeVarLong(payload.deckVersion);
    }

    private static RequestBattlePilePayload read(RegistryFriendlyByteBuf buf) {
        return new RequestBattlePilePayload(buf.readUUID(), buf.readEnum(BattlePileSource.class), buf.readVarLong());
    }
}
