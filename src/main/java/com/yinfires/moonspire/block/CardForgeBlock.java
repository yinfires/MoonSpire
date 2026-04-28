package com.yinfires.moonspire.block;

import com.mojang.serialization.MapCodec;
import com.yinfires.moonspire.network.OpenCardForgeScreenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public class CardForgeBlock extends Block {
    public static final MapCodec<CardForgeBlock> CODEC = simpleCodec(CardForgeBlock::new);

    public CardForgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenCardForgeScreenPayload(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
