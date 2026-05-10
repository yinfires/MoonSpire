package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.RandomSource;

public class BattleDeck {
    private final List<CardInstance> drawPile = new ArrayList<>();
    private final List<CardInstance> discardPile = new ArrayList<>();
    private final List<CardInstance> exhaustPile = new ArrayList<>();
    private final List<CardInstance> hand = new ArrayList<>();
    private boolean firstTurn = true;
    private long version;

    public BattleDeck(List<CardInstance> cards, RandomSource random) {
        for (CardInstance card : cards) {
            drawPile.add(card);
        }
    }

    public List<CardInstance> drawPile() {
        return drawPile;
    }

    public List<CardInstance> discardPile() {
        return discardPile;
    }

    public List<CardInstance> exhaustPile() {
        return exhaustPile;
    }

    public List<CardInstance> hand() {
        return hand;
    }

    public long version() {
        return version;
    }

    public void startTurn(RandomSource random) {
        int innateInHand = firstTurn ? moveInnateCardsToOpeningHand() : 0;
        firstTurn = false;
        draw(Math.max(0, CardBalance.STARTING_HAND_SIZE - innateInHand), random);
    }

    public void discardHand() {
        discardHand(false);
    }

    public void discardHand(boolean handleEndOfTurnKeywords) {
        if (hand.isEmpty()) {
            return;
        }
        if (!handleEndOfTurnKeywords) {
            discardPile.addAll(hand);
            hand.clear();
            markChanged();
            return;
        }
        List<CardInstance> retained = new ArrayList<>();
        for (CardInstance card : hand) {
            if (card.hasEffect(CardEffectKind.ETHEREAL)) {
                exhaustPile.add(card);
            } else if (card.hasEffect(CardEffectKind.RETAIN)) {
                retained.add(card);
            } else {
                discardPile.add(card);
            }
        }
        hand.clear();
        hand.addAll(retained);
        markChanged();
    }

    public void draw(int count, RandomSource random) {
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    return;
                }
                drawPile.addAll(discardPile);
                discardPile.clear();
            }
            CardInstance drawn = drawRandom(drawPile, random);
            if (hand.size() >= CardBalance.MAX_HAND_SIZE) {
                discardPile.add(drawn);
            } else {
                hand.add(drawn);
            }
            markChanged();
        }
    }

    public CardInstance peekHand(int index) {
        if (index < 0 || index >= hand.size()) {
            return null;
        }
        return hand.get(index);
    }

    public CardInstance useHand(int index) {
        if (index < 0 || index >= hand.size()) {
            return null;
        }
        CardInstance card = hand.remove(index);
        markChanged();
        return card;
    }

    public void replaceHandCard(int index, CardInstance card) {
        if (card != null && index >= 0 && index < hand.size()) {
            hand.set(index, card);
            markChanged();
        }
    }

    public void discard(CardInstance card) {
        if (card != null) {
            discardPile.add(card);
            markChanged();
        }
    }

    public void exhaust(CardInstance card) {
        if (card != null) {
            exhaustPile.add(card);
            markChanged();
        }
    }

    public List<CardInstance> removeHandByIds(List<UUID> ids) {
        Set<UUID> selectedIds = Set.copyOf(ids == null ? List.of() : ids);
        List<CardInstance> removed = new ArrayList<>();
        if (selectedIds.isEmpty()) {
            return removed;
        }
        for (int i = hand.size() - 1; i >= 0; i--) {
            if (selectedIds.contains(hand.get(i).id())) {
                removed.add(0, hand.remove(i));
            }
        }
        if (!removed.isEmpty()) {
            markChanged();
        }
        return removed;
    }

    public List<CardInstance> removeFirstHandCards(int count) {
        List<CardInstance> removed = new ArrayList<>();
        int capped = Math.min(Math.max(0, count), hand.size());
        for (int i = 0; i < capped; i++) {
            removed.add(hand.remove(0));
        }
        if (!removed.isEmpty()) {
            markChanged();
        }
        return removed;
    }

    public void discardAll(List<CardInstance> cards) {
        if (cards != null && !cards.isEmpty()) {
            discardPile.addAll(cards);
            markChanged();
        }
    }

    public void exhaustAll(List<CardInstance> cards) {
        if (cards != null && !cards.isEmpty()) {
            exhaustPile.addAll(cards);
            markChanged();
        }
    }

    private int moveInnateCardsToOpeningHand() {
        int movedToHand = 0;
        for (int i = drawPile.size() - 1; i >= 0; i--) {
            CardInstance card = drawPile.get(i);
            if (!card.hasEffect(CardEffectKind.INNATE)) {
                continue;
            }
            drawPile.remove(i);
            if (movedToHand < CardBalance.MAX_HAND_SIZE) {
                hand.add(card);
                movedToHand++;
            } else {
                discardPile.add(card);
            }
            markChanged();
        }
        return movedToHand;
    }

    public int firstAffordableAttack(int energyLeft) {
        for (int i = 0; i < hand.size(); i++) {
            CardInstance card = hand.get(i);
            if (card.hasAttack() && card.cost() <= energyLeft) {
                return i;
            }
        }
        return -1;
    }

    public int firstAffordableDefense(int energyLeft) {
        for (int i = 0; i < hand.size(); i++) {
            CardInstance card = hand.get(i);
            if (card.hasDefense() && card.cost() <= energyLeft) {
                return i;
            }
        }
        return -1;
    }

    public int firstAffordableAction(int energyLeft) {
        for (int i = 0; i < hand.size(); i++) {
            CardInstance card = hand.get(i);
            if (card.hasAnyEffect() && card.cost() <= energyLeft) {
                return i;
            }
        }
        return -1;
    }

    public boolean hasAffordableAction(int energyLeft) {
        return hand.stream().anyMatch(card -> card.hasAnyEffect() && card.cost() <= energyLeft);
    }

    private static CardInstance drawRandom(List<CardInstance> cards, RandomSource random) {
        int index = cards.size() == 1 ? 0 : random.nextInt(cards.size());
        int last = cards.size() - 1;
        CardInstance drawn = cards.get(index);
        CardInstance tail = cards.remove(last);
        if (index < last) {
            cards.set(index, tail);
        }
        return drawn;
    }

    private void markChanged() {
        version++;
    }
}
