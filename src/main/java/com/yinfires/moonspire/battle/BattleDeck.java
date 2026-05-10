package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import java.util.ArrayList;
import java.util.Collections;
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

    public BattleDeck(List<CardInstance> cards, RandomSource random) {
        for (CardInstance card : cards) {
            drawPile.add(card.copyForBattle());
        }
        shuffle(drawPile, random);
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

    public void startTurn(RandomSource random) {
        int innateInHand = firstTurn ? moveInnateCardsToOpeningHand() : 0;
        firstTurn = false;
        draw(Math.max(0, CardBalance.STARTING_HAND_SIZE - innateInHand), random);
    }

    public void discardHand() {
        discardHand(false);
    }

    public void discardHand(boolean handleEndOfTurnKeywords) {
        if (!handleEndOfTurnKeywords) {
            discardPile.addAll(hand);
            hand.clear();
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
    }

    public void draw(int count, RandomSource random) {
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    return;
                }
                drawPile.addAll(discardPile);
                discardPile.clear();
                shuffle(drawPile, random);
            }
            CardInstance drawn = drawPile.remove(drawPile.size() - 1);
            if (hand.size() >= CardBalance.MAX_HAND_SIZE) {
                discardPile.add(drawn);
            } else {
                hand.add(drawn);
            }
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
        return card;
    }

    public void replaceHandCard(int index, CardInstance card) {
        if (card != null && index >= 0 && index < hand.size()) {
            hand.set(index, card);
        }
    }

    public void discard(CardInstance card) {
        if (card != null) {
            discardPile.add(card);
        }
    }

    public void exhaust(CardInstance card) {
        if (card != null) {
            exhaustPile.add(card);
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
        return removed;
    }

    public List<CardInstance> removeFirstHandCards(int count) {
        List<CardInstance> removed = new ArrayList<>();
        int capped = Math.min(Math.max(0, count), hand.size());
        for (int i = 0; i < capped; i++) {
            removed.add(hand.remove(0));
        }
        return removed;
    }

    public void discardAll(List<CardInstance> cards) {
        if (cards != null) {
            discardPile.addAll(cards);
        }
    }

    public void exhaustAll(List<CardInstance> cards) {
        if (cards != null) {
            exhaustPile.addAll(cards);
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

    private static void shuffle(List<CardInstance> cards, RandomSource random) {
        Collections.shuffle(cards, new java.util.Random(random.nextLong()));
    }
}
