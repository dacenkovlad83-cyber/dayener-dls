package com.triger.triggerbot.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Cooldown HUD: start / end tick of one cooldown (the record class is private in vanilla). */
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
	@Accessor("startTime")
	int dls$getStartTime();

	@Accessor("endTime")
	int dls$getEndTime();
}
