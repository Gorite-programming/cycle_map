package com.gorite.cyclemap.search

object SearchNormalizer {

    /**
     * ひらがな、カタカナ、全角英数、大文字小文字を正規化
     * places.search_text 生成規則と整合 (cautions.md §2.3)
     */
    fun normalize(input: String?): String {
        if (input == null) return ""
        val sb = StringBuilder()
        for (ch in input.trim()) {
            when {
                // 全角英数 -> 半角英数 (小文字化)
                ch in 'Ａ'..'Ｚ' -> sb.append((ch - 'Ａ' + 'a'.code).toChar())
                ch in 'ａ'..'ｚ' -> sb.append(ch)
                ch in 'A'..'Z' -> sb.append(ch.lowercaseChar())
                ch in '０'..'９' -> sb.append((ch - '０' + '0'.code).toChar())
                // カタカナ -> ひらがな
                ch in 'ァ'..'ヶ' -> sb.append((ch - 0x60).toChar())
                // 長音記号、スペース正規化
                ch == 'ー' || ch == '－' || ch == '―' -> sb.append('ー')
                ch.isWhitespace() -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace("\\s+".toRegex(), " ")
    }
}
