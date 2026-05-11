package com.yinfires.moonspire.client.compat.jei;

import com.yinfires.moonspire.MoonSpire;
import com.yinfires.moonspire.card.CardFactory;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class MoonSpireJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CardForgeRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(CardForgeRecipeCategory.TYPE, cardForgeRecipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.CARD_FORGE.get()), CardForgeRecipeCategory.TYPE);
    }

    private static List<CardForgeJeiRecipe> cardForgeRecipes() {
        List<CardForgeJeiRecipe> recipes = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack input = new ItemStack(item);
            if (!CardFactory.canConvert(input)) {
                continue;
            }
            CardInstance card = CardFactory.fromItem(input);
            if (card == null) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null) {
                continue;
            }
            recipes.add(new CardForgeJeiRecipe(recipeId(itemId), input, card));
        }
        return List.copyOf(recipes);
    }

    private static ResourceLocation recipeId(ResourceLocation itemId) {
        return ResourceLocation.fromNamespaceAndPath(MoonSpire.MOD_ID, "card_forge/" + itemId.getNamespace() + "/" + itemId.getPath());
    }
}
