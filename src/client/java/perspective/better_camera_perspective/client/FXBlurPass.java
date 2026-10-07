package perspective.better_camera_perspective.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import perspective.better_camera_perspective.clips.FXData;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 特效移植（FX Port）——模糊转场 GL 后期（v1.2-Alpha 实验性更新）。
 *
 * 在画面叠加渲染时对「当前主 framebuffer」执行原地模糊：
 *   framebuffer 纹理 →（模糊 shader）→ 临时 FBO →（拷贝）→ framebuffer。
 * 因此预览画面与导出/截图画面均呈现模糊效果，不依赖 BBS 后处理管线。
 *
 * 模糊参数（来自 FXClip 的关键帧轴，每帧插值）：
 *  - blur：强度 0..1（0 时跳过，避免无谓开销）；
 *  - blurX / blurY：点扩散中心偏移（像素，0 = 画面正中心；百分比类型按分辨率换算）；
 *  - spreadMode：0=基础（全面指向模糊） 1=扩散（中心清晰边缘模糊） 2=收缩（中心模糊边缘清晰）；
 *  - spreadType：0=圆形 1=百分比 2=表达式；
 *  - expr：扩散表达式（GLSL 片段，变量 x / y / r，返回 0..1 权重；实验品中的实验品）。
 */
@Environment(EnvType.CLIENT)
public class FXBlurPass {
    private static final String VERTEX_SHADER = """
        #version 150
        out vec2 vUv;
        void main() {
            vec2 pos = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            vUv = pos;
            gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT_HEADER = """
        #version 150
        uniform sampler2D uTex;
        uniform vec2 uResolution;
        uniform float uBlur;
        uniform vec2 uCenter;
        uniform int uMode;
        uniform int uType;
        in vec2 vUv;
        out vec4 fragColor;
        """;

    private static final String FRAGMENT_BODY = """
        void main() {
            vec2 uv = vUv;
            vec2 dir = uv - uCenter;
            float dist = length(dir) * 2.0;
            float radial;
            if (uType == 2) {
                radial = clamp(bcpSpread(dir), 0.0, 1.0);
            } else if (uMode == 1) {
                radial = clamp(dist, 0.0, 1.0);
            } else if (uMode == 2) {
                radial = 1.0 - clamp(dist, 0.0, 1.0);
            } else {
                radial = 1.0;
            }
            float sigma = uBlur * (0.5 + radial * 5.5);
            if (sigma < 0.001) {
                fragColor = texture(uTex, uv);
                return;
            }
            vec2 off = vec2(1.0 / uResolution.x, 1.0 / uResolution.y) * sigma;
            vec3 c = texture(uTex, uv).rgb * 0.36;
            c += texture(uTex, uv + vec2(off.x, 0.0)).rgb * 0.16;
            c += texture(uTex, uv - vec2(off.x, 0.0)).rgb * 0.16;
            c += texture(uTex, uv + vec2(0.0, off.y)).rgb * 0.16;
            c += texture(uTex, uv - vec2(0.0, off.y)).rgb * 0.16;
            fragColor = vec4(c, 1.0);
        }
        """;

    private static final Map<String, Integer> PROGRAMS = new HashMap<>();
    private static int blankVao = -1;
    private static int tempFbo = -1;
    private static int tempTex = -1;
    private static int tempW = -1;
    private static int tempH = -1;

    private static int blurLoc = -1;
    private static int centerLoc = -1;
    private static int modeLoc = -1;
    private static int typeLoc = -1;
    private static int resLoc = -1;

    private FXBlurPass() {}

    /**
     * 对当前主 framebuffer 执行模糊。无活动模糊剪辑或强度为 0 时由调用方跳过。
     */
    public static void apply(FXData fx) {
        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        int fboId = fb.fbo;
        int w = fb.textureWidth;
        int h = fb.textureHeight;
        if (fboId <= 0 || w <= 0 || h <= 0) {
            return;
        }

        RenderSystem.assertOnRenderThread();

        int[] texOut = new int[1];
        GL30.glGetFramebufferAttachmentParameteriv(fboId, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, texOut);
        int srcTex = texOut[0];
        if (srcTex == 0) {
            return;
        }

        ensureResources(w, h);

        // —— 状态保存 ——
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevActive = GL11.glGetInteger(GL30.GL_ACTIVE_TEXTURE);

        GL30.glBindVertexArray(blankVao);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        // 中心点（归一化 0..1，默认 0.5 = 画面正中心）
        double bx = fx.blurX;
        double by = fx.blurY;
        if (fx.spreadType == 1) {
            // 百分比：±100 对应水平/垂直全幅
            bx = bx / 100.0 * w;
            by = by / 100.0 * h;
        }
        float cx = (float) clamp01(0.5 + bx / w);
        float cy = (float) clamp01(0.5 + by / h);

        // 表达式类型且表达式非空 → 按表达式编译专属模糊程序（实验品中的实验品）
        String exprKey = fx.spreadType == 2 && fx.expr != null && !fx.expr.isEmpty() ? fx.expr : "";

        try {
            // —— pass 1：framebuffer → 临时 FBO（模糊）——
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
            GL11.glViewport(0, 0, w, h);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
            useProgram(getProgram(exprKey), true);
            setUniforms(fx, w, h, cx, cy, true);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // —— pass 2：临时 FBO → 原 framebuffer（拷贝，blur=0）——
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
            GL11.glViewport(0, 0, w, h);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tempTex);
            useProgram(PROGRAMS.get(""), true);
            setUniforms(fx, w, h, cx, cy, false);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            // —— 状态恢复 ——
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            GL30.glActiveTexture(prevActive);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);
        }
    }

    private static void setUniforms(FXData fx, int w, int h, float cx, float cy, boolean blurEnabled) {
        if (resLoc >= 0) {
            GL20.glUniform2f(resLoc, w, h);
        }
        if (blurLoc >= 0) {
            GL20.glUniform1f(blurLoc, blurEnabled ? (float) Math.min(1.0D, Math.max(0.0D, fx.blur)) : 0.0F);
        }
        if (centerLoc >= 0) {
            GL20.glUniform2f(centerLoc, cx, cy);
        }
        if (modeLoc >= 0) {
            GL20.glUniform1i(modeLoc, fx.spreadMode);
        }
        if (typeLoc >= 0) {
            GL20.glUniform1i(typeLoc, fx.spreadType);
        }
    }

    private static void useProgram(int program, boolean active) {
        GL20.glUseProgram(active ? program : 0);
        if (active) {
            resLoc = GL20.glGetUniformLocation(program, "uResolution");
            blurLoc = GL20.glGetUniformLocation(program, "uBlur");
            centerLoc = GL20.glGetUniformLocation(program, "uCenter");
            modeLoc = GL20.glGetUniformLocation(program, "uMode");
            typeLoc = GL20.glGetUniformLocation(program, "uType");
        }
    }

    private static void ensureResources(int w, int h) {
        if (blankVao < 0) {
            blankVao = GL30.glGenVertexArrays();
        }
        if (tempW != w || tempH != h) {
            if (tempTex >= 0) {
                GL11.glDeleteTextures(tempTex);
            }
            if (tempFbo >= 0) {
                GL30.glDeleteFramebuffers(tempFbo);
            }
            tempW = w;
            tempH = h;
            tempTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tempTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            tempFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tempTex, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
        getProgram("");
    }

    /** 按表达式取（或编译缓存）模糊程序；空表达式 = 通用均匀权重。 */
    private static int getProgram(String exprKey) {
        return PROGRAMS.computeIfAbsent(exprKey, FXBlurPass::compileProgramFor);
    }

    private static int compileProgramFor(String exprKey) {
        String spreadFn;
        if (exprKey == null || exprKey.isEmpty()) {
            spreadFn = "float bcpSpread(vec2 d) { return 1.0; }\n";
        } else {
            // 表达式片段：变量 x = 水平方向分量、y = 垂直方向分量、r = 距中心欧氏距离
            spreadFn = "float bcpSpread(vec2 d) { float x = d.x; float y = d.y; float r = length(d); return " + exprKey + "; }\n";
        }
        return compileProgram(spreadFn);
    }

    private static int compileProgram(String spreadFn) {
        int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vs, VERTEX_SHADER);
        GL20.glCompileShader(vs);
        checkShader(vs, "vertex");

        int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fs, FRAGMENT_HEADER + spreadFn + FRAGMENT_BODY);
        GL20.glCompileShader(fs);
        checkShader(fs, "fragment");

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("[BCP] 模糊 shader 链接失败: " + GL20.glGetProgramInfoLog(program));
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return program;
    }

    private static void checkShader(int shader, String stage) {
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("[BCP] 模糊 shader " + stage + " 编译失败: " + GL20.glGetShaderInfoLog(shader));
        }
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }
}
