package de.autoknigge.kredo

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Verlauf der Restschuld mit und ohne Sonderzahlungen. */
class ChartView(context: Context) : View(context) {
    private val d = context.resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9DB5AD.toInt(); textSize = 11 * d }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private var loan = 0.0
    private var base: Calc.Sim? = null
    private var with: Calc.Sim? = null

    fun set(loan: Double, base: Calc.Sim, with: Calc.Sim) {
        this.loan = loan
        this.base = base
        this.with = with
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 0.62f).toInt())
    }

    private fun ticks(maxV: Double, count: Int): List<Double> {
        var step = maxV / count
        val mag = 10.0.pow(floor(log10(step)))
        val f = step / mag
        val nice = if (f <= 1) 1.0 else if (f <= 2) 2.0 else if (f <= 2.5) 2.5 else if (f <= 5) 5.0 else 10.0
        step = nice * mag
        val out = ArrayList<Double>()
        var v = 0.0
        while (true) { out.add(v); if (v >= maxV - step * 0.001) break; v += step }
        return out
    }

    private fun short(v: Double): String = when {
        v >= 1e6 -> String.format(java.util.Locale.GERMANY, "%.1f Mio. €", v / 1e6)
        v >= 1000 -> "${Math.round(v / 1000)} Tsd. €"
        else -> "${Math.round(v)} €"
    }

    override fun onDraw(c: Canvas) {
        val b = base ?: return
        val w = with ?: return
        val ml = 58 * d
        val mr = 10 * d
        val mt = 8 * d
        val mb = 26 * d
        val pw = width - ml - mr
        val ph = height - mt - mb
        val maxM = max(1, b.months).toDouble()
        val tk = ticks(loan, 4)
        val yMax = tk.last()
        fun x(m: Double) = (ml + m / maxM * pw).toFloat()
        fun y(v: Double) = (mt + ph - v / yMax * ph).toFloat()

        line.pathEffect = null
        line.strokeWidth = d
        line.color = 0xFF28413B.toInt()
        txt.textAlign = Paint.Align.RIGHT
        for (v in tk) {
            c.drawLine(ml, y(v), width - mr, y(v), line)
            c.drawText(short(v), ml - 6 * d, y(v) + 4 * d, txt)
        }
        txt.textAlign = Paint.Align.CENTER
        val years = maxM / 12
        val stepY = max(1, Math.round(years / 6).toInt())
        var yr = 0
        while (yr <= years + 0.001) {
            c.drawText(yr.toString(), x(yr * 12.0), height - 6 * d, txt)
            yr += stepY
        }

        fun path(s: Calc.Sim): Path {
            val p = Path()
            p.moveTo(x(0.0), y(loan))
            for (r in s.rows) p.lineTo(x(r.m.toDouble()), y(r.bal))
            return p
        }
        line.strokeWidth = 2 * d
        line.color = 0xFF9DB5AD.toInt()
        line.pathEffect = DashPathEffect(floatArrayOf(6 * d, 5 * d), 0f)
        c.drawPath(path(b), line)
        line.pathEffect = null
        line.strokeWidth = 3 * d
        line.color = 0xFF46C2AE.toInt()
        c.drawPath(path(w), line)
        line.strokeWidth = 2 * d
        line.color = 0xFFE0A64A.toInt()
        dot.color = 0xFFE0A64A.toInt()
        for (r in w.rows) {
            if (r.extra > 0) {
                c.drawLine(x(r.m.toDouble()), y(r.bal + r.extra), x(r.m.toDouble()), y(r.bal), line)
                c.drawCircle(x(r.m.toDouble()), y(r.bal), 4 * d, dot)
            }
        }
    }
}
