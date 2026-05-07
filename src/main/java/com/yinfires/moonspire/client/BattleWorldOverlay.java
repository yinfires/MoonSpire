package com.yinfires.moonspire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yinfires.moonspire.battle.BattleCombatantSnapshot;
import com.yinfires.moonspire.battle.BattleEffectType;
import com.yinfires.moonspire.battle.BattleEffectSnapshot;
import com.yinfires.moonspire.battle.BattleSnapshot;
import com.yinfires.moonspire.card.CardEffect;
import com.yinfires.moonspire.card.CardEffectKind;
import com.yinfires.moonspire.card.CardInstance;
import com.yinfires.moonspire.client.ui.MoonSpireUiTextures;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class BattleWorldOverlay {
    private static final double COMBATANT_OVERLAY_Y_OFFSET = 0.9D;

    private BattleWorldOverlay() {
    }

    public static void renderLevel(PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource bufferSource) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        renderHighlight(minecraft.level.getEntity(snapshot.player().entityId()), snapshot.player().entityId(), poseStack, camera, bufferSource);
        renderHighlight(minecraft.level.getEntity(snapshot.monster().entityId()), snapshot.monster().entityId(), poseStack, camera, bufferSource);
    }

    public static boolean suppressBattleNameTag(Entity entity) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            return false;
        }
        return entity.getId() == snapshot.player().entityId() || entity.getId() == snapshot.monster().entityId();
    }

    public static void renderLivingOverlay(Entity entity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            return;
        }
        BattleCombatantSnapshot combatant = null;
        boolean monster = false;
        if (entity.getId() == snapshot.player().entityId()) {
            combatant = snapshot.player();
        } else if (entity.getId() == snapshot.monster().entityId()) {
            combatant = snapshot.monster();
            monster = true;
        }
        if (combatant == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + COMBATANT_OVERLAY_Y_OFFSET, 0.0D);
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        int light = Math.max(packedLight, LightTexture.FULL_BRIGHT);
        drawBar(font, matrix, bufferSource, combatant, light);
        if (monster) {
            drawIntent(font, matrix, bufferSource, snapshot.monsterIntentCards(), light);
        }
        drawEffects(font, matrix, bufferSource, combatant.effects(), light);
        drawBlockGainAnimations(matrix, bufferSource, entity, light);
        drawDamageNumbers(font, matrix, bufferSource, entity.getId(), light);
        poseStack.popPose();
    }

    private static void renderHighlight(Entity entity, int entityId, PoseStack poseStack, Camera camera, MultiBufferSource bufferSource) {
        if (entity == null) {
            return;
        }
        boolean selected = ClientBattleState.selectedTargetId() == entityId;
        boolean hovered = ClientBattleState.isHoveredEntityId(entityId);
        if (!selected && !hovered) {
            return;
        }
        Vec3 cameraPos = camera.getPosition();
        AABB box = entity.getBoundingBox().inflate(selected ? 0.09D : 0.05D).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        float r = selected ? 1.0F : 0.45F;
        float g = selected ? 0.82F : 0.78F;
        float b = selected ? 0.35F : 1.0F;
        LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), box, r, g, b, selected ? 0.95F : 0.75F);
    }

    private static void drawBar(Font font, Matrix4f matrix, MultiBufferSource bufferSource, BattleCombatantSnapshot combatant, int packedLight) {
        int backgroundWidth = 90;
        int backgroundHeight = 16;
        int insetX = 2;
        int insetY = 2;
        int barWidth = backgroundWidth - insetX * 2;
        int barHeight = backgroundHeight - insetY * 2;
        int backgroundX = -backgroundWidth / 2;
        int backgroundY = -3;
        int barX = backgroundX + insetX;
        int barY = backgroundY + insetY;
        VertexConsumer textured = bufferSource.getBuffer(RenderType.text(MoonSpireUiTextures.HEALTH_BAR));
        MoonSpireUiTextures.drawWorldNinePatch(matrix, textured, backgroundX, backgroundY, backgroundWidth, backgroundHeight, 0.0F, packedLight, 8, 8, 4, 4, 32, 16);
        CardRenderHelper.BarSegments segments = CardRenderHelper.combatantBarSegments(combatant, barWidth);
        drawTexturedFill(matrix, bufferSource, MoonSpireUiTextures.HEALTH_FILL, barX, barY, segments.healthWidth(), barHeight, packedLight);
        if (combatant.defense() > 0) {
            drawTexturedFill(matrix, bufferSource, MoonSpireUiTextures.BLOCK_FILL, barX + segments.blockX(), barY, segments.blockWidth(), barHeight, packedLight);
        }
        Component text = combatant.defense() > 0
                ? Component.translatable("screen.moonspire.world_bar_block", Math.round(combatant.health()), combatant.defense(), combatant.roundSpeed())
                : Component.translatable("screen.moonspire.world_bar", Math.round(combatant.health()), combatant.roundSpeed());
        font.drawInBatch(text, -font.width(text) / 2.0F, -15, 0xFFFFFFFF, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
    }

    private static void drawIntent(Font font, Matrix4f matrix, MultiBufferSource bufferSource, List<CardInstance> intentCards, int packedLight) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        int attack = 0;
        int block = 0;
        int negative = 0;
        int positive = 0;
        for (CardInstance card : intentCards) {
            attack += Math.max(0, card.hasAttack() ? CardRenderHelper.previewAttack(card, snapshot.monster(), snapshot.player()) : 0);
            block += Math.max(0, card.selfEffectAmount(CardEffectKind.BLOCK));
            for (CardEffect effect : card.effects()) {
                if (effect.kind() == CardEffectKind.BLEED) {
                    negative += Math.max(0, effect.amount()) * Math.max(1, effect.count());
                } else if (effect.kind() == CardEffectKind.GUARD && effect.target().targetsSelf()) {
                    positive += Math.max(0, effect.amount()) * Math.max(1, effect.count());
                }
            }
        }
        int x = -43;
        if (attack > 0) {
            Component text = Component.translatable("screen.moonspire.intent_attack", attack);
            font.drawInBatch(text, x, -23, 0xFFFF6961, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
            x += 28;
        }
        if (block > 0) {
            Component text = Component.translatable("screen.moonspire.intent_block", block);
            font.drawInBatch(text, x, -23, 0xFF66BFFF, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
            x += 28;
        }
        if (positive > 0) {
            Component text = Component.translatable("screen.moonspire.intent_positive", positive);
            font.drawInBatch(text, x, -23, 0xFF8DE6A6, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
            x += 28;
        }
        if (negative > 0) {
            Component text = Component.translatable("screen.moonspire.intent_effect", negative);
            font.drawInBatch(text, x, -23, 0xFFFF8AA0, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
        }
    }

    private static void drawEffects(Font font, Matrix4f matrix, MultiBufferSource bufferSource, List<BattleEffectSnapshot> effects, int packedLight) {
        int x = -effects.size() * 9;
        for (BattleEffectSnapshot effect : effects) {
            int iconX = x - 8;
            int iconY = 11;
            int iconSize = 18;
            ResourceLocation texture = effectIconTexture(effect.type());
            if (texture != null) {
                VertexConsumer textured = bufferSource.getBuffer(RenderType.text(texture));
                MoonSpireUiTextures.drawWorldBillboard(matrix, textured, iconX, iconY, iconSize, iconSize, 0.06F, packedLight, 0.0F, 0.0F, 1.0F, 1.0F);
            } else {
                Component marker = Component.translatable("screen.moonspire.effect_unknown_icon");
                font.drawInBatch(marker, iconX + (iconSize - font.width(marker)) / 2.0F, iconY + 4, 0xFFFFB1C0, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
            }
            Component text = Component.translatable("screen.moonspire.effect_short", effect.amount());
            font.drawInBatch(text, iconX + iconSize - font.width(text), iconY + iconSize - 9, 0xFFFFFFFF, true, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
            x += 18;
        }
    }

    private static ResourceLocation effectIconTexture(BattleEffectType type) {
        if (type.iconTexturePath() == null || type.iconTexturePath().isBlank()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(com.yinfires.moonspire.MoonSpire.MOD_ID, "textures/" + type.iconTexturePath());
    }

    private static void drawDamageNumbers(Font font, Matrix4f matrix, MultiBufferSource bufferSource, int entityId, int packedLight) {
        for (ClientBattleState.DamageNumber number : ClientBattleState.damageNumbers()) {
            if (number.entityId() != entityId || number.visibleAge() <= 0) {
                continue;
            }
            int y = -24 - number.visibleAge();
            int color = number.block() ? 0xFF66BFFF : 0xFFFF5454;
            String text = Integer.toString(number.amount());
            font.drawInBatch(text, -font.width(text) / 2.0F, y, color, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
        }
    }

    private static void drawBlockGainAnimations(Matrix4f matrix, MultiBufferSource bufferSource, Entity entity, int packedLight) {
        long now = System.nanoTime();
        for (ClientBattleState.BlockGainAnimation animation : ClientBattleState.blockGainAnimations()) {
            if (animation.entityId() != entity.getId()) {
                continue;
            }
            float progress = animation.progress(now);
            int alpha = blockGainAlpha(progress);
            if (alpha <= 0) {
                continue;
            }
            int size = blockGainSize(entity);
            int x = -size / 2;
            int y = blockGainY(entity, progress, size);
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.text(MoonSpireUiTextures.BLOCK_GAIN_ANIMATION));
            MoonSpireUiTextures.drawWorldBillboard(matrix, consumer, x, y, size, size, 0.12F, packedLight, 255, 255, 255, alpha, 0.0F, 0.0F, 1.0F, 1.0F);
        }
    }

    private static int blockGainSize(Entity entity) {
        float bodyScale = Math.max(entity.getBbWidth() * 60.0F, entity.getBbHeight() * 26.0F);
        return Math.max(42, Math.round(bodyScale));
    }

    private static int blockGainY(Entity entity, float progress, int size) {
        float drop = progress < 0.28F ? easeOutCubic(progress / 0.28F) : 1.0F;
        int bodyCenterY = Math.round((0.55F + entity.getBbHeight() * 0.5F) / 0.025F);
        int centerY = bodyCenterY - size / 2;
        return Math.round(centerY - 22.0F * (1.0F - drop));
    }

    private static int blockGainAlpha(float progress) {
        if (progress < 0.28F) {
            return Math.round(255.0F * easeOutCubic(progress / 0.28F));
        }
        if (progress < 0.74F) {
            return 255;
        }
        return Math.round(255.0F * Math.max(0.0F, 1.0F - easeInCubic((progress - 0.74F) / 0.26F)));
    }

    private static float easeOutCubic(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        float inverse = 1.0F - clamped;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeInCubic(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * clamped;
    }

    private static void drawTexturedFill(Matrix4f matrix, MultiBufferSource bufferSource, net.minecraft.resources.ResourceLocation texture, int x, int y, int width, int height, int packedLight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.text(texture));
        MoonSpireUiTextures.drawWorldBillboard(matrix, consumer, x, y, width, height, 0.02F, packedLight, 0.0F, 0.0F, 1.0F, 1.0F);
    }
}
