package perspective.better_camera_perspective;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.events.RegisterKeyframeFactoriesEvent;
import mchorse.bbs_mod.api.events.RegisterSettingsEvent;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perspective.better_camera_perspective.clips.MultiTrackerClip;
import perspective.better_camera_perspective.data.XyzFrameDataKeyframeFactory;
import perspective.better_camera_perspective.data.PhaseFrameDataKeyframeFactory;

/**
 * BBS 附加包服务端入口（fabric.mod.json 的 "bbs-addon" 字段）。
 *
 * 参照 BBS-Cubed 的注册方式：在 BBS 的 RegisterSettingsEvent 事件中把
 * 多样追踪剪辑注册进 BBS 的相机剪辑工厂。
 */
public class BBSAddonEntry implements BBSAddonMod {
    public static final String MULTI_TRACKER_ID = "multi_tracker";
    public static final String MULTI_TRACKER_ICON_COLOR_HEX = "0xFFB74D";

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective");

    /**
     * 关键帧数据工厂注册（V1.6.0 修复「重进客户端 XYZ 关键帧被删除」）。
     *
     * BBS 的 {@code KeyframeChannel} 序列化不认「通道自带的工厂实例」，而是按**全局类型名**
     * 存取（toData 用 getKey(FACTORIES, factory) 反查名字写盘；fromData 用名字取工厂再读 keyframes）。
     * 若 xd/yd/zd 的 XyzFrameDataKeyframeFactory 未注册，存档写出的 type 为 null，
     * 读档时 {@code FACTORIES.get(null)} == null → 该通道 keyframes 被整段丢弃。
     * 本事件即 BBS 提供的官方注册入口（BBSMod 初始化时 post，早于任何影片读写）。
     */
    @Subscribe
    public void onRegisterKeyframeFactories(RegisterKeyframeFactoriesEvent event) {
        event.register(XyzFrameDataKeyframeFactory.TYPE, XyzFrameDataKeyframeFactory.INSTANCE);
        event.register(PhaseFrameDataKeyframeFactory.TYPE, PhaseFrameDataKeyframeFactory.INSTANCE);
        LOGGER.info("[BCP] XyzFrameData / PhaseFrameData 关键帧工厂已注册（type={} / {}）",
            XyzFrameDataKeyframeFactory.TYPE, PhaseFrameDataKeyframeFactory.TYPE);
    }

    @Subscribe
    public void onRegisterSettings(RegisterSettingsEvent event) {
        try {
            MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
            if (factory == null) {
                LOGGER.warn("[BCP] 相机剪辑工厂为空，无法注册「多样追踪」剪辑");
                return;
            }

            factory.register(
                Link.bbs(MULTI_TRACKER_ID),
                MultiTrackerClip.class,
                new ClipFactoryData(Icons.USER, 0xFFB74D)
            );
            LOGGER.info("[BCP] 「多样追踪」剪辑 (MultiTrackerClip) 服务端注册成功");
        } catch (Exception e) {
            LOGGER.error("[BCP] 注册「多样追踪」剪辑失败", e);
        }
    }
}
