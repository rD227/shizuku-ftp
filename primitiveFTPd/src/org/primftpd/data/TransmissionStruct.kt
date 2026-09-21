package org.primftpd.data

/**
 * 把传输相关的开关放在一起，方便只用一个参数在 OutputStream 之间传递。
 *
 * @param flushRightAway 每次 write 后是否立刻 flush
 * @param autoZipTransmission 是否尝试对文本文件做自动压缩传输
 */
data class TransmissionStruct(
    val flushRightAway: Boolean,
    val autoZipTransmission: Boolean,
)
