package net.superpro;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaySleep implements ModInitializer {
	public static final String MOD_ID = "day-sleep";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		EntitySleepEvents.ALLOW_SLEEP_TIME.register(this::allowSleepTime);
	}

	private ActionResult allowSleepTime(PlayerEntity player, BlockPos sleepingPos, boolean vanillaResult) {
		return ActionResult.SUCCESS;
	}
}