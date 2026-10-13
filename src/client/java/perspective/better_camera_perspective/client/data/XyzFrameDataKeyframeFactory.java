package perspective.better_camera_perspective.client.data;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * {@link XyzFrameData} 的 BBS 关键帧数据工厂（薄包装）。
 *
 * 插值：三个 double 字段各自走 IInterp 基础插值（与 DoubleKeyframeFactory
 * 的 value 版路径一致）；模式字段为离散值，段内按 t 取 before/after 端。
 *
 * 通道直接持有本工厂单例，无需注册进 KeyframeFactories 全局表。
 */
public class XyzFrameDataKeyframeFactory implements IKeyframeFactory<XyzFrameData> {
    public static final XyzFrameDataKeyframeFactory INSTANCE = new XyzFrameDataKeyframeFactory();

    private XyzFrameDataKeyframeFactory() {
    }

    @Override
    public BaseType toData(XyzFrameData value) {
        return value.toData();
    }

    @Override
    public XyzFrameData fromData(BaseType type) {
        XyzFrameData data = new XyzFrameData();
        if (type.isMap()) {
            data.fromData(type.asMap());
        }
        return data;
    }

    @Override
    public XyzFrameData createEmpty() {
        return new XyzFrameData();
    }

    @Override
    public XyzFrameData copy(XyzFrameData value) {
        return value.copy();
    }

    @Override
    public XyzFrameData interpolate(
        Keyframe<XyzFrameData> prev, Keyframe<XyzFrameData> before, Keyframe<XyzFrameData> after,
        Keyframe<XyzFrameData> next, IInterp interp, float t
    ) {
        return this.interpolate(prev.getValue(), before.getValue(), after.getValue(), next.getValue(), interp, t);
    }

    @Override
    public XyzFrameData interpolate(
        XyzFrameData prev, XyzFrameData before, XyzFrameData after, XyzFrameData next, IInterp interp, float t
    ) {
        // 三个 double 字段分别走 IInterp 基础插值
        double roam = interp.interpolate(
            IInterp.context.set(prev.roamValue, before.roamValue, after.roamValue, next.roamValue, t)
        );
        double trackRel = interp.interpolate(
            IInterp.context.set(prev.trackRelative, before.trackRelative, after.trackRelative, next.trackRelative, t)
        );
        double trackAbs = interp.interpolate(
            IInterp.context.set(prev.trackAbsolute, before.trackAbsolute, after.trackAbsolute, next.trackAbsolute, t)
        );
        // 离散模式：段内按 t 取端值
        int roamMode = t < 0.5F ? before.roamMode : after.roamMode;
        int trackMode = t < 0.5F ? before.trackMode : after.trackMode;
        return new XyzFrameData(roam, trackRel, trackAbs, roamMode, trackMode);
    }
}
