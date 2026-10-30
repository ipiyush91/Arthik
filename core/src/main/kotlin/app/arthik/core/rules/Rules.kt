package app.arthik.core.rules

import app.arthik.core.model.AccountType
import app.arthik.core.model.Channel
import app.arthik.core.model.Direction
import app.arthik.core.model.TxnKind
import app.arthik.core.text.Amounts
import app.arthik.core.text.Dates
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class RuleType { TXN, STATEMENT, UPCOMING, SNAPSHOT, IGNORE }

/**
 * One SMS format. `patterns` are regexes extended with placeholders:
 *  {amount} {balance} {limit} {total} {min} -> "Rs <number>" captured as paise
 *  {money:name} / {num:name}               -> same, custom group name / number without currency
 *  {acct} / {acct:name}                    -> masked account or card number (XX468, **2383, 0021)
 *  {date} / {date:name}                    -> any supported date shape (see Dates.PATTERN)
 *  {merchant} / {text:name}                -> lazy text
 *  {ref} / {ref:name}                      -> alphanumeric reference (UPI RRN, UTR)
 *  {any}                                   -> lazy skip
 * A literal space matches any run of whitespace, including none. Regex quantifiers ({4}, {3,9}) are allowed.
 * A TXN rule without {amount} is accepted only when it captures `qty` (securities moved, amount 0).
 */
@Serializable
data class Rule(
    val id: String,
    val institution: String,
    val type: RuleType = RuleType.TXN,
    val patterns: List<String>,
    val senders: List<String> = emptyList(),
    val accountType: AccountType = AccountType.UNKNOWN,
    val direction: Direction? = null,
    val channel: Channel = Channel.OTHER,
    val kind: TxnKind? = null,
    val priority: Int = 100,
    /** user | public-verbatim | public-reconstructed | imported */
    val source: String = "public-verbatim",
    val verifiedOn: String? = null,
    val reason: String? = null,
    val sample: String? = null,
)

@Serializable
data class RulePack(
    val version: Int = 1,
    val rules: List<Rule> = emptyList(),
)

@Serializable
data class SenderInfo(
    val institution: String,
    val cores: List<String>,
    @SerialName("bodyNames") val bodyNames: List<String> = emptyList(),
)

@Serializable
data class SenderTable(val institutions: List<SenderInfo>)

object RuleJson {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false; prettyPrint = true }
}

class CompiledRule(val rule: Rule) {
    val regexes: List<Regex> = rule.patterns.map { TemplateCompiler.compile(it) }
}

object TemplateCompiler {
    // Must start with a letter so regex quantifiers such as \d{4} or [A-Z]{3} are left alone.
    private val placeholder = Regex("""\{([A-Za-z]\w*)(?::(\w+))?\}""")
    private const val MONEY_PREFIX = """Rs ?"""

    fun compile(template: String): Regex {
        val used = mutableSetOf<String>()
        val sb = StringBuilder()
        var last = 0
        for (m in placeholder.findAll(template)) {
            sb.append(literal(template.substring(last, m.range.first)))
            val kind = m.groupValues[1]
            val name = m.groupValues[2].ifEmpty { defaultName(kind) }
            sb.append(expand(kind, name, used))
            last = m.range.last + 1
        }
        sb.append(literal(template.substring(last)))
        return Regex(sb.toString(), setOf(RegexOption.IGNORE_CASE))
    }

    // Literal text stays a regex; a space matches optional whitespace.
    private fun literal(s: String) = s.replace(" ", """\s*""")

    private fun defaultName(kind: String) = when (kind) {
        "amount", "balance", "limit", "total", "min", "acct", "date", "merchant", "ref" -> kind
        else -> ""
    }

    private fun group(name: String, body: String, used: MutableSet<String>): String {
        if (name.isEmpty() || !used.add(name)) return "(?:$body)"
        return "(?<$name>$body)"
    }

    private fun expand(kind: String, name: String, used: MutableSet<String>): String = when (kind) {
        "amount", "balance", "limit", "total", "min", "money" ->
            MONEY_PREFIX + group(name.ifEmpty { kind }, Amounts.NUMBER, used)
        "num" -> group(name, Amounts.NUMBER, used)
        "acct" -> """(?:[Xx*.]+\s*)?""" + group(name, """\d{3,6}""", used)
        "date" -> group(name, Dates.PATTERN, used)
        "merchant", "text" -> group(name, """.+?""", used)
        "ref" -> group(name, """[A-Za-z0-9]{6,25}""", used)
        "any" -> """.*?"""
        else -> error("Unknown placeholder {$kind}")
    }
}
