import SwiftUI

/// Marka paleti (Android ile aynı): sıcak espresso zemin + altın vurgu.
enum Brand {
    static let espresso = Color(hex: 0x2B2420)
    static let espressoDeep = Color(hex: 0x131110)
    static let charcoal = Color(hex: 0x1C1917)
    static let charcoalHigh = Color(hex: 0x26221F)
    static let gold = Color(hex: 0xC9A06A)
    static let goldLight = Color(hex: 0xE6CFA8)
    static let goldDark = Color(hex: 0x8E6A3C)
    static let champagne = Color(hex: 0xF3E7D5)
    static let ivory = Color(hex: 0xFAF7F2)
    static let success = Color(hex: 0x2F8A6E)
    static let cancel = Color(hex: 0xC75252)
    static let pending = Color(hex: 0xB7791F)
}

/// Açık/koyu temaya uyumlu anlamsal renkler.
enum Palette {
    static let background = Color(light: Brand.ivory, dark: Brand.espressoDeep)
    static let card = Color(light: .white, dark: Brand.charcoal)
    static let cardHigh = Color(light: Color(hex: 0xF3EEE6), dark: Brand.charcoalHigh)
    static let text = Color(light: Color(hex: 0x1C1917), dark: Color(hex: 0xF3EEE7))
    static let muted = Color(light: Color(hex: 0x6B625A), dark: Color(hex: 0xC4B9AC))
    static let accent = Color(light: Brand.goldDark, dark: Brand.gold)
    static let primary = Color(light: Brand.espresso, dark: Brand.gold)
    static let onPrimary = Color(light: .white, dark: Brand.espressoDeep)
    static let outline = Color(light: Color(hex: 0xE7E0D5), dark: Color(hex: 0x39332D))
}

extension Color {
    init(hex: UInt32) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: 1)
    }

    init(light: Color, dark: Color) {
        self.init(UIColor { $0.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light) })
    }
}

enum Spacing {
    static let xs: CGFloat = 4
    static let s: CGFloat = 8
    static let m: CGFloat = 12
    static let l: CGFloat = 16
    static let xl: CGFloat = 24
    static let screen: CGFloat = 20
}
