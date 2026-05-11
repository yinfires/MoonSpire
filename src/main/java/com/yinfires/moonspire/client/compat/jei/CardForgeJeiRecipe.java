package com.yinfires.moonspire.client.compat.jei;

import com.yinfires.moonspire.card.CardInstance;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record CardForgeJeiRecipe(ResourceLocation id, ItemStack input, CardInstance card) {
    public CardForgeJeiRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(card, "card");
        input = input.copy();
        input.setCount(1);
    }
}
