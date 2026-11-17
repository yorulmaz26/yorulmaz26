import Foundation

// MARK: - Para (kuruş, tam sayı)

/// Tutarlar kuruş cinsinden tam sayıdır (sunucu ve Android ile aynı).
struct Kurus: Hashable, Comparable {
    let value: Int64
    init(_ value: Int64) { self.value = value }
    init(_ value: Int) { self.value = Int64(value) }

    static func < (a: Kurus, b: Kurus) -> Bool { a.value < b.value }
    static func + (a: Kurus, b: Kurus) -> Kurus { Kurus(a.value + b.value) }
    static func - (a: Kurus, b: Kurus) -> Kurus { Kurus(a.value - b.value) }
    static let zero = Kurus(0)

    private static let formatter: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.locale = Locale(identifier: "tr_TR")
        f.currencyCode = "TRY"
        f.maximumFractionDigits = 2
        f.minimumFractionDigits = 2
        return f
    }()

    /// 40000 → "₺400,00"
    var formatted: String { Kurus.formatter.string(from: NSNumber(value: Double(value) / 100.0)) ?? "\(value / 100) TL" }
}

// MARK: - Telefon

enum PhoneNumbers {
    /// Türkiye numarasını E.164'e çevirir (sunucudaki phone.ts ile aynı kurallar). Geçersizse nil.
    static func normalizeTR(_ raw: String, mobileOnly: Bool = false) -> String? {
        var digits = raw.filter { !" -().".contains($0) }
        if digits.hasPrefix("+") { digits.removeFirst() }
        guard !digits.isEmpty, digits.allSatisfy(\.isNumber) else { return nil }
        if digits.hasPrefix("90") && digits.count == 12 { digits = String(digits.dropFirst(2)) }
        else if digits.hasPrefix("0") && digits.count == 11 { digits = String(digits.dropFirst()) }
        guard digits.count == 10, let first = digits.first else { return nil }
        let ok = mobileOnly ? first == "5" : ("2"..."5").contains(String(first))
        return ok ? "+90" + digits : nil
    }

    /// "+905321234567" → "0532 123 45 67"
    static func display(_ e164: String) -> String {
        guard e164.hasPrefix("+90"), e164.count == 13 else { return e164 }
        let d = Array(e164.dropFirst(3))
        return "0\(String(d[0..<3])) \(String(d[3..<6])) \(String(d[6..<8])) \(String(d[8..<10]))"
    }
}

// MARK: - Zaman (iş mantığı Europe/Istanbul)

enum BusinessTime {
    static let zone = TimeZone(identifier: "Europe/Istanbul")!
    static let locale = Locale(identifier: "tr_TR")

    static var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = zone
        c.locale = locale
        c.firstWeekday = 2
        return c
    }()

    static func formatter(_ pattern: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = locale
        f.timeZone = zone
        f.dateFormat = pattern
        return f
    }

    static let dayLong = formatter("d MMMM yyyy, EEEE")
    static let dayShort = formatter("d MMM")
    static let weekdayShort = formatter("EEE")
    static let time = formatter("HH:mm")
    static let isoDate = formatter("yyyy-MM-dd")

    /// İstanbul'a göre bugünün "yyyy-MM-dd" değeri
    static func today(_ now: Date = Date()) -> String { isoDate.string(from: now) }
}

// MARK: - Randevu durumları

enum AppointmentStatus: String, CaseIterable {
    case pending = "PENDING"
    case confirmed = "CONFIRMED"
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
    case cancelledByCustomer = "CANCELLED_BY_CUSTOMER"
    case cancelledByStaff = "CANCELLED_BY_STAFF"
    case expired = "EXPIRED"
    case noShow = "NO_SHOW"

    var label: String {
        switch self {
        case .pending: return "Onay bekliyor"
        case .confirmed: return "Onaylandı"
        case .inProgress: return "İşlemde"
        case .completed: return "Tamamlandı"
        case .cancelledByCustomer: return "İptal edildi"
        case .cancelledByStaff: return "Salon iptal etti"
        case .expired: return "Süresi doldu"
        case .noShow: return "Gelinmedi"
        }
    }

    var isActive: Bool { self == .pending || self == .confirmed || self == .inProgress }
}

enum AppointmentRules {
    /// Müşteri başlangıçtan en geç 2 saat öncesine kadar uygulamadan iptal edebilir (tam sınırda izinli).
    static let customerCancelCutoff: TimeInterval = 2 * 3600

    static func canCustomerCancel(now: Date, startAt: Date) -> Bool {
        now.timeIntervalSince1970 <= startAt.timeIntervalSince1970 - customerCancelCutoff
    }
}

// MARK: - Yasal metinler (sunucudaki LEGAL_VERSIONS ile aynı olmalı)

enum Legal {
    static let kvkkVersion = "2026-11-v1"
    static let termsVersion = "2026-11-v1"
    static var consentPayload: [String: Any] { ["kvkkVersion": kvkkVersion, "termsVersion": termsVersion] }
}

// MARK: - Arama anahtarı (Türkçe karakter duyarsız)

enum SearchKey {
    static func of(_ text: String) -> String {
        let lower = text.lowercased(with: BusinessTime.locale)
        let map: [Character: Character] = ["ç": "c", "ğ": "g", "ı": "i", "ö": "o", "ş": "s", "ü": "u", "â": "a", "î": "i", "û": "u"]
        return String(lower.map { map[$0] ?? $0 }).trimmingCharacters(in: .whitespaces)
    }
}

/// Ad/soyad doğrulaması (sunucudaki personName ile aynı): harfle başlar, 2–50 karakter.
enum NameRules {
    static func isValid(_ s: String) -> Bool {
        let t = s.trimmingCharacters(in: .whitespaces)
        guard (2...50).contains(t.count), let first = t.first, first.isLetter, let last = t.last, last.isLetter || last == "." else { return false }
        return t.allSatisfy { $0.isLetter || $0 == " " || $0 == "." || $0 == "'" || $0 == "-" }
    }
}
