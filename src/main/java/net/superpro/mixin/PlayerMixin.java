package net.superpro.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerMixin extends LivingEntity {
	@Shadow public int experiencePickUpDelay;

	@Shadow private int sleepTimer;

	@Shadow protected abstract boolean updateWaterSubmersionState();

	@Shadow protected abstract void closeHandledScreen();

	@Shadow public ScreenHandler currentScreenHandler;

	@Shadow @Final public PlayerScreenHandler playerScreenHandler;

	@Shadow protected abstract void updateCapeAngles();

	@Shadow protected HungerManager hungerManager;

	@Shadow public abstract void incrementStat(Identifier stat);

	@Shadow private ItemStack selectedItem;

	@Shadow public abstract void resetLastAttackedTicks();

	@Shadow protected abstract void updateTurtleHelmet();

	@Shadow protected abstract boolean isEquipped(Item item);

	@Shadow @Final private ItemCooldownManager itemCooldownManager;

	@Shadow protected abstract void updatePose();

	@Shadow private int currentExplosionResetGraceTime;

	protected PlayerMixin(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "tick", at = @At(value = "HEAD"), cancellable = true)
	public void tick(CallbackInfo ci) {
		this.noClip = this.isSpectator();
		if (this.isSpectator() || this.hasVehicle()) {
			this.setOnGround(false);
		}

		if (this.experiencePickUpDelay > 0) {
			--this.experiencePickUpDelay;
		}

		if (this.isSleeping()) {
			++this.sleepTimer;
			if (this.sleepTimer > 100) {
				this.sleepTimer = 100;
			}
		} else if (this.sleepTimer > 0) {
			++this.sleepTimer;
			if (this.sleepTimer >= 110) {
				this.sleepTimer = 0;
			}
		}

		this.updateWaterSubmersionState();
		super.tick();
		if (!this.getWorld().isClient && this.currentScreenHandler != null && !this.currentScreenHandler.canUse((PlayerEntity) (Object)this)) {
			this.closeHandledScreen();
			this.currentScreenHandler = this.playerScreenHandler;
		}

		this.updateCapeAngles();
		if ((PlayerEntity)(Object)this instanceof ServerPlayerEntity serverPlayerEntity) {
			this.hungerManager.update(serverPlayerEntity);
			this.incrementStat(Stats.PLAY_TIME);
			this.incrementStat(Stats.TOTAL_WORLD_TIME);
			if (this.isAlive()) {
				this.incrementStat(Stats.TIME_SINCE_DEATH);
			}

			if (this.isSneaky()) {
				this.incrementStat(Stats.SNEAK_TIME);
			}

			if (!this.isSleeping()) {
				this.incrementStat(Stats.TIME_SINCE_REST);
			}
		}

		int i = 29999999;
		double d = MathHelper.clamp(this.getX(), -2.9999999E7, 2.9999999E7);
		double e = MathHelper.clamp(this.getZ(), -2.9999999E7, 2.9999999E7);
		if (d != this.getX() || e != this.getZ()) {
			this.setPosition(d, this.getY(), e);
		}

		++this.lastAttackedTicks;
		ItemStack itemStack = this.getMainHandStack();
		if (!ItemStack.areEqual(this.selectedItem, itemStack)) {
			if (!ItemStack.areItemsEqual(this.selectedItem, itemStack)) {
				this.resetLastAttackedTicks();
			}

			this.selectedItem = itemStack.copy();
		}

		if (!this.isSubmergedIn(FluidTags.WATER) && this.isEquipped(Items.TURTLE_HELMET)) {
			this.updateTurtleHelmet();
		}

		this.itemCooldownManager.update();
		this.updatePose();
		if (this.currentExplosionResetGraceTime > 0) {
			--this.currentExplosionResetGraceTime;
		}
		ci.cancel();
	}
}