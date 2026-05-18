package com.yinfires.moonspire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class BattleWorldOverlay {
    private static final double COMBATANT_OVERLAY_Y_OFFSET = 0.9D;
    private static final float WORLD_TEXT_Z = 0.18F;
    private static final ResourceLocation GUARDIAN_BEAM_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/guardian_beam.png");
    private static final RenderType GUARDIAN_BEAM_RENDER_TYPE = RenderType.entityCutoutNoCull(GUARDIAN_BEAM_TEXTURE);
    private static final RenderOnlyProjectileEntities PROJECTILE_ENTITIES = new RenderOnlyProjectileEntities();

    private BattleWorldOverlay() {
    }

    public static void renderLevel(PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        renderProjectileVisuals(minecraft, poseStack, camera, bufferSource, partialTick);
        for (BattleCombatantSnapshot combatant : snapshot.players()) {
            if (combatant.fakeDead()) {
                continue;
            }
            renderHighlight(minecraft.level.getEntity(combatant.entityId()), combatant.entityId(), poseStack, camera, bufferSource);
        }
        for (BattleCombatantSnapshot combatant : snapshot.enemies()) {
            if (combatant.fakeDead()) {
                continue;
            }
            renderHighlight(minecraft.level.getEntity(combatant.entityId()), combatant.entityId(), poseStack, camera, bufferSource);
        }
        renderGuardianBeams(minecraft, poseStack, camera, bufferSource, partialTick);
    }

    public static boolean suppressBattleNameTag(Entity entity) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            return false;
        }
        return snapshot.combatant(entity.getId()) != null;
    }

    public static void renderLivingOverlay(Entity entity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        if (!snapshot.active()) {
            return;
        }
        BattleCombatantSnapshot combatant = null;
        boolean monster = false;
        combatant = snapshot.combatant(entity.getId());
        monster = snapshot.isEnemyEntity(entity.getId());
        if (combatant == null) {
            return;
        }
        if (combatant.fakeDead()) {
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
        if (snapshot.hasIntentCardsFor(entity.getId())) {
            drawIntent(font, matrix, bufferSource, snapshot.intentCardsFor(entity.getId()), combatant, light);
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
        boolean selected = !ClientBattleState.hasHoveredEntityIds() && ClientBattleState.selectedTargetId() == entityId;
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

    private static void renderGuardianBeams(Minecraft minecraft, PoseStack poseStack, Camera camera, MultiBufferSource bufferSource, float partialTick) {
        Vec3 cameraPos = camera.getPosition();
        VertexConsumer consumer = bufferSource.getBuffer(GUARDIAN_BEAM_RENDER_TYPE);
        for (ClientBattleState.GuardianBeamAnimation beam : ClientBattleState.guardianBeamAnimations()) {
            Entity attacker = minecraft.level.getEntity(beam.attackerId());
            Entity target = minecraft.level.getEntity(beam.targetId());
            if (!(attacker instanceof LivingEntity livingAttacker) || target == null) {
                continue;
            }
            renderGuardianBeam(poseStack, consumer, livingAttacker, target, cameraPos, beam, partialTick);
        }
    }

    private static void renderProjectileVisuals(Minecraft minecraft, PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        if (minecraft.level == null) {
            return;
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Vec3 cameraPos = camera.getPosition();
        for (ClientBattleState.ProjectileVisual visual : ClientBattleState.projectileVisuals()) {
            if (!visual.visible(partialTick)) {
                continue;
            }
            Entity projectile = PROJECTILE_ENTITIES.entityFor(minecraft, visual);
            if (projectile == null) {
                continue;
            }
            Vec3 position = visual.position(partialTick);
            Vec3 direction = visual.direction();
            float yaw = (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
            float pitch = (float) (Mth.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * Mth.RAD_TO_DEG);
            projectile.setPos(position);
            projectile.xOld = position.x;
            projectile.yOld = position.y;
            projectile.zOld = position.z;
            projectile.tickCount = Math.max(3, visual.age());
            projectile.setYRot(yaw);
            projectile.yRotO = yaw;
            projectile.setXRot(pitch);
            projectile.xRotO = pitch;
            dispatcher.render(projectile, position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z, yaw, partialTick, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
        }
    }

    private static void renderGuardianBeam(PoseStack poseStack, VertexConsumer consumer, LivingEntity attacker, Entity target, Vec3 cameraPos, ClientBattleState.GuardianBeamAnimation beam, float partialTick) {
        float attackScale = beam.attackScale(partialTick);
        float attackTime = beam.attackTime(partialTick);
        float textureOffset = attackTime * 0.5F % 1.0F;
        float eyeHeight = attacker.getEyeHeight();
        Vec3 targetPos = new Vec3(
                Mth.lerp(partialTick, target.xOld, target.getX()),
                Mth.lerp(partialTick, target.yOld, target.getY()) + target.getBbHeight() * 0.5D,
                Mth.lerp(partialTick, target.zOld, target.getZ()));
        Vec3 attackerPos = new Vec3(
                Mth.lerp(partialTick, attacker.xOld, attacker.getX()),
                Mth.lerp(partialTick, attacker.yOld, attacker.getY()) + eyeHeight,
                Mth.lerp(partialTick, attacker.zOld, attacker.getZ()));
        Vec3 direction = targetPos.subtract(attackerPos);
        float length = (float) (direction.length() + 1.0D);
        if (length <= 0.05F) {
            return;
        }
        direction = direction.normalize();
        float xRotation = (float) Math.acos(direction.y);
        float yRotation = (float) Math.atan2(direction.z, direction.x);
        poseStack.pushPose();
        poseStack.translate(attackerPos.x - cameraPos.x, attackerPos.y - cameraPos.y, attackerPos.z - cameraPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(((float) (Math.PI / 2.0D) - yRotation) * (180.0F / (float) Math.PI)));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRotation * (180.0F / (float) Math.PI)));
        float scroll = attackTime * 0.05F * -1.5F;
        float colorScale = attackScale * attackScale;
        int red = 64 + (int) (colorScale * 191.0F);
        int green = 32 + (int) (colorScale * 191.0F);
        int blue = 128 - (int) (colorScale * 64.0F);
        float wide = 0.282F;
        float narrow = 0.2F;
        float x0 = Mth.cos(scroll + (float) Math.PI) * narrow;
        float z0 = Mth.sin(scroll + (float) Math.PI) * narrow;
        float x1 = Mth.cos(scroll) * narrow;
        float z1 = Mth.sin(scroll) * narrow;
        float x2 = Mth.cos(scroll + (float) (Math.PI / 2.0D)) * narrow;
        float z2 = Mth.sin(scroll + (float) (Math.PI / 2.0D)) * narrow;
        float x3 = Mth.cos(scroll + (float) (Math.PI * 3.0D / 2.0D)) * narrow;
        float z3 = Mth.sin(scroll + (float) (Math.PI * 3.0D / 2.0D)) * narrow;
        float x4 = Mth.cos(scroll + (float) (Math.PI * 3.0D / 4.0D)) * wide;
        float z4 = Mth.sin(scroll + (float) (Math.PI * 3.0D / 4.0D)) * wide;
        float x5 = Mth.cos(scroll + (float) (Math.PI / 4.0D)) * wide;
        float z5 = Mth.sin(scroll + (float) (Math.PI / 4.0D)) * wide;
        float x6 = Mth.cos(scroll + (float) (Math.PI * 5.0D / 4.0D)) * wide;
        float z6 = Mth.sin(scroll + (float) (Math.PI * 5.0D / 4.0D)) * wide;
        float x7 = Mth.cos(scroll + (float) (Math.PI * 7.0D / 4.0D)) * wide;
        float z7 = Mth.sin(scroll + (float) (Math.PI * 7.0D / 4.0D)) * wide;
        float v0 = -1.0F + textureOffset;
        float v1 = length * 2.5F + v0;
        PoseStack.Pose pose = poseStack.last();
        vertex(consumer, pose, x0, length, z0, red, green, blue, 0.4999F, v1);
        vertex(consumer, pose, x0, 0.0F, z0, red, green, blue, 0.4999F, v0);
        vertex(consumer, pose, x1, 0.0F, z1, red, green, blue, 0.0F, v0);
        vertex(consumer, pose, x1, length, z1, red, green, blue, 0.0F, v1);
        vertex(consumer, pose, x2, length, z2, red, green, blue, 0.4999F, v1);
        vertex(consumer, pose, x2, 0.0F, z2, red, green, blue, 0.4999F, v0);
        vertex(consumer, pose, x3, 0.0F, z3, red, green, blue, 0.0F, v0);
        vertex(consumer, pose, x3, length, z3, red, green, blue, 0.0F, v1);
        float capV = attacker.tickCount % 2 == 0 ? 0.5F : 0.0F;
        vertex(consumer, pose, x4, length, z4, red, green, blue, 0.5F, capV + 0.5F);
        vertex(consumer, pose, x5, length, z5, red, green, blue, 1.0F, capV + 0.5F);
        vertex(consumer, pose, x7, length, z7, red, green, blue, 1.0F, capV);
        vertex(consumer, pose, x6, length, z6, red, green, blue, 0.5F, capV);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int red, int green, int blue, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
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
        drawWorldText(font, text, -font.width(text) / 2.0F, -15, 0xFFFFFFFF, false, matrix, bufferSource, packedLight);
    }

    private static void drawIntent(Font font, Matrix4f matrix, MultiBufferSource bufferSource, List<CardInstance> intentCards, BattleCombatantSnapshot enemy, int packedLight) {
        BattleSnapshot snapshot = ClientBattleState.snapshot();
        boolean enemySideActor = snapshot.isEnemyEntity(enemy.entityId());
        BattleCombatantSnapshot primaryOpponent = primaryOpponent(snapshot, enemySideActor);
        int attack = 0;
        int block = 0;
        int negative = 0;
        int positive = 0;
        int paralysis = Math.max(0, CardRenderHelper.effectAmount(enemy, BattleEffectType.PARALYSIS));
        for (CardInstance card : intentCards) {
            boolean paralyzedAttack = paralysis > 0 && card.hasAttack();
            attack += Math.max(0, card.hasAttack() && primaryOpponent != null ? CardRenderHelper.previewAttack(card, enemy, primaryOpponent, paralyzedAttack) : 0);
            if (paralyzedAttack) {
                paralysis--;
            }
            for (CardEffect effect : card.effects()) {
                if (effect.kind() == CardEffectKind.BLOCK && effect.target().targetsSelf()) {
                    block += Math.max(0, effect.amount()) * Math.max(1, effect.count());
                } else if (effect.kind() == CardEffectKind.BLEED && effect.target().targetsEnemy()) {
                    negative += Math.max(0, effect.amount()) * Math.max(1, effect.count());
                } else if (negativeEffect(effect.kind()) && effect.target().targetsEnemy()) {
                    negative += Math.max(0, effect.amount()) * Math.max(1, effect.count());
                } else if (positiveEffect(effect.kind()) && effect.target().targetsSelf()) {
                    positive += Math.max(0, effect.amount()) * Math.max(1, effect.count());
                }
            }
        }
        int x = -43;
        if (attack > 0) {
            Component text = Component.translatable("screen.moonspire.intent_attack", attack);
            drawWorldText(font, text, x, -23, 0xFFFF6961, false, matrix, bufferSource, packedLight);
            x += 28;
        }
        if (block > 0) {
            Component text = Component.translatable("screen.moonspire.intent_block", block);
            drawWorldText(font, text, x, -23, 0xFF66BFFF, false, matrix, bufferSource, packedLight);
            x += 28;
        }
        if (positive > 0) {
            Component text = Component.translatable("screen.moonspire.intent_positive", positive);
            drawWorldText(font, text, x, -23, 0xFF8DE6A6, false, matrix, bufferSource, packedLight);
            x += 28;
        }
        if (negative > 0) {
            Component text = Component.translatable("screen.moonspire.intent_effect", negative);
            drawWorldText(font, text, x, -23, 0xFFFF8AA0, false, matrix, bufferSource, packedLight);
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
                drawWorldText(font, marker, iconX + (iconSize - font.width(marker)) / 2.0F, iconY + 4, 0xFFFFB1C0, false, matrix, bufferSource, packedLight);
            }
            Component text = Component.translatable("screen.moonspire.effect_short", effect.amount());
            int amountColor = effect.type() == BattleEffectType.STRENGTH && effect.amount() < 0 ? 0xFFFF5454 : 0xFFFFFFFF;
            drawWorldText(font, text, iconX + iconSize - font.width(text), iconY + iconSize - 9, amountColor, true, matrix, bufferSource, packedLight);
            x += 18;
        }
    }

    private static boolean positiveEffect(CardEffectKind kind) {
        return kind == CardEffectKind.HEAL
                || kind == CardEffectKind.DRAW_CARDS
                || kind == CardEffectKind.GAIN_ENERGY
                || kind == CardEffectKind.GUARD
                || kind == CardEffectKind.UNDYING
                || CardEffect.isSummonKind(kind)
                || kind == CardEffectKind.STRENGTH
                || kind == CardEffectKind.REGENERATION
                || kind == CardEffectKind.HASTE
                || kind == CardEffectKind.PHASE
                || kind == CardEffectKind.THORNS
                || kind == CardEffectKind.FUSE;
    }

    private static boolean negativeEffect(CardEffectKind kind) {
        return kind == CardEffectKind.GAZE
                || kind == CardEffectKind.LOSE_STRENGTH
                || kind == CardEffectKind.POISON
                || kind == CardEffectKind.BURN
                || kind == CardEffectKind.WITHER
                || kind == CardEffectKind.TIDAL_EROSION
                || kind == CardEffectKind.PARALYSIS
                || kind == CardEffectKind.HUNGER
                || kind == CardEffectKind.WEAKNESS
                || kind == CardEffectKind.SLOWNESS
                || kind == CardEffectKind.GLOWING;
    }

    private static BattleCombatantSnapshot primaryOpponent(BattleSnapshot snapshot, boolean enemySideActor) {
        List<BattleCombatantSnapshot> candidates = enemySideActor ? snapshot.players() : snapshot.enemies();
        for (BattleCombatantSnapshot candidate : candidates) {
            if (!candidate.fakeDead()) {
                return candidate;
            }
        }
        return null;
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
            int color = number.healing() ? 0xFF70E083 : number.block() ? 0xFF66BFFF : 0xFFFF5454;
            String text = number.healing() ? "+" + number.amount() : Integer.toString(number.amount());
            drawWorldText(font, text, -font.width(text) / 2.0F, y, color, false, matrix, bufferSource, packedLight);
        }
    }

    private static void drawWorldText(Font font, Component text, float x, float y, int color, boolean shadow, Matrix4f matrix, MultiBufferSource bufferSource, int packedLight) {
        Matrix4f textMatrix = new Matrix4f(matrix).translate(0.0F, 0.0F, WORLD_TEXT_Z);
        font.drawInBatch(text, x, y, color, shadow, textMatrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
    }

    private static void drawWorldText(Font font, String text, float x, float y, int color, boolean shadow, Matrix4f matrix, MultiBufferSource bufferSource, int packedLight) {
        Matrix4f textMatrix = new Matrix4f(matrix).translate(0.0F, 0.0F, WORLD_TEXT_Z);
        font.drawInBatch(text, x, y, color, shadow, textMatrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
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
            int y = blockGainY(entity, size);
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.textSeeThrough(MoonSpireUiTextures.BLOCK_GAIN_ANIMATION));
            MoonSpireUiTextures.drawWorldBillboard(matrix, consumer, x, y, size, size, 0.12F, packedLight, 255, 255, 255, alpha, 0.0F, 0.0F, 1.0F, 1.0F);
        }
    }

    private static int blockGainSize(Entity entity) {
        float bodyScale = Math.max(entity.getBbWidth() * 60.0F, entity.getBbHeight() * 26.0F);
        return Math.max(42, Math.round(bodyScale));
    }

    private static int blockGainY(Entity entity, int size) {
        int bodyCenterY = Math.round((0.55F + entity.getBbHeight() * 0.5F) / 0.025F);
        return bodyCenterY - size / 2;
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

    private static final class RenderOnlyProjectileEntities {
        private Arrow arrow;
        private SpectralArrow spectralArrow;
        private ThrownTrident trident;
        private ThrownPotion potion;
        private BreezeWindCharge breezeWindCharge;
        private WindCharge windCharge;
        private SmallFireball smallFireball;
        private LargeFireball largeFireball;
        private ShulkerBullet shulkerBullet;
        private int nextVisualId = -1000000;

        private Entity entityFor(Minecraft minecraft, ClientBattleState.ProjectileVisual visual) {
            if (minecraft.level == null) {
                return null;
            }
            if (visual.animationType() == com.yinfires.moonspire.battle.BattleVisualEvent.AnimationType.BLAZE_FIREBALL) {
                if (smallFireball == null || smallFireball.level() != minecraft.level) {
                    smallFireball = EntityType.SMALL_FIREBALL.create(minecraft.level);
                    if (smallFireball != null) {
                        initializeVisualEntity(smallFireball);
                    }
                }
                return smallFireball;
            }
            if (visual.animationType() == com.yinfires.moonspire.battle.BattleVisualEvent.AnimationType.GHAST_FIREBALL) {
                if (largeFireball == null || largeFireball.level() != minecraft.level) {
                    largeFireball = EntityType.FIREBALL.create(minecraft.level);
                    if (largeFireball != null) {
                        initializeVisualEntity(largeFireball);
                    }
                }
                return largeFireball;
            }
            if (visual.animationType() == com.yinfires.moonspire.battle.BattleVisualEvent.AnimationType.SHULKER_BULLET) {
                if (shulkerBullet == null || shulkerBullet.level() != minecraft.level) {
                    shulkerBullet = EntityType.SHULKER_BULLET.create(minecraft.level);
                    if (shulkerBullet != null) {
                        initializeVisualEntity(shulkerBullet);
                    }
                }
                return shulkerBullet;
            }
            ItemStack stack = visual.stack();
            if (visual.animationType() == com.yinfires.moonspire.battle.BattleVisualEvent.AnimationType.WIND_CHARGE) {
                return windChargeEntity(minecraft, visual);
            }
            if (visual.animationType() == com.yinfires.moonspire.battle.BattleVisualEvent.AnimationType.POTION_THROW || stack.is(Items.SPLASH_POTION)) {
                if (potion == null || potion.level() != minecraft.level) {
                    potion = new ThrownPotion(minecraft.level, 0.0D, 0.0D, 0.0D);
                    initializeVisualEntity(potion);
                }
                potion.setItem(stack);
                return potion;
            }
            if (stack.is(Items.TRIDENT)) {
                if (trident == null || trident.level() != minecraft.level) {
                    trident = new ThrownTrident(minecraft.level, 0.0D, 0.0D, 0.0D, stack.copy());
                    initializeVisualEntity(trident);
                }
                return trident;
            }
            if (stack.is(Items.SPECTRAL_ARROW)) {
                if (spectralArrow == null || spectralArrow.level() != minecraft.level) {
                    spectralArrow = new SpectralArrow(minecraft.level, 0.0D, 0.0D, 0.0D, stack.copy(), null);
                    initializeVisualEntity(spectralArrow);
                }
                prepareArrow(spectralArrow);
                return spectralArrow;
            }
            if (arrow == null || arrow.level() != minecraft.level) {
                arrow = new Arrow(minecraft.level, 0.0D, 0.0D, 0.0D, stack.copy(), null);
                initializeVisualEntity(arrow);
            }
            prepareArrow(arrow);
            return arrow;
        }

        private Entity windChargeEntity(Minecraft minecraft, ClientBattleState.ProjectileVisual visual) {
            Entity attacker = minecraft.level.getEntity(visual.attackerId());
            if (attacker != null && attacker.getType() == net.minecraft.world.entity.EntityType.BREEZE) {
                if (breezeWindCharge == null || breezeWindCharge.level() != minecraft.level) {
                    breezeWindCharge = new BreezeWindCharge(net.minecraft.world.entity.EntityType.BREEZE_WIND_CHARGE, minecraft.level);
                    initializeVisualEntity(breezeWindCharge);
                }
                return breezeWindCharge;
            }
            if (windCharge == null || windCharge.level() != minecraft.level) {
                windCharge = new WindCharge(net.minecraft.world.entity.EntityType.WIND_CHARGE, minecraft.level);
                initializeVisualEntity(windCharge);
            }
            return windCharge;
        }

        private void initializeVisualEntity(Entity entity) {
            entity.setId(nextVisualId--);
            entity.setNoGravity(true);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.tickCount = 1;
            entity.setSilent(true);
            entity.setInvisible(false);
            entity.setInvulnerable(true);
            entity.setSharedFlagOnFire(false);
        }

        private void prepareArrow(AbstractArrow arrow) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setBaseDamage(0.0D);
            arrow.setNoGravity(true);
        }
    }
}
