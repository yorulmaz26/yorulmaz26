import Foundation
import FirebaseCore
import FirebaseFirestore

struct Invitation: Identifiable, Hashable {
    let salonId: String
    let inviteId: String
    let salonName: String
    let specialty: String
    var id: String { inviteId }
}

struct ReportLine {
    let completedCount: Int
    let amount: Kurus
    let commission: Kurus
}

struct StaffReportLine: Identifiable {
    let id: String
    let name: String
    let role: String
    let line: ReportLine
}

struct Report {
    let salon: ReportLine?
    let staff: [StaffReportLine]
}

/// Haftalık program (1=Pazartesi … 7=Pazar). Basit düzenleyici: gün başına bir vardiya ve en fazla bir mola.
struct DayPlan: Equatable {
    var off: Bool
    var start: String
    var end: String
    var breakStart: String
    var breakEnd: String
    var hasBreak: Bool { !breakStart.isEmpty && !breakEnd.isEmpty }
}

struct ScheduleConflict: Identifiable {
    let id: String
    let localDate: String
    let start: String
    let customerName: String
}

/// İşletme tarafı işlemleri (yetkiler sunucuda ve kurallarda denetlenir).
enum BusinessRepo {
    private static var db: Firestore { Backend.db }

    static func invitations() async throws -> [Invitation] {
        let r = try await Backend.call("bootstrapSession")
        return r.list("pendingInvitations").map {
            Invitation(salonId: $0.str("salonId") ?? "", inviteId: $0.str("inviteId") ?? "", salonName: $0.str("salonName") ?? "", specialty: $0.str("specialty") ?? "")
        }
    }

    static func accept(_ i: Invitation) async throws {
        try await Backend.call("acceptInvitation", ["salonId": i.salonId, "inviteId": i.inviteId, "consents": Legal.consentPayload])
    }

    static func decline(_ i: Invitation) async throws {
        try await Backend.call("declineInvitation", ["salonId": i.salonId, "inviteId": i.inviteId])
    }

    // MARK: Randevular

    static func observeRange(salonId: String, staffUid: String?, from: Date, to: Date) -> AsyncThrowingStream<QuerySnapshot, Error> {
        var q: Query = db.collection("appointments")
        if let staffUid { q = q.whereField("staffUid", isEqualTo: staffUid) }
        q = q.whereField("salonId", isEqualTo: salonId)
            .whereField("startAt", isGreaterThanOrEqualTo: Timestamp(date: from))
            .whereField("startAt", isLessThan: Timestamp(date: to))
            .order(by: "startAt").limit(to: 300)
        return q.stream()
    }

    /// action: CONFIRM | REJECT | MARK_ARRIVED | MARK_NO_SHOW | COMPLETE | CANCEL_BY_STAFF
    static func transition(_ id: String, action: String, reason: String? = nil) async throws -> String {
        var d: [String: Any] = ["appointmentId": id, "action": action]
        if let reason, !reason.isEmpty { d["reason"] = reason }
        return try await Backend.call("transitionAppointment", d).str("status") ?? ""
    }

    static func walkIn(salonId: String, staffUid: String, serviceIds: [String], startAt: Date, name: String, phone: String?, key: String) async throws -> String {
        var d: [String: Any] = [
            "salonId": salonId, "staffUid": staffUid, "serviceIds": serviceIds,
            "startAt": ISO8601DateFormatter().string(from: startAt), "customerName": name, "idempotencyKey": key,
        ]
        if let phone, !phone.isEmpty { d["customerPhone"] = phone }
        return try await Backend.call("createWalkInAppointment", d).str("appointmentId") ?? ""
    }

    // MARK: Raporlar

    static func report(salonId: String, periodType: String, anchorDate: String) async throws -> Report {
        let r = try await Backend.call("getReports", ["salonId": salonId, "periodType": periodType, "anchorDate": anchorDate])
        func line(_ m: [String: Any]) -> ReportLine {
            ReportLine(completedCount: m.int("completedCount") ?? 0, amount: Kurus(m.int64("amountKurus") ?? 0), commission: Kurus(m.int64("commissionKurus") ?? 0))
        }
        return Report(
            salon: (r["salon"] as? [String: Any]).map(line),
            staff: r.list("staff").map { StaffReportLine(id: $0.str("staffUid") ?? "", name: $0.str("name") ?? "", role: $0.str("role") ?? "", line: line($0)) }
        )
    }

    // MARK: Hizmetler (istemci yazar; kurallar alanları doğrular)

    static func myServices(salonId: String, uid: String) async throws -> [SalonService] {
        do {
            let qs = try await db.collection("salons").document(salonId).collection("services")
                .whereField("staffUid", isEqualTo: uid).whereField("deleted", isEqualTo: false).order(by: "nameLower").getDocuments()
            return qs.documents.map { SalonService($0) }
        } catch { throw AppError.from(error) }
    }

    static func allMyServicesRaw(salonId: String, uid: String) async throws -> [(SalonService, Bool)] {
        do {
            let qs = try await db.collection("salons").document(salonId).collection("services")
                .whereField("staffUid", isEqualTo: uid).whereField("deleted", isEqualTo: false).order(by: "nameLower").getDocuments()
            return qs.documents.map { (SalonService($0), $0.get("active") as? Bool ?? true) }
        } catch { throw AppError.from(error) }
    }

    static func saveService(salonId: String, uid: String, id: String?, name: String, description: String, price: Kurus, minutes: Int, active: Bool) async throws {
        let col = db.collection("salons").document(salonId).collection("services")
        var data: [String: Any] = [
            "staffUid": uid, "salonId": salonId, "name": name, "nameLower": SearchKey.of(name), "description": description,
            "priceKurus": NSNumber(value: price.value), "durationMinutes": minutes, "active": active, "deleted": false,
            "updatedAt": FieldValue.serverTimestamp(),
        ]
        do {
            if let id {
                let cur = try await col.document(id).getDocument()
                data["createdAt"] = cur.get("createdAt")
                try await col.document(id).setData(data)
            } else {
                data["createdAt"] = FieldValue.serverTimestamp()
                try await col.document().setData(data)
            }
        } catch { throw AppError.from(error) }
    }

    /// Silme = deleted:true + active:false (geçmiş randevu kayıtları etkilenmez).
    static func deleteService(salonId: String, id: String) async throws {
        let ref = db.collection("salons").document(salonId).collection("services").document(id)
        do {
            let cur = try await ref.getDocument()
            guard var data = cur.data() else { return }
            data["deleted"] = true
            data["active"] = false
            data["updatedAt"] = FieldValue.serverTimestamp()
            try await ref.setData(data)
        } catch { throw AppError.from(error) }
    }

    // MARK: Haftalık program

    static func loadSchedule(salonId: String, uid: String) async throws -> [Int: DayPlan] {
        do {
            let d = try await db.collection("salons").document(salonId).collection("members").document(uid).collection("schedule").document("weekly").getDocument()
            let days = d.get("days") as? [String: Any] ?? [:]
            let off = Set((d.get("weeklyDaysOff") as? [NSNumber] ?? []).map(\.intValue))
            var out: [Int: DayPlan] = [:]
            for i in 1...7 {
                let day = days["\(i)"] as? [String: Any] ?? [:]
                let shift = (day["shifts"] as? [[String: Any]])?.first
                let brk = (day["breaks"] as? [[String: Any]])?.first
                out[i] = DayPlan(
                    off: off.contains(i) || shift == nil,
                    start: shift?["start"] as? String ?? "09:00", end: shift?["end"] as? String ?? "19:00",
                    breakStart: brk?["start"] as? String ?? "", breakEnd: brk?["end"] as? String ?? ""
                )
            }
            return out
        } catch { throw AppError.from(error) }
    }

    /// dryRun: yalnızca çakışma analizi. Çakışma varsa uygulamak için acknowledge gerekir.
    static func saveSchedule(salonId: String, plan: [Int: DayPlan], dryRun: Bool, acknowledge: Bool) async throws -> (applied: Bool, conflicts: [ScheduleConflict]) {
        var days: [String: Any] = [:]
        var off: [Int] = []
        for i in 1...7 {
            let p = plan[i] ?? DayPlan(off: true, start: "", end: "", breakStart: "", breakEnd: "")
            if p.off {
                off.append(i)
                days["\(i)"] = ["shifts": [], "breaks": []]
            } else {
                days["\(i)"] = [
                    "shifts": [["start": p.start, "end": p.end]],
                    "breaks": p.hasBreak ? [["start": p.breakStart, "end": p.breakEnd]] : [],
                ]
            }
        }
        let r = try await Backend.call("updateWeeklySchedule", [
            "salonId": salonId, "days": days, "weeklyDaysOff": off, "dryRun": dryRun, "acknowledgeConflicts": acknowledge,
        ])
        let conflicts = r.list("conflicts").map {
            ScheduleConflict(id: $0.str("appointmentId") ?? UUID().uuidString, localDate: $0.str("localDate") ?? "", start: $0.str("start") ?? "", customerName: $0.str("customerName") ?? "")
        }
        return (r["applied"] as? Bool ?? false, conflicts)
    }

    // MARK: Ekip

    static func members(salonId: String) async throws -> [(StaffMember, String, String)] {
        do {
            let qs = try await db.collection("salons").document(salonId).collection("members").order(by: "firstName").getDocuments()
            return qs.documents.map { (StaffMember($0), $0.get("role") as? String ?? "", $0.get("status") as? String ?? "") }
        } catch { throw AppError.from(error) }
    }

    static func invite(salonId: String, firstName: String, lastName: String, specialty: String, phone: String, key: String) async throws {
        try await Backend.call("createStaffInvitation", [
            "salonId": salonId, "firstName": firstName, "lastName": lastName, "specialty": specialty, "phone": phone, "idempotencyKey": key,
        ])
    }

    /// Randevu linki: https://<proje>.web.app/s/<kod>
    static func salonLink(code: String) -> String {
        let project = FirebaseApp.app()?.options.projectID ?? "kuaforumcepte"
        return "https://\(project).web.app/s/\(code)"
    }
}
