package com.yinfires.moonspire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.battle.BattleManager;
import com.yinfires.moonspire.card.MoonSpireCardRegistry;
import com.yinfires.moonspire.card.PlayerCardData;
import com.yinfires.moonspire.registry.ModAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = MoonSpire.MOD_ID)
public final class MoonSpireCommands {
    private MoonSpireCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(MoonSpire.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear_deck")
                        .executes(context -> clearDeck(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> clearDeck(context.getSource(), EntityArgument.getPlayer(context, "target")))))
                .then(Commands.literal("give_card")
                        .then(Commands.argument("card_id", StringArgumentType.word())
                                .executes(context -> giveDeveloperCard(context.getSource(), context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "card_id")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> giveDeveloperCard(context.getSource(), EntityArgument.getPlayer(context, "target"), StringArgumentType.getString(context, "card_id")))))));
    }

    private static int clearDeck(CommandSourceStack source, ServerPlayer target) {
        PlayerCardData data = target.getData(ModAttachments.PLAYER_CARDS.get());
        int removed = data.collection().size();
        data.collection().clear();
        data.deck().clear();
        target.setData(ModAttachments.PLAYER_CARDS.get(), data);
        target.syncData(ModAttachments.PLAYER_CARDS.get());
        BattleManager.syncCardData(target);
        source.sendSuccess(() -> Component.translatable("command.moonspire.clear_deck.success", target.getDisplayName(), removed), true);
        return removed;
    }

    private static int giveDeveloperCard(CommandSourceStack source, ServerPlayer target, String cardId) {
        var card = MoonSpireCardRegistry.cardInstance(cardId);
        if (card.isEmpty()) {
            source.sendFailure(Component.translatable("command.moonspire.give_card.missing", cardId));
            return 0;
        }
        PlayerCardData data = target.getData(ModAttachments.PLAYER_CARDS.get());
        data.addCard(card.get());
        target.setData(ModAttachments.PLAYER_CARDS.get(), data);
        target.syncData(ModAttachments.PLAYER_CARDS.get());
        BattleManager.syncCardData(target);
        source.sendSuccess(() -> Component.translatable("command.moonspire.give_card.success", cardId, target.getDisplayName()), true);
        return 1;
    }
}
