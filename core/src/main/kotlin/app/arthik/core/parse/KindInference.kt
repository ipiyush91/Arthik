package app.arthik.core.parse

import app.arthik.core.model.Channel
import app.arthik.core.model.Direction
import app.arthik.core.model.TxnKind

internal object KindInference {
    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val cashWithdrawal = r("""NFS\*CASH|CASH WDL|ATM WDL|withdrawn at|cash withdrawal|\bATM\b.*\bwithdraw""")
    private val cashDeposit = r("""\bCAM\*|cash deposit|\bCDM\b|deposited in cash|BY CASH""")
    private val salary = r("""\bSALARY\b|\bSAL\b|PAYROLL""")
    private val interest = r("""\binterest\b|\bINT\.?PD\b|\bINT CREDIT""")
    private val refund = r("""refund|reversal|reversed|\bREVERSE\b|\bREV\b|cashback|charge ?back""")
    private val achCredit = r("""(?:\b|(?<=Info))ACH\b|NACH""")
    private val mfInflow = r("""MUTUAL F|\bREDEMPTION\b""")
    private val cardPayment = r(
        """\bCRED\b|CRED Club|credit ?card (?:bill )?payment|towards .*credit card|\bBBPS\b|CCPAYMENT|CC PAYMENT|CARD PAYMENT"""
    )
    private val walletTopup = r("""Amazon Pay Bal|AMAZON PAY BALANCE|PAYZAPP|wallet (?:load|top.?up)|add(?:ed)? money|FASTag recharge|Recharge of .*FASTag""")
    private val emi = r("""EMI\b|LOAN|FINLEASE|FINANCE|FINANCI|Loan Recovery""")
    private val investment = r("""\bSIP\b|MUTUAL F|\bMF\b|ZERODHA|GROWW|UPSTOX|ANGEL ONE|INDMONEY|\bDHAN\b|CLEARING|Invest""")

    fun infer(body: String, merchant: String?, direction: Direction): TxnKind? {
        val m = merchant.orEmpty()
        return when {
            cashWithdrawal.containsMatchIn(body) && direction == Direction.DEBIT -> TxnKind.CASH_WITHDRAWAL
            cashDeposit.containsMatchIn(body) && direction == Direction.CREDIT -> TxnKind.CASH_DEPOSIT
            direction == Direction.CREDIT && refund.containsMatchIn(body) -> TxnKind.REFUND
            direction == Direction.CREDIT && mfInflow.containsMatchIn(body) -> TxnKind.MF_REDEMPTION
            direction == Direction.CREDIT && salary.containsMatchIn(body) -> TxnKind.SALARY
            direction == Direction.CREDIT && interest.containsMatchIn(body) -> TxnKind.INTEREST
            direction == Direction.CREDIT && achCredit.containsMatchIn(body) -> TxnKind.DIVIDEND
            direction == Direction.DEBIT && cardPayment.containsMatchIn(m) -> TxnKind.CARD_PAYMENT
            direction == Direction.DEBIT && walletTopup.containsMatchIn(m) -> TxnKind.WALLET_TOPUP
            direction == Direction.DEBIT && investment.containsMatchIn(m) -> TxnKind.INVESTMENT
            direction == Direction.DEBIT && emi.containsMatchIn(m) -> TxnKind.EMI
            else -> null
        }
    }

    private val upi = r("""\bUPI\b|\bVPA\b|@[a-z]{2,}""")
    private val imps = r("""\bIMPS\b""")
    private val neft = r("""\bNEFT\b|\bRTGS\b|\bUTR\b""")
    private val atm = r("""\bATM\b|\bNFS\b|CASH WDL""")
    private val mandate = r("""(?:\b|(?<=Info))ACH\b|\bNACH\b|\bECS\b|e-?mandate|AutoPay|Standing Instruction""")
    private val billpay = r("""BIL\*|BillPay|Bill Paid|\bBBPS\b""")
    private val card = r("""\bcard\b|\bPOS\b""")

    fun channel(body: String): Channel = when {
        upi.containsMatchIn(body) -> Channel.UPI
        imps.containsMatchIn(body) -> Channel.IMPS
        neft.containsMatchIn(body) -> Channel.NEFT
        atm.containsMatchIn(body) -> Channel.ATM
        mandate.containsMatchIn(body) -> Channel.MANDATE
        billpay.containsMatchIn(body) -> Channel.BILLPAY
        card.containsMatchIn(body) -> Channel.CARD
        else -> Channel.OTHER
    }
}
