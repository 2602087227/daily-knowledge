package com.dailyknowledge.util

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * TTS 管理器 — 封装 Android TextToSpeech
 * 支持朗读/停止切换，优先使用中文语音
 * TTS 实例在 Application 中创建一次并复用，语言设置只在初始化时执行一次
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    @Volatile private var isInitialized = false
    @Volatile private var isSpeaking = false
    private var onStatusChanged: ((Boolean) -> Unit)? = null
    private var onTtsReady: ((Boolean) -> Unit)? = null
    private val appContext = context.applicationContext

    /** 预热阶段标记 — 预热期间的 utterance 回调不通知外部，避免状态错乱 */
    @Volatile private var isWarmingUp = false

    /** 原子计数器替代 System.currentTimeMillis()，减少 JNI 调用 */
    private val utteranceCounter = AtomicInteger(0)

    companion object {
        private const val TAG = "TtsManager"
    }

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        isInitialized = (status == TextToSpeech.SUCCESS)
        if (isInitialized && tts != null) {
            // 语言设置：仅在初始化时执行一次
            val result = tts!!.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val result2 = tts!!.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result2 == TextToSpeech.LANG_MISSING_DATA || result2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts!!.setLanguage(Locale.getDefault())
                }
            }
            tts!!.setSpeechRate(0.9f)
            tts!!.setPitch(1.0f)

            // 监听朗读状态
            tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (isWarmingUp) return
                    isSpeaking = true
                    onStatusChanged?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    if (isWarmingUp) return
                    isSpeaking = false
                    onStatusChanged?.invoke(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (isWarmingUp) return
                    isSpeaking = false
                    onStatusChanged?.invoke(false)
                }
            })

            // 预热 TTS 引擎：先合成到文件预热合成管道，再 speak 预热音频管道
            warmUpEngine()
        }
        onTtsReady?.invoke(isInitialized)
    }

    /**
     * 预热 TTS 引擎 — 两步预热确保首次朗读即快速响应
     * 1. synthesizeToFile：预热语音合成管道（不出声）
     * 2. speak：预热音频播放管道（会播放但立即被后续操作覆盖）
     */
    private fun warmUpEngine() {
        try {
            isWarmingUp = true

            // 第一步：用有意义文本预热合成管道
            val tempFile = File(appContext.cacheDir, "tts_warmup.wav")
            tempFile.delete()
            val warmupText = "你好"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.synthesizeToFile(warmupText, null, tempFile, "warmup_synth")
            } else {
                @Suppress("DEPRECATION")
                tts?.synthesizeToFile(warmupText, null, tempFile.absolutePath)
            }

            // 第二步：发送静默语音预热音频播放管道
            // 用 QUEUE_ADD + speak("") 激活音频轨道，后续真实朗读会 flush 掉
            try {
                tts?.speak("", TextToSpeech.QUEUE_ADD, null, "warmup_speak")
            } catch (_: Exception) {}

            // 清理临时文件
            tempFile.delete()
            isWarmingUp = false
        } catch (e: Exception) {
            isWarmingUp = false
            Log.w(TAG, "TTS 预热失败: ${e.message}")
        }
    }

    /**
     * 朗读文本；若正在朗读则停止。
     * 点击按钮后直接调用，无协程、无数据库操作、无延迟
     * @return 操作结果：true=已开始朗读或已停止，false=TTS 不可用
     */
    fun speakOrStop(text: String): Boolean {
        if (!isInitialized || tts == null) return false

        if (isSpeaking) {
            // 停止朗读
            tts?.stop()
            isSpeaking = false
            // 手动触发状态回调 — 某些 TTS 引擎 stop() 后不回调 onDone/onError
            onStatusChanged?.invoke(false)
            return true
        }

        // 立即开始朗读：用 QUEUE_FLUSH 清除预热残留，用计数器生成唯一 ID
        val utteranceId = "tts_${utteranceCounter.incrementAndGet()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return true
    }

    /** 停止朗读 */
    fun stop() {
        if (isInitialized && tts != null && isSpeaking) {
            tts!!.stop()
            isSpeaking = false
            onStatusChanged?.invoke(false)
        }
    }

    /** 当前是否正在朗读 */
    fun isSpeaking(): Boolean = isSpeaking

    /** TTS 是否初始化成功 */
    fun isReady(): Boolean = isInitialized

    /** 设置朗读状态变化回调 */
    fun setOnStatusChanged(callback: ((Boolean) -> Unit)?) {
        onStatusChanged = callback
    }

    /** 设置 TTS 就绪回调 */
    fun setOnTtsReady(callback: ((Boolean) -> Unit)?) {
        onTtsReady = callback
        if (isInitialized) {
            callback?.invoke(true)
        }
    }

    /** 释放 TTS 资源 */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        isSpeaking = false
    }
}
