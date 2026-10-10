package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
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
 *    「往期日志.md」按行渲染为只读 UILabel（版本标题黄色高亮、引用说明灰色缩进、列表保留）。
 */
@Environment(EnvType.CLIENT)
public class BcpPastLogsModule {
    public static final String CATEGORY = "past_logs";
    public static final String VALUE_CONTENT = "content";

    private static final String MD_RESOURCE = "/assets/better_camera_perspective/md/past_logs.md";

    /** 版本标题（# / ## / ###）高亮色 */
    private static final int COLOR_TITLE = 0xFFFFC107;
    /** 正文常规色 */
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    /** 引用 / 说明行（>）灰 */
    private static final int COLOR_QUOTE = 0xFFAAAAAA;

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

    /** 将 .md 按行渲染为只读 UILabel 列表 */
    private static List<UIElement> renderMarkdown(String md, UIElement row) {
        List<UIElement> els = new ArrayList<>();
        if (md == null || md.isEmpty()) {
            return els;
        }
        for (String raw : md.split("\n", -1)) {
            String line = raw.replace("\r", "");
            if (line.isBlank()) {
                els.add(lineBreak(row));
                continue;
            }
            int color = COLOR_TEXT;
            String text = line;
            if (line.startsWith("### ")) {
                text = line.substring(4);
                color = COLOR_TITLE;
            } else if (line.startsWith("## ")) {
                text = line.substring(3);
                color = COLOR_TITLE;
            } else if (line.startsWith("# ")) {
                text = line.substring(2);
                color = COLOR_TITLE;
            } else if (line.startsWith("> ")) {
                text = "▍" + line.substring(2);
                color = COLOR_QUOTE;
            } else if (line.matches("\\s*[-*]\\s+.*")) {
                text = "· " + line.replaceFirst("\\s*[-*]\\s+", "");
            }
            UILabel label = new UILabel(IKey.raw(text), color);
            label.relative(row).w(1.0F).h(12);
            els.add(label);
        }
        return els;
    }

    private static UIElement lineBreak(UIElement row) {
        UIElement spacer = new UIElement();
        spacer.relative(row).w(1.0F).h(4);
        return spacer;
    }
}
