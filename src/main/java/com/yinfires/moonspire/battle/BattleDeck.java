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
    private final List<UUID> lastStartTurnDrawn = new ArrayList<>();
    private boolean firstTurn = true;
    private int lastStartTurnDrawReduction;
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
        startTurn(random, 0);
    }

    public void startTurn(RandomSource random, int drawReduction) {
        int reduction = Math.max(0, drawReduction);
        lastStartTurnDrawn.clear();
        lastStartTurnDrawReduction = reduction;
        int innateInHand = firstTurn ? moveInnateCardsToOpeningHand() : 0;
        firstTurn = false;
        for (CardInstance card : draw(Math.max(0, CardBalance.STARTING_HAND_SIZE - innateInHand - reduction), random)) {
            lastStartTurnDrawn.add(card.id());
        }
    }

    public boolean applyAdditionalStartTurnDrawReduction(int drawReduction) {
        int reduction = Math.max(0, drawReduction);
        int additionalReduction = Math.max(0, reduction - lastStartTurnDrawReduction);
        boolean changed = false;
        if (additionalReduction > 0) {
            changed = returnLastStartTurnDrawsToDrawPile(additionalReduction) > 0;
        }
        lastStartTurnDrawReduction = Math.max(lastStartTurnDrawReduction, reduction);
        return changed;
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

    public List<CardInstance> draw(int count, RandomSource random) {
        List<CardInstance> drawnCards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    return drawnCards;
                }
                drawPile.addAll(discardPile);
                discardPile.clear();
            }
            CardInstance drawn = drawRandom(drawPile, random);
            drawnCards.add(drawn);
            if (hand.size() >= CardBalance.MAX_HAND_SIZE) {
                discardPile.add(drawn);
            } else {
                hand.add(drawn);
            }
            markChanged();
        }
        return drawnCards;
    }

    private int returnLastStartTurnDrawsToDrawPile(int count) {
        int returned = 0;
        for (int i = lastStartTurnDrawn.size() - 1; i >= 0 && returned < count; i--) {
            UUID id = lastStartTurnDrawn.remove(i);
            CardInstance card = removeCardById(hand, id);
            if (card == null) {
                card = removeCardById(discardPile, id);
            }
            if (card != null) {
                drawPile.add(card);
                returned++;
            }
        }
        if (returned > 0) {
            markChanged();
        }
        return returned;
    }

    private static CardInstance removeCardById(List<CardInstance> cards, UUID id) {
        for (int i = cards.size() - 1; i >= 0; i--) {
            CardInstance card = cards.get(i);
            if (card.id().equals(id)) {
                return cards.remove(i);
            }
        }
        return null;
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

    public void addGeneratedToHandOrDiscard(CardInstance card) {
        if (card == null) {
            return;
        }
        if (hand.size() >= CardBalance.MAX_HAND_SIZE) {
            discardPile.add(card);
        } else {
            hand.add(card);
        }
        markChanged();
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
