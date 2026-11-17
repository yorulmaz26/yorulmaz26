import XCTest
@testable import KuaforumCepte

/// Android (core:domain) ve sunucu (functions/src/domain) ile aynı kuralların iOS karşılıkları.
final class DomainTests: XCTestCase {
    func testPhoneNormalization() {
        XCTAssertEqual(PhoneNumbers.normalizeTR("0532 123 45 67"), "+905321234567")
        XCTAssertEqual(PhoneNumbers.normalizeTR("+90 (532) 123-45-67"), "+905321234567")
        XCTAssertEqual(PhoneNumbers.normalizeTR("5321234567", mobileOnly: true), "+905321234567")
        XCTAssertEqual(PhoneNumbers.normalizeTR("02125556677"), "+902125556677")
        XCTAssertNil(PhoneNumbers.normalizeTR("02125556677", mobileOnly: true))
        XCTAssertNil(PhoneNumbers.normalizeTR("12345"))
        XCTAssertEqual(PhoneNumbers.display("+905321234567"), "0532 123 45 67")
    }

    func testMoneyFormatting() {
        XCTAssertTrue(Kurus(40000).formatted.contains("400,00"))
        XCTAssertEqual((Kurus(40000) + Kurus(15000)).value, 55000)
    }

    func testCustomerCancelCutoffIsTwoHoursInclusive() {
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        XCTAssertTrue(AppointmentRules.canCustomerCancel(now: start.addingTimeInterval(-7200), startAt: start))
        XCTAssertFalse(AppointmentRules.canCustomerCancel(now: start.addingTimeInterval(-7199), startAt: start))
    }

    func testSearchKeyIgnoresTurkishCharacters() {
        XCTAssertEqual(SearchKey.of("IŞILTI Güzellik"), SearchKey.of("isilti guzellik"))
        XCTAssertTrue(SearchKey.of("Çankaya").contains(SearchKey.of("cank")))
    }

    func testNameRules() {
        XCTAssertTrue(NameRules.isValid("Ayşe"))
        XCTAssertTrue(NameRules.isValid("Ali Can"))
        XCTAssertFalse(NameRules.isValid("A"))
        XCTAssertFalse(NameRules.isValid("Ali1"))
    }

    func testIstanbulDayKey() {
        // 2026-11-17T22:30Z = İstanbul'da 18 Kasım 01:30
        XCTAssertEqual(BusinessTime.today(Date(timeIntervalSince1970: 1794954600)), "2026-11-18")
    }

    func testDurationLabels() {
        XCTAssertEqual(formatDuration(45), "45 dk")
        XCTAssertEqual(formatDuration(90), "1 sa 30 dk")
        XCTAssertEqual(formatDuration(120), "2 sa")
    }
}
