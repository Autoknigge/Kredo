package de.autoknigge.kredo

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** Kredo – Kaufnebenkosten und Annuitätendarlehen mit Sonderzahlungen. */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val URL_HOME = "https://autoknigge.de"
        private const val URL_APPS = "https://autoknigge.de/apps/"
        private const val URL_IMPRINT = "https://autoknigge.de/impressum"
        private const val URL_PRIVACY = "https://autoknigge.de/kredo/datenschutz.html"
        private val GOLD = Color.parseColor("#D4AF37")
        private val INK = Color.parseColor("#E5F1ED")
        private val DIM = Color.parseColor("#9DB5AD")
        private val TEAL = Color.parseColor("#46C2AE")
        private val BRASS = Color.parseColor("#E0A64A")
        private val BAD = Color.parseColor("#EE8A79")
        private val RED = Color.parseColor("#FF2B2B")
        private val PANEL = Color.parseColor("#142622")
        private val FIELD = Color.parseColor("#0F1F1C")
        private val LINE = Color.parseColor("#28413B")
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val MONTHS = arrayOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
        private val MONTHS_LONG = arrayOf(
            "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"
        )
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var adManager: AdManager
    private val eur0: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }
    private val eur2: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    private val num0: NumberFormat = NumberFormat.getIntegerInstance(Locale.GERMANY)

    // Eingaben
    private lateinit var price: EditText
    private lateinit var gest: EditText
    private lateinit var notar: EditText
    private lateinit var brokerPct: EditText
    private lateinit var equity: EditText
    private lateinit var other: EditText
    private lateinit var amount: EditText
    private lateinit var rate: EditText
    private lateinit var years: EditText
    private lateinit var tilg: EditText
    private lateinit var bind: EditText
    private var termMode = 0
    private val termButtons = ArrayList<TextView>()
    private var lastSnap: Snapshot? = null
    private lateinit var flat: EditText
    private lateinit var flatFrom: EditText
    private lateinit var flatTo: EditText
    private lateinit var stateButton: TextView
    private lateinit var brokerBox: View
    private lateinit var brokerCheck: CheckBox
    private lateinit var autoCheck: CheckBox
    private lateinit var startButton: TextView
    private lateinit var flatMonthButton: TextView
    private lateinit var flatBox: View
    private lateinit var flatLabel: TextView
    private lateinit var flatUnit: TextView
    private lateinit var customBox: LinearLayout
    private lateinit var addCustom: TextView
    private val modeButtons = ArrayList<TextView>()
    private val viewButtons = ArrayList<TextView>()

    // Ausgaben
    private lateinit var costsBox: LinearLayout
    private lateinit var eqNote: TextView
    private lateinit var errorText: TextView
    private lateinit var resultBox: LinearLayout
    private lateinit var limitNote: TextView
    private lateinit var chart: ChartView
    private lateinit var planBox: LinearLayout
    private lateinit var chartCard: View
    private lateinit var planCard: View

    private var stateName = "Niedersachsen"
    private var startY = 0
    private var startM = 0
    private var flatMode = 1
    private var flatMonth = 12
    private var planMonthly = false
    private var settingAmount = false
    private class Custom(var amount: String, var month: String)
    private val custom = ArrayList<Custom>()
    private var lastSim: Calc.Sim? = null
    private var lastLoan: Calc.Loan? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------ Aufbau

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = getSharedPreferences("kredo", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_screen)
        }
        Ui.insets(root)

        val scroll = ScrollView(this).apply { isFillViewport = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Kopf mit Logo und Version
        val logoBox = FrameLayout(this)
        logoBox.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.logo_kredo)
            adjustViewBounds = true
            contentDescription = "Kredo"
        }, FrameLayout.LayoutParams(dp(210), WRAP, Gravity.CENTER_HORIZONTAL))
        val ver = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        logoBox.addView(text(ver, 10f, DIM), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END))
        col.addView(logoBox, LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(text("Kaufnebenkosten und Immobiliendarlehen mit Sonderzahlungen", 13f, DIM).apply { gravity = Gravity.CENTER })

        // --- Kauf und Nebenkosten
        val kauf = card("Kauf und Nebenkosten")
        price = addField(kauf, "Kaufpreis", "€", true)
        stateButton = button("") { pickState() }
        kauf.addView(label("Bundesland"), lp(top = 10))
        kauf.addView(stateButton, lp(top = 4))
        val two = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        gest = field("Grunderwerbsteuer", "%", true, two)
        notar = field("Notar und Grundbuch", "%", true, two)
        kauf.addView(two, lp(top = 10))
        brokerCheck = check("Kauf über einen Makler (Käuferprovision)") { brokerBox.visibility = if (it) View.VISIBLE else View.GONE; schedule() }
        kauf.addView(brokerCheck, lp(top = 10))
        val bb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brokerPct = addField(bb, "Maklerprovision inkl. MwSt.", "%", true)
        brokerBox = bb
        kauf.addView(bb, lp(top = 6))
        other = addField(kauf, "Sonstige Anschaffungskosten", "€", true)
        kauf.addView(text("Zum Beispiel Einbauküche, Entrümpelung, Heizung, Malerarbeiten. Bei älteren Häusern kommt oft Sanierungs- und Modernisierungsbedarf dazu. Lass dir dafür Angebote geben und trage die Summe ein.", 11.5f, DIM), lp(top = 4))
        equity = addField(kauf, "Eigenkapital", "€", true)
        costsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        kauf.addView(costsBox, lp(top = 12))
        eqNote = text("", 12.5f, INK).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { setColor(Color.parseColor("#3B2E14")); cornerRadius = dp(9).toFloat() }
            visibility = View.GONE
        }
        kauf.addView(eqNote, lp(top = 10))
        kauf.addView(text("Überschlägig: Notar und Grundbuch liegen meist bei 1,5 % (ohne Darlehen) bis 2 % (mit Grundschuld). Die Käuferprovision ist je nach Region 2,98 % bis 3,57 %. Die Steuersätze der Länder können sich ändern, du kannst sie überschreiben.", 11.5f, DIM), lp(top = 8))
        col.addView(kauf, lp(top = 12))

        // --- Darlehen
        val dar = card("Darlehen")
        autoCheck = check("Darlehensbetrag aus dem Kauf berechnen") { schedule() }
        dar.addView(autoCheck, lp(top = 0))
        amount = addField(dar, "Darlehensbetrag", "€", true)
        val two2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rate = field("Sollzins pro Jahr", "%", true, two2)
        bind = field("Zinsbindung", "Jahre", false, two2)
        dar.addView(two2, lp(top = 10))
        dar.addView(label("Rückzahlung festlegen über"), lp(top = 12))
        val seg3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tn = arrayOf("Laufzeit", "Anfangstilgung")
        for (i in tn.indices) {
            val b = text(tn[i], 13f, DIM, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener { termMode = i; updateTermUi(); schedule() }
            }
            termButtons.add(b)
            seg3.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) leftMargin = dp(6) })
        }
        dar.addView(seg3, lp(top = 4))
        val two3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        years = field("Laufzeit bis zur Tilgung", "Jahre", false, two3)
        tilg = field("Anfangstilgung", "%", true, two3)
        dar.addView(two3, lp(top = 10))
        dar.addView(label("Beginn der Rückzahlung"), lp(top = 10))
        startButton = button("") { pickStart() }
        dar.addView(startButton, lp(top = 4))
        dar.addView(text("Der Zinssatz gilt hier für die ganze Laufzeit. Bei einer kürzeren Zinsbindung ändert sich die Rate danach meist.", 11.5f, DIM), lp(top = 8))
        col.addView(dar, lp(top = 12))

        // --- Sonderzahlungen
        val sz = card("Sonderzahlungen")
        sz.addView(text("PAUSCHAL, JEDES JAHR", 11f, DIM, true).apply { letterSpacing = 0.08f })
        val seg = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = arrayOf("Keine", "Betrag in €", "% der Summe")
        for (i in labels.indices) {
            val b = text(labels[i], 13f, DIM, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener { flatMode = i; updateModeUi(); schedule() }
            }
            modeButtons.add(b)
            seg.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) leftMargin = dp(6) })
        }
        sz.addView(seg, lp(top = 8))
        val fb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val fWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        flatLabel = label("Betrag pro Jahr")
        fWrap.addView(flatLabel)
        val fFrame = FrameLayout(this)
        flat = edit(true)
        fFrame.addView(flat, FrameLayout.LayoutParams(MATCH, WRAP))
        flatUnit = text("€", 14f, DIM).apply { }
        fFrame.addView(flatUnit, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(11) })
        fWrap.addView(fFrame, lp(top = 4))
        r1.addView(fWrap, LinearLayout.LayoutParams(0, WRAP, 1f))
        val mWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mWrap.addView(label("Zahlung im Monat"))
        flatMonthButton = button("") { pickFlatMonth() }
        mWrap.addView(flatMonthButton, lp(top = 4))
        r1.addView(mWrap, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(10) })
        fb.addView(r1)
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        flatFrom = field("Ab Jahr", null, false, r2)
        flatTo = field("Bis Jahr (leer = Ende)", null, false, r2)
        fb.addView(r2, lp(top = 10))
        flatBox = fb
        sz.addView(fb, lp(top = 12))

        sz.addView(text("EINZELN, BIS ZU DREIMAL", 11f, DIM, true).apply { letterSpacing = 0.08f }, lp(top = 16))
        customBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sz.addView(customBox, lp(top = 8))
        addCustom = button("＋  Sonderzahlung hinzufügen") {
            if (custom.size < 3) { custom.add(Custom("", "")); renderCustom(); schedule() }
        }
        addCustom.setTextColor(BRASS)
        sz.addView(addCustom, lp(top = 4))
        limitNote = text("", 12.5f, INK).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { setColor(Color.parseColor("#3B2E14")); cornerRadius = dp(9).toFloat() }
            visibility = View.GONE
        }
        sz.addView(limitNote, lp(top = 10))
        sz.addView(text("Sonderzahlungen werden zusätzlich zur monatlichen Rate gezahlt. Die Rate bleibt gleich, die Laufzeit wird kürzer.", 11.5f, DIM), lp(top = 8))
        col.addView(sz, lp(top = 12))

        // --- Ergebnis
        val res = card("Ergebnis")
        errorText = text("", 13f, BAD).apply { visibility = View.GONE }
        res.addView(errorText)
        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        res.addView(resultBox)
        col.addView(res, lp(top = 12))

        chartCard = card("Restschuld im Verlauf").also { c ->
            chart = ChartView(this)
            c.addView(chart, LinearLayout.LayoutParams(MATCH, WRAP))
            c.addView(text("━ mit Sonderzahlungen   ┅ ohne   ● Sonderzahlung", 11.5f, DIM), lp(top = 6))
        }
        col.addView(chartCard, lp(top = 12))

        planCard = card("Tilgungsplan").also { c ->
            val seg2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val names = arrayOf("Pro Jahr", "Pro Monat")
            for (i in names.indices) {
                val b = text(names[i], 13f, DIM, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(10), dp(8), dp(10))
                    setOnClickListener { planMonthly = i == 1; updateViewUi(); renderPlan() }
                }
                viewButtons.add(b)
                seg2.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) leftMargin = dp(6) })
            }
            c.addView(seg2, lp(top = 0))
            planBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(planBox, lp(top = 10))
        }
        col.addView(planCard, lp(top = 12))

        col.addView(text("Vereinfachte Berechnung: Zinsen werden monatlich auf die Restschuld berechnet (Zinssatz geteilt durch 12), Rate und Sonderzahlung wirken zum Monatsende. Die Zahlen der Bank können wegen Zinsberechnungsmethode, Gebühren und Zahlungstermin leicht abweichen. Keine Finanzberatung.", 11.5f, DIM), lp(top = 14))
        col.addView(button("Zurücksetzen") { resetAll() }, lp(top = 12))

        // Fußzeile und Banner
        val adSettings = link("Werbe-Einstellungen", null)
        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
        }
        footer.addView(link("Datenschutz", URL_PRIVACY))
        footer.addView(text("·", 12f, GOLD))
        footer.addView(link("Impressum", URL_IMPRINT))
        footer.addView(text("·", 12f, GOLD))
        footer.addView(adSettings)
        footer.addView(text("·", 12f, GOLD))
        footer.addView(link("Weitere Apps", URL_APPS))
        root.addView(footer, LinearLayout.LayoutParams(MATCH, WRAP))
        val adBox = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_ad)
            minimumHeight = dp(60)
        }
        root.addView(adBox, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)
        adManager = AdManager(this, adBox, adSettings, true)

        // Startwerte, Listener
        val c = Calendar.getInstance()
        startY = c.get(Calendar.YEAR)
        startM = c.get(Calendar.MONTH) + 2
        if (startM > 12) { startM = 1; startY++ }
        applyDefaults()
        load()
        for (e in listOf(price, gest, notar, brokerPct, other, equity, amount, rate, years, tilg, bind, flat, flatFrom, flatTo)) {
            e.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                override fun afterTextChanged(s: Editable?) { if (!settingAmount) schedule() }
            })
        }
        updateModeUi()
        updateViewUi()
        updateTermUi()
        renderCustom()
        recompute()
        showIntro { adManager.start() }
    }

    // ------------------------------------------------------------------ Bausteine

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun label(t: String) = text(t, 12.5f, DIM)

    private fun link(label: String, url: String?) = text(label, 12f, GOLD).apply {
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setPadding(dp(6), dp(8), dp(6), dp(8))
        maxLines = 1
        if (url != null) setOnClickListener { Ui.openUrl(this@MainActivity, url) }
    }

    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply {
            setColor(PANEL)
            setStroke(dp(1), LINE)
            cornerRadius = dp(14).toFloat()
        }
        addView(text(title, 16f, GOLD, true), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
    }

    private fun edit(decimal: Boolean): EditText = EditText(this).apply {
        setTextColor(INK)
        setHintTextColor(DIM)
        textSize = 16f
        typeface = Typeface.MONOSPACE
        maxLines = 1
        setSingleLine(true)
        filters = arrayOf(InputFilter.LengthFilter(14))
        background = GradientDrawable().apply {
            setColor(FIELD)
            setStroke(dp(1), LINE)
            cornerRadius = dp(9).toFloat()
        }
        setPadding(dp(11), dp(10), dp(36), dp(10))
        minHeight = dp(46)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or if (decimal) android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
        keyListener = DigitsKeyListener.getInstance(if (decimal) "0123456789.," else "0123456789")
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
    }

    /** Beschriftetes Eingabefeld mit Einheit rechts in der Zeile [row] (nebeneinander). */
    private fun field(labelText: String, unit: String?, decimal: Boolean, row: LinearLayout): EditText {
        val w = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        w.addView(label(labelText))
        val f = FrameLayout(this)
        val e = edit(decimal)
        f.addView(e, FrameLayout.LayoutParams(MATCH, WRAP))
        if (unit != null) f.addView(text(unit, 13f, DIM), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(10) })
        w.addView(f, lp(top = 4))
        row.addView(w, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (row.childCount > 0) leftMargin = dp(10) })
        return e
    }

    private fun addField(parent: LinearLayout, labelText: String, unit: String?, decimal: Boolean): EditText {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val e = field(labelText, unit, decimal, row)
        parent.addView(row, lp(top = if (parent.childCount > 0) 10 else 0))
        return e
    }

    private fun button(label: String, onClick: () -> Unit): TextView = text(label, 14.5f, INK, true).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            setColor(FIELD)
            setStroke(dp(1), LINE)
            cornerRadius = dp(9).toFloat()
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        minHeight = dp(46)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun check(label: String, onChange: (Boolean) -> Unit): CheckBox = CheckBox(this).apply {
        text = label
        textSize = 14f
        setTextColor(INK)
        buttonTintList = android.content.res.ColorStateList.valueOf(TEAL)
        isChecked = true
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    private fun kv(parent: LinearLayout, k: String, v: String, color: Int = INK, bold: Boolean = false, sub: String? = null, line: Boolean = true) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(k, 13.5f, color, bold))
        if (sub != null) left.addView(text(sub, 11.5f, DIM))
        row.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(text(v, 13.5f, color, bold).apply { typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL); gravity = Gravity.END })
        parent.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        if (line) parent.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(MATCH, 1))
    }

    private fun tile(k: String, v: String, color: Int, fill: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(11), dp(9), dp(11), dp(9))
        background = GradientDrawable().apply { setColor(fill); cornerRadius = dp(11).toFloat() }
        addView(text(k, 11.5f, DIM))
        addView(text(v, 16.5f, color, true).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) })
    }

    // ------------------------------------------------------------------ Zustand

    private fun applyDefaults() {
        price.setText("400.000"); stateName = "Niedersachsen"; gest.setText("5,0"); notar.setText("2,0")
        brokerCheck.isChecked = true; brokerPct.setText("3,57"); other.setText(""); equity.setText("80.000"); autoCheck.isChecked = true
        amount.setText("320.000"); rate.setText("3,80"); years.setText("25"); tilg.setText("2,0"); bind.setText("10"); termMode = 0
        flatMode = 1; flat.setText("5.000"); flatMonth = 12; flatFrom.setText("1"); flatTo.setText("")
        custom.clear(); custom.add(Custom("15.000", "36"))
        brokerBox.visibility = View.VISIBLE
        refreshButtons()
    }

    private fun refreshButtons() {
        stateButton.text = "$stateName  (${pct(Calc.stateRate(stateName))})"
        startButton.text = "${MONTHS_LONG[startM - 1]} $startY"
        flatMonthButton.text = MONTHS_LONG[flatMonth - 1]
    }

    private fun resetAll() {
        val c = Calendar.getInstance()
        startY = c.get(Calendar.YEAR); startM = c.get(Calendar.MONTH) + 2
        if (startM > 12) { startM = 1; startY++ }
        applyDefaults(); updateModeUi(); updateTermUi(); renderCustom(); recompute()
    }

    private fun updateModeUi() {
        for ((i, b) in modeButtons.withIndex()) {
            val on = i == flatMode
            b.setTextColor(if (on) BRASS else DIM)
            b.background = GradientDrawable().apply {
                setColor(if (on) Color.parseColor("#3B2E14") else FIELD)
                setStroke(dp(1), if (on) BRASS else LINE)
                cornerRadius = dp(9).toFloat()
            }
        }
        flatBox.visibility = if (flatMode == 0) View.GONE else View.VISIBLE
        flatLabel.text = if (flatMode == 2) "Anteil der Darlehenssumme pro Jahr" else "Betrag pro Jahr"
        flatUnit.text = if (flatMode == 2) "%" else "€"
    }

    private fun updateTermUi() {
        for ((i, b) in termButtons.withIndex()) {
            val on = i == termMode
            b.setTextColor(if (on) TEAL else DIM)
            b.background = GradientDrawable().apply {
                setColor(if (on) Color.parseColor("#17413A") else FIELD)
                setStroke(dp(1), if (on) TEAL else LINE)
                cornerRadius = dp(9).toFloat()
            }
        }
        (years.parent.parent as View).visibility = if (termMode == 0) View.VISIBLE else View.GONE
        (tilg.parent.parent as View).visibility = if (termMode == 1) View.VISIBLE else View.GONE
    }

    private fun updateViewUi() {
        for ((i, b) in viewButtons.withIndex()) {
            val on = (i == 1) == planMonthly
            b.setTextColor(if (on) TEAL else DIM)
            b.background = GradientDrawable().apply {
                setColor(if (on) Color.parseColor("#17413A") else FIELD)
                setStroke(dp(1), if (on) TEAL else LINE)
                cornerRadius = dp(9).toFloat()
            }
        }
    }

    private fun pickState() {
        val names = Calc.STATES.map { "${it.first}  (${pct(it.second)})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Bundesland")
            .setItems(names) { _, i ->
                stateName = Calc.STATES[i].first
                gest.setText(String.format(Locale.GERMANY, "%.1f", Calc.STATES[i].second))
                refreshButtons(); schedule()
            }.show()
    }

    private fun pickFlatMonth() {
        AlertDialog.Builder(this).setTitle("Zahlung im Monat")
            .setItems(MONTHS_LONG) { _, i -> flatMonth = i + 1; refreshButtons(); schedule() }.show()
    }

    private fun pickStart() {
        val box = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val mp = NumberPicker(this).apply { minValue = 1; maxValue = 12; displayedValues = MONTHS_LONG; value = startM; wrapSelectorWheel = true }
        val now = Calendar.getInstance().get(Calendar.YEAR)
        val yp = NumberPicker(this).apply { minValue = now - 1; maxValue = now + 10; value = startY.coerceIn(now - 1, now + 10); wrapSelectorWheel = false }
        box.addView(mp, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(yp, LinearLayout.LayoutParams(0, WRAP, 1f))
        AlertDialog.Builder(this).setTitle("Beginn der Rückzahlung").setView(box)
            .setPositiveButton("OK") { _, _ -> startM = mp.value; startY = yp.value; refreshButtons(); renderCustom(); schedule() }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun renderCustom() {
        customBox.removeAllViews()
        for ((i, c) in custom.withIndex()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
            val a = field("Betrag", "€", true, row)
            val monthWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            monthWrap.addView(label("Im Monat Nr."))
            val m = edit(false).apply { setPadding(dp(11), dp(10), dp(11), dp(10)) }
            monthWrap.addView(m, lp(top = 4))
            val hint = text("", 11f, DIM)
            monthWrap.addView(hint)
            row.addView(monthWrap, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(10) })
            val x = text("✕", 17f, DIM).apply {
                gravity = Gravity.CENTER
                contentDescription = "Sonderzahlung entfernen"
                setOnClickListener { custom.removeAt(i); renderCustom(); schedule() }
            }
            row.addView(x, LinearLayout.LayoutParams(dp(40), dp(46)).apply { leftMargin = dp(4); bottomMargin = dp(18) })
            a.setText(c.amount); m.setText(c.month)
            hint.text = monthHint(c.month)
            a.addTextChangedListener(simpleWatcher { c.amount = a.text.toString(); schedule() })
            m.addTextChangedListener(simpleWatcher { c.month = m.text.toString(); hint.text = monthHint(c.month); schedule() })
            customBox.addView(row, lp(top = 8))
        }
        addCustom.alpha = if (custom.size >= 3) 0.4f else 1f
    }

    private fun simpleWatcher(f: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { f() }
    }

    private fun monthHint(v: String): String {
        val m = Calc.parseNum(v)?.toInt() ?: return "Monat der Zahlung"
        if (m < 1) return "Monat der Zahlung"
        val n = if (termMode == 1) 600 else (Calc.parseNum(years.text.toString())?.toInt() ?: 0) * 12
        if (m > n) return "Nach Ende der Laufzeit"
        return "Jahr ${(m + 11) / 12} · ${dateOf(m)}"
    }

    private fun dateOf(m: Int): String {
        val idx = (startM - 1) + (m - 1)
        return "${MONTHS[idx % 12]} ${startY + idx / 12}"
    }

    private fun pct(x: Double): String = String.format(Locale.GERMANY, "%s %%", trim(x))
    private fun trim(x: Double): String {
        val s = String.format(Locale.GERMANY, "%.2f", x)
        return s.trimEnd('0').trimEnd(',')
    }

    private fun monthsText(n: Int): String {
        val y = n / 12
        val r = n % 12
        val out = ArrayList<String>()
        if (y > 0) out.add("$y " + if (y == 1) "Jahr" else "Jahre")
        if (r > 0) out.add("$r " + if (r == 1) "Monat" else "Monate")
        return if (out.isEmpty()) "0 Monate" else out.joinToString(" ")
    }

    // ------------------------------------------------------------------ Berechnung

    private val recomputeTask = Runnable { recompute() }
    private fun schedule() {
        main.removeCallbacks(recomputeTask)
        main.postDelayed(recomputeTask, 180)
    }

    private fun costsNow(): Calc.Costs? {
        val p = Calc.parseNum(price.text.toString()) ?: return null
        if (p <= 0) return null
        val g = (Calc.parseNum(gest.text.toString()) ?: 0.0).coerceAtLeast(0.0)
        val nt = (Calc.parseNum(notar.text.toString()) ?: 0.0).coerceAtLeast(0.0)
        val mk = if (brokerCheck.isChecked) (Calc.parseNum(brokerPct.text.toString()) ?: 0.0).coerceAtLeast(0.0) else 0.0
        val eq = (Calc.parseNum(equity.text.toString()) ?: 0.0).coerceAtLeast(0.0)
        val ot = (Calc.parseNum(other.text.toString()) ?: 0.0).coerceAtLeast(0.0)
        return Calc.Costs(p, g, nt, mk, eq, ot)
    }

    private fun recompute() {
        // Kauf und Nebenkosten
        val p = Calc.parseNum(price.text.toString())
        costsBox.removeAllViews()
        eqNote.visibility = View.GONE
        var need: Double? = null
        if (p != null && p > 0) {
            val g = (Calc.parseNum(gest.text.toString()) ?: 0.0).coerceAtLeast(0.0)
            val nt = (Calc.parseNum(notar.text.toString()) ?: 0.0).coerceAtLeast(0.0)
            val mk = if (brokerCheck.isChecked) (Calc.parseNum(brokerPct.text.toString()) ?: 0.0).coerceAtLeast(0.0) else 0.0
            val eq = (Calc.parseNum(equity.text.toString()) ?: 0.0).coerceAtLeast(0.0)
            val ot = (Calc.parseNum(other.text.toString()) ?: 0.0).coerceAtLeast(0.0)
            val c = Calc.Costs(p, g, nt, mk, eq, ot)
            kv(costsBox, "Kaufpreis", eur0.format(c.price))
            kv(costsBox, "Grunderwerbsteuer", eur0.format(c.gAmt), sub = pct(g))
            if (mk > 0) kv(costsBox, "Makler", eur0.format(c.mkAmt), sub = pct(mk))
            kv(costsBox, "Notar und Grundbuch", eur0.format(c.ntAmt), sub = pct(nt))
            kv(costsBox, "Nebenkosten gesamt", eur0.format(c.extra), bold = true, sub = pct(c.extra / c.price * 100) + " vom Kaufpreis")
            if (ot > 0) kv(costsBox, "Sonstige Anschaffungskosten", eur0.format(c.other))
            kv(costsBox, "Gesamtkosten", eur0.format(c.total))
            kv(costsBox, "abzüglich Eigenkapital", "− " + eur0.format(minOf(eq, c.total)))
            kv(costsBox, "Darlehensbedarf", eur0.format(c.need), color = TEAL, bold = true, line = false)
            need = c.need
            if (eq < c.extra - 0.5) {
                eqNote.text = "Das Eigenkapital deckt die Nebenkosten (${eur0.format(c.extra)}) nicht ganz. Viele Banken wollen, dass mindestens die Nebenkosten aus eigenem Geld kommen."
                eqNote.visibility = View.VISIBLE
            }
        }
        amount.isEnabled = !autoCheck.isChecked
        amount.alpha = if (autoCheck.isChecked) 0.7f else 1f
        if (autoCheck.isChecked && need != null) {
            settingAmount = true
            amount.setText(num0.format(Math.round(need)))
            settingAmount = false
        }

        // Darlehen
        val errs = ArrayList<String>()
        val loan = Calc.parseNum(amount.text.toString())
        val pa = Calc.parseNum(rate.text.toString())
        val yrs = if (termMode == 0) Calc.parseNum(years.text.toString()) else 30.0
        val tl = Calc.parseNum(tilg.text.toString())
        if (loan == null || loan < 1000) errs.add("Bitte einen Darlehensbetrag ab 1.000 € eingeben.")
        if (pa == null || pa < 0 || pa > 20) errs.add("Der Zinssatz sollte zwischen 0 und 20 % liegen.")
        if (termMode == 0 && (yrs == null || yrs < 1 || yrs > 50)) errs.add("Die Laufzeit sollte zwischen 1 und 50 Jahren liegen.")
        if (termMode == 1 && (tl == null || tl < 0.1 || tl > 20)) errs.add("Die Anfangstilgung sollte zwischen 0,1 und 20 % liegen.")
        var fixed: Double? = null
        if (errs.isEmpty() && termMode == 1) {
            fixed = loan!! * (pa!! + tl!!) / 100 / 12
            if (fixed <= loan * pa / 100 / 12 + 0.01) errs.add("Mit dieser Rate sinkt die Schuld nicht. Bitte eine höhere Anfangstilgung wählen.")
        }
        if (errs.isNotEmpty()) {
            errorText.text = errs.joinToString(" ")
            errorText.visibility = View.VISIBLE
            resultBox.visibility = View.GONE
            chartCard.visibility = View.GONE
            planCard.visibility = View.GONE
            limitNote.visibility = View.GONE
            return
        }
        errorText.visibility = View.GONE
        resultBox.visibility = View.VISIBLE
        chartCard.visibility = View.VISIBLE
        planCard.visibility = View.VISIBLE
        val n = Math.round(yrs!!).toInt() * 12

        var flatAmt = 0.0
        var from = 1
        var to = Int.MAX_VALUE
        if (flatMode != 0) {
            val f = Calc.parseNum(flat.text.toString())
            if (f != null && f > 0) flatAmt = if (flatMode == 2) loan!! * f / 100 else f
            Calc.parseNum(flatFrom.text.toString())?.let { if (it >= 1) from = Math.round(it).toInt() }
            Calc.parseNum(flatTo.text.toString())?.let { if (it >= 1) to = Math.round(it).toInt() }
        }
        val singles = HashMap<Int, Double>()
        for (c in custom) {
            val a = Calc.parseNum(c.amount) ?: continue
            val mo = Calc.parseNum(c.month)?.let { Math.round(it).toInt() } ?: continue
            if (a > 0 && mo in 1..n) singles[mo] = (singles[mo] ?: 0.0) + a
        }
        val lo = Calc.Loan(loan!!, pa!!, Math.round(yrs).toInt(), startY, startM, fixed, flatAmt, flatMonth, from, to, singles)
        val base = Calc.simulate(lo.amount, lo.pa, lo.years, null, fixed)
        val withEx = Calc.simulate(lo.amount, lo.pa, lo.years, { lo.extraAt(it) }, fixed)
        if (fixed != null && base.rows.last().bal > 0.5) {
            errorText.text = "Mit dieser Rate ist das Darlehen nach 50 Jahren nicht getilgt. Bitte eine höhere Anfangstilgung wählen."
            errorText.visibility = View.VISIBLE
            resultBox.visibility = View.GONE; chartCard.visibility = View.GONE; planCard.visibility = View.GONE
            return
        }
        lastSim = withEx
        lastLoan = lo
        val bindYears = Calc.parseNum(bind.text.toString())?.let { Math.round(it).toInt() }?.takeIf { it in 1..50 }
        val flatText = when {
            flatAmt <= 0 -> ""
            flatMode == 2 -> "${String.format(Locale.GERMANY, "%.2f", Calc.parseNum(flat.text.toString()) ?: 0.0).trimEnd('0').trimEnd(',')} % der Darlehenssumme pro Jahr (${eur0.format(flatAmt)}), im ${MONTHS_LONG[flatMonth - 1]}, ab Jahr $from" + if (to != Int.MAX_VALUE) " bis Jahr $to" else ""
            else -> "${eur0.format(flatAmt)} pro Jahr, im ${MONTHS_LONG[flatMonth - 1]}, ab Jahr $from" + if (to != Int.MAX_VALUE) " bis Jahr $to" else ""
        }
        val cs = costsNow()
        lastSnap = Snapshot(cs, stateName, brokerCheck.isChecked, lo, if (termMode == 1) tl else null, base, withEx, bindYears, flatText, singles.toSortedMap()) { dateOf(it) }
        renderResults(lo, base, withEx)
        chart.set(lo.amount, base, withEx)
        renderPlan()
        save()
    }

    private fun renderResults(lo: Calc.Loan, base: Calc.Sim, withEx: Calc.Sim) {
        resultBox.removeAllViews()
        val saveInt = base.interest - withEx.interest
        val saveM = base.months - withEx.months
        val hasExtra = withEx.extras > 0.5
        val t1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        t1.addView(tile("Monatliche Rate", eur2.format(base.rate), RED, Color.parseColor("#3A1212")), LinearLayout.LayoutParams(0, WRAP, 1f))
        t1.addView(tile("Laufzeit mit Sonderzahlung", monthsText(withEx.months), INK, FIELD), LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(8) })
        val t2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        t2.addView(tile("Zinsen gesamt", eur0.format(withEx.interest), INK, FIELD), LinearLayout.LayoutParams(0, WRAP, 1f))
        t2.addView(tile("Zinsersparnis", eur0.format(maxOf(saveInt, 0.0)), BRASS, Color.parseColor("#3B2E14")), LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(8) })
        resultBox.addView(t1)
        resultBox.addView(t2, lp(top = 8))

        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(4)) }
        head.addView(text("", 11.5f, DIM), LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(text("Ohne", 11.5f, DIM, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(text("Mit Sonderzahlung", 11.5f, DIM, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, WRAP, 1.3f))
        resultBox.addView(head)
        fun cmp(k: String, a: String, b: String, color: Int = INK, bold: Boolean = false) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(7), 0, dp(7)) }
            row.addView(text(k, 13f, if (color == INK) DIM else color, bold), LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(text(a, 13f, color, bold).apply { gravity = Gravity.END; typeface = Typeface.MONOSPACE }, LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(text(b, 13f, color, bold).apply { gravity = Gravity.END; typeface = Typeface.MONOSPACE }, LinearLayout.LayoutParams(0, WRAP, 1.3f))
            resultBox.addView(row)
            resultBox.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(MATCH, 1))
        }
        cmp("Laufzeit", monthsText(base.months), monthsText(withEx.months))
        cmp("Zinsen gesamt", eur0.format(base.interest), eur0.format(withEx.interest))
        cmp("Sonderzahlungen", "–", if (hasExtra) eur0.format(withEx.extras) else "–")
        cmp("Gesamt zu zahlen", eur0.format(lo.amount + base.interest), eur0.format(lo.amount + withEx.interest))
        if (hasExtra) cmp("Ersparnis", eur0.format(maxOf(saveInt, 0.0)), if (saveM > 0) monthsText(saveM) + " kürzer" else "–", BRASS, true)
        resultBox.addView(
            text("Schuldenfrei: ohne Sonderzahlung ${dateOf(base.months)}, mit Sonderzahlung ${dateOf(withEx.months)}.", 12f, DIM),
            lp(top = 10)
        )

        val bindYears = Calc.parseNum(bind.text.toString())?.let { Math.round(it).toInt() }?.takeIf { it in 1..50 }
        if (bindYears != null) {
            val rest = if (bindYears * 12 > withEx.rows.size) 0.0 else withEx.rows[bindYears * 12 - 1].bal
            resultBox.addView(
                text("Restschuld nach $bindYears Jahren (Ende der Zinsbindung): ${eur0.format(rest)}", 13f, TEAL, true),
                lp(top = 8)
            )
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(button("📄  PDF teilen") { exportPdf(0) }.apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, WRAP, 1.3f))
        bar.addView(button("🖨  Drucken") { exportPdf(1) }.apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(6) })
        bar.addView(button("💾") { exportPdf(2) }.apply { gravity = Gravity.CENTER; contentDescription = "PDF speichern" }, LinearLayout.LayoutParams(0, WRAP, 0.5f).apply { leftMargin = dp(6) })
        resultBox.addView(bar, lp(top = 14))

        // Hinweis auf die übliche Grenze von 5 % pro Jahr
        val perYear = HashMap<Int, Double>()
        for (r in withEx.rows) if (r.extra > 0) perYear[(r.m + 11) / 12] = (perYear[(r.m + 11) / 12] ?: 0.0) + r.extra
        val worst = perYear.values.maxOrNull() ?: 0.0
        if (worst > lo.amount * 0.05 + 0.5) {
            limitNote.text = "Im stärksten Jahr sind das ${eur0.format(worst)} (${String.format(Locale.GERMANY, "%.1f", worst / lo.amount * 100)} % der Darlehenssumme). Viele Verträge erlauben kostenlos nur 5 %, manchmal 10 % pro Jahr. Prüfe deine Konditionen."
            limitNote.visibility = View.VISIBLE
        } else limitNote.visibility = View.GONE
    }

    /** Erstellt die PDF-Übersicht und teilt, druckt oder speichert sie (0 = teilen, 1 = drucken, 2 = speichern). */
    private fun exportPdf(action: Int) {
        val snap = lastSnap ?: return
        Thread {
            val r = runCatching { PdfExport.build(this, snap) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                r.onFailure { android.widget.Toast.makeText(this, "Die PDF konnte nicht erstellt werden.", android.widget.Toast.LENGTH_LONG).show() }
                r.onSuccess { file ->
                    when (action) {
                        0 -> {
                            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", file)
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Finanzierungsübersicht (Kredo)")
                                clipData = android.content.ClipData.newRawUri("", uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                startActivity(android.content.Intent.createChooser(send, "PDF senden an"))
                            } catch (e: android.content.ActivityNotFoundException) {
                                android.widget.Toast.makeText(this, "Keine App zum Senden gefunden.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        1 -> printPdf(file)
                        else -> {
                            val saved = runCatching { PdfExport.saveToDownloads(this, file, "Kredo_Finanzierung.pdf") }
                            android.widget.Toast.makeText(
                                this,
                                if (saved.isSuccess) "Gespeichert in „Downloads/${PdfExport.FOLDER}“." else "Die PDF konnte nicht gespeichert werden.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }.start()
    }

    private fun printPdf(file: java.io.File) {
        val pm = getSystemService(PRINT_SERVICE) as android.print.PrintManager
        val adapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(old: android.print.PrintAttributes?, attrs: android.print.PrintAttributes?, cancel: android.os.CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(
                    android.print.PrintDocumentInfo.Builder("Kredo_Finanzierung.pdf")
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true
                )
            }

            override fun onWrite(pages: Array<out android.print.PageRange>?, dest: android.os.ParcelFileDescriptor, cancel: android.os.CancellationSignal?, cb: WriteResultCallback) {
                try {
                    java.io.FileInputStream(file).use { i -> java.io.FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                    cb.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    cb.onWriteFailed(e.message)
                }
            }
        }
        pm.print("Kredo Finanzierung", adapter, null)
    }

    private fun renderPlan() {
        val sim = lastSim ?: return
        val lo = lastLoan ?: return
        planBox.removeAllViews()
        fun row(a: String, b: String, c: String, d: String, e: String, head: Boolean, shade: Boolean) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(5), dp(4), dp(5))
                if (shade) setBackgroundColor(Color.parseColor("#0F1F1C"))
            }
            val ws = floatArrayOf(1.25f, 1.1f, 1.1f, 1.1f, 1.25f)
            val vs = arrayOf(a, b, c, d, e)
            for (i in vs.indices) {
                r.addView(
                    text(vs[i], if (head) 10.5f else 11f, if (head) DIM else if (i == 3) BRASS else INK, head).apply {
                        gravity = if (i == 0) Gravity.START else Gravity.END
                        if (!head) typeface = Typeface.MONOSPACE
                        maxLines = 1
                    },
                    LinearLayout.LayoutParams(0, WRAP, ws[i])
                )
            }
            planBox.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val money = { v: Double -> num0.format(Math.round(v)) }
        if (planMonthly) {
            row("Monat", "Zinsen", "Tilgung", "Sonder", "Restschuld", true, false)
            for ((i, r) in sim.rows.withIndex()) {
                row("${r.m} · ${dateOf(r.m).replace(" 20", " ")}", money(r.interest), money(r.princ), if (r.extra > 0) money(r.extra) else "", money(r.bal), false, i % 2 == 1)
            }
        } else {
            row("Jahr", "Zinsen", "Tilgung", "Sonder", "Restschuld", true, false)
            var y = 1
            var idx = 0
            while (idx < sim.rows.size) {
                var i = 0.0; var p = 0.0; var x = 0.0; var bal = 0.0
                while (idx < sim.rows.size && (sim.rows[idx].m + 11) / 12 == y) {
                    val r = sim.rows[idx]; i += r.interest; p += r.princ; x += r.extra; bal = r.bal; idx++
                }
                row("$y", money(i), money(p), if (x > 0) money(x) else "", money(bal), false, y % 2 == 0)
                y++
            }
        }
        planBox.addView(text("Beträge in €", 11f, DIM), lp(top = 6))
    }

    // ------------------------------------------------------------------ Speichern

    private fun save() {
        val o = JSONObject()
        o.put("price", price.text.toString()).put("state", stateName).put("gest", gest.text.toString())
            .put("notar", notar.text.toString()).put("broker", brokerCheck.isChecked).put("brokerPct", brokerPct.text.toString())
            .put("other", other.text.toString()).put("equity", equity.text.toString()).put("auto", autoCheck.isChecked).put("amount", amount.text.toString())
            .put("rate", rate.text.toString()).put("years", years.text.toString()).put("tilg", tilg.text.toString()).put("bind", bind.text.toString()).put("termMode", termMode).put("startY", startY).put("startM", startM)
            .put("flatMode", flatMode).put("flat", flat.text.toString()).put("flatMonth", flatMonth)
            .put("flatFrom", flatFrom.text.toString()).put("flatTo", flatTo.text.toString())
        o.put("custom", JSONArray().apply { custom.forEach { put(JSONObject().put("a", it.amount).put("m", it.month)) } })
        prefs.edit().putString("inputs", o.toString()).apply()
    }

    private fun load() {
        try {
            val o = JSONObject(prefs.getString("inputs", null) ?: return)
            price.setText(o.optString("price", "400.000")); stateName = o.optString("state", stateName)
            gest.setText(o.optString("gest", "5,0")); notar.setText(o.optString("notar", "2,0"))
            brokerCheck.isChecked = o.optBoolean("broker", true); brokerPct.setText(o.optString("brokerPct", "3,57"))
            other.setText(o.optString("other", "")); equity.setText(o.optString("equity", "80.000")); autoCheck.isChecked = o.optBoolean("auto", true)
            amount.setText(o.optString("amount", "320.000")); rate.setText(o.optString("rate", "3,80")); years.setText(o.optString("years", "25"))
            tilg.setText(o.optString("tilg", "2,0")); bind.setText(o.optString("bind", "10")); termMode = o.optInt("termMode", 0).coerceIn(0, 1)
            startY = o.optInt("startY", startY); startM = o.optInt("startM", startM).coerceIn(1, 12)
            flatMode = o.optInt("flatMode", 1).coerceIn(0, 2); flat.setText(o.optString("flat", "")); flatMonth = o.optInt("flatMonth", 12).coerceIn(1, 12)
            flatFrom.setText(o.optString("flatFrom", "1")); flatTo.setText(o.optString("flatTo", ""))
            custom.clear()
            val a = o.optJSONArray("custom")
            if (a != null) for (i in 0 until minOf(a.length(), 3)) custom.add(Custom(a.getJSONObject(i).optString("a"), a.getJSONObject(i).optString("m")))
            brokerBox.visibility = if (brokerCheck.isChecked) View.VISIBLE else View.GONE
            refreshButtons()
            updateTermUi()
        } catch (e: Exception) {
            // Standardwerte bleiben
        }
    }

    // ------------------------------------------------------------------ Werbehinweis

    private fun showIntro(then: () -> Unit) {
        if (prefs.getBoolean("intro_seen", false)) {
            then()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Kostenlos dank Werbung")
            .setMessage(
                "Diese App ist kostenlos und finanziert sich durch Werbung: eine kurze Anzeige beim Start und ein Banner am unteren Rand.\n\n" +
                    "Im nächsten Schritt kannst du der Nutzung deiner Daten für Werbezwecke zustimmen und damit die kostenlose App unterstützen. " +
                    "Deine Auswahl kannst du jederzeit unter „Werbe-Einstellungen“ ändern."
            )
            .setCancelable(false)
            .setPositiveButton("Weiter") { _, _ ->
                prefs.edit().putBoolean("intro_seen", true).apply()
                then()
            }
            .show()
    }

    override fun onPause() {
        save()
        if (::adManager.isInitialized) adManager.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::adManager.isInitialized) adManager.resume()
    }

    override fun onDestroy() {
        main.removeCallbacks(recomputeTask)
        if (::adManager.isInitialized) adManager.destroy()
        super.onDestroy()
    }
}
