package perspective.better_camera_perspective.data;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * {@link XyzFrameData} 的 BBS 关键帧数据工厂。
 *
 * 插值：三个 double 字段各自走 IInterp 基础插值（与 DoubleKeyframeFactory
 * 的 value 版路径一致）；模式字段为离散值，段内按 t 取 before/after 端。
 *
 * <p><b>必须在启动时注册进 BBS 全局表。</b>BBS 的 {@code KeyframeChannel} 在
 * 序列化时用 {@code CollectionUtils.getKey(KeyframeFactories.FACTORIES, factory)}
 * 反查本工厂的类型名写盘，读盘时又用该类型名 {@code KeyframeFactories.FACTORIES.get(type)}
 * 取回工厂——若未注册，存档写出的 type 为 null，读档时 keyframes 会被
 * {@code "has unknown value type"} 整段丢弃（即「重进客户端 XYZ 关键帧消失」）。
 * 注册入口：{@code RegisterKeyframeFactoriesEvent}（BBSMod 初始化时 post）。</p>
 */
public class XyzFrameDataKeyframeFactory implements IKeyframeFactory<XyzFrameData> {
    public static final XyzFrameDataKeyframeFactory INSTANCE = new XyzFrameDataKeyframeFactory();

    /** 全局序列化类型名（写入影片存档；务必保持稳定，改名会导致旧存档读不回）。 */
    public static final String TYPE = "bcp_xyz_frame";

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

    /**
     * P0 防污染垫片（V1.7.22-Alpha）：BBS 的标量曲线图（含 posecurve）只认
     * {@code IKeyframeFactory#getY} / {@code yToValue} 这一对标量钩子。
     * 默认实现里 {@code getY} 恒 0、{@code yToValue} 返回 {@link Double}——
     * posecurve 的 {@code bbsplus$setValue} 会把 {@code Double} 直接塞回
     * {@code Keyframe<XyzFrameData>}，后续读取强转 CCE、存档写坏。
     *
     * <p>这里覆写为「恒返回完整帧数据」，任何走标量路径的写入最多覆盖数值，
     * 不会改变值类型；三轴曲线视图本身不经过该路径（自绘面板直接改字段）。</p>
     *
     * @return 主值是游动·绝对值（仅用于外部标量图显示）
     */
    @Override
    public double getY(XyzFrameData value) {
        return value == null ? 0.0D : value.roamValue;
    }

    /** 标量写入兜底：返回三通道同值的完整帧数据，绝不返回 {@link Double}。 */
    @Override
    public Object yToValue(double y) {
        return new XyzFrameData(y, y, y, 1, 0);
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
