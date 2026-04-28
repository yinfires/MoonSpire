package com.yinfires.moonspire.card;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class CardFactory {
    private CardFactory() {
    }

    public static boolean canConvert(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof TieredItem || stack.getItem() instanceof ArmorItem || attackFromAttributes(stack) > 0.0D);
    }

    public static CardInstance fromItem(ItemStack stack) {
        ItemStack displayStack = stack.copy();
        displayStack.setCount(1);
        CardSourceType type = sourceType(stack);
        int attack = switch (type) {
            case WEAPON, TOOL -> Math.max(1, (int) Math.ceil(attackFromAttributes(stack)));
            case ARMOR -> 0;
            default -> Math.max(0, (int) Math.ceil(attackFromAttributes(stack)));
        };
        int defense = switch (type) {
            case ARMOR -> Math.max(1, armorDefense(stack));
            case TOOL -> Math.max(0, attack / 3);
            default -> 0;
        };
        int rarityBonus = rarityBonus(stack.getRarity());
        int durabilityBonus = stack.isDamageableItem() ? Math.max(0, (stack.getMaxDamage() - stack.getDamageValue()) / 350) : 0;
        int cost = Math.max(0, Math.min(3, (attack + defense + 3) / 5 - rarityBonus));
        int speed = Math.max(1, Math.min(10, 5 + rarityBonus + durabilityBonus - Math.max(0, cost - 1)));
        String name = Component.translatable(stack.getDescriptionId()).getString();
        String description = switch (type) {
            case WEAPON -> "A strike card converted from a weapon.";
            case ARMOR -> "A guard card converted from armor.";
            case TOOL -> "A flexible card converted from a tool.";
            default -> "A card converted from equipment.";
        };
        return new CardInstance(UUID.randomUUID(), displayStack, name, description, attack, defense, cost, speed, type);
    }

    private static CardSourceType sourceType(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem) {
            return CardSourceType.ARMOR;
        }
        if (stack.getItem() instanceof TieredItem) {
            return attackFromAttributes(stack) >= 4.0D ? CardSourceType.WEAPON : CardSourceType.TOOL;
        }
        return CardSourceType.UNKNOWN;
    }

    private static int armorDefense(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return Math.max(1, armorItem.getDefense());
        }
        return (int) Math.ceil(attributeValue(stack, Attributes.ARMOR));
    }

    private static double attackFromAttributes(ItemStack stack) {
        return attributeValue(stack, Attributes.ATTACK_DAMAGE);
    }

    private static double attributeValue(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        final double[] value = {0.0D};
        modifiers.modifiers().forEach(entry -> {
            if (entry.attribute().equals(attribute) && entry.slot().test(EquipmentSlot.MAINHAND)) {
                AttributeModifier modifier = entry.modifier();
                if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    value[0] += modifier.amount();
                }
            }
        });
        return value[0];
    }

    private static int rarityBonus(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0;
            case UNCOMMON -> 1;
            case RARE -> 2;
            case EPIC -> 3;
        };
    }
}
