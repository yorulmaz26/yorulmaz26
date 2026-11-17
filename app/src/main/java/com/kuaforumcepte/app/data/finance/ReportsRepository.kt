package com.kuaforumcepte.app.data.finance

import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.app.core.data.list
import com.kuaforumcepte.app.core.data.str
import com.kuaforumcepte.domain.model.Kurus
import com.kuaforumcepte.domain.time.ReportPeriod
import javax.inject.Inject
import javax.inject.Singleton

/** Dönemdeki tamamlanan işlem sayısı ve gerçekleşen hizmet bedeli (tahsilat değildir). */
data class ReportLine(val completedCount: Int, val amount: Kurus, val listed: Kurus, val commission: Kurus = Kurus(0)) {
    /** Gerçekleşen − liste (rezervasyondaki) tutar; düzeltmeler/indirimler. */
    val difference: Kurus get() = amount - listed
}

data class StaffReportLine(val staffUid: String, val name: String, val role: String, val active: Boolean, val line: ReportLine, val commissionPercent: Int = 0)

data class Report(val period: ReportPeriod, val salon: ReportLine?, val staff: List<StaffReportLine>)

@Singleton
class ReportsRepository @Inject constructor(private val functions: FunctionsClient) {
    suspend fun load(salonId: String, period: ReportPeriod): Report {
        val r = functions.call(
            "getReports",
            mapOf("salonId" to salonId, "periodType" to period.type.name, "anchorDate" to period.startDate.toString()),
        )
        fun line(m: Map<String, Any?>) = ReportLine(
            (m["completedCount"] as? Number)?.toInt() ?: 0,
            Kurus((m["amountKurus"] as? Number)?.toLong() ?: 0),
            Kurus((m["listedKurus"] as? Number)?.toLong() ?: 0),
            Kurus((m["commissionKurus"] as? Number)?.toLong() ?: 0),
        )
        val salon = r["salon"] as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        return Report(
            period = period,
            salon = salon?.let { line(it as Map<String, Any?>) },
            staff = r.list("staff").map { StaffReportLine(it.str("staffUid").orEmpty(), it.str("name").orEmpty(), it.str("role").orEmpty(), it.str("status") == "ACTIVE", line(it), (it["commissionPercent"] as? Number)?.toInt() ?: 0) },
        )
    }
}

