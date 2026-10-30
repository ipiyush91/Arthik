package app.arthik.core

import app.arthik.core.model.ParseResult
import java.io.File
import java.time.LocalDate
import kotlin.test.Test

class ZzProbeTest {
    @Test fun probe() {
        val dir = "/tmp/claude-0/-home-user/79cc92f7-2861-5923-8ad5-f804de516ea7/scratchpad/probe"
        val f = File("$dir/samples.tsv")
        if (!f.exists()) return
        val out = StringBuilder()
        f.readLines().filter { it.isNotBlank() }.forEach { line ->
            val (id, sender, date, body) = line.split("\t", limit = 4)
            val at = LocalDate.parse(date).atTime(12, 0)
            val r = runCatching { TestSupport.parse(body.replace("\\n", "\n"), sender, at) }.getOrElse { ParseResult.Ignored("CRASH $it") }
            val s = when (r) {
                is ParseResult.Transaction -> r.txn.let { t ->
                    "TXN ${t.ruleId} ${t.institution} ${t.accountType} ${t.direction} amt=${t.amount} acct=${t.accountLast4} m=[${t.merchant}] ref=${t.reference} d=${t.date} ch=${t.channel} k=${t.kind} bal=${t.balance} lim=${t.availableLimit} fx=${t.foreignCurrency} inv=${t.investment}"
                }
                else -> r.toString()
            }
            out.append("$id\t$s\n")
        }
        File("$dir/out.txt").writeText(out.toString())
    }
}
