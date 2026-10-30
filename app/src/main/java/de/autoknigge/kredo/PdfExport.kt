package de.autoknigge.kredo

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/** Alles, was die PDF-Übersicht braucht. */
class Snapshot(
    val costs: Calc.Costs?,
    val state: String,
    val brokerOn: Boolean,
    val lo: Calc.Loan,
    val tilgPct: Double?,
    val base: Calc.Sim,
    val withEx: Calc.Sim,
    val bindYears: Int?,
    val flatText: String,
    val singles: Map<Int, Double>,
    val dateOf: (Int) -> String
)

/** Erstellt eine druck- und versandfertige PDF-Übersicht (A4) mit dem Android-eigenen PDF-Erzeuger. */
object PdfExport {
    const val FOLDER = "Kredo"
    private const val W = 595
    private const val H = 842
    private const val M = 42f
    private val INK = Color.parseColor("#14201C")
    private val DIM = Color.parseColor("#5B6B66")
    private val TEAL = Color.parseColor("#0F6B5C")
    private val BRASS = Color.parseColor("#9A640D")
    private val LINE = Color.parseColor("#CBD6D1")
    private val eur0: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY).apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }
    private val eur2: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    private val num0: NumberFormat = NumberFormat.getIntegerInstance(Locale.GERMANY)

    private fun months(n: Int): String {
        val y = n / 12
        val r = n % 12
        val o = ArrayList<String>()
        if (y > 0) o.add("$y " + if (y == 1) "Jahr" else "Jahre")
        if (r > 0) o.add("$r " + if (r == 1) "Monat" else "Monate")
        return if (o.isEmpty()) "0 Monate" else o.joinToString(" ")
    }

    private fun pct(x: Double): String = String.format(Locale.GERMANY, "%.2f", x).trimEnd('0').trimEnd(',') + " %"

    private class Writer(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = M
        var n = 0
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF; color = INK; textSize = 10f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE; strokeWidth = 0.7f; style = Paint.Style.STROKE }

        fun newPage() {
            finish()
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            c = page!!.canvas
            y = M
        }

        fun finish() {
            val p = page ?: return
            val cv = c!!
            text.textSize = 8f; text.color = DIM; text.typeface = Typeface.SANS_SERIF; text.textAlign = Paint.Align.LEFT
            cv.drawText("Erstellt mit Kredo · autoknigge.de · überschlägige Berechnung, keine Finanzberatung", M, H - 22f, text)
            text.textAlign = Paint.Align.RIGHT
            cv.drawText("Seite $n", W - M, H - 22f, text)
            text.textAlign = Paint.Align.LEFT
            doc.finishPage(p)
            page = null
        }

        fun ensure(h: Float) { if (page == null || y + h > H - 46f) newPage() }

        fun heading(t: String) {
            ensure(34f)
            y += 10f
            text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); text.textSize = 12f; text.color = TEAL
            c!!.drawText(t, M, y + 12f, text)
            y += 18f
            c!!.drawLine(M, y, W - M, y, line)
            y += 4f
        }

        fun row(k: String, v: String, bold: Boolean = false, color: Int = INK, sub: String? = null) {
            ensure(17f)
            text.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            text.textSize = 10f; text.color = color
            text.textAlign = Paint.Align.LEFT
            c!!.drawText(k + if (sub != null) "  ($sub)" else "", M, y + 12f, text)
            text.textAlign = Paint.Align.RIGHT
            c!!.drawText(v, W - M, y + 12f, text)
            text.textAlign = Paint.Align.LEFT
            y += 16f
        }

        /** Tabellenzeile mit [xs] als rechte Kanten der Spalten; die erste Spalte steht links. */
        fun table(cells: List<String>, xs: List<Float>, bold: Boolean = false, color: Int = INK, shade: Boolean = false) {
            ensure(16f)
            if (shade) {
                val bg = Paint().apply { this.color = Color.parseColor("#F1F5F3") }
                c!!.drawRect(M, y, W - M, y + 15f, bg)
            }
            text.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            text.textSize = 9.5f
            for (i in cells.indices) {
                text.color = if (!bold && i == 3 && cells[i].isNotEmpty()) BRASS else color
                text.textAlign = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
                c!!.drawText(cells[i], if (i == 0) M + 3f else xs[i], y + 11f, text)
            }
            text.textAlign = Paint.Align.LEFT
            y += 15f
        }

        fun paragraph(t: String, size: Float = 9f, color: Int = DIM) {
            val tp = TextPaint(text).apply { textSize = size; this.color = color; typeface = Typeface.SANS_SERIF }
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, tp, (W - 2 * M).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1.5f, 1f).build()
            ensure(layout.height + 6f)
            c!!.save(); c!!.translate(M, y + 2f); layout.draw(c!!); c!!.restore()
            y += layout.height + 8f
        }
    }

    fun build(ctx: Context, s: Snapshot): File {
        val doc = PdfDocument()
        val w = Writer(doc)
        try {
            w.newPage()
            val c = w.c!!
            // Titel
            w.text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); w.text.textSize = 22f; w.text.color = TEAL
            c.drawText("Kredo", M, w.y + 20f, w.text)
            w.text.typeface = Typeface.SANS_SERIF; w.text.textSize = 11f; w.text.color = DIM
            c.drawText("Finanzierungsübersicht Immobilie", M + 78f, w.y + 19f, w.text)
            val cal = Calendar.getInstance()
            w.text.textAlign = Paint.Align.RIGHT
            c.drawText(String.format(Locale.GERMANY, "Stand: %02d.%02d.%d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)), W - M, w.y + 19f, w.text)
            w.text.textAlign = Paint.Align.LEFT
            w.y += 30f

            val lo = s.lo
            val cs = s.costs
            if (cs != null) {
                w.heading("Kauf und Nebenkosten")
                w.row("Kaufpreis", eur0.format(cs.price))
                w.row("Grunderwerbsteuer", eur0.format(cs.gAmt), sub = "${pct(cs.g)}, ${s.state}")
                if (cs.mk > 0) w.row("Makler (Käuferprovision)", eur0.format(cs.mkAmt), sub = pct(cs.mk))
                w.row("Notar und Grundbuch", eur0.format(cs.ntAmt), sub = pct(cs.nt))
                w.row("Nebenkosten gesamt", eur0.format(cs.extra), bold = true, sub = pct(cs.extra / cs.price * 100) + " vom Kaufpreis")
                w.row("Gesamtkosten", eur0.format(cs.total))
                w.row("abzüglich Eigenkapital", "− " + eur0.format(minOf(cs.eq, cs.total)))
                w.row("Darlehensbedarf", eur0.format(cs.need), bold = true, color = TEAL)
            }

            w.heading("Darlehen")
            w.row("Darlehensbetrag", eur0.format(lo.amount), bold = true)
            w.row("Sollzins pro Jahr", pct(lo.pa))
            if (s.tilgPct != null) w.row("Anfangstilgung", pct(s.tilgPct)) else w.row("Laufzeit bis zur Tilgung", "${lo.years} Jahre")
            w.row("Beginn der Rückzahlung", s.dateOf(1))
            w.row("Monatliche Rate", eur2.format(s.base.rate), bold = true, color = TEAL)

            w.heading("Sonderzahlungen")
            var any = false
            if (s.flatText.isNotEmpty()) { w.row("Pauschal", s.flatText); any = true }
            for ((m, a) in s.singles.toSortedMap()) { w.row("Einmalig im Monat $m (${s.dateOf(m)})", eur0.format(a)); any = true }
            if (!any) w.row("Keine Sonderzahlungen", "–")

            w.heading("Ergebnis")
            val xs = listOf(0f, W - M - 190f, W - M - 80f, W - M)
            w.table(listOf("", "", "Ohne", "Mit Sonderzahlung"), listOf(0f, 0f, W - M - 130f, W - M), bold = true, color = DIM)
            fun cmp(k: String, a: String, b: String, bold: Boolean = false, color: Int = INK) {
                w.ensure(17f)
                w.text.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
                w.text.textSize = 10f; w.text.color = color
                w.c!!.drawText(k, M + 3f, w.y + 12f, w.text)
                w.text.textAlign = Paint.Align.RIGHT
                w.c!!.drawText(a, W - M - 130f, w.y + 12f, w.text)
                w.c!!.drawText(b, W - M, w.y + 12f, w.text)
                w.text.textAlign = Paint.Align.LEFT
                w.y += 16f
            }
            val saveInt = s.base.interest - s.withEx.interest
            val saveM = s.base.months - s.withEx.months
            cmp("Laufzeit", months(s.base.months), months(s.withEx.months))
            cmp("Zinsen gesamt", eur0.format(s.base.interest), eur0.format(s.withEx.interest))
            cmp("Sonderzahlungen", "–", if (s.withEx.extras > 0.5) eur0.format(s.withEx.extras) else "–")
            cmp("Gesamt zu zahlen", eur0.format(lo.amount + s.base.interest), eur0.format(lo.amount + s.withEx.interest))
            if (s.withEx.extras > 0.5) cmp("Ersparnis", eur0.format(max(saveInt, 0.0)), if (saveM > 0) months(saveM) + " kürzer" else "–", bold = true, color = BRASS)
            w.y += 4f
            w.paragraph("Schuldenfrei: ohne Sonderzahlung ${s.dateOf(s.base.months)}, mit Sonderzahlung ${s.dateOf(s.withEx.months)}.", 9.5f, INK)
            if (s.bindYears != null) {
                val idx = s.bindYears * 12
                val rest = if (idx >= s.withEx.rows.size) 0.0 else s.withEx.rows[idx - 1].bal
                w.row("Restschuld nach ${s.bindYears} Jahren (Ende der Zinsbindung)", eur0.format(rest), bold = true)
            }

            // Diagramm
            w.heading("Restschuld im Verlauf")
            val ch = 170f
            w.ensure(ch + 20f)
            drawChart(w.c!!, M, w.y + 4f, W - 2 * M, ch, lo.amount, s.base, s.withEx)
            w.y += ch + 12f

            // Tilgungsplan pro Jahr
            w.heading("Tilgungsplan pro Jahr")
            val tx = listOf(0f, M + 150f, M + 255f, M + 365f, W - M)
            val head = listOf("Jahr", "Zinsen", "Tilgung", "Sonderzahlung", "Restschuld")
            w.table(head, tx, bold = true, color = DIM)
            var yr = 1
            var idx = 0
            val rows = s.withEx.rows
            while (idx < rows.size) {
                var i = 0.0; var p = 0.0; var x = 0.0; var bal = 0.0
                while (idx < rows.size && (rows[idx].m + 11) / 12 == yr) {
                    val r = rows[idx]; i += r.interest; p += r.princ; x += r.extra; bal = r.bal; idx++
                }
                if (w.y + 16f > H - 46f) { w.newPage(); w.table(head, tx, bold = true, color = DIM) }
                w.table(listOf("$yr", num0.format(Math.round(i)), num0.format(Math.round(p)), if (x > 0) num0.format(Math.round(x)) else "", num0.format(Math.round(bal))), tx, shade = yr % 2 == 0)
                yr++
            }
            w.text.textSize = 8.5f; w.text.color = DIM
            w.y += 4f
            w.paragraph("Beträge in €. Vereinfachte Berechnung: Zinsen werden monatlich auf die Restschuld berechnet (Zinssatz geteilt durch 12), Rate und Sonderzahlung wirken zum Monatsende. Der Zinssatz gilt für die ganze Laufzeit. Zahlen der Bank können wegen Zinsberechnung, Gebühren und Zahlungsterminen abweichen.")
            w.finish()

            val dir = File(ctx.cacheDir, "teilen").apply { mkdirs() }
            val f = File(dir, "Kredo_Finanzierung.pdf")
            f.outputStream().use { doc.writeTo(it) }
            return f
        } finally {
            doc.close()
        }
    }

    private fun drawChart(c: Canvas, x0: Float, y0: Float, w: Float, h: Float, loan: Double, base: Calc.Sim, with: Calc.Sim) {
        val ml = 54f
        val mb = 16f
        val pw = w - ml - 6f
        val ph = h - mb - 4f
        val maxM = max(1, base.months).toDouble()
        val tk = Calc.niceTicks(loan, 4)
        val yMax = tk.last()
        fun x(m: Double) = (x0 + ml + m / maxM * pw).toFloat()
        fun y(v: Double) = (y0 + 4f + ph - v / yMax * ph).toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.6f; p.color = LINE
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIM; textSize = 8f; typeface = Typeface.SANS_SERIF }
        for (v in tk) {
            c.drawLine(x0 + ml, y(v), x0 + w - 6f, y(v), p)
            t.textAlign = Paint.Align.RIGHT
            c.drawText(if (v >= 1e6) String.format(Locale.GERMANY, "%.1f Mio. €", v / 1e6) else if (v >= 1000) "${Math.round(v / 1000)} Tsd. €" else "${Math.round(v)} €", x0 + ml - 4f, y(v) + 3f, t)
        }
        t.textAlign = Paint.Align.CENTER
        val years = maxM / 12
        val step = max(1, Math.round(years / 8).toInt())
        var yy = 0
        while (yy <= years + 0.001) { c.drawText("$yy", x(yy * 12.0), y0 + h - 3f, t); yy += step }
        fun path(s: Calc.Sim): Path {
            val q = Path(); q.moveTo(x(0.0), y(loan))
            for (r in s.rows) q.lineTo(x(r.m.toDouble()), y(r.bal))
            return q
        }
        p.strokeWidth = 1.4f; p.color = DIM; p.pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        c.drawPath(path(base), p)
        p.pathEffect = null; p.strokeWidth = 2f; p.color = TEAL
        c.drawPath(path(with), p)
        p.strokeWidth = 1.2f; p.color = BRASS
        val d = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRASS }
        for (r in with.rows) if (r.extra > 0) {
            c.drawLine(x(r.m.toDouble()), y(r.bal + r.extra), x(r.m.toDouble()), y(r.bal), p)
            c.drawCircle(x(r.m.toDouble()), y(r.bal), 2.5f, d)
        }
        t.textAlign = Paint.Align.LEFT
        t.color = TEAL
        c.drawText("— mit Sonderzahlungen", x0 + ml + 8f, y0 + 12f, t)
        t.color = DIM
        c.drawText("- - ohne", x0 + ml + 110f, y0 + 12f, t)
        t.color = BRASS
        c.drawText("● Sonderzahlung", x0 + ml + 160f, y0 + 12f, t)
    }

    /** Legt die PDF in „Downloads/Kredo“ ab. */
    fun saveToDownloads(ctx: Context, src: File, fileName: String): Uri {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Die Datei konnte nicht angelegt werden.")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Die Datei konnte nicht geschrieben werden.")
            BufferedOutputStream(out, 64 * 1024).use { o -> src.inputStream().use { it.copyTo(o, 64 * 1024) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (t: Throwable) {
            try { resolver.delete(uri, null, null) } catch (ignored: Exception) {}
            throw t
        }
    }
}
