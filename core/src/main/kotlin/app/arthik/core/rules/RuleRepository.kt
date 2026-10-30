package app.arthik.core.rules

/**
 * Loads the built-in rule packs shipped as resources, plus any imported packs. Imported rules
 * override built-in ones with the same id.
 */
class RuleRepository(
    builtIn: List<RulePack>,
    imported: List<RulePack> = emptyList(),
    val senders: SenderTable,
) {
    val rules: List<CompiledRule>

    init {
        val byId = LinkedHashMap<String, Rule>()
        builtIn.flatMap { it.rules }.forEach { byId[it.id] = it }
        imported.flatMap { it.rules }.forEach { byId[it.id] = it }
        // Rules from the user's own samples win over public ones, then explicit priority.
        rules = byId.values
            .sortedWith(compareByDescending<Rule> { sourceRank(it.source) }.thenByDescending { it.priority })
            .map(::CompiledRule)
    }

    private fun sourceRank(source: String) = when (source) {
        "user" -> 3
        "imported" -> 2
        "public-verbatim" -> 1
        else -> 0
    }

    companion object {
        val BUILT_IN_PACKS = listOf(
            "ignore", "icici", "sbi", "hdfc", "axis", "kotak", "other_banks", "cards", "wallets", "investments",
        )

        fun fromResources(imported: List<RulePack> = emptyList()): RuleRepository {
            val loader = RuleRepository::class.java.classLoader!!
            fun read(path: String) = loader.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
                ?: error("Missing resource $path")
            val packs = BUILT_IN_PACKS.map { RuleJson.json.decodeFromString<RulePack>(read("arthik/rules/$it.json")) }
            val senders = RuleJson.json.decodeFromString<SenderTable>(read("arthik/senders.json"))
            return RuleRepository(packs, imported, senders)
        }

        fun parsePack(text: String): RulePack = RuleJson.json.decodeFromString(text)
    }
}
