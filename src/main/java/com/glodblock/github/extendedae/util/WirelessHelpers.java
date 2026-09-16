package com.glodblock.github.extendedae.util;

import com.glodblock.github.extendedae.ExtendedAE;
import com.glodblock.github.extendedae.common.me.wireless.WirelessFail;
import com.glodblock.github.extendedae.common.tileentities.TileWirelessConnector;
import com.glodblock.github.extendedae.common.tileentities.TileWirelessHub;
import com.glodblock.github.extendedae.config.EPPConfig;
import com.mojang.datafixers.util.Pair;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

public class WirelessHelpers {
    public static boolean hasNoPorts(TileWirelessHub tile, Player player) {
        if (tile.allocatePort() < 0) {
            player.displayClientMessage(WirelessFail.OUT_OF_PORT.getTranslation(), true);
            return true;
        }
        return false;
    }

    public static void bindWireless(CompoundTag nbt, long freq, GlobalPos pos) {
        nbt.putLong("freq", freq);
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos)
                .result()
                .ifPresent(tag -> nbt.put("bind", tag));
    }

    public static InteractionResult connectWireless(
            Consumer<Long> setThisFreq,
            CompoundTag nbt,
            Level world,
            BlockPos thisPos,
            Player player,
            Runnable onSuccess) {
        if (!nbt.contains("bind")) {
            player.displayClientMessage(WirelessFail.MISSING.getTranslation(), true);
            return InteractionResult.FAIL;
        }

        GlobalPos globalPos = GlobalPos.CODEC.decode(NbtOps.INSTANCE, nbt.get("bind"))
                .resultOrPartial(Util.prefix("Connector position", ExtendedAE.LOGGER::error))
                .map(Pair::getFirst)
                .orElse(null);

        if (globalPos == null) {
            player.displayClientMessage(WirelessFail.MISSING.getTranslation(), true);
            return InteractionResult.FAIL;
        }

        BlockPos otherPos = globalPos.pos();
        ResourceKey<Level> otherWorld = globalPos.dimension();
        ResourceKey<Level> thisWorld = world.dimension();

        if (otherPos.equals(thisPos) && otherWorld.equals(thisWorld)) {
            player.displayClientMessage(WirelessFail.SELF_REFERENCE.getTranslation(), true);
            return InteractionResult.FAIL;
        }

        if (!otherWorld.equals(thisWorld)) {
            player.displayClientMessage(WirelessFail.CROSS_DIMENSION.getTranslation(), true);
            return InteractionResult.FAIL;
        }

        if (Math.sqrt(otherPos.distSqr(thisPos)) > EPPConfig.wirelessMaxRange) {
            player.displayClientMessage(WirelessFail.OUT_OF_RANGE.getTranslation(), true);
            return InteractionResult.FAIL;
        }

        ServerLevel otherWorldInstance = world.getServer().getLevel(otherWorld);
        if (otherWorldInstance == null) {
            player.displayClientMessage(WirelessFail.MISSING.getTranslation(), true);
            return InteractionResult.FAIL;
        }

        BlockEntity otherTile = otherWorldInstance.getBlockEntity(globalPos.pos());
        long freq = nbt.getLong("freq");

        if (otherTile instanceof TileWirelessConnector) {
            TileWirelessConnector otherConnector = (TileWirelessConnector) otherTile;
            otherConnector.setFrequency(freq);
            setThisFreq.accept(freq);
            onSuccess.run();
            player.displayClientMessage(
                    Component.translatable("chat.wireless_connect", thisPos.getX(), thisPos.getY(), thisPos.getZ()), true);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else if (otherTile instanceof TileWirelessHub) {
            TileWirelessHub otherHub = (TileWirelessHub) otherTile;
            int otherPort = otherHub.allocatePort();
            if (otherPort < 0) {
                player.displayClientMessage(WirelessFail.OUT_OF_PORT.getTranslation(), true);
                return InteractionResult.FAIL;
            }
            otherHub.setFrequency(freq, otherPort);
            setThisFreq.accept(freq);
            onSuccess.run();
            player.displayClientMessage(
                    Component.translatable("chat.wireless_connect", thisPos.getX(), thisPos.getY(), thisPos.getZ()), true);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            player.displayClientMessage(WirelessFail.MISSING.getTranslation(), true);
            return InteractionResult.FAIL;
        }
    }
}
