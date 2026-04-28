package com.yinfires.moonspire.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class PlayerCardData implements INBTSerializable<CompoundTag> {
    public static final int MIN_DECK_SIZE = 15;
    public static final int MAX_DECK_SIZE = 30;
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerCardData> STREAM_CODEC = StreamCodec.of(
            PlayerCardData::writeToBuffer,
            PlayerCardData::readFromBuffer);

    private final List<CardInstance> collection = new ArrayList<>();
    private final List<UUID> deck = new ArrayList<>();

    public List<CardInstance> collection() {
        return collection;
    }

    public List<UUID> deck() {
        return deck;
    }

    public void addCard(CardInstance card) {
        collection.add(card);
        if (deck.size() < MAX_DECK_SIZE) {
            deck.add(card.id());
        }
    }

    public boolean removeCard(UUID id) {
        deck.removeIf(id::equals);
        return collection.removeIf(card -> card.id().equals(id));
    }

    public void setDeck(List<UUID> cardIds) {
        deck.clear();
        for (UUID id : cardIds) {
            if (deck.size() >= MAX_DECK_SIZE) {
                break;
            }
            if (findCard(id).isPresent()) {
                deck.add(id);
            }
        }
    }

    public Optional<CardInstance> findCard(UUID id) {
        return collection.stream().filter(card -> card.id().equals(id)).findFirst();
    }

    public List<CardInstance> deckCards() {
        List<CardInstance> cards = new ArrayList<>();
        for (UUID id : deck) {
            findCard(id).ifPresent(cards::add);
        }
        return cards;
    }

    public boolean hasValidDeck() {
        int count = deckCards().size();
        return count >= MIN_DECK_SIZE && count <= MAX_DECK_SIZE;
    }

    public int validDeckSize() {
        return deckCards().size();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag collectionTag = new ListTag();
        for (CardInstance card : collection) {
            collectionTag.add(card.save(provider));
        }
        tag.put("collection", collectionTag);

        ListTag deckTag = new ListTag();
        for (UUID id : deck) {
            CompoundTag idTag = new CompoundTag();
            idTag.putUUID("id", id);
            deckTag.add(idTag);
        }
        tag.put("deck", deckTag);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        collection.clear();
        deck.clear();
        ListTag collectionTag = tag.getList("collection", Tag.TAG_COMPOUND);
        for (int i = 0; i < collectionTag.size(); i++) {
            collection.add(CardInstance.load(collectionTag.getCompound(i), provider));
        }
        ListTag deckTag = tag.getList("deck", Tag.TAG_COMPOUND);
        for (int i = 0; i < deckTag.size(); i++) {
            CompoundTag idTag = deckTag.getCompound(i);
            if (idTag.hasUUID("id")) {
                deck.add(idTag.getUUID("id"));
            }
        }
        deck.removeIf(id -> findCard(id).isEmpty());
        if (deck.size() > MAX_DECK_SIZE) {
            deck.subList(MAX_DECK_SIZE, deck.size()).clear();
        }
    }

    private static void writeToBuffer(RegistryFriendlyByteBuf buf, PlayerCardData data) {
        buf.writeVarInt(data.collection.size());
        for (CardInstance card : data.collection) {
            CardInstance.STREAM_CODEC.encode(buf, card);
        }
        buf.writeVarInt(data.deck.size());
        for (UUID id : data.deck) {
            buf.writeUUID(id);
        }
    }

    private static PlayerCardData readFromBuffer(RegistryFriendlyByteBuf buf) {
        PlayerCardData data = new PlayerCardData();
        int collectionSize = buf.readVarInt();
        for (int i = 0; i < collectionSize; i++) {
            data.collection.add(CardInstance.STREAM_CODEC.decode(buf));
        }
        int deckSize = buf.readVarInt();
        for (int i = 0; i < deckSize; i++) {
            data.deck.add(buf.readUUID());
        }
        data.deck.removeIf(id -> data.findCard(id).isEmpty());
        return data;
    }
}
