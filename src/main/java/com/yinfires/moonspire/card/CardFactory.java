package com.yinfires.moonspire.card;

import net.minecraft.world.item.ItemStack;

public final class CardFactory {
    private CardFactory() {
    }

    public static boolean canConvert(ItemStack stack) {
        return MoonSpireCardRegistry.canConvert(stack);
    }

    public static CardInstance fromItem(ItemStack stack) {
        ItemStack displayStack = stack.copy();
        displayStack.setCount(1);
        RegisteredCardDefinition definition = MoonSpireCardRegistry.convertedCard(displayStack);
        if (definition == null) {
            return null;
        }
        return definition.createInstance(displayStack);
    }
}
