package cn.zhonjc.quickwriter.mixin;

import cn.zhonjc.quickwriter.client.EditController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes mouse buttons to {@link EditController} while edit mode is on. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void quickwriter$startAttack(CallbackInfoReturnable<Boolean> cir) {
        if (EditController.onAttack((Minecraft) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void quickwriter$continueAttack(boolean down, CallbackInfo ci) {
        if (EditController.onContinueAttack()) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void quickwriter$startUseItem(CallbackInfo ci) {
        if (EditController.onUse((Minecraft) (Object) this)) ci.cancel();
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void quickwriter$pick(CallbackInfo ci) {
        if (EditController.onPick((Minecraft) (Object) this)) ci.cancel();
    }
}
