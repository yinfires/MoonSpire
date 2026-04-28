package com.yinfires.moonspire.card;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record CardInstance(
        UUID id,
        ItemStack sourceStack,
        String name,
        String description,
        int attack,
        int defense,
        int cost,
        int speed,
        CardSourceType sourceType) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CardInstance> STREAM_CODEC = StreamCodec.of(
            CardInstance::writeToBuffer,
            CardInstance::readFromBuffer);

    public static CardInstance simpleMonsterCard(String name, String description, int attack, int defense, int cost, int speed) {
        return new CardInstance(UUID.randomUUID(), ItemStack.EMPTY, name, description, attack, defense, cost, speed, CardSourceType.MONSTER);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        if (!sourceStack.isEmpty()) {
            tag.put("sourceStack", sourceStack.save(provider));
        }
        tag.putString("name", name);
        tag.putString("description", description);
        tag.putInt("attack", attack);
        tag.putInt("defense", defense);
        tag.putInt("cost", cost);
        tag.putInt("speed", speed);
        tag.putString("sourceType", sourceType.name());
        return tag;
    }

    public static CardInstance load(CompoundTag tag, HolderLookup.Provider provider) {
        ItemStack stack = ItemStack.EMPTY;
        if (tag.contains("sourceStack")) {
            stack = ItemStack.parseOptional(provider, tag.getCompound("sourceStack"));
        }
        return new CardInstance(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                stack,
                tag.getString("name"),
                tag.getString("description"),
                tag.getInt("attack"),
                tag.getInt("defense"),
                Math.max(0, tag.getInt("cost")),
                Math.max(0, tag.getInt("speed")),
                CardSourceType.byName(tag.getString("sourceType")));
    }

    public CardInstance copyForBattle() {
        return new CardInstance(UUID.randomUUID(), sourceStack.copy(), name, description, attack, defense, cost, speed, sourceType);
    }

    public boolean hasAttack() {
        return attack > 0;
    }

    public boolean hasDefense() {
        return defense > 0;
    }

    private static void writeToBuffer(RegistryFriendlyByteBuf buf, CardInstance card) {
        buf.writeUUID(card.id);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, card.sourceStack);
        buf.writeUtf(card.name);
        buf.writeUtf(card.description);
        buf.writeVarInt(card.attack);
        buf.writeVarInt(card.defense);
        buf.writeVarInt(card.cost);
        buf.writeVarInt(card.speed);
        buf.writeEnum(card.sourceType);
    }

    private static CardInstance readFromBuffer(RegistryFriendlyByteBuf buf) {
        return new CardInstance(
                buf.readUUID(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readEnum(CardSourceType.class));
    }
}
