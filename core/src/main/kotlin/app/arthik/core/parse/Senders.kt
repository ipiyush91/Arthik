package app.arthik.core.parse

import app.arthik.core.rules.SenderTable

class Senders(table: SenderTable) {
    private val byCore: Map<String, String> =
        table.institutions.flatMap { info -> info.cores.map { it.uppercase() to info.institution } }.toMap()
    private val byName: List<Pair<Regex, String>> = table.institutions.flatMap { info ->
        info.bodyNames.map { Regex("""(?i)\b""" + Regex.escape(it).replace(" ", """\E\s*\Q""") + """\b""") to info.institution }
    }

    fun institutionFor(sender: String, body: String): String? =
        byCore[core(sender)] ?: byName.firstOrNull { it.first.containsMatchIn(body) }?.second

    fun isKnownCore(sender: String) = core(sender) in byCore

    companion object {
        private val dltPrefix = Regex("""^[A-Z]{2}-""")
        private val dltSuffix = Regex("""-[STPG]$""")
        private val phone = Regex("""^\+?\d{10,13}$""")

        /** "JD-ICICIT-S" -> "ICICIT". Matching on the core ignores the operator prefix and category suffix. */
        fun core(sender: String): String =
            sender.trim().uppercase().replace(dltPrefix, "").replace(dltSuffix, "")

        /** A bank-style message from an ordinary mobile number is treated as phishing. */
        fun isPhoneNumber(sender: String) = phone.matches(sender.replace(" ", ""))
    }
}
