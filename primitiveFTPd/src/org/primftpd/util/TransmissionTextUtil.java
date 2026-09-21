package org.primftpd.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 一些判断“这个文件是不是普通文本”的辅助方法。
 *
 * 自动压缩前先做判断会比较安全：
 *  - 先看扩展名；
 *  - 再用内容特征兜底；
 *  - 最终建议还是以扩展名为准，内容探测只作为补充。
 */
public final class TransmissionTextUtil {

    private static final int SAMPLE_SIZE = 8 * 1024;

    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "txt", "text", "log", "md", "markdown",
            "json", "xml", "yml", "yaml", "toml", "ini", "conf", "cfg", "properties",
            "csv", "tsv",
            "html", "htm", "css", "js", "mjs", "cjs", "ts", "tsx", "jsx",
            "java", "kt", "kts", "c", "h", "cpp", "hpp", "cs", "go", "rs", "py", "rb", "php",
            "sh", "bash", "zsh", "bat", "cmd", "ps1",
            "sql", "gradle", "pro", "gitignore", "editorconfig"
    ));

    private TransmissionTextUtil() {
    }

    public static boolean hasTextExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            // 没有扩展名时，也允许一些常见无扩展名文本文件
            return lower.equals("dockerfile")
                    || lower.equals("makefile")
                    || lower.equals("readme")
                    || lower.equals("license");
        }
        return TEXT_EXTENSIONS.contains(lower.substring(dot + 1));
    }

    /**
     * 只用文件前 SAMPLE_SIZE 字节判断，避免一次性读完整文件。
     */
    public static boolean isProbablyTextFile(File file) throws IOException {
        if (file == null || !file.isFile()) {
            return false;
        }
        if (hasTextExtension(file.getName())) {
            return true;
        }

        byte[] buffer = new byte[SAMPLE_SIZE];
        int length;
        try (InputStream in = new FileInputStream(file)) {
            length = in.read(buffer);
        }
        if (length <= 0) {
            return true;
        }
        return isProbablyText(buffer, length);
    }

    /**
     * 内容启发式判断：
     *  - 有 NUL 字节 -> 基本可以直接判为二进制；
     *  - 控制字符比例太高 -> 二进制；
     *  - 能严格按 UTF-8 解码 -> 更可能是文本；
     *  - 否则再看是否只有常见的 \r\n\t 等控制字符。
     */
    public static boolean isProbablyText(byte[] data, int length) {
        if (data == null || length <= 0) {
            return true;
        }

        if (hasBinaryBom(data, length)) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (data[i] == 0) {
                return false;
            }
        }

        if (isValidUtf8(data, length)) {
            return true;
        }

        int controlCount = 0;
        for (int i = 0; i < length; i++) {
            int b = data[i] & 0xFF;
            if (b < 0x09 || (b > 0x0D && b < 0x20) || b == 0x7F) {
                controlCount++;
            }
        }
        return controlCount * 100 / length < 10;
    }

    private static boolean hasBinaryBom(byte[] data, int length) {
        if (length >= 2) {
            int b0 = data[0] & 0xFF;
            int b1 = data[1] & 0xFF;
            // UTF-16 BOM 也是文本，不算二进制；这里只排除明显二进制 BOM。
            if (b0 == 0xFF && b1 == 0xFE) {
                return false;
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return false;
            }
        }
        // 如果出现大量不常见的 0xFF/0xFE，交给下面的控制字符/NUL 判断。
        return false;
    }

    private static boolean isValidUtf8(byte[] data, int length) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, 0, length));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
