package com.yinfires.moonspire.network;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleSnapshot;
import com.yinfires.moonspire.client.ClientBattleState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BattleSnapshotPayload(BattleSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<BattleSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "battle_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleSnapshotPayload> STREAM_CODEC = BattleSnapshot.STREAM_CODEC.map(BattleSnapshotPayload::new, BattleSnapshotPayload::snapshot);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattleSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBattleState.setSnapshot(payload.snapshot));
    }
}
