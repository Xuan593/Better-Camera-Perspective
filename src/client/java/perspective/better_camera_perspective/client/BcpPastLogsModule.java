package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * V1.5.0：设置页「摄像机编辑设置」新增子设置页「往期日志」。
 *
 *  - {@link #registerValues(SettingsBuilder)}：注册 category「past_logs」+ {@link BcpMarkdownValue}
 *    （在 BcpConflictSettingsModule 的 builder 回调中最先调用，配合 UISettingsOverlayPanelMixin 锁定 1 号位）；
 *  - {@link #register()}：向 UIValueMap 注册 BcpMarkdownValue 的渲染 —— 将打包进 mod 的
 *    「往期日志.md」全文渲染为可滚动 UITextarea（自动换行 + 垂直滚动查看全部内容）。
 */
@Environment(EnvType.CLIENT)
public class BcpPastLogsModule {
    public static final String CATEGORY = "past_logs";
    public static final String VALUE_CONTENT = "content";

    private static final String MD_RESOURCE = "/assets/better_camera_perspective/md/past_logs.md";

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static boolean valuesRegistered = false;
    private static boolean uiRegistered = false;

    /** 注册子设置页 category + 占位值（在 conflict_features 模块 builder 回调中最先调用） */
    public static void registerValues(SettingsBuilder builder) {
        if (valuesRegistered) return;
        try {
            builder.category(CATEGORY);
            builder.register(new BcpMarkdownValue(VALUE_CONTENT));
            valuesRegistered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 子设置页「往期日志」注册失败", e);
        }
    }

    /** 注册 BcpMarkdownValue 的只读文本渲染 */
    public static void register() {
        if (uiRegistered) return;
        try {
            UIValueMap.register(BcpMarkdownValue.class, (value, row) -> renderMarkdown(loadMarkdown(), row));
            uiRegistered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 子设置页「往期日志」UI 注册失败", e);
        }
    }

    /** 从 mod 资源读取往期日志 .md */
    private static String loadMarkdown() {
        try (InputStream in = BcpPastLogsModule.class.getResourceAsStream(MD_RESOURCE)) {
            if (in == null) {
                return "（未找到往期日志资源 past_logs.md）";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("[BCP] 读取往期日志资源失败", e);
            return "（往期日志读取失败）";
        }
    }

    /** 将 .md 全文渲染为可滚动 UITextarea（长内容自适应：自动换行 + 垂直滚动查看全部） */
    private static List<UIElement> renderMarkdown(String md, UIElement row) {
        List<UIElement> els = new ArrayList<>();
        if (md == null || md.isEmpty()) {
            return els;
        }
        UITextarea<TextLine> area = new UITextarea<>(s -> {
            // 只读查看：忽略编辑回调
        });
        area.setText(md);
        area.lineHeight(16).wrap(true).background(true).padding(8);
        area.setColor(0xFFF2F2F2, true);
        area.relative(row).w(1.0F).h(480);
        // 打开时默认从最新版本（顶部）开始查看
        area.vertical.scrollTo(0);
        area.horizontal.scrollTo(0);
        els.add(area);
        return els;
    }
}
