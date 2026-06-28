package com.dailyknowledge.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * TTS 管理器 — 封装 Android TextToSpeech
 * 支持朗读/停止切换，优先使用中文语音
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    private var onStatusChanged: ((Boolean) -> Unit)? = null
    private var onTtsReady: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        isInitialized = (status == TextToSpeech.SUCCESS)
        if (isInitialized && tts != null) {
            // 设置中文语音
            val chineseLocale = Locale.CHINESE
            val result = tts!!.setLanguage(chineseLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // 回退到默认语言
                tts!!.setLanguage(Locale.getDefault())
            }
            tts!!.setSpeechRate(0.9f)
            tts!!.setPitch(1.0f)

            // 监听朗读完成
            tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    onStatusChanged?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    onStatusChanged?.invoke(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    onStatusChanged?.invoke(false)
                }
            })
        }
        onTtsReady?.invoke(isInitialized)
    }

    /** 朗读文本；若正在朗读则停止 */
    fun speakOrStop(text: String) {
        if (!isInitialized || tts == null) return

        if (isSpeaking) {
            tts!!.stop()
            isSpeaking = false
            return
        }

        // Android Lollipop+ 使用 speak(CharSequence, ...)
        val utteranceId = "knowledge_${System.currentTimeMillis()}"
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /** 停止朗读 */
    fun stop() {
        if (isInitialized && tts != null && isSpeaking) {
            tts!!.stop()
            isSpeaking = false
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
        // 如果已经初始化完成，立即回调
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
