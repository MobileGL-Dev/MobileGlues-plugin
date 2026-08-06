package com.fcl.plugin.mobileglues.settings

import android.content.Context
import androidx.annotation.StringRes
import com.fcl.plugin.mobileglues.R

/**
 * 一个会被原样写进 `MG/config.json` 的取值。
 *
 * [wire] 是磁盘上的整数，必须与 MobileGlues native 端 `config/settings.h` 中对应的枚举一致，
 * 并且永远不能修改——它已经存在于用户既有的配置文件里。
 */
interface WireValue {
    val wire: Int
}

/**
 * 会出现在 Spinner 里的取值。
 *
 * 选项列表直接由枚举生成（见 MainActivity.setupSpinners），因此不存在
 * 「选项数量和取值范围对不上」的可能——新增一档只需要在枚举里加一行。
 * 也正因为如此，枚举的 `ordinal` 就是它在 Spinner 中的位置。
 */
interface SpinnerOption : WireValue {
    fun label(context: Context): CharSequence
}

/** 磁盘上的整数 → 枚举。缺失或越界时回落到 [fallback]，绝不抛异常。 */
internal fun <T : WireValue> List<T>.fromWire(wire: Int?, fallback: T): T =
    firstOrNull { it.wire == wire } ?: fallback

/** `enableANGLE` */
enum class AngleConfig(override val wire: Int, @param:StringRes private val labelRes: Int) :
    SpinnerOption {
    DisableIfPossible(0, R.string.option_angle_disable_if_possible),
    EnableIfPossible(1, R.string.option_angle_enable_if_possible),
    ForceDisable(2, R.string.option_angle_disable),
    ForceEnable(3, R.string.option_angle_enable);

    override fun label(context: Context): CharSequence = context.getString(labelRes)
}

/** `enableNoError` */
enum class NoErrorConfig(override val wire: Int, @param:StringRes private val labelRes: Int) :
    SpinnerOption {
    Auto(0, R.string.option_no_error_auto),
    DoNotIgnore(1, R.string.option_no_error_enable),
    IgnoreShaderProgram(2, R.string.option_no_error_disable_pri),
    IgnoreShaderProgramFramebuffer(3, R.string.option_no_error_disable_sec);

    override fun label(context: Context): CharSequence = context.getString(labelRes)
}

/**
 * MultiDraw 的一种实现方式。
 *
 * 写进 `config.json` 的是 [key] 这个**名字**而不是序号——native 特意这么改的：
 * 序号一旦新增或调整，就会把用户已经写好的取值指到别的实现上。
 */
enum class MultidrawBackend(val key: String, @param:StringRes private val labelRes: Int) {
    Auto("auto", R.string.md_backend_auto),

    // 前三种：每个子绘制各发一次驱动调用。
    Unroll("unroll", R.string.md_backend_unroll),
    BaseVertex("basevertex", R.string.md_backend_basevertex),
    Indirect("indirect", R.string.md_backend_indirect),

    // 后三种：整批只发一次。声明顺序与 native 的 md_backend_t 一致，代价高低一目了然。
    MultiArrays("multiarrays", R.string.md_backend_multiarrays),
    MultiBaseVertex("multibasevertex", R.string.md_backend_multibasevertex),
    MultiIndirect("multiindirect", R.string.md_backend_multiindirect),

    Compute("compute", R.string.md_backend_compute);

    fun label(context: Context): CharSequence = context.getString(labelRes)

    companion object {
        /** 与 native 的 `md_parse_backend` 一致：忽略大小写，以及空格 / 下划线 / 连字符。 */
        fun parse(raw: String?): MultidrawBackend? {
            val normalized = raw
                ?.filterNot { it == ' ' || it == '\t' || it == '_' || it == '-' }
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            return entries.firstOrNull { it.key == normalized }
        }
    }
}

/**
 * 有多种实现可选的 MultiDraw 入口点。
 *
 * [allowed] 抄自 native 的 `k_md_entries`：某个后端对某个入口点来说不是一种「不同的实现」时
 * （例如 glMultiDrawElements 没有 base vertex），native 会拒绝并回退到 auto，
 * 所以界面上也不该把它列出来。
 */
enum class MultidrawEntry(
    val key: String,
    val glFunction: String,
    val allowed: List<MultidrawBackend>,
) {
    Arrays(
        "multidrawModeArrays", "glMultiDrawArrays",
        listOf(
            MultidrawBackend.Auto, MultidrawBackend.MultiArrays,
            MultidrawBackend.MultiIndirect, MultidrawBackend.Unroll,
        ),
    ),
    Elements(
        "multidrawModeElements", "glMultiDrawElements",
        listOf(
            MultidrawBackend.Auto, MultidrawBackend.MultiIndirect, MultidrawBackend.MultiArrays,
            MultidrawBackend.MultiBaseVertex, MultidrawBackend.Indirect, MultidrawBackend.Unroll,
        ),
    ),
    ElementsBaseVertex(
        "multidrawModeElementsBaseVertex", "glMultiDrawElementsBaseVertex",
        listOf(
            MultidrawBackend.Auto, MultidrawBackend.MultiIndirect,
            MultidrawBackend.MultiBaseVertex, MultidrawBackend.Indirect,
            MultidrawBackend.BaseVertex, MultidrawBackend.Unroll,
            // 自动挡的阶梯里没有 compute，只能显式选，所以排在最后。
            MultidrawBackend.Compute,
        ),
    ),
    ArraysIndirect(
        "multidrawModeArraysIndirect", "glMultiDrawArraysIndirect",
        listOf(
            MultidrawBackend.Auto, MultidrawBackend.MultiIndirect, MultidrawBackend.Indirect,
        ),
    ),
    ElementsIndirect(
        "multidrawModeElementsIndirect", "glMultiDrawElementsIndirect",
        listOf(
            MultidrawBackend.Auto, MultidrawBackend.MultiIndirect, MultidrawBackend.Indirect,
        ),
    ),
}

/**
 * MultiDraw 的全部设置。
 *
 * [backends] 只记录「不是 auto」的入口点：auto 等同于不写这个键，两种表示法必须只有一种，
 * 否则两个内容相同的配置会判定为不相等，去抖保存就会被无谓地触发。
 */
data class MultidrawSettings(
    val backends: Map<MultidrawEntry, MultidrawBackend> = emptyMap(),
    val disabledBackends: Set<MultidrawBackend> = emptySet(),
) {
    fun backendOf(entry: MultidrawEntry): MultidrawBackend =
        backends[entry] ?: MultidrawBackend.Auto

    fun with(entry: MultidrawEntry, backend: MultidrawBackend): MultidrawSettings = copy(
        backends = if (backend == MultidrawBackend.Auto) {
            backends - entry
        } else {
            backends + (entry to backend)
        },
    )

    fun withBackendDisabled(backend: MultidrawBackend, disabled: Boolean): MultidrawSettings = copy(
        disabledBackends = if (disabled) {
            disabledBackends + backend
        } else {
            disabledBackends - backend
        },
    )

    /** 偏离默认值的项数，用来在折叠状态下给出一句话摘要。 */
    val customizedCount: Int get() = backends.size + disabledBackends.size

    companion object {
        val Default = MultidrawSettings()
    }
}

/**
 * `angleDepthClearFixMode`。
 *
 * native 的 `AngleDepthClearFixMode` 还有一个 `Mode2 = 2`，但那一档不对外开放，
 * 本 App 不提供、也不接受它。
 */
enum class DepthClearFixMode(override val wire: Int, @param:StringRes private val labelRes: Int) :
    SpinnerOption {
    Disabled(0, R.string.option_angle_clear_workaround_disable),
    Mode1(1, R.string.option_angle_clear_workaround_enable_1);

    override fun label(context: Context): CharSequence = context.getString(labelRes)
}

/**
 * `customGLVersion`。
 *
 * 只列出 native 认可的档位：`config/settings.cpp` 会把 34..39、31 及以下等中间值夹到这些档位上，
 * 所以让用户选一个 native 会二次修改的值没有意义。
 */
enum class GlVersion(override val wire: Int, private val literal: String?) : SpinnerOption {
    Default(0, null),
    Gl46(46, "OpenGL 4.6"),
    Gl45(45, "OpenGL 4.5"),
    Gl44(44, "OpenGL 4.4"),
    Gl43(43, "OpenGL 4.3"),
    Gl42(42, "OpenGL 4.2"),
    Gl41(41, "OpenGL 4.1"),
    Gl40(40, "OpenGL 4.0"),
    Gl33(33, "OpenGL 3.3"),
    Gl32(32, "OpenGL 3.2");

    override fun label(context: Context): CharSequence =
        literal ?: context.getString(R.string.option_custom_gl_version_default)

    companion object {
        /**
         * 这一项不能像别的枚举那样「不认识就回落到默认值」。
         *
         * native 对 customGLVersion 是**夹取**而不是重置（settings.cpp）：47 当 4.6 用，38 当 3.3 用，
         * 31 当 3.2 用。若把这些值统统读成 0，本 App 下一次保存就会把它们写成 0，
         * 而 0 在 native 那边等于 DEFAULT_GL_VERSION(40)——用户没动过任何开关，目标版本却被改了。
         * 所以这里照抄 native 的夹取规则，保证两边对同一个文件的理解永远一致。
         */
        fun fromWire(wire: Int?): GlVersion {
            if (wire == null) return Default
            entries.firstOrNull { it.wire == wire }?.let { return it }
            return when {
                wire > 46 -> Gl46
                wire in 34..39 -> Gl33
                wire in 1..31 -> Gl32
                else -> Default
            }
        }
    }
}

/**
 * `fsr1Setting`。
 *
 * native 端是 5 档画质预设，界面上目前只提供一个开关；把完整取值建模出来是为了让
 * 「配置里已经是 Balanced」这种情况能被正确识别并原样保留，而不是被开关改写成 1。
 */
enum class Fsr1Preset(override val wire: Int) : WireValue {
    Disabled(0),
    UltraQuality(1),
    Quality(2),
    Balanced(3),
    Performance(4),
}

/** `maxGlslCacheSize`。把「关闭」这个用负数表达的状态显式建模出来。 */
sealed interface GlslCacheSize {

    val wire: Int

    /**
     * 不使用 GLSL 缓存。
     *
     * native 的判据是 `maxGlslCacheSize > 0`，任何小于等于 0 的值都等于关闭；
     * 写回磁盘时统一用 -1，与历史配置保持一致。
     */
    data object Disabled : GlslCacheSize {
        override val wire: Int get() = -1
    }

    /** 以 MiB 为单位的上限，必须为正数。 */
    data class Limited(val mebibytes: Int) : GlslCacheSize {
        init {
            require(mebibytes > 0) { "GLSL cache size must be positive, was $mebibytes" }
        }

        override val wire: Int get() = mebibytes
    }

    /** 滑块上的位置：关闭对应 0。 */
    val mebibytesOrZero: Int get() = (this as? Limited)?.mebibytes ?: 0

    companion object {
        val Default: GlslCacheSize = Limited(32)

        /** 滑块回来的 MiB 数 → 配置值，0 表示关闭。 */
        fun ofMebibytes(mebibytes: Int): GlslCacheSize =
            if (mebibytes > 0) Limited(mebibytes) else Disabled

        fun fromWire(wire: Int?): GlslCacheSize = when {
            wire == null -> Default
            wire > 0 -> Limited(wire)
            // native 把 0 也当成关闭；旧版界面却把它显示成 32MiB，两边对同一个文件的理解是不一致的。
            else -> Disabled
        }
    }
}

/**
 * MobileGlues 的配置，一个不可变的值。
 *
 * 它不知道文件、不知道 UI、也没有任何副作用：改配置就是 [copy]，落盘由 [MGConfigStore] 负责。
 * 这里的默认值是全 App 唯一的一份——[MGConfigCodec] 解析时的回落值也取自这里。
 */
data class MGConfig(
    val angle: AngleConfig = AngleConfig.EnableIfPossible,
    val noError: NoErrorConfig = NoErrorConfig.Auto,
    val multidraw: MultidrawSettings = MultidrawSettings.Default,
    val depthClearFix: DepthClearFixMode = DepthClearFixMode.Disabled,
    val glVersion: GlVersion = GlVersion.Default,
    val glslCache: GlslCacheSize = GlslCacheSize.Default,
    val extComputeShader: Boolean = false,
    /** 磁盘上 1 表示「启用 timer_query 扩展」；界面上的开关文案是「禁用」，取反只发生在渲染那一处。 */
    val extTimerQuery: Boolean = true,
    val extDirectStateAccess: Boolean = false,
    val fsr1: Fsr1Preset = Fsr1Preset.Disabled,
) {
    val fsr1Enabled: Boolean get() = fsr1 != Fsr1Preset.Disabled

    companion object {
        val Default = MGConfig()
    }
}
