package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.values.core.ValueString;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * V1.5.0：往期日志子设置页的展示用值。
 *
 * 该值仅作为占位使「往期日志」子设置页获得一个设置项（从而可被 BBS 设置页渲染）；
 * 实际展示的 .md 内容由 {@link BcpPastLogsModule} 从 mod 资源读取，值本身不持久化任何内容。
 */
@Environment(EnvType.CLIENT)
public class BcpMarkdownValue extends ValueString {
    public BcpMarkdownValue(String id) {
        super(id, "");
    }
}
