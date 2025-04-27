package net.superpro.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerMixin extends PlayerEntity {
	public ServerPlayerMixin(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Shadow public abstract void sendMessage(Text message);

	@Shadow public abstract void sendMessage(Text message, boolean overlay);

	@Shadow public abstract ServerWorld getServerWorld();


	@Shadow public abstract void setSpawnPoint(RegistryKey<World> dimension, @Nullable BlockPos pos, float angle, boolean forced, boolean sendMessage);

	@Shadow protected abstract boolean isBedObstructed(BlockPos pos, Direction direction);

	@Shadow protected abstract boolean isBedWithinRange(BlockPos pos, Direction direction);

	@Inject(method = "trySleep", at = @At(value = "HEAD"), cancellable = true)
	protected void trySleep(BlockPos pos, CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> cir) {
		Direction direction = this.getWorld().getBlockState(pos).get(HorizontalFacingBlock.FACING);
		if (!this.isSleeping() && this.isAlive()) {
			if (!this.getWorld().getDimension().natural()) {
				cir.setReturnValue(Either.left(PlayerEntity.SleepFailureReason.NOT_POSSIBLE_HERE));
			} else if (!this.isBedWithinRange(pos, direction)) {
				cir.setReturnValue(Either.left(PlayerEntity.SleepFailureReason.TOO_FAR_AWAY));
			} else if (this.isBedObstructed(pos, direction)) {
				cir.setReturnValue(Either.left(PlayerEntity.SleepFailureReason.OBSTRUCTED));
			} else {
				this.setSpawnPoint(this.getWorld().getRegistryKey(), pos, this.getYaw(), false, true);
				if (!this.isCreative()) {
					Vec3d vec3d = Vec3d.ofBottomCenter(pos);
					List<HostileEntity> list = this.getWorld().getEntitiesByClass(HostileEntity.class, new Box(vec3d.getX() - 8.0, vec3d.getY() - 5.0, vec3d.getZ() - 8.0, vec3d.getX() + 8.0, vec3d.getY() + 5.0, vec3d.getZ() + 8.0), (entity) -> entity.isAngryAt(this.getServerWorld(), this));
					if (!list.isEmpty()) {
						cir.setReturnValue(Either.left(PlayerEntity.SleepFailureReason.NOT_SAFE));
					}
				}
				Either<PlayerEntity.SleepFailureReason, Unit> either = super.trySleep(pos).ifRight((unit) -> {
					this.incrementStat(Stats.SLEEP_IN_BED);
					Criteria.SLEPT_IN_BED.trigger((ServerPlayerEntity) (Object)this);
				});
				if (!this.getServerWorld().isSleepingEnabled()) {
					this.sendMessage(Text.translatable("sleep.not_possible"), true);
				}
				((ServerWorld)this.getWorld()).updateSleepingPlayers();
				cir.setReturnValue(either);
			}
		} else {
			cir.setReturnValue(Either.left(PlayerEntity.SleepFailureReason.OTHER_PROBLEM));
		}
	}
}