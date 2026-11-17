import SwiftUI

// MARK: - Logo (Android'deki vektörle aynı geometri, 96×96 tuvalde)

struct BrandLogo: View {
    var size: CGFloat = 96

    var body: some View {
        Canvas { ctx, sz in
            let k = sz.width / 96
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * k, y: y * k) }
            func circle(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) -> Path {
                Path(ellipseIn: CGRect(x: (cx - r) * k, y: (cy - r) * k, width: 2 * r * k, height: 2 * r * k))
            }
            ctx.fill(circle(48, 48, 46), with: .color(Brand.espresso))
            ctx.stroke(circle(48, 48, 42), with: .color(Brand.gold), lineWidth: 2.5 * k)
            ctx.stroke(circle(48, 48, 37), with: .color(Brand.goldDark), lineWidth: 0.8 * k)
            var blade1 = Path(); blade1.addLines([p(37.5, 60.5), p(42.3, 62.6), p(64.6, 20.6), p(62.8, 19.6)]); blade1.closeSubpath()
            var blade2 = Path(); blade2.addLines([p(58.5, 60.5), p(53.7, 62.6), p(31.4, 20.6), p(33.2, 19.6)]); blade2.closeSubpath()
            ctx.fill(blade1, with: .color(Brand.goldLight))
            ctx.fill(blade2, with: .color(Brand.gold))
            ctx.stroke(circle(35, 69, 7), with: .color(Brand.gold), lineWidth: 3.4 * k)
            ctx.stroke(circle(61, 69, 7), with: .color(Brand.goldLight), lineWidth: 3.4 * k)
            ctx.fill(circle(48, 47.5, 3.2), with: .color(Brand.espresso))
            ctx.fill(circle(48, 47.5, 1.8), with: .color(Brand.goldLight))
        }
        .frame(width: size, height: size)
        .accessibilityLabel("Kuaförüm Cepte logosu")
    }
}

// MARK: - Düğmeler

struct PrimaryButtonStyle: ButtonStyle {
    var loading = false
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: Spacing.s) {
            if loading { ProgressView().tint(Palette.onPrimary) }
            configuration.label.font(.headline)
        }
        .frame(maxWidth: .infinity, minHeight: 54)
        .foregroundStyle(Palette.onPrimary)
        .background(Palette.primary.opacity(configuration.isPressed ? 0.85 : 1), in: RoundedRectangle(cornerRadius: 14))
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 50)
            .foregroundStyle(Palette.accent)
            .background(RoundedRectangle(cornerRadius: 14).stroke(Palette.accent.opacity(0.6), lineWidth: 1))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

struct PrimaryButton: View {
    let title: String
    var loading = false
    var enabled = true
    let action: () -> Void
    var body: some View {
        Button(action: action) { Text(title) }
            .buttonStyle(PrimaryButtonStyle(loading: loading))
            .disabled(!enabled || loading)
            .opacity(enabled ? 1 : 0.5)
    }
}

// MARK: - Kart, bilgi bandı, durum etiketi

struct CardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(Spacing.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.outline, lineWidth: 1))
    }
}

extension View {
    func card() -> some View { modifier(CardModifier()) }

    /// Tüm ekranlarda ortak zemin.
    func screenBackground() -> some View { background(Palette.background.ignoresSafeArea()) }
}

struct InfoBanner: View {
    let text: String
    var tint: Color = Palette.accent
    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s) {
            Image(systemName: "info.circle").foregroundStyle(tint)
            Text(text).font(.footnote).foregroundStyle(Palette.text)
        }
        .padding(Spacing.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
    }
}

struct StatusChip: View {
    let status: AppointmentStatus
    var body: some View {
        Text(status.label)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10).padding(.vertical, 4)
            .foregroundStyle(color)
            .background(color.opacity(0.15), in: Capsule())
    }
    private var color: Color {
        switch status {
        case .confirmed, .completed, .inProgress: return Brand.success
        case .pending: return Brand.pending
        default: return Brand.cancel
        }
    }
}

struct EmptyStateView: View {
    let systemImage: String
    let title: String
    var message: String? = nil
    var body: some View {
        VStack(spacing: Spacing.m) {
            Image(systemName: systemImage).font(.system(size: 40)).foregroundStyle(Palette.accent)
            Text(title).font(.headline).foregroundStyle(Palette.text).multilineTextAlignment(.center)
            if let message { Text(message).font(.subheadline).foregroundStyle(Palette.muted).multilineTextAlignment(.center) }
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity)
    }
}

struct StarRow: View {
    let value: Int
    var body: some View {
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { i in
                Image(systemName: i <= value ? "star.fill" : "star").font(.caption).foregroundStyle(Brand.gold)
            }
        }
    }
}

struct StarPicker: View {
    let label: String
    @Binding var value: Int
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).font(.subheadline.weight(.semibold))
            HStack(spacing: Spacing.s) {
                ForEach(1...5, id: \.self) { i in
                    Button { value = i } label: {
                        Image(systemName: i <= value ? "star.fill" : "star").font(.title2).foregroundStyle(Brand.gold)
                    }
                    .accessibilityLabel("\(i) yıldız")
                }
            }
        }
    }
}

/// Basit metin alanı (başlıklı, hata mesajlı).
struct LabeledField: View {
    let title: String
    @Binding var text: String
    var error: String? = nil
    var keyboard: UIKeyboardType = .default
    var prefix: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title).font(.caption).foregroundStyle(Palette.muted)
            HStack(spacing: Spacing.s) {
                if let prefix { Text(prefix).foregroundStyle(Palette.muted) }
                TextField("", text: $text).keyboardType(keyboard).foregroundStyle(Palette.text)
            }
            .padding(Spacing.m)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(error == nil ? Palette.outline : Brand.cancel, lineWidth: 1))
            if let error { Text(error).font(.caption).foregroundStyle(Brand.cancel) }
        }
    }
}
