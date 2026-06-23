package ai.desertant.emo

/** Preferred emoji skin tone variant for skin-tone-capable emoji. */
enum class EmojiSkinTone(internal val modifier: Int?) {
    /** Default emoji presentation with no skin tone modifier. */
    DEFAULT(null),
    LIGHT(0x1F3FB),
    MEDIUM_LIGHT(0x1F3FC),
    MEDIUM(0x1F3FD),
    MEDIUM_DARK(0x1F3FE),
    DARK(0x1F3FF),
}

internal fun String.applyingSkinTone(skinTone: EmojiSkinTone): String {
    val modifier = skinTone.modifier ?: return this
    val codePoints = this.codePoints().toArray()
    val out = StringBuilder()
    var i = 0
    while (i < codePoints.size) {
        val cp = codePoints[i]
        if (isSkinToneModifier(cp)) {
            i++
            continue
        }
        out.appendCodePoint(cp)
        if (isEmojiModifierBase(cp)) {
            out.appendCodePoint(modifier)
            while (i + 1 < codePoints.size && (isSkinToneModifier(codePoints[i + 1]) || codePoints[i + 1] == 0xFE0F)) i++
        }
        i++
    }
    return out.toString()
}

private fun isSkinToneModifier(cp: Int): Boolean = cp in 0x1F3FB..0x1F3FF

private fun isEmojiModifierBase(cp: Int): Boolean = cp == 0x261D || cp == 0x26F9 ||
    cp in 0x270A..0x270D || cp == 0x1F385 || cp in 0x1F3C2..0x1F3C4 || cp == 0x1F3C7 ||
    cp in 0x1F3CA..0x1F3CC || cp in 0x1F442..0x1F443 || cp in 0x1F446..0x1F450 ||
    cp in 0x1F466..0x1F478 || cp == 0x1F47C || cp in 0x1F481..0x1F483 ||
    cp in 0x1F485..0x1F487 || cp == 0x1F48F || cp == 0x1F491 || cp == 0x1F4AA ||
    cp in 0x1F574..0x1F575 || cp == 0x1F57A || cp == 0x1F590 || cp in 0x1F595..0x1F596 ||
    cp in 0x1F645..0x1F647 || cp in 0x1F64B..0x1F64F || cp == 0x1F6A3 ||
    cp in 0x1F6B4..0x1F6B6 || cp == 0x1F6C0 || cp == 0x1F6CC || cp == 0x1F90C ||
    cp in 0x1F918..0x1F91F || cp == 0x1F926 || cp in 0x1F930..0x1F939 ||
    cp in 0x1F93D..0x1F93E || cp == 0x1F977 || cp in 0x1F9B5..0x1F9B6 ||
    cp in 0x1F9B8..0x1F9B9 || cp in 0x1F9CD..0x1F9CF || cp in 0x1F9D1..0x1F9DD ||
    cp in 0x1FAF0..0x1FAF8
