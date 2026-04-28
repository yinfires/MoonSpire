package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class MonsterDeckProfile {
    private MonsterDeckProfile() {
    }

    public static List<CardInstance> createDeck(LivingEntity monster) {
        EntityType<?> type = monster.getType();
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            return repeat(
                    CardInstance.simpleMonsterCard("Claw", "A close-range monster attack.", 5, 0, 1, 4),
                    CardInstance.simpleMonsterCard("Rotten Guard", "A crude defensive posture.", 0, 4, 1, 3),
                    CardInstance.simpleMonsterCard("Lunge", "A heavier monster attack.", 8, 0, 2, 3));
        }
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED) {
            return repeat(
                    CardInstance.simpleMonsterCard("Bone Shot", "A ranged monster attack.", 6, 0, 1, 6),
                    CardInstance.simpleMonsterCard("Sidestep", "A quick defensive move.", 0, 3, 1, 7),
                    CardInstance.simpleMonsterCard("Aimed Volley", "A strong prepared shot.", 9, 0, 2, 5));
        }
        if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) {
            return repeat(
                    CardInstance.simpleMonsterCard("Pounce", "A fast monster attack.", 5, 0, 1, 8),
                    CardInstance.simpleMonsterCard("Skitter", "A fast evasive guard.", 0, 3, 1, 9),
                    CardInstance.simpleMonsterCard("Bite", "A committed monster bite.", 7, 0, 2, 7));
        }
        return fallback(monster);
    }

    private static List<CardInstance> fallback(LivingEntity monster) {
        int attack = Math.max(3, (int) Math.ceil(monster.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        int defense = Math.max(2, monster.getArmorValue());
        int speed = Math.max(3, Math.min(9, 5 + (int) Math.round(monster.getAttributeValue(Attributes.MOVEMENT_SPEED) * 10.0D)));
        return repeat(
                CardInstance.simpleMonsterCard("Strike", "A generated monster attack.", attack, 0, attack >= 8 ? 2 : 1, speed),
                CardInstance.simpleMonsterCard("Guard", "A generated monster defense.", 0, defense, 1, speed),
                CardInstance.simpleMonsterCard("Heavy Strike", "A generated heavy attack.", attack + 3, 0, 2, Math.max(1, speed - 2)));
    }

    private static List<CardInstance> repeat(CardInstance first, CardInstance second, CardInstance third) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cards.add(first.copyForBattle());
            cards.add(second.copyForBattle());
            cards.add(third.copyForBattle());
        }
        return cards;
    }
}
