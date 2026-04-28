package com.yinfires.moonspire.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ChallengeTargetPayload.TYPE, ChallengeTargetPayload.STREAM_CODEC, ChallengeTargetPayload::handle);
        registrar.playToServer(PrepareCardsPayload.TYPE, PrepareCardsPayload.STREAM_CODEC, PrepareCardsPayload::handle);
        registrar.playToServer(UsePreparedCardPayload.TYPE, UsePreparedCardPayload.STREAM_CODEC, UsePreparedCardPayload::handle);
        registrar.playToServer(ConvertSlotPayload.TYPE, ConvertSlotPayload.STREAM_CODEC, ConvertSlotPayload::handle);
        registrar.playToServer(SetDeckPayload.TYPE, SetDeckPayload.STREAM_CODEC, SetDeckPayload::handle);
        registrar.playToClient(PlayerCardDataPayload.TYPE, PlayerCardDataPayload.STREAM_CODEC, PlayerCardDataPayload::handle);
        registrar.playToClient(BattleSnapshotPayload.TYPE, BattleSnapshotPayload.STREAM_CODEC, BattleSnapshotPayload::handle);
        registrar.playToClient(OpenCardForgeScreenPayload.TYPE, OpenCardForgeScreenPayload.STREAM_CODEC, OpenCardForgeScreenPayload::handle);
    }
}
