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
        case businessComingSoon
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
            case .businessComingSoon: ComingSoonView(
                title: "Kuaför paneli iPhone'a geliyor",
                message: "Randevu yönetimi, hizmetler ve raporlar iPhone uygulamasının bir sonraki sürümünde. Şimdilik kuaför işlemleri için Android uygulamasını kullanabilirsiniz."
            )
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
