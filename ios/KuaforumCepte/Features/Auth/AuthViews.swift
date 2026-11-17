import SwiftUI

// MARK: - Rol seçimi

struct RoleSelectionView: View {
    @EnvironmentObject var app: AppState

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                hero
                VStack(alignment: .leading, spacing: Spacing.m) {
                    Text("Hoş geldiniz").font(.title2.bold()).foregroundStyle(Palette.text)
                    Text("Devam etmek için giriş türünü seçin.").font(.subheadline).foregroundStyle(Palette.muted)
                    RoleCard(icon: "person.fill", title: "Müşteri Girişi",
                             text: "Salon bulun, uzmanınızı seçin ve randevunuzu saniyeler içinde alın.") { choose(.customer) }
                    RoleCard(icon: "scissors", title: "Kuaför / Uzman Girişi",
                             text: "Salonunuzu, randevularınızı, hizmetlerinizi ve raporlarınızı yönetin.") { choose(.business) }
                    HStack(alignment: .top, spacing: Spacing.s) {
                        Image(systemName: "lock").font(.caption).foregroundStyle(Palette.muted)
                        Text("Aynı telefon numarasıyla hem müşteri hem uzman olarak giriş yapabilirsiniz. Yetkileriniz hesabınıza bağlı salon üyeliklerinden belirlenir.")
                            .font(.caption).foregroundStyle(Palette.muted)
                    }
                    .padding(.top, Spacing.s)
                }
                .padding(Spacing.screen)
            }
        }
        .ignoresSafeArea(edges: .top)
        .screenBackground()
    }

    private var hero: some View {
        VStack(spacing: Spacing.m) {
            BrandLogo(size: 112)
            Text("Kuaförüm Cepte").font(.system(size: 34, weight: .bold, design: .serif)).foregroundStyle(Color(hex: 0xF3EEE7))
            Text("Güzelliğe Bir Dokunuş, Randevuya Tek Tık.").font(.subheadline.weight(.medium)).foregroundStyle(Brand.gold)
            HStack(spacing: Spacing.s) {
                pill("bolt", "Hızlı randevu")
                pill("checkmark.seal", "Anlık onay")
                pill("bell", "Hatırlatma")
            }
        }
        .padding(.top, 72).padding(.bottom, Spacing.xl).padding(.horizontal, Spacing.screen)
        .frame(maxWidth: .infinity)
        .background(LinearGradient(colors: [Brand.espressoDeep, Brand.espresso], startPoint: .top, endPoint: .bottom))
        .clipShape(UnevenRoundedRectangleCompat(radius: 32))
    }

    private func pill(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon).font(.caption2).foregroundStyle(Brand.gold)
            Text(text).font(.caption2.weight(.medium)).foregroundStyle(Color(hex: 0xF3EEE7)).lineLimit(1)
        }
        .padding(.horizontal, 8).padding(.vertical, 5)
        .background(Color.white.opacity(0.08), in: Capsule())
        .overlay(Capsule().stroke(Brand.gold.opacity(0.5), lineWidth: 1))
    }

    private func choose(_ role: EntryRole) {
        if SessionRepo.uid != nil { app.go(.gate(role, skipAdmin: false)) } else { app.go(.phone(role)) }
    }
}

/// Yalnızca alt köşeleri yuvarlatılmış şekil (iOS 16 uyumlu).
struct UnevenRoundedRectangleCompat: Shape {
    let radius: CGFloat
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.minY))
        p.addLine(to: CGPoint(x: r.maxX, y: r.minY))
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY - radius))
        p.addQuadCurve(to: CGPoint(x: r.maxX - radius, y: r.maxY), control: CGPoint(x: r.maxX, y: r.maxY))
        p.addLine(to: CGPoint(x: r.minX + radius, y: r.maxY))
        p.addQuadCurve(to: CGPoint(x: r.minX, y: r.maxY - radius), control: CGPoint(x: r.minX, y: r.maxY))
        p.closeSubpath()
        return p
    }
}

struct RoleCard: View {
    let icon: String
    let title: String
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.l) {
                ZStack {
                    Circle().fill(LinearGradient(colors: [Brand.goldLight, Brand.gold, Brand.goldDark], startPoint: .topLeading, endPoint: .bottomTrailing))
                    Image(systemName: icon).font(.title3).foregroundStyle(Brand.espressoDeep)
                }
                .frame(width: 56, height: 56)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(Palette.text)
                    Text(text).font(.footnote).foregroundStyle(Palette.muted).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").foregroundStyle(Brand.gold)
            }
            .padding(Spacing.l)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Brand.gold.opacity(0.45), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Telefon

struct PhoneEntryView: View {
    @EnvironmentObject var app: AppState
    let role: EntryRole
    @State private var phone = ""
    @State private var sending = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.l) {
            BackBar(title: role == .customer ? "Müşteri Girişi" : "Kuaför / Uzman Girişi") { app.go(.roleSelection) }
            Text("Telefon numaranız").font(.title2.bold()).foregroundStyle(Palette.text)
            Text("Numaranıza 6 haneli bir doğrulama kodu göndereceğiz. Hesabınız yoksa doğrulamadan sonra kayıt adımına geçersiniz.")
                .font(.subheadline).foregroundStyle(Palette.muted)
            LabeledField(title: "Cep telefonu", text: $phone, error: error, keyboard: .phonePad, prefix: "+90")
            PrimaryButton(title: "Kod Gönder", loading: sending, enabled: !phone.isEmpty) { send() }
            Spacer()
        }
        .padding(Spacing.screen)
        .screenBackground()
    }

    private func send() {
        guard let e164 = PhoneNumbers.normalizeTR(phone, mobileOnly: true) else { error = ErrorMessages.messages["INVALID_PHONE"]; return }
        error = nil
        sending = true
        Task {
            do {
                let id = try await SessionRepo.sendCode(phoneE164: e164)
                app.go(.otp(role, verificationId: id, phone: e164))
            } catch { self.error = ErrorMessages.text(error) }
            sending = false
        }
    }
}

struct OtpView: View {
    @EnvironmentObject var app: AppState
    let role: EntryRole
    let verificationId: String
    let phone: String
    @State private var code = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.l) {
            BackBar(title: "Doğrulama Kodu") { app.go(.phone(role)) }
            Text("\(PhoneNumbers.display(phone)) numarasına gönderilen 6 haneli kodu girin.").font(.subheadline).foregroundStyle(Palette.muted)
            LabeledField(title: "Doğrulama kodu", text: $code, error: error, keyboard: .numberPad)
                .onChange(of: code) { v in
                    let digits = String(v.filter(\.isNumber).prefix(6))
                    if digits != v { code = digits }
                    if digits.count == 6 && !busy { verify() }
                }
            PrimaryButton(title: "Doğrula", loading: busy, enabled: code.count == 6) { verify() }
            Spacer()
        }
        .padding(Spacing.screen)
        .screenBackground()
    }

    private func verify() {
        busy = true
        error = nil
        Task {
            do {
                try await SessionRepo.verify(verificationId: verificationId, code: code)
                app.go(.gate(role, skipAdmin: false))
            } catch { self.error = ErrorMessages.text(error) }
            busy = false
        }
    }
}

// MARK: - Oturum kapısı (yönlendirme)

struct SessionGateView: View {
    @EnvironmentObject var app: AppState
    let role: EntryRole
    let skipAdmin: Bool
    @State private var error: String?

    var body: some View {
        VStack(spacing: Spacing.l) {
            BrandLogo(size: 88)
            if let error {
                Text(error).font(.subheadline).foregroundStyle(Palette.text).multilineTextAlignment(.center)
                PrimaryButton(title: "Tekrar dene") { Task { await route() } }
                Button("Çıkış yap") { app.signOut() }.buttonStyle(SecondaryButtonStyle())
            } else {
                ProgressView().tint(Palette.accent)
                Text("Hesabınız hazırlanıyor…").font(.subheadline).foregroundStyle(Palette.muted)
            }
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .screenBackground()
        .task { await route() }
    }

    private func route() async {
        error = nil
        do {
            app.remember(role: role)
            let snap = try await SessionRepo.bootstrap()
            app.session = snap
            if !skipAdmin, await SessionRepo.isPlatformAdmin() { app.go(.adminComingSoon); return }
            switch role {
            case .customer: app.go(snap.profileExists ? .customerHome : .customerRegister)
            case .business: app.go(.businessComingSoon)
            }
        } catch {
            let e = AppError.from(error)
            if e.reason == "UNAUTHENTICATED" { app.signOut(); return }
            self.error = ErrorMessages.text(e)
        }
    }
}

// MARK: - Müşteri kaydı

struct CustomerRegisterView: View {
    @EnvironmentObject var app: AppState
    @State private var first = ""
    @State private var last = ""
    @State private var kvkk = false
    @State private var terms = false
    @State private var busy = false
    @State private var error: String?
    @State private var legal: LegalDoc?
    @State private var key = UUID().uuidString

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                BackBar(title: "Müşteri Kaydı") { app.signOut() }
                Text("Sizi tanıyalım").font(.title2.bold()).foregroundStyle(Palette.text)
                LabeledField(title: "Ad", text: $first)
                LabeledField(title: "Soyad", text: $last)
                ConsentRow(isOn: $kvkk, text: "KVKK Aydınlatma Metni'ni okudum.") { legal = .kvkk }
                ConsentRow(isOn: $terms, text: "Kullanım Koşulları'nı kabul ediyorum.") { legal = .terms }
                if let error { Text(error).font(.footnote).foregroundStyle(Brand.cancel) }
                PrimaryButton(title: "Kaydı Tamamla", loading: busy, enabled: kvkk && terms) { submit() }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .sheet(item: $legal) { LegalView(doc: $0) }
    }

    private func submit() {
        guard NameRules.isValid(first), NameRules.isValid(last) else { error = ErrorMessages.messages["INVALID_NAME"]; return }
        busy = true
        error = nil
        Task {
            do {
                try await SessionRepo.registerCustomer(firstName: first, lastName: last, key: key)
                app.go(.gate(.customer, skipAdmin: true))
            } catch { self.error = ErrorMessages.text(error) }
            busy = false
        }
    }
}

struct ConsentRow: View {
    @Binding var isOn: Bool
    let text: String
    let open: () -> Void
    var body: some View {
        HStack(alignment: .top, spacing: Spacing.m) {
            Button { isOn.toggle() } label: {
                Image(systemName: isOn ? "checkmark.square.fill" : "square").font(.title3).foregroundStyle(Palette.accent)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(text).font(.subheadline).foregroundStyle(Palette.text)
                Button("Metni oku", action: open).font(.footnote.weight(.semibold))
            }
        }
    }
}

enum LegalDoc: String, Identifiable {
    case kvkk = "kvkk_aydinlatma"
    case terms = "kullanim_kosullari"
    var id: String { rawValue }
    var title: String { self == .kvkk ? "KVKK Aydınlatma Metni" : "Kullanım Koşulları" }
}

struct LegalView: View {
    let doc: LegalDoc
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                Text(text).font(.footnote).foregroundStyle(Palette.text).padding(Spacing.screen).frame(maxWidth: .infinity, alignment: .leading)
            }
            .screenBackground()
            .navigationTitle(doc.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Kapat") { dismiss() } } }
        }
    }
    private var text: String {
        guard let url = Bundle.main.url(forResource: doc.rawValue, withExtension: "md"), let s = try? String(contentsOf: url) else { return "Metin bulunamadı." }
        return s.replacingOccurrences(of: "#", with: "")
    }
}

/// Basit üst çubuk (geri + başlık)
struct BackBar: View {
    let title: String
    let back: () -> Void
    var body: some View {
        HStack(spacing: Spacing.m) {
            Button(action: back) { Image(systemName: "chevron.left").font(.title3.weight(.semibold)) }
            Text(title).font(.title3.weight(.semibold)).foregroundStyle(Palette.text)
            Spacer()
        }
        .padding(.top, Spacing.s)
    }
}
