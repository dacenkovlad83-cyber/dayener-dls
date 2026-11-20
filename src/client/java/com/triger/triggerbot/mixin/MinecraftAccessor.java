package com.triger.triggerbot.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	/** The real launcher user, ignoring the Alt Manager override. */
	@Accessor("user")
	User dls$getRealUser();

	/** FastExp: delay between item uses while the use key is held. */
	@Accessor("rightClickDelay")
	int dls$getRightClickDelay();

	@Accessor("rightClickDelay")
	void dls$setRightClickDelay(int delay);
}
