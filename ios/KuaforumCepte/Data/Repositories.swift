import Foundation
import FirebaseAuth
import FirebaseFirestore

/// Oturum: giriş, önyükleme (bootstrapSession), yönetici kontrolü, çıkış.
enum SessionRepo {
    static var uid: String? { Auth.auth().currentUser?.uid }
    static var phone: String? { Auth.auth().currentUser?.phoneNumber }

    static func sendCode(phoneE164: String) async throws -> String {
        do {
            return try await PhoneAuthProvider.provider().verifyPhoneNumber(phoneE164, uiDelegate: nil)
        } catch { throw AppError.from(error) }
    }

    static func verify(verificationId: String, code: String) async throws {
        let credential = PhoneAuthProvider.provider().credential(withVerificationID: verificationId, verificationCode: code)
        do { _ = try await Auth.auth().signIn(with: credential) } catch { throw AppError.from(error) }
    }

    static func bootstrap() async throws -> SessionSnapshot {
        let r = try await Backend.call("bootstrapSession")
        let user = r["user"] as? [String: Any] ?? [:]
        return SessionSnapshot(
            profileExists: user["exists"] as? Bool ?? false,
            firstName: user["firstName"] as? String,
            lastName: user["lastName"] as? String,
            phoneE164: user["phoneE164"] as? String,
            memberships: r.list("memberships").map {
                Membership(salonId: $0.str("salonId") ?? "", salonName: $0.str("salonName") ?? "", role: $0.str("role") ?? "", status: $0.str("status") ?? "")
            },
            pendingInvitationCount: r.list("pendingInvitations").count
        )
    }

    static func isPlatformAdmin() async -> Bool {
        guard let user = Auth.auth().currentUser,
              let token = try? await user.getIDTokenResult(forcingRefresh: true) else { return false }
        return token.claims["admin"] as? Bool == true
    }

    static func registerCustomer(firstName: String, lastName: String, key: String) async throws {
        try await Backend.call("registerCustomer", [
            "firstName": firstName.trimmingCharacters(in: .whitespaces),
            "lastName": lastName.trimmingCharacters(in: .whitespaces),
            "consents": Legal.consentPayload,
            "idempotencyKey": key,
        ])
    }

    static func signOut() { try? Auth.auth().signOut() }
}

/// Salon bulma, salon sayfası, uzmanlar ve hizmetler.
enum SalonRepo {
    private static var salons: CollectionReference { Backend.db.collection("salons") }

    /// Onaylı ve aktif salonlar (ada göre). Arama istemcide Türkçe karakter duyarsız yapılır.
    static func directory(limit: Int = 200) async throws -> [Salon] {
        do {
            let qs = try await salons.whereField("status", isEqualTo: "ACTIVE").whereField("verified", isEqualTo: true)
                .order(by: "nameLower").limit(to: limit).getDocuments()
            return qs.documents.compactMap { Salon($0) }
        } catch { throw AppError.from(error) }
    }

    /// Salon kodu (6 hane) → salon kimliği
    static func resolve(code: String) async throws -> String? {
        do { return try await Backend.db.collection("salonCodes").document(code).getDocument().get("salonId") as? String }
        catch { throw AppError.from(error) }
    }

    static func observe(_ id: String) -> AsyncThrowingStream<DocumentSnapshot, Error> { salons.document(id).stream() }

    static func staff(_ salonId: String) async throws -> [StaffMember] {
        do {
            let qs = try await salons.document(salonId).collection("members")
                .whereField("status", isEqualTo: "ACTIVE").whereField("acceptsBookings", isEqualTo: true)
                .order(by: "firstName").getDocuments()
            return qs.documents.map { StaffMember($0) }
        } catch { throw AppError.from(error) }
    }

    static func services(_ salonId: String, staffUid: String) async throws -> [SalonService] {
        do {
            let qs = try await salons.document(salonId).collection("services")
                .whereField("staffUid", isEqualTo: staffUid).whereField("active", isEqualTo: true).whereField("deleted", isEqualTo: false)
                .order(by: "nameLower").getDocuments()
            return qs.documents.map { SalonService($0) }
        } catch { throw AppError.from(error) }
    }
}

/// Müsaitlik, fiyat önizlemesi ve randevu oluşturma (sunucu her şeyi transaction içinde yeniden doğrular).
enum BookingRepo {
    static func availability(salonId: String, staffUid: String, serviceIds: [String], from: String, days: Int, forSelf: Bool) async throws -> Availability {
        let r = try await Backend.call("getAvailability", [
            "salonId": salonId, "staffUid": staffUid, "serviceIds": serviceIds, "fromDate": from, "days": days, "forSelf": forSelf,
        ])
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let isoPlain = ISO8601DateFormatter()
        return Availability(
            total: Kurus(r.int64("totalKurus") ?? 0),
            totalMinutes: r.int("totalMinutes") ?? 0,
            days: r.list("days").map { d in
                AvailabilityDay(date: d.str("date") ?? "", slots: d.list("slots").compactMap { s in
                    guard let raw = s.str("startAt"), let date = iso.date(from: raw) ?? isoPlain.date(from: raw) else { return nil }
                    return Slot(startAt: date, label: s.str("label") ?? "")
                })
            }
        )
    }

    static func preview(salonId: String, staffUid: String, serviceIds: [String], code: String?, useLoyalty: Bool) async throws -> PricePreview {
        var data: [String: Any] = ["salonId": salonId, "staffUid": staffUid, "serviceIds": serviceIds, "useLoyaltyReward": useLoyalty]
        if let code, !code.isEmpty { data["discountCode"] = code }
        let r = try await Backend.call("previewBookingPrice", data)
        let l = r["loyalty"] as? [String: Any]
        var hint: String? = nil
        if let l, let v = l.int("visitsRequired"), let p = l.int("rewardPercent") {
            let next = l.int("visitsToNext").map { " Ödüle \($0) ziyaret kaldı." } ?? ""
            hint = "Sadakat programı: her \(v) ziyarette %\(p) indirim.\(next)"
        }
        return PricePreview(
            total: Kurus(r.int64("totalKurus") ?? 0), discount: Kurus(r.int64("discountKurus") ?? 0), finalTotal: Kurus(r.int64("finalKurus") ?? 0),
            codeError: r.str("codeError"), loyaltyAvailable: l?.int("rewardsAvailable") ?? 0, loyaltyPercent: l?.int("rewardPercent") ?? 0, loyaltyHint: hint
        )
    }

    /// @return oluşan randevu kimliği
    static func create(
        salonId: String, staffUid: String, serviceIds: [String], startAt: Date, expectedTotal: Kurus, key: String,
        guestName: String?, discountCode: String?, useLoyalty: Bool
    ) async throws -> String {
        let iso = ISO8601DateFormatter()
        var data: [String: Any] = [
            "salonId": salonId, "staffUid": staffUid, "serviceIds": serviceIds, "startAt": iso.string(from: startAt),
            "expectedTotalKurus": expectedTotal.value, "idempotencyKey": key, "useLoyaltyReward": useLoyalty,
        ]
        if let guestName, !guestName.isEmpty { data["guestName"] = guestName }
        if let discountCode, !discountCode.isEmpty { data["discountCode"] = discountCode }
        let r = try await Backend.call("createAppointment", data)
        return r.str("appointmentId") ?? ""
    }
}

/// Müşterinin randevuları, iptal, iletişim ve puanlama.
enum AppointmentRepo {
    private static var col: CollectionReference { Backend.db.collection("appointments") }

    static func observe(_ id: String) -> AsyncThrowingStream<DocumentSnapshot, Error> { col.document(id).stream() }

    static func observeMine(uid: String) -> AsyncThrowingStream<QuerySnapshot, Error> {
        col.whereField("customerUid", isEqualTo: uid).order(by: "startAt", descending: true).limit(to: 50).stream()
    }

    static func cancelByCustomer(_ id: String, reason: String?) async throws -> String {
        var data: [String: Any] = ["appointmentId": id, "action": "CANCEL_BY_CUSTOMER"]
        if let reason, !reason.isEmpty { data["reason"] = reason }
        return try await Backend.call("transitionAppointment", data).str("status") ?? ""
    }

    static func contacts(_ id: String) async throws -> [Contact] {
        try await Backend.call("getAppointmentContacts", ["appointmentId": id]).list("contacts").map {
            Contact(role: $0.str("role") ?? "", name: $0.str("name") ?? "", phoneE164: $0.str("phoneE164") ?? "")
        }
    }

    static func review(appointmentId: String, salon: Int, staff: Int, service: Int?, comment: String) async throws {
        var data: [String: Any] = ["appointmentId": appointmentId, "salonRating": salon, "staffRating": staff, "comment": comment]
        if let service { data["serviceRating"] = service }
        try await Backend.call("submitReview", data)
    }
}
