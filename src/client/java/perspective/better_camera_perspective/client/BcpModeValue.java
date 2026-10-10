package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.values.core.ValueString;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

/**
 * v1.5-Alpha：三态模式设置值（如 默认/单体/混合、原始/覆盖/混合）。
 *
 * 存字符串值（可序列化），候选模式列表与语言键前缀由构造传入，
 * 由 {@link BcpModeValueUi} 渲染为 BBS 原生循环按钮（UICirculate）。
 */
@Environment(EnvType.CLIENT)
public class BcpModeValue extends ValueString {
    private final List<String> modes;
    private final String labelPrefix;

    public BcpModeValue(String id, String defaultValue, List<String> modes, String labelPrefix) {
        super(id, defaultValue);
        this.modes = modes;
        this.labelPrefix = labelPrefix;
    }

    /** 候选模式（按循环顺序） */
    public List<String> getModes() {
        return this.modes;
    }

    /** 语言键前缀：{prefix}.{mode} 即各模式显示文本 */
    public String getLabelPrefix() {
        return this.labelPrefix;
    }

    /** 当前值在候选中的索引（未命中回 0） */
    public int getModeIndex() {
        int i = this.modes.indexOf(this.get());
        return i < 0 ? 0 : i;
    }
}
