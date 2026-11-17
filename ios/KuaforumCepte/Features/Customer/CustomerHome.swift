import SwiftUI

/// Rezervasyon akışında taşınan seçim (uzman + hizmetler).
struct BookingDraft: Hashable {
    let salonId: String
    let salonName: String
    let staffUid: String
    let staffName: String
    let services: [SalonService]
    var serviceIds: [String] { services.map(\.id) }
    var total: Kurus { services.reduce(Kurus.zero) { $0 + $1.price } }
    var minutes: Int { services.reduce(0) { $0 + $1.durationMinutes } }
}

enum CustomerRoute: Hashable {
    case salon(String)
    case slots(BookingDraft)
    case summary(BookingDraft, Slot, forSelf: Bool)
    case appointment(String)
    case review(String)
}

struct CustomerHomeView: View {
    @EnvironmentObject var app: AppState
    @State private var tab = 0
    @State private var bookPath = NavigationPath()
    @State private var minePath = NavigationPath()

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack(path: $bookPath) {
                SalonListView()
                    .navigationDestination(for: CustomerRoute.self) { route in destination(route, path: $bookPath) }
            }
            .tabItem { Label("Randevu Al", systemImage: "scissors") }
            .tag(0)

            NavigationStack(path: $minePath) {
                MyAppointmentsView()
                    .navigationDestination(for: CustomerRoute.self) { route in destination(route, path: $minePath) }
            }
            .tabItem { Label("Randevularım", systemImage: "calendar") }
            .tag(1)

            NavigationStack { AccountView() }
                .tabItem { Label("Hesap", systemImage: "person.crop.circle") }
                .tag(2)
        }
    }

    @ViewBuilder
    private func destination(_ route: CustomerRoute, path: Binding<NavigationPath>) -> some View {
        switch route {
        case .salon(let id): SalonDetailView(salonId: id)
        case .slots(let draft): SlotPickerView(draft: draft)
        case .summary(let draft, let slot, let forSelf):
            BookingSummaryView(draft: draft, slot: slot, forSelf: forSelf) { appointmentId in
                // Başarılı rezervasyon: akışı kapat, randevu biletini aç.
                bookPath = NavigationPath()
                minePath = NavigationPath()
                minePath.append(CustomerRoute.appointment(appointmentId))
                tab = 1
            }
        case .appointment(let id): AppointmentDetailView(appointmentId: id)
        case .review(let id): ReviewView(appointmentId: id)
        }
    }
}

// MARK: - Salon listesi

struct SalonListView: View {
    @State private var salons: [Salon] = []
    @State private var loading = true
    @State private var error: String?
    @State private var query = ""
    @State private var code = ""
    @State private var codeError: String?
    @State private var openId: String?

    var body: some View {
        List {
            Section {
                HStack(spacing: Spacing.s) {
                    TextField("Salon kodu (6 hane)", text: $code).keyboardType(.numberPad)
                    Button("Aç") { openByCode() }.disabled(code.count != 6)
                }
                if let codeError { Text(codeError).font(.caption).foregroundStyle(Brand.cancel) }
            } header: { Text("Kod veya QR ile") }

            Section {
                if loading { ProgressView().frame(maxWidth: .infinity) }
                else if let error { Text(error).foregroundStyle(Brand.cancel) }
                else if filtered.isEmpty { EmptyStateView(systemImage: "magnifyingglass", title: "Salon bulunamadı", message: "Aramayı değiştirin veya salon kodunu girin.") }
                ForEach(filtered) { s in
                    NavigationLink(value: CustomerRoute.salon(s.id)) { SalonRow(salon: s) }
                }
            } header: { Text("Salonlar") }
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .searchable(text: $query, prompt: "Salon, il veya ilçe ara")
        .navigationTitle("Randevu Al")
        .navigationDestination(isPresented: Binding(get: { openId != nil }, set: { if !$0 { openId = nil } })) {
            if let openId { SalonDetailView(salonId: openId) }
        }
        .refreshable { await load() }
        .task { if salons.isEmpty { await load() } }
    }

    private var filtered: [Salon] {
        let k = SearchKey.of(query)
        guard !k.isEmpty else { return salons }
        return salons.filter { [$0.name, $0.city, $0.district].contains { SearchKey.of($0).contains(k) } }
    }

    private func load() async {
        loading = salons.isEmpty
        do { salons = try await SalonRepo.directory(); error = nil } catch { self.error = ErrorMessages.text(error) }
        loading = false
    }

    private func openByCode() {
        codeError = nil
        Task {
            do {
                if let id = try await SalonRepo.resolve(code: code) { openId = id } else { codeError = "Bu kodla salon bulunamadı." }
            } catch { codeError = ErrorMessages.text(error) }
        }
    }
}

struct SalonRow: View {
    let salon: Salon
    var body: some View {
        HStack(spacing: Spacing.m) {
            AsyncImage(url: URL(string: salon.logoUrl)) { img in img.resizable().scaledToFill() } placeholder: {
                ZStack { Palette.cardHigh; Image(systemName: "scissors").foregroundStyle(Palette.accent) }
            }
            .frame(width: 52, height: 52).clipShape(RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text(salon.name).font(.headline).foregroundStyle(Palette.text)
                Text("\(salon.type?.label ?? "") · \(salon.district) / \(salon.city)").font(.caption).foregroundStyle(Palette.muted)
                HStack(spacing: 4) {
                    Image(systemName: "star.fill").font(.caption2).foregroundStyle(Brand.gold)
                    Text("\(salon.rating.label) (\(salon.rating.count))").font(.caption).foregroundStyle(Palette.muted)
                }
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Hesap

struct AccountView: View {
    @EnvironmentObject var app: AppState
    @State private var confirmSignOut = false

    var body: some View {
        List {
            Section {
                HStack(spacing: Spacing.m) {
                    BrandLogo(size: 48)
                    VStack(alignment: .leading) {
                        Text(app.session?.fullName ?? "").font(.headline)
                        Text(PhoneNumbers.display(SessionRepo.phone ?? "")).font(.subheadline).foregroundStyle(Palette.muted)
                    }
                }
            }
            Section {
                Button { app.go(.gate(.business, skipAdmin: false)) } label: { Label("Kuaför / Uzman paneline geç", systemImage: "scissors") }
            }
            Section {
                Button(role: .destructive) { confirmSignOut = true } label: { Label("Çıkış yap", systemImage: "rectangle.portrait.and.arrow.right") }
            } footer: {
                Text("Hesap silme, ad/telefon değişikliği ve takvim aboneliği şimdilik Android uygulamasında.")
            }
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .navigationTitle("Hesap")
        .confirmationDialog("Çıkış yapılsın mı?", isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Çıkış yap", role: .destructive) { app.signOut() }
        }
    }
}
