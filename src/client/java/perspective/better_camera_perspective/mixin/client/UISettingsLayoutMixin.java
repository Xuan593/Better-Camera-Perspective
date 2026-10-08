package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.settings.ui.UISettingsLayout;
import mchorse.bbs_mod.settings.values.core.ValueString;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.BcpConflictManager;
import perspective.better_camera_perspective.client.ui.BcpConflictApplyRow;
import perspective.better_camera_perspective.client.ui.BcpConflictChoiceRow;

/**
 * v1.3.1：向 BBS 设置行注册表注入「冲突功能」自定义行。
 *
 * UISettingsLayout.build() 是私有的静态懒构建，mixin 在其尾部追加注册：
 *  - 每个冲突功能一行（功能名 + 来源 Mod 按钮）；
 *  - 页末「应用」按钮行（占位值 apply）。
 */
@Environment(EnvType.CLIENT)
@Mixin(UISettingsLayout.class)
public abstract class UISettingsLayoutMixin {
    @Shadow
    private static void register(UISettingsLayout.IValueRow row) {
        throw new AssertionError();
    }

    @Inject(method = "build", at = @At("TAIL"))
    private static void bcp$registerConflictRows(CallbackInfo ci) {
        try {
            for (String featureId : BcpConflictManager.getFeatureIds()) {
                ValueString value = BcpConflictManager.getChoiceValue(featureId);
                if (value != null) {
                    register(new BcpConflictChoiceRow(featureId, value));
                }
            }
            register(new BcpConflictApplyRow(new ValueString("apply", "")));
        } catch (Exception e) {
            // 注册失败仅导致设置页不显示该行，不阻断游戏
        }
    }
}
