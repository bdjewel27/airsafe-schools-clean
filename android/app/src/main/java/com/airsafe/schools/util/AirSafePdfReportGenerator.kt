package com.airsafe.schools.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.airsafe.schools.R
import com.airsafe.schools.data.model.SchoolAnalyticsSummary
import com.airsafe.schools.data.model.formatLocalizedNumber
import com.airsafe.schools.data.model.toBengaliDigits
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AirSafePdfReportGenerator {
    /**
     * Generates an official, printable PDF Report using native Android PdfDocument
     * and authentic F-13 7-day analytics metrics. Launches the Android Share Sheet
     * with an application/pdf payload and granted read URI permissions.
     */
    fun generateAndShareWeeklyReport(
        context: Context,
        analytics: SchoolAnalyticsSummary,
        schoolName: String = "Dhaka Model School",
        isBengali: Boolean = true,
    ): Boolean {
        try {
            val pdfDoc = PdfDocument()
            // Standard A4 page size in PostScript points: 595 x 842
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Header Banner
            paint.color = Color.rgb(27, 94, 32) // Forest green #1B5E20
            canvas.drawRect(0f, 0f, 595f, 90f, paint)

            // Header Title
            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val titleText = context.getString(R.string.pdf_report_title)
            canvas.drawText(titleText, 36f, 40f, paint)

            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val subText = "$schoolName • Policy v1.2 Verified Decision Engine"
            canvas.drawText(subText, 36f, 65f, paint)

            // Report Meta Box
            var yPos = 120f
            paint.color = Color.rgb(240, 244, 240)
            canvas.drawRoundRect(36f, yPos, 559f, yPos + 60f, 10f, 10f, paint)

            paint.color = Color.rgb(33, 33, 33)
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val weekLabel = if (isBengali) analytics.weekLabelBn else analytics.weekLabelEn
            val weekText = context.getString(R.string.pdf_eval_week, weekLabel)
            canvas.drawText(weekText, 50f, yPos + 25f, paint)

            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.rgb(100, 100, 100)
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()).toBengaliDigits(isBengali)
            val dateLabel = context.getString(R.string.pdf_generated_at, dateStr)
            canvas.drawText(dateLabel, 50f, yPos + 45f, paint)

            yPos += 80f

            // Section 1: Executive KPI Metrics
            paint.color = Color.rgb(27, 94, 32)
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val sec1Title = context.getString(R.string.pdf_sec1_title)
            canvas.drawText(sec1Title, 36f, yPos, paint)

            yPos += 20f

            // Metric Cards (2x2 grid)
            val minUnit = context.getString(R.string.analytics_unit_minutes)
            val hrsUnit = context.getString(R.string.analytics_unit_hours)
            val metrics =
                listOf(
                    Pair(
                        context.getString(R.string.pdf_kpi_lost_mins),
                        "${formatLocalizedNumber(analytics.lostOutdoorMinutes, isBengali, 0)} $minUnit",
                    ),
                    Pair(
                        context.getString(R.string.pdf_kpi_hours_above),
                        "${formatLocalizedNumber(analytics.hoursAboveThreshold, isBengali, 1)} $hrsUnit",
                    ),
                    Pair(
                        context.getString(R.string.pdf_kpi_total_sessions),
                        formatLocalizedNumber(analytics.proceedCount + analytics.cautionCount + analytics.moveIndoorsCount, isBengali, 0),
                    ),
                    Pair(
                        context.getString(R.string.pdf_kpi_measured_ratio),
                        "${formatLocalizedNumber(analytics.measuredRatioPct, isBengali, 0)}%",
                    ),
                )

            val boxWidth = 250f
            val boxHeight = 55f
            metrics.forEachIndexed { i, m ->
                val col = i % 2
                val row = i / 2
                val left = 36f + col * 273f
                val top = yPos + row * 65f

                paint.color = Color.rgb(248, 249, 250)
                canvas.drawRoundRect(left, top, left + boxWidth, top + boxHeight, 8f, 8f, paint)

                paint.color = Color.rgb(100, 100, 100)
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(m.first, left + 14f, top + 20f, paint)

                paint.color = Color.rgb(27, 94, 32)
                paint.textSize = 18f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(m.second, left + 14f, top + 44f, paint)
            }

            yPos += 145f

            // Section 2: Deterministic Policy Decision Breakdown
            paint.color = Color.rgb(27, 94, 32)
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val sec2Title = context.getString(R.string.pdf_sec2_title)
            canvas.drawText(sec2Title, 36f, yPos, paint)

            yPos += 20f

            val decisions =
                listOf(
                    Triple(
                        context.getString(R.string.pdf_row_safe),
                        formatLocalizedNumber(analytics.proceedCount, isBengali, 0),
                        Color.rgb(46, 125, 50),
                    ),
                    Triple(
                        context.getString(R.string.pdf_row_caution),
                        formatLocalizedNumber(analytics.cautionCount, isBengali, 0),
                        Color.rgb(245, 127, 23),
                    ),
                    Triple(
                        context.getString(R.string.pdf_row_move_indoors),
                        formatLocalizedNumber(analytics.moveIndoorsCount, isBengali, 0),
                        Color.rgb(198, 40, 40),
                    ),
                    Triple(
                        context.getString(R.string.pdf_row_overrides),
                        formatLocalizedNumber(analytics.overridesCount, isBengali, 0),
                        Color.rgb(66, 66, 66),
                    ),
                )

            decisions.forEach { item ->
                paint.color = Color.rgb(250, 250, 250)
                canvas.drawRoundRect(36f, yPos, 559f, yPos + 32f, 6f, 6f, paint)

                // Color accent pill
                paint.color = item.third
                canvas.drawCircle(52f, yPos + 16f, 5f, paint)

                paint.color = Color.rgb(33, 33, 33)
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(item.first, 68f, yPos + 20f, paint)

                paint.textSize = 12f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = item.third
                canvas.drawText(item.second, 520f, yPos + 20f, paint)

                yPos += 38f
            }

            yPos += 30f

            // Section 3: Governance & Audit Signoff
            paint.color = Color.rgb(27, 94, 32)
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val sec3Title = context.getString(R.string.pdf_sec3_title)
            canvas.drawText(sec3Title, 36f, yPos, paint)

            yPos += 20f
            paint.color = Color.rgb(110, 110, 110)
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val note1 = context.getString(R.string.pdf_note_1)
            val note2 = context.getString(R.string.pdf_note_2)
            val note3 = context.getString(R.string.pdf_note_3)

            canvas.drawText(note1, 36f, yPos, paint)
            canvas.drawText(note2, 36f, yPos + 16f, paint)
            canvas.drawText(note3, 36f, yPos + 32f, paint)

            // Footer Divider & Disclaimer
            paint.color = Color.rgb(200, 200, 200)
            canvas.drawLine(36f, 790f, 559f, 790f, paint)

            paint.color = Color.rgb(140, 140, 140)
            paint.textSize = 8.5f
            val footerText = "AirSafe Schools (v1.0.0) • Government of Bangladesh & School Board Compliant • Official Record"
            canvas.drawText(footerText, 36f, 805f, paint)

            pdfDoc.finishPage(page)

            // Save PDF to cache directory
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }
            val fileName = "AirSafe_Weekly_Safety_Report_${System.currentTimeMillis()}.pdf"
            val pdfFile = File(reportsDir, fileName)

            FileOutputStream(pdfFile).use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()

            // Share via FileProvider & Android Share Sheet
            val contentUri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile,
                )

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        context.getString(R.string.pdf_share_subject, schoolName),
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            val chooserTitle = context.getString(R.string.pdf_share_chooser)
            val chooserIntent =
                Intent.createChooser(shareIntent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            if (shareIntent.resolveActivity(context.packageManager) == null) {
                return false
            }
            context.startActivity(chooserIntent)
            return true
        } catch (e: Exception) {
            AppLog.e("AirSafePdf", "Error generating PDF weekly report: ${e.message}", e)
            return false
        }
    }
}
