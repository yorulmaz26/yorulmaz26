import Foundation
import FirebaseFirestore

enum SalonType: String, CaseIterable {
    case beautySalon = "BEAUTY_SALON"
    case menHairdresser = "MEN_HAIRDRESSER"
    case womenHairdresser = "WOMEN_HAIRDRESSER"
    case unisexHairdresser = "UNISEX_HAIRDRESSER"

    var label: String {
        switch self {
        case .beautySalon: return "Güzellik Salonu"
        case .menHairdresser: return "Erkek Kuaförü"
        case .womenHairdresser: return "Kadın Kuaförü"
        case .unisexHairdresser: return "Erkek & Kadın Kuaförü"
        }
    }
}

enum EntryRole: String { case customer = "CUSTOMER", business = "BUSINESS" }

struct Membership: Identifiable {
    let salonId: String
    let salonName: String
    let role: String
    let status: String
    var id: String { salonId }
    var isActive: Bool { status == "ACTIVE" }
}

struct SessionSnapshot {
    let profileExists: Bool
    let firstName: String?
    let lastName: String?
    let phoneE164: String?
    let memberships: [Membership]
    let pendingInvitationCount: Int

    var fullName: String { [firstName, lastName].compactMap { $0 }.joined(separator: " ") }
}

struct Rating {
    let average: Double?
    let count: Int
    var label: String { average.map { String(format: "%.1f", locale: BusinessTime.locale, $0) } ?? "Yeni" }
}

struct Salon: Identifiable, Hashable {
    let id: String
    let name: String
    let code: String
    let type: SalonType?
    let city: String
    let district: String
    let addressLine: String
    let description: String
    let logoUrl: String
    let businessPhoneE164: String
    let rating: Rating
    let verified: Bool
    let active: Bool
    let loyaltyText: String?

    var fullAddress: String { "\(addressLine), \(district) / \(city)" }

    static func == (a: Salon, b: Salon) -> Bool { a.id == b.id }
    func hash(into h: inout Hasher) { h.combine(id) }

    init?(_ d: DocumentSnapshot) {
        guard d.exists, let x = d.data() else { return nil }
        id = d.documentID
        name = x["name"] as? String ?? ""
        code = x["code"] as? String ?? ""
        type = SalonType(rawValue: x["type"] as? String ?? "")
        city = x["city"] as? String ?? ""
        district = x["district"] as? String ?? ""
        addressLine = x["addressLine"] as? String ?? ""
        description = x["description"] as? String ?? ""
        logoUrl = x["logoUrl"] as? String ?? ""
        businessPhoneE164 = x["businessPhoneE164"] as? String ?? ""
        rating = Rating(average: (x["ratingAvg"] as? NSNumber)?.doubleValue, count: (x["ratingCount"] as? NSNumber)?.intValue ?? 0)
        verified = x["verified"] as? Bool ?? false
        active = (x["status"] as? String) == "ACTIVE"
        if let l = x["loyalty"] as? [String: Any], l["enabled"] as? Bool == true,
           let v = (l["visitsRequired"] as? NSNumber)?.intValue, let p = (l["rewardPercent"] as? NSNumber)?.intValue {
            loyaltyText = "Sadakat programı: her \(v) ziyarette %\(p) indirim"
        } else {
            loyaltyText = nil
        }
    }
}

struct StaffMember: Identifiable, Hashable {
    let id: String
    let firstName: String
    let lastName: String
    let specialty: String
    let rating: Rating
    var fullName: String { "\(firstName) \(lastName)" }

    static func == (a: StaffMember, b: StaffMember) -> Bool { a.id == b.id }
    func hash(into h: inout Hasher) { h.combine(id) }

    init(_ d: QueryDocumentSnapshot) {
        let x = d.data()
        id = d.documentID
        firstName = x["firstName"] as? String ?? ""
        lastName = x["lastName"] as? String ?? ""
        specialty = x["specialty"] as? String ?? ""
        rating = Rating(average: (x["ratingAvg"] as? NSNumber)?.doubleValue, count: (x["ratingCount"] as? NSNumber)?.intValue ?? 0)
    }
}

struct SalonService: Identifiable, Hashable {
    let id: String
    let staffUid: String
    let name: String
    let description: String
    let price: Kurus
    let durationMinutes: Int

    init(_ d: QueryDocumentSnapshot) {
        let x = d.data()
        id = d.documentID
        staffUid = x["staffUid"] as? String ?? ""
        name = x["name"] as? String ?? ""
        description = x["description"] as? String ?? ""
        price = Kurus((x["priceKurus"] as? NSNumber)?.int64Value ?? 0)
        durationMinutes = (x["durationMinutes"] as? NSNumber)?.intValue ?? 0
    }
}

struct AppointmentItem: Hashable {
    let serviceId: String
    let name: String
    let price: Kurus
    let durationMinutes: Int
}

struct Appointment: Identifiable, Hashable {
    let id: String
    let number: String
    let salonId: String
    let salonName: String
    let salonAddress: String
    let staffUid: String
    let staffName: String
    let customerName: String
    let guestName: String?
    let items: [AppointmentItem]
    let total: Kurus
    let finalTotal: Kurus
    let discount: Kurus
    let discountLabel: String?
    let totalMinutes: Int
    let startAt: Date
    let endAt: Date
    let status: AppointmentStatus
    let pendingExpiresAt: Date
    let cancelReason: String?
    let reviewId: String?

    static func == (a: Appointment, b: Appointment) -> Bool { a.id == b.id && a.status == b.status && a.startAt == b.startAt }
    func hash(into h: inout Hasher) { h.combine(id) }

    /// Zamanlayıcı henüz işlemediyse bile süresi dolmuş talep "Süresi doldu" gösterilir.
    func effectiveStatus(_ now: Date) -> AppointmentStatus {
        status == .pending && now >= pendingExpiresAt ? .expired : status
    }

    func customerCanCancel(_ now: Date) -> Bool {
        effectiveStatus(now).isActive && status != .inProgress && AppointmentRules.canCustomerCancel(now: now, startAt: startAt)
    }

    /// Aktif ama son 2 saate girildi → müşteri salonu aramalı.
    func customerMustCall(_ now: Date) -> Bool {
        effectiveStatus(now).isActive && status != .inProgress && !AppointmentRules.canCustomerCancel(now: now, startAt: startAt)
    }

    var whenLabel: String {
        "\(BusinessTime.dayLong.string(from: startAt)) · \(BusinessTime.time.string(from: startAt))–\(BusinessTime.time.string(from: endAt))"
    }

    init?(_ d: DocumentSnapshot) {
        guard d.exists, let x = d.data(), let status = AppointmentStatus(rawValue: x["status"] as? String ?? "") else { return nil }
        id = d.documentID
        number = x["number"] as? String ?? ""
        salonId = x["salonId"] as? String ?? ""
        salonName = x["salonName"] as? String ?? ""
        salonAddress = x["salonAddress"] as? String ?? ""
        staffUid = x["staffUid"] as? String ?? ""
        staffName = x["staffName"] as? String ?? ""
        customerName = x["customerName"] as? String ?? ""
        guestName = x["guestName"] as? String
        items = (x["items"] as? [[String: Any]] ?? []).map {
            AppointmentItem(
                serviceId: $0["serviceId"] as? String ?? "", name: $0["name"] as? String ?? "",
                price: Kurus(($0["priceKurus"] as? NSNumber)?.int64Value ?? 0), durationMinutes: ($0["durationMinutes"] as? NSNumber)?.intValue ?? 0
            )
        }
        total = Kurus((x["totalKurus"] as? NSNumber)?.int64Value ?? 0)
        finalTotal = Kurus((x["finalKurus"] as? NSNumber)?.int64Value ?? total.value)
        let disc = x["discount"] as? [String: Any]
        discount = Kurus((disc?["discountKurus"] as? NSNumber)?.int64Value ?? 0)
        discountLabel = disc.map { ($0["type"] as? String) == "LOYALTY" ? "Sadakat ödülü" : "İndirim (\($0["code"] as? String ?? ""))" }
        totalMinutes = (x["totalMinutes"] as? NSNumber)?.intValue ?? 0
        startAt = (x["startAt"] as? Timestamp)?.dateValue() ?? .distantPast
        endAt = (x["endAt"] as? Timestamp)?.dateValue() ?? .distantPast
        self.status = status
        pendingExpiresAt = (x["pendingExpiresAt"] as? Timestamp)?.dateValue() ?? .distantPast
        cancelReason = x["cancelReason"] as? String
        reviewId = x["reviewId"] as? String
    }
}

struct Slot: Hashable { let startAt: Date; let label: String }
struct AvailabilityDay: Hashable, Identifiable { let date: String; let slots: [Slot]; var id: String { date } }
struct Availability { let total: Kurus; let totalMinutes: Int; let days: [AvailabilityDay] }

struct PricePreview {
    let total: Kurus
    let discount: Kurus
    let finalTotal: Kurus
    let codeError: String?
    let loyaltyAvailable: Int
    let loyaltyPercent: Int
    let loyaltyHint: String?
}

struct Contact: Hashable { let role: String; let name: String; let phoneE164: String
    var roleLabel: String { role == "STAFF" ? "Uzman" : role == "OWNER" ? "Salon sahibi" : "Salon" }
}

func formatDuration(_ minutes: Int) -> String {
    let h = minutes / 60, m = minutes % 60
    if h == 0 { return "\(m) dk" }
    return m == 0 ? "\(h) sa" : "\(h) sa \(m) dk"
}
