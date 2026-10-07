package cn.zhonjc.quickwriter.mixin;

import com.mojang.math.Transformation;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("createTransformation")
    static Transformation quickwriter$createTransformation(SynchedEntityData data) {
        throw new AssertionError();
    }
}
