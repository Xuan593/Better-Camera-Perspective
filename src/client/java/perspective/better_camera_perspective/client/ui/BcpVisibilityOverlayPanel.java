package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.BcpVisibility;

/**
 * v1.2.1-Alpha：「可视性设置」面板（overlay）。
 *
 * 从摄像机编辑器右键添加剪辑菜单中的「可视性设置」项打开。
 * 每个剪辑一行（名称 + 开关），调整即时生效并写盘；
 * 底部「返回」关闭面板。进入编辑器时会恢复全开，
 * 再次打开本面板会重新应用用户保存的筛选。
 */
@Environment(EnvType.CLIENT)
public class BcpVisibilityOverlayPanel extends UIOverlayPanel {
    public BcpVisibilityOverlayPanel() {
        super(L10n.lang("bbs.ui.camera.visibility_settings"));

        // 剪辑列表（可滚动）：行元素直接挂在 scrollView 上，由列布局撑高
        UIScrollView scroll = UI.scrollView(4, 6);
        scroll.relative(this.content).xy(0, 0).w(1.0F).h(1.0F, -34);

        MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
        if (factory != null) {
            for (Link key : factory.getKeys()) {
                IKey label = UIKeys.C_CLIP.get(key);
                UIToggle toggle = new UIToggle(
                    label,
                    BcpVisibility.getVisible(key),
                    t -> BcpVisibility.setVisible(key, t.getValue())
                );
                toggle.relative(scroll).w(1.0F).h(20);
                scroll.add(toggle);
            }
        }

        this.content.add(scroll);

        // 返回按钮
        UIButton back = new UIButton(L10n.lang("bbs.ui.camera.panels.back"), b -> this.close());
        back.relative(this.content).x(1.0F, -90).y(1.0F, -26).w(80).h(20);
        this.content.add(back);
    }
}
