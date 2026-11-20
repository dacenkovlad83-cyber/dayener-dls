package com.triger.triggerbot.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Cooldown HUD: the cooldown table (group → start/end tick) and the current tick. */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
	@Accessor("cooldowns")
	Map<Identifier, ?> dls$getCooldowns();

	@Accessor("tickCount")
	int dls$getTickCount();
}
