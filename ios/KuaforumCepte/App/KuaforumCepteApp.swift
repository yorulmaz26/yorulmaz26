import SwiftUI
import FirebaseAuth

@main
struct KuaforumCepteApp: App {
    @StateObject private var app = AppState()

    init() { FirebaseSetup.configureIfPossible() }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .tint(Palette.accent)
                // reCAPTCHA dönüşü (telefonla giriş, APNs yoksa)
                .onOpenURL { url in _ = Auth.auth().canHandle(url) }
        }
    }
}

/// Uygulama genel durumu: hangi ana ekranda olduğumuz ve oturum özeti.
@MainActor
final class AppState: ObservableObject {
    enum Screen: Equatable {
        case setupRequired
        case roleSelection
        case phone(EntryRole)
        case otp(EntryRole, verificationId: String, phone: String)
        case gate(EntryRole, skipAdmin: Bool)
        case customerRegister
        case customerHome
        case invitations
        case salonPicker
        case businessNoSalon
        case business(Membership)
        case adminComingSoon
    }

    @Published var screen: Screen
    @Published var session: SessionSnapshot?
    /// Müşteri ana ekranında açılacak salon (kod/QR ile)
    @Published var pendingSalonId: String?

    private let lastRoleKey = "lastEntryRole"

    init() {
        if !FirebaseSetup.isConfigured { screen = .setupRequired; return }
        if Auth.auth().currentUser != nil, let raw = UserDefaults.standard.string(forKey: lastRoleKey), let role = EntryRole(rawValue: raw) {
            screen = .gate(role, skipAdmin: false)
        } else {
            screen = .roleSelection
        }
    }

    func go(_ s: Screen) { withAnimation(.easeInOut(duration: 0.25)) { screen = s } }

    func remember(role: EntryRole) { UserDefaults.standard.set(role.rawValue, forKey: lastRoleKey) }

    /// Birden çok salonda çalışan uzman için son seçilen salon
    var lastSalonId: String? {
        get { UserDefaults.standard.string(forKey: "lastBusinessSalon") }
        set { UserDefaults.standard.set(newValue, forKey: "lastBusinessSalon") }
    }

    /// İşletme girişi yönlendirmesi (Android SessionRouting ile aynı): davet → davetler; üyelik yok → bilgi;
    /// tek salon → panel; birden çok → son seçilen veya seçim ekranı.
    func routeBusiness(_ snap: SessionSnapshot) {
        let active = snap.memberships.filter(\.isActive)
        if snap.pendingInvitationCount > 0 { go(.invitations); return }
        if active.isEmpty { go(.businessNoSalon); return }
        if active.count == 1 { go(.business(active[0])); return }
        if let last = lastSalonId, let m = active.first(where: { $0.salonId == last }) { go(.business(m)); return }
        go(.salonPicker)
    }

    func signOut() {
        SessionRepo.signOut()
        session = nil
        UserDefaults.standard.removeObject(forKey: lastRoleKey)
        go(.roleSelection)
    }
}

struct RootView: View {
    @EnvironmentObject var app: AppState

    var body: some View {
        Group {
            switch app.screen {
            case .setupRequired: SetupRequiredView()
            case .roleSelection: RoleSelectionView()
            case .phone(let role): PhoneEntryView(role: role)
            case .otp(let role, let id, let phone): OtpView(role: role, verificationId: id, phone: phone)
            case .gate(let role, let skip): SessionGateView(role: role, skipAdmin: skip)
            case .customerRegister: CustomerRegisterView()
            case .customerHome: CustomerHomeView()
            case .invitations: InvitationsView()
            case .salonPicker: SalonPickerView()
            case .businessNoSalon: ComingSoonView(
                title: "Henüz bir salona bağlı değilsiniz",
                message: "Bir salonun sahibi sizi uzman olarak davet ettiğinde davet burada görünür. Kendi salonunuzu kaydetmek şimdilik Android uygulamasından yapılıyor; kayıttan sonra iPhone'dan yönetebilirsiniz."
            )
            case .business(let m): BusinessHomeView(membership: m)
            case .adminComingSoon: ComingSoonView(
                title: "Moderatör paneli iPhone'a geliyor",
                message: "Salon onayı, salon kaldırma ve yorum moderasyonu şimdilik Android uygulamasında."
            )
            }
        }
        .transition(.opacity)
    }
}

struct SetupRequiredView: View {
    var body: some View {
        VStack(spacing: Spacing.l) {
            BrandLogo(size: 96)
            Text("Firebase yapılandırılmadı").font(.title2.bold()).foregroundStyle(Palette.text)
            Text("Bu derleme GoogleService-Info.plist olmadan oluşturuldu. Uygulamanın çalışması için Firebase iOS yapılandırması gerekir (ios/README.md).")
                .font(.subheadline).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .screenBackground()
    }
}

struct ComingSoonView: View {
    @EnvironmentObject var app: AppState
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: Spacing.l) {
            BrandLogo(size: 88)
            Text(title).font(.title3.bold()).foregroundStyle(Palette.text).multilineTextAlignment(.center)
            Text(message).font(.subheadline).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
            PrimaryButton(title: "Müşteri olarak devam et") { app.go(.gate(.customer, skipAdmin: true)) }
            Button("Çıkış yap") { app.signOut() }.buttonStyle(SecondaryButtonStyle())
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .screenBackground()
    }
}
