package app.arthik.core.text

import java.text.Normalizer as JNormalizer

object Normalizer {
    private val currency = Regex("""(?i)(?<![A-Za-z])(?:Rs|INR)[\s.:]*(?=\d)|₹\s*(?=\d)""")
    private val spaces = Regex("""[ \t \r\n]+""")

    /**
     * NFKC (SBI Card sends some words in mathematical sans-serif letters), collapse whitespace
     * including newlines, and unify every currency marker to "Rs ".
     */
    fun normalise(body: String): String {
        val nfkc = JNormalizer.normalize(body, JNormalizer.Form.NFKC)
        return currency.replace(spaces.replace(nfkc, " ").trim(), "Rs ")
    }
}
