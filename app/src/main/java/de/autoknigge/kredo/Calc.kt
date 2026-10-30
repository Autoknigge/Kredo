package de.autoknigge.kredo

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Rechenkern: Kaufnebenkosten und Annuitätendarlehen mit Sonderzahlungen. */
object Calc {

    /** Grunderwerbsteuer der Bundesländer in Prozent (Stand: 2025/26, bitte vor Veröffentlichung prüfen). */
    val STATES = listOf(
        "Baden-Württemberg" to 5.0, "Bayern" to 3.5, "Berlin" to 6.0, "Brandenburg" to 6.5, "Bremen" to 5.0,
        "Hamburg" to 5.5, "Hessen" to 6.0, "Mecklenburg-Vorpommern" to 6.0, "Niedersachsen" to 5.0,
        "Nordrhein-Westfalen" to 6.5, "Rheinland-Pfalz" to 5.0, "Saarland" to 6.5, "Sachsen" to 5.5,
        "Sachsen-Anhalt" to 5.0, "Schleswig-Holstein" to 6.5, "Thüringen" to 5.0
    )

    fun stateRate(name: String): Double = STATES.firstOrNull { it.first == name }?.second ?: 5.0

    /** „1.234,56“, „1234,5“, „300.000“ oder „3.8“ als Zahl; null, wenn keine Zahl. */
    fun parseNum(input: String?): Double? {
        var s = (input ?: "").trim().replace(Regex("[\\s€%]"), "")
        if (s.isEmpty()) return null
        if (s.contains(',')) {
            s = s.replace(".", "").replace(',', '.')
        } else if (s.count { it == '.' } > 1 || Regex("^\\d{1,3}\\.\\d{3}$").matches(s)) {
            s = s.replace(".", "")
        }
        val v = s.toDoubleOrNull() ?: return null
        return if (v.isFinite()) v else null
    }

    class Costs(val price: Double, val g: Double, val nt: Double, val mk: Double, val eq: Double) {
        val gAmt = price * g / 100
        val mkAmt = price * mk / 100
        val ntAmt = price * nt / 100
        val extra = gAmt + mkAmt + ntAmt
        val total = price + extra
        val need = max(0.0, total - eq)
    }

    class Row(val m: Int, val interest: Double, val princ: Double, val extra: Double, val bal: Double)

    class Sim(val rows: List<Row>, val rate: Double, val interest: Double, val extras: Double) {
        val months get() = rows.size
    }

    fun annuity(loan: Double, pa: Double, n: Int): Double {
        val r = pa / 100 / 12
        return if (r > 0) loan * r / (1 - (1 + r).pow(-n)) else loan / n
    }

    fun niceTicks(maxV: Double, count: Int): List<Double> {
        var step = maxV / count
        val mag = 10.0.pow(kotlin.math.floor(kotlin.math.log10(step)))
        val f = step / mag
        val nice = if (f <= 1) 1.0 else if (f <= 2) 2.0 else if (f <= 2.5) 2.5 else if (f <= 5) 5.0 else 10.0
        step = nice * mag
        val out = ArrayList<Double>()
        var v = 0.0
        while (v <= maxV + step * 0.001) { out.add(v); v += step }
        return out
    }

    /**
     * [extraAt] liefert die Sonderzahlung am Ende von Monat m (1 = erster Monat).
     * Mit [fixedRate] (Anfangstilgung) steht die Monatsrate fest und die Laufzeit ergibt sich (höchstens 50 Jahre).
     */
    fun simulate(loan: Double, pa: Double, years: Int, extraAt: ((Int) -> Double)?, fixedRate: Double? = null): Sim {
        val n = if (fixedRate != null) 600 else years * 12
        val r = pa / 100 / 12
        val a = fixedRate ?: annuity(loan, pa, n)
        var bal = loan
        var interest = 0.0
        var extras = 0.0
        var m = 0
        val rows = ArrayList<Row>()
        while (bal > 0.005 && m < n + 12) {
            m++
            val i = bal * r
            val pay = min(a, bal + i)
            val princ = pay - i
            bal -= princ
            val ex = if (extraAt != null) min(extraAt(m), max(bal, 0.0)) else 0.0
            bal -= ex
            if (bal < 0.005) bal = 0.0
            interest += i
            extras += ex
            rows.add(Row(m, i, princ, ex, bal))
        }
        return Sim(rows, a, interest, extras)
    }

    /** Alle Angaben für die Berechnung des Darlehens. */
    class Loan(
        val amount: Double, val pa: Double, val years: Int, val startY: Int, val startM: Int, val fixedRate: Double?,
        val flat: Double, val flatMonth: Int, val flatFrom: Int, val flatTo: Int,
        val singles: Map<Int, Double>
    ) {
        fun extraAt(m: Int): Double {
            var x = singles[m] ?: 0.0
            if (flat > 0) {
                val cal = ((startM - 1) + (m - 1)) % 12 + 1
                val yr = ceil(m / 12.0).toInt()
                if (cal == flatMonth && yr >= flatFrom && yr <= flatTo) x += flat
            }
            return x
        }
    }
}
