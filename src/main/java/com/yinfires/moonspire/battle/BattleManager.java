package com.yinfires.moonspire.battle;

import com.yinfires.moonspire.card.CardFactory;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.card.PlayerCardData;
import com.yinfires.moonspire.network.BattleSnapshotPayload;
import com.yinfires.moonspire.network.PlayerCardDataPayload;
import com.yinfires.moonspire.registry.ModAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BattleManager {
    private static final double CHALLENGE_RANGE_SQR = 8.0D * 8.0D;
    private static final Map<UUID, BattleState> BY_PLAYER = new HashMap<>();
    private static final Map<Integer, BattleState> BY_ENTITY_ID = new HashMap<>();

    private BattleManager() {
    }

    public static void tick() {
        List<BattleState> finished = new ArrayList<>();
        for (BattleState battle : BY_PLAYER.values()) {
            if (battle.tick()) {
                finished.add(battle);
            }
            sync(battle);
        }
        for (BattleState battle : finished) {
            endBattle(battle);
        }
    }

    public static boolean isInBattle(LivingEntity entity) {
        return battleFor(entity) != null;
    }

    public static BattleState battleFor(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            return BY_PLAYER.get(player.getUUID());
        }
        return BY_ENTITY_ID.get(entity.getId());
    }

    public static void challenge(ServerPlayer player, int targetId) {
        Entity entity = player.level().getEntity(targetId);
        if (!(entity instanceof LivingEntity monster)) {
            player.displayClientMessage(Component.translatable("message.moonspire.no_target"), true);
            return;
        }
        if (!canChallenge(player, monster)) {
            return;
        }
        PlayerCardData data = player.getData(ModAttachments.PLAYER_CARDS.get());
        if (!data.hasValidDeck()) {
            player.displayClientMessage(Component.translatable("message.moonspire.invalid_deck", data.validDeckSize()), true);
            syncCardData(player);
            return;
        }
        BattleState battle = new BattleState(player, monster, data.deckCards(), MonsterDeckProfile.createDeck(monster));
        BY_PLAYER.put(player.getUUID(), battle);
        BY_ENTITY_ID.put(player.getId(), battle);
        BY_ENTITY_ID.put(monster.getId(), battle);
        battle.start();
        sync(battle);
        player.displayClientMessage(Component.translatable("message.moonspire.battle_started", monster.getDisplayName()), true);
    }

    public static void prepare(ServerPlayer player, List<Integer> handIndexes) {
        endTurn(player);
    }

    public static void usePreparedCard(ServerPlayer player, int handIndex, int targetId) {
        useCard(player, handIndex, targetId);
    }

    public static void useCard(ServerPlayer player, int handIndex, int targetId) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            battle.usePlayerCard(handIndex, targetId);
            sync(battle);
        }
    }

    public static void setThinking(ServerPlayer player, boolean thinking) {
        // The turn-based battle screen no longer pauses with Tab.
    }

    public static void endTurn(ServerPlayer player) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            battle.endPlayerTurn();
            sync(battle);
        }
    }

    public static void cancelBattle(ServerPlayer player) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            endBattle(battle);
        }
    }

    public static void selectTarget(ServerPlayer player, int targetId) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            battle.selectTarget(targetId);
            sync(battle);
        }
    }

    public static void confirmHandSelection(ServerPlayer player, List<UUID> cardIds) {
        BattleState battle = BY_PLAYER.get(player.getUUID());
        if (battle != null) {
            battle.confirmHandSelection(cardIds);
            sync(battle);
        }
    }

    public static boolean handleDamage(LivingEntity target, Entity sourceEntity) {
        BattleState battle = battleFor(target);
        if (battle == null) {
            return false;
        }
        if (battle.suppressDamageEvent()) {
            return false;
        }
        if (sourceEntity instanceof LivingEntity attacker && battle.involves(attacker)) {
            if (!(attacker instanceof ServerPlayer)) {
                battle.handleAttack(attacker, target);
            }
            sync(battle);
        } else if (sourceEntity instanceof LivingEntity attacker) {
            battle.pacifyOutsideAttacker(attacker);
        }
        return battle.involves(target);
    }

    public static void cleanup(LivingEntity entity) {
        BattleState battle = battleFor(entity);
        if (battle != null) {
            endBattle(battle);
        }
    }

    public static void syncCardData(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PlayerCardDataPayload(player.getData(ModAttachments.PLAYER_CARDS.get())));
    }

    public static void convertInventorySlot(ServerPlayer player, int slot, BlockPos forgePos) {
        if (isInBattle(player)) {
            return;
        }
        if (!player.blockPosition().closerThan(forgePos, 8.0D)) {
            player.displayClientMessage(Component.translatable("message.moonspire.forge_too_far"), true);
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (!CardFactory.canConvert(stack)) {
            player.displayClientMessage(Component.translatable("message.moonspire.not_convertible"), true);
            return;
        }
        PlayerCardData data = player.getData(ModAttachments.PLAYER_CARDS.get());
        CardInstance card = CardFactory.fromItem(stack);
        if (card == null) {
            player.displayClientMessage(Component.translatable("message.moonspire.not_convertible"), true);
            return;
        }
        data.addCard(card);
        player.setData(ModAttachments.PLAYER_CARDS.get(), data);
        stack.shrink(1);
        player.getInventory().setChanged();
        player.syncData(ModAttachments.PLAYER_CARDS.get());
        syncCardData(player);
        player.displayClientMessage(Component.translatable("message.moonspire.card_created"), true);
    }

    public static void setDeck(ServerPlayer player, List<UUID> cardIds) {
        if (isInBattle(player)) {
            return;
        }
        PlayerCardData data = player.getData(ModAttachments.PLAYER_CARDS.get());
        data.setDeck(cardIds);
        player.setData(ModAttachments.PLAYER_CARDS.get(), data);
        player.syncData(ModAttachments.PLAYER_CARDS.get());
        syncCardData(player);
    }

    private static boolean canChallenge(ServerPlayer player, LivingEntity target) {
        if (!target.isAlive() || target == player || target.getType().getCategory() != MobCategory.MONSTER) {
            player.displayClientMessage(Component.translatable("message.moonspire.invalid_target"), true);
            return false;
        }
        if (isInBattle(player) || isInBattle(target)) {
            player.displayClientMessage(Component.translatable("message.moonspire.already_in_battle"), true);
            return false;
        }
        if (player.distanceToSqr(target) > CHALLENGE_RANGE_SQR) {
            player.displayClientMessage(Component.translatable("message.moonspire.target_too_far"), true);
            return false;
        }
        return true;
    }

    private static void sync(BattleState battle) {
        PacketDistributor.sendToPlayer(battle.player(), new BattleSnapshotPayload(battle.snapshot()));
    }

    private static void endBattle(BattleState battle) {
        BY_PLAYER.remove(battle.player().getUUID());
        BY_ENTITY_ID.remove(battle.player().getId());
        BY_ENTITY_ID.remove(battle.monster().getId());
        battle.finish();
        PacketDistributor.sendToPlayer(battle.player(), new BattleSnapshotPayload(BattleSnapshot.inactive()));
    }
}
