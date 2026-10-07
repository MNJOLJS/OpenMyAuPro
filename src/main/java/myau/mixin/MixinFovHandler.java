package myau.mixin;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class MixinFovHandler {
    // Optional Patcher integration: disabled unless the external Patcher class is present.
    // Keeping this class as a harmless no-op prevents Mixin from crashing when the target class does not exist.
}