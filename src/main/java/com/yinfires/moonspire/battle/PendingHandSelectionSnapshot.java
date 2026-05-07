package com.yinfires.moonspire.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record PendingHandSelectionSnapshot(Action action, int requiredCount, int ownerEntityId, List<UUID> candidateCardIds) {
    public static final PendingHandSelectionSnapshot NONE = new PendingHandSelectionSnapshot(Action.NONE, 0, -1, List.of());
    public static final StreamCodec<RegistryFriendlyByteBuf, PendingHandSelectionSnapshot> STREAM_CODEC = StreamCodec.of(
            PendingHandSelectionSnapshot::write,
            PendingHandSelectionSnapshot::read);

    public PendingHandSelectionSnapshot {
        action = action == null ? Action.NONE : action;
        requiredCount = Math.max(0, requiredCount);
        ownerEntityId = ownerEntityId;
        candidateCardIds = List.copyOf(candidateCardIds == null ? List.of() : candidateCardIds);
    }

    public boolean active() {
        return action != Action.NONE && requiredCount > 0 && ownerEntityId >= 0 && !candidateCardIds.isEmpty();
    }

    private static void write(RegistryFriendlyByteBuf buf, PendingHandSelectionSnapshot snapshot) {
        buf.writeEnum(snapshot.action);
        buf.writeVarInt(snapshot.requiredCount);
        buf.writeVarInt(snapshot.ownerEntityId);
        buf.writeVarInt(snapshot.candidateCardIds.size());
        for (UUID id : snapshot.candidateCardIds) {
            buf.writeUUID(id);
        }
    }

    private static PendingHandSelectionSnapshot read(RegistryFriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        int requiredCount = buf.readVarInt();
        int ownerEntityId = buf.readVarInt();
        int size = Math.min(32, buf.readVarInt());
        List<UUID> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readUUID());
        }
        return new PendingHandSelectionSnapshot(action, requiredCount, ownerEntityId, ids);
    }

    public enum Action {
        NONE,
        EXHAUST,
        DISCARD
    }
}
