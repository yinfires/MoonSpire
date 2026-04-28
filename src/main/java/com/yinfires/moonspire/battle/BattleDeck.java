package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardBalance;
import com.yinfires.moonspire.card.CardInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.RandomSource;

public class BattleDeck {
    private final List<CardInstance> drawPile = new ArrayList<>();
    private final List<CardInstance> discardPile = new ArrayList<>();
    private final List<CardInstance> hand = new ArrayList<>();
    private final List<CardInstance> prepared = new ArrayList<>();

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

    public List<CardInstance> hand() {
        return hand;
    }

    public List<CardInstance> prepared() {
        return prepared;
    }

    public void startRound(RandomSource random) {
        discardPile.addAll(hand);
        discardPile.addAll(prepared);
        hand.clear();
        prepared.clear();
        draw(CardBalance.STARTING_HAND_SIZE, random);
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
            hand.add(drawPile.remove(drawPile.size() - 1));
        }
    }

    public int prepare(List<Integer> handIndexes, int maxEnergy) {
        int spent = 0;
        List<CardInstance> selected = new ArrayList<>();
        for (int index : handIndexes) {
            if (index < 0 || index >= hand.size()) {
                continue;
            }
            CardInstance card = hand.get(index);
            if (spent + card.cost() <= maxEnergy && !selected.contains(card)) {
                selected.add(card);
                spent += card.cost();
            }
        }
        hand.removeAll(selected);
        prepared.addAll(selected);
        return spent;
    }

    public CardInstance popNextAttack() {
        for (int i = 0; i < prepared.size(); i++) {
            CardInstance card = prepared.get(i);
            if (card.hasAttack()) {
                prepared.remove(i);
                discardPile.add(card);
                return card;
            }
        }
        return null;
    }

    public CardInstance popNextDefense() {
        for (int i = 0; i < prepared.size(); i++) {
            CardInstance card = prepared.get(i);
            if (card.hasDefense()) {
                prepared.remove(i);
                discardPile.add(card);
                return card;
            }
        }
        return null;
    }

    public CardInstance peekNextAction() {
        for (CardInstance card : prepared) {
            if (card.hasAttack() || card.hasDefense()) {
                return card;
            }
        }
        return null;
    }

    public CardInstance popNextAction() {
        for (int i = 0; i < prepared.size(); i++) {
            CardInstance card = prepared.get(i);
            if (card.hasAttack() || card.hasDefense()) {
                prepared.remove(i);
                discardPile.add(card);
                return card;
            }
        }
        return null;
    }

    public boolean hasPreparedActions() {
        return prepared.stream().anyMatch(card -> card.hasAttack() || card.hasDefense());
    }

    public int bestPreparedSpeed() {
        return prepared.stream().mapToInt(CardInstance::speed).max().orElse(0);
    }

    private static void shuffle(List<CardInstance> cards, RandomSource random) {
        Collections.shuffle(cards, new java.util.Random(random.nextLong()));
    }
}
