package com.yinfires.moonspire.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

public final class MoonSpireArmPoses {
    public static final EnumProxy<HumanoidModel.ArmPose> EVOKER_SPELLCASTING = new EnumProxy<>(
            HumanoidModel.ArmPose.class,
            true,
            (net.neoforged.neoforge.client.IArmPoseTransformer) MoonSpireArmPoses::applyEvokerSpellcastingPose);

    private MoonSpireArmPoses() {
    }

    public static HumanoidModel.ArmPose evokerSpellcasting() {
        return EVOKER_SPELLCASTING.getValue();
    }

    private static void applyEvokerSpellcastingPose(HumanoidModel<?> model, net.minecraft.world.entity.LivingEntity entity, HumanoidArm arm) {
        ModelPart modelArm = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float side = arm == HumanoidArm.RIGHT ? -1.0F : 1.0F;
        modelArm.z = 0.0F;
        modelArm.x = side * 5.0F;
        modelArm.xRot = Mth.cos(entity.tickCount * 0.6662F) * 0.25F;
        modelArm.zRot = -side * (float) (Math.PI * 3.0D / 4.0D);
        modelArm.yRot = 0.0F;
    }
}
