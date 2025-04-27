package net.superpro.mixin;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.SleepManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.village.raid.RaidManager;
import net.minecraft.world.EntityList;
import net.minecraft.world.GameRules;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.tick.TickManager;
import net.minecraft.world.tick.WorldTickScheduler;
import net.superpro.BedTickInfo;
import net.superpro.ModMenuIntegration;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin extends World {
	@Shadow private boolean inBlockTick;

	@Shadow protected abstract void tickWeather();

	@Shadow public abstract GameRules getGameRules();

	@Shadow @Final private SleepManager sleepManager;

	@Shadow public abstract void setTimeOfDay(long timeOfDay);

	@Shadow protected abstract void wakeSleepingPlayers();

	@Shadow public abstract void resetWeather();

	@Shadow public abstract void tick(BooleanSupplier shouldKeepTicking);

	@Shadow protected abstract void tickTime();

	@Shadow @Final private WorldTickScheduler<Block> blockTickScheduler;

	@Shadow @Final private WorldTickScheduler<Fluid> fluidTickScheduler;

	@Shadow protected abstract void tickFluid(BlockPos pos, Fluid fluid);

	@Shadow protected abstract void tickBlock(BlockPos pos, Block block);

	@Shadow protected abstract void processSyncedBlockEvents();

	@Shadow @Final protected RaidManager raidManager;

	@Shadow @Final private ServerEntityManager<Entity> entityManager;

	@Shadow @Final private EntityList entityList;

	@Shadow @Final private ServerChunkManager chunkManager;

	@Shadow public abstract void tickEntity(Entity entity);

	@Shadow @Final private List<ServerPlayerEntity> players;

	@Shadow public abstract LongSet getForcedChunks();

	@Shadow public abstract void resetIdleTimeout();

	@Shadow private int idleTimeout;

	@Shadow private @Nullable EnderDragonFight enderDragonFight;

	protected ServerWorldMixin(MutableWorldProperties properties, RegistryKey<World> registryRef, DynamicRegistryManager registryManager, RegistryEntry<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
		super(properties, registryRef, registryManager, dimensionEntry, isClient, debugWorld, seed, maxChainedNeighborUpdates);
	}

	@Inject(at = @At("HEAD"), method = "tick", cancellable = true)
	private void tick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
		for (int repete = 1; repete <= (BedTickInfo.isSpeeding ? ModMenuIntegration.gameSpeedMultiplier : 1); repete++) {
			Profiler profiler = Profilers.get();
			this.inBlockTick = true;
			TickManager tickManager = this.getTickManager();
			boolean bl = tickManager.shouldTick();
			if (bl) {
				profiler.push("world border");
				this.getWorldBorder().tick();
				profiler.swap("weather");
				this.tickWeather();
				profiler.pop();
			}

			int i = this.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);

			if(!isDay() && BedTickInfo.isSpeeding) {
				this.wakeSleepingPlayers();
				BedTickInfo.isSpeeding = false;
			}

			if (this.sleepManager.canSkipNight(i) && this.sleepManager.canResetTime(i, this.players)) {
				if(isDay()) {
					BedTickInfo.isSpeeding = true;
				} else {
					if (this.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) {
						long l = this.properties.getTimeOfDay() + 24000L;
						this.setTimeOfDay(l - l % 24000L);
					}

					this.wakeSleepingPlayers();
					if (this.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE) && this.isRaining()) {
						this.resetWeather();
					}
				}
			} else {
				BedTickInfo.isSpeeding = false;
			}

			this.calculateAmbientDarkness();
			if (bl) {
				this.tickTime();
			}

			profiler.push("tickPending");
			if (!this.isDebugWorld() && bl) {
				long l = this.getTime();
				profiler.push("blockTicks");
				this.blockTickScheduler.tick(l, 65536, this::tickBlock);
				profiler.swap("fluidTicks");
				this.fluidTickScheduler.tick(l, 65536, this::tickFluid);
				profiler.pop();
			}

			profiler.swap("raid");
			if (bl) {
				this.raidManager.tick();
			}

			profiler.swap("chunkSource");
			this.getChunkManager().tick(shouldKeepTicking, true);
			profiler.swap("blockEvents");
			if (bl) {
				this.processSyncedBlockEvents();
			}

			this.inBlockTick = false;
			profiler.pop();
			boolean bl2 = !this.players.isEmpty() || !this.getForcedChunks().isEmpty();
			if (bl2) {
				this.resetIdleTimeout();
			}

			if (bl2 || this.idleTimeout++ < 300) {
				profiler.push("entities");
				if (this.enderDragonFight != null && bl) {
					profiler.push("dragonFight");
					this.enderDragonFight.tick();
					profiler.pop();
				}

				this.entityList.forEach(entity -> {
					if (!entity.isRemoved()) {
						if (!tickManager.shouldSkipTick(entity)) {
							profiler.push("checkDespawn");
							entity.checkDespawn();
							profiler.pop();
							if (entity instanceof ServerPlayerEntity || this.chunkManager.chunkLoadingManager.getTicketManager().shouldTickEntities(entity.getChunkPos().toLong())) {
								Entity entity2 = entity.getVehicle();
								if (entity2 != null) {
									if (!entity2.isRemoved() && entity2.hasPassenger(entity)) {
										return;
									}

									entity.stopRiding();
								}

								profiler.push("tick");
								this.tickEntity(this::tickEntity, entity);
								profiler.pop();
							}
						}
					}
				});
				profiler.pop();
				this.tickBlockEntities();
			}

			profiler.push("entityManagement");
			this.entityManager.tick();
			profiler.pop();
		}
		ci.cancel();
	}
}