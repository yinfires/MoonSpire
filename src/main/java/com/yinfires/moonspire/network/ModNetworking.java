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
        registrar.playToServer(UseCardPayload.TYPE, UseCardPayload.STREAM_CODEC, UseCardPayload::handle);
        registrar.playToServer(EndTurnPayload.TYPE, EndTurnPayload.STREAM_CODEC, EndTurnPayload::handle);
        registrar.playToServer(CancelBattlePayload.TYPE, CancelBattlePayload.STREAM_CODEC, CancelBattlePayload::handle);
        registrar.playToServer(SelectBattleTargetPayload.TYPE, SelectBattleTargetPayload.STREAM_CODEC, SelectBattleTargetPayload::handle);
        registrar.playToServer(SelectHandCardsPayload.TYPE, SelectHandCardsPayload.STREAM_CODEC, SelectHandCardsPayload::handle);
        registrar.playToServer(SetThinkingPayload.TYPE, SetThinkingPayload.STREAM_CODEC, SetThinkingPayload::handle);
        registrar.playToServer(ConvertSlotPayload.TYPE, ConvertSlotPayload.STREAM_CODEC, ConvertSlotPayload::handle);
        registrar.playToServer(SetDeckPayload.TYPE, SetDeckPayload.STREAM_CODEC, SetDeckPayload::handle);
        registrar.playToServer(RequestDeveloperCenterPayload.TYPE, RequestDeveloperCenterPayload.STREAM_CODEC, RequestDeveloperCenterPayload::handle);
        registrar.playToServer(SaveDeveloperDataPayload.TYPE, SaveDeveloperDataPayload.STREAM_CODEC, SaveDeveloperDataPayload::handle);
        registrar.playToServer(GiveDeveloperCardPayload.TYPE, GiveDeveloperCardPayload.STREAM_CODEC, GiveDeveloperCardPayload::handle);
        registrar.playToClient(PlayerCardDataPayload.TYPE, PlayerCardDataPayload.STREAM_CODEC, PlayerCardDataPayload::handle);
        registrar.playToClient(BattleSnapshotPayload.TYPE, BattleSnapshotPayload.STREAM_CODEC, BattleSnapshotPayload::handle);
        registrar.playToClient(OpenCardForgeScreenPayload.TYPE, OpenCardForgeScreenPayload.STREAM_CODEC, OpenCardForgeScreenPayload::handle);
        registrar.playToClient(DeveloperCenterPayload.TYPE, DeveloperCenterPayload.STREAM_CODEC, DeveloperCenterPayload::handle);
    }
}
