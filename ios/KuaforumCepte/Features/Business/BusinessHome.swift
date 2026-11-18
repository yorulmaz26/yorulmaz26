import SwiftUI

enum BizRoute: Hashable {
    case appointment(String)
    case walkIn
    case services
    case schedule
    case team
    case share
}

/// Kuaför paneli: Randevular · Raporlar · Salonum (Android ile aynı yapı).
struct BusinessHomeView: View {
    @EnvironmentObject var app: AppState
    let membership: Membership
    @State private var tab = 0
    @State private var path1 = NavigationPath()
    @State private var path3 = NavigationPath()

    var isOwner: Bool { membership.role == "OWNER" }

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack(path: $path1) {
                BizAppointmentsView(membership: membership)
                    .navigationDestination(for: BizRoute.self) { destination($0) }
            }
            .tabItem { Label("Randevular", systemImage: "calendar") }.tag(0)

            NavigationStack { ReportsView(membership: membership) }
                .tabItem { Label("Raporlar", systemImage: "chart.bar") }.tag(1)

            NavigationStack(path: $path3) {
                SalonumView(membership: membership)
                    .navigationDestination(for: BizRoute.self) { destination($0) }
            }
            .tabItem { Label("Salonum", systemImage: "storefront") }.tag(2)
        }
    }

    @ViewBuilder private func destination(_ r: BizRoute) -> some View {
        switch r {
        case .appointment(let id): BizAppointmentDetailView(appointmentId: id, membership: membership)
        case .walkIn: WalkInView(membership: membership)
        case .services: ServicesView(membership: membership)
        case .schedule: ScheduleView(membership: membership)
        case .team: TeamView(membership: membership)
        case .share: ShareSalonView(membership: membership)
        }
    }
}

/// Üst çubuktaki hesap menüsü (salon değiştir, müşteri tarafı, çıkış).
struct BizAccountMenu: View {
    @EnvironmentObject var app: AppState
    var body: some View {
        Menu {
            if (app.session?.memberships.filter(\.isActive).count ?? 0) > 1 {
                Button { app.go(.salonPicker) } label: { Label("Salon değiştir", systemImage: "arrow.left.arrow.right") }
            }
            Button { app.go(.gate(.customer, skipAdmin: true)) } label: { Label("Müşteri olarak devam et", systemImage: "person") }
            Button(role: .destructive) { app.signOut() } label: { Label("Çıkış yap", systemImage: "rectangle.portrait.and.arrow.right") }
        } label: { Image(systemName: "person.crop.circle") }
    }
}

// MARK: - Randevular

struct BizAppointmentsView: View {
    let membership: Membership
    enum Range: String, CaseIterable { case today = "Bugün", tomorrow = "Yarın", week = "7 gün", month = "30 gün" }
    @State private var range: Range = .today
    @State private var onlyMine = false
    @State private var items: [Appointment] = []
    @State private var loading = true
    @State private var error: String?
    @State private var now = Date()

    var body: some View {
        List {
            Section {
                Picker("Aralık", selection: $range) { ForEach(Range.allCases, id: \.self) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented)
                if membership.role == "OWNER" { Toggle("Yalnızca benim randevularım", isOn: $onlyMine) }
            }
            let pending = items.filter { $0.effectiveStatus(now) == .pending }
            if !pending.isEmpty {
                Section("Onay bekleyen (\(pending.count))") { ForEach(pending) { row($0) } }
            }
            Section("Randevular") {
                if loading { ProgressView().frame(maxWidth: .infinity) }
                else if let error { Text(error).foregroundStyle(Brand.cancel) }
                else if items.isEmpty { EmptyStateView(systemImage: "calendar", title: "Bu aralıkta randevu yok") }
                ForEach(items.filter { $0.effectiveStatus(now) != .pending }) { row($0) }
            }
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .navigationTitle(membership.salonName)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) { BizAccountMenu() }
            ToolbarItem(placement: .navigationBarTrailing) { NavigationLink(value: BizRoute.walkIn) { Label("Elden randevu", systemImage: "person.badge.plus") } }
        }
        .task(id: "\(range.rawValue)-\(onlyMine)") { await observe() }
    }

    private func row(_ a: Appointment) -> some View {
        NavigationLink(value: BizRoute.appointment(a.id)) {
            HStack(alignment: .top, spacing: Spacing.m) {
                VStack {
                    Text(BusinessTime.time.string(from: a.startAt)).font(.headline)
                    Text(BusinessTime.dayShort.string(from: a.startAt)).font(.caption2).foregroundStyle(Palette.muted)
                }
                .frame(width: 56)
                VStack(alignment: .leading, spacing: 2) {
                    HStack { Text(a.customerName).font(.subheadline.weight(.semibold)); if a.walkIn { Text("Elden").font(.caption2).foregroundStyle(Palette.accent) } }
                    Text(a.items.map(\.name).joined(separator: ", ")).font(.caption).foregroundStyle(Palette.muted).lineLimit(1)
                    if membership.role == "OWNER" { Text(a.staffName).font(.caption).foregroundStyle(Palette.muted) }
                }
                Spacer()
                StatusChip(status: a.effectiveStatus(now))
            }
        }
    }

    private func observe() async {
        let cal = BusinessTime.calendar
        let startOfToday = cal.startOfDay(for: Date())
        let (from, days): (Date, Int) = {
            switch range {
            case .today: return (startOfToday, 1)
            case .tomorrow: return (cal.date(byAdding: .day, value: 1, to: startOfToday)!, 1)
            case .week: return (startOfToday, 7)
            case .month: return (startOfToday, 30)
            }
        }()
        let to = cal.date(byAdding: .day, value: days, to: from)!
        let staff: String? = (membership.role == "OWNER" && !onlyMine) ? nil : SessionRepo.uid
        loading = true
        do {
            for try await snap in BusinessRepo.observeRange(salonId: membership.salonId, staffUid: staff, from: from, to: to) {
                items = snap.documents.compactMap { Appointment($0) }
                now = Date()
                loading = false
                error = nil
            }
        } catch { self.error = ErrorMessages.text(error); loading = false }
    }
}

// MARK: - Randevu detayı (işletme)

struct BizAppointmentDetailView: View {
    let appointmentId: String
    let membership: Membership
    @State private var a: Appointment?
    @State private var busy = false
    @State private var message: String?
    @State private var reasonFor: String?
    @State private var reason = ""
    @State private var confirmComplete = false
    @State private var now = Date()
    private let ticker = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                if let a {
                    VStack(alignment: .leading, spacing: Spacing.s) {
                        HStack { Text(a.number).font(.caption.weight(.semibold)).foregroundStyle(Palette.muted); Spacer(); StatusChip(status: a.effectiveStatus(now)) }
                        Text(a.customerName).font(.title2.bold())
                        if a.walkIn { Text("Elden randevu (müşterinin uygulama hesabı yok)").font(.caption).foregroundStyle(Palette.accent) }
                        if !a.customerPhoneE164.isEmpty { Text(PhoneNumbers.display(a.customerPhoneE164)).font(.subheadline) }
                        Text(a.whenLabel).font(.headline)
                        Text("Uzman: \(a.staffName)").font(.subheadline)
                        Divider()
                        ForEach(a.items, id: \.self) { i in HStack { Text("\(i.name) · \(formatDuration(i.durationMinutes))"); Spacer(); Text(i.price.formatted) }.font(.subheadline) }
                        Divider()
                        if a.discount.value > 0, let l = a.discountLabel { HStack { Text(l); Spacer(); Text("−\(a.discount.formatted)") }.font(.subheadline).foregroundStyle(Palette.accent) }
                        HStack { Text("Toplam").font(.headline); Spacer(); Text(a.finalTotal.formatted).font(.headline) }
                        if let r = a.cancelReason { Text("Gerekçe: \(r)").font(.caption).foregroundStyle(Brand.cancel) }
                    }
                    .foregroundStyle(Palette.text)
                    .card()
                    actions(a)
                } else { ProgressView().frame(maxWidth: .infinity) }
                if let message { InfoBanner(text: message) }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Randevu")
        .navigationBarTitleDisplayMode(.inline)
        .onReceive(ticker) { now = $0 }
        .task {
            do { for try await snap in AppointmentRepo.observe(appointmentId) { a = Appointment(snap) } } catch { message = ErrorMessages.text(error) }
        }
        .alert(reasonFor == "REJECT" ? "Talebi reddet" : "Gerekçeyle iptal et", isPresented: Binding(get: { reasonFor != nil }, set: { if !$0 { reasonFor = nil } })) {
            TextField("Gerekçe (müşteriye iletilir)", text: $reason)
            Button("Gönder", role: .destructive) { if let act = reasonFor { run(act, reason: reason) } }
            Button("Vazgeç", role: .cancel) {}
        }
        .confirmationDialog("İşlem tamamlansın mı?", isPresented: $confirmComplete, titleVisibility: .visible) {
            Button("İşlemi Tamamla") { run("COMPLETE") }
        } message: { Text("Tutar raporlara işlenir ve müşteriye değerlendirme daveti gider.") }
    }

    @ViewBuilder private func actions(_ a: Appointment) -> some View {
        let st = a.effectiveStatus(now)
        switch st {
        case .pending:
            PrimaryButton(title: "Onayla", loading: busy) { run("CONFIRM") }
            Button("Reddet") { reason = ""; reasonFor = "REJECT" }.buttonStyle(SecondaryButtonStyle()).disabled(busy)
        case .confirmed:
            if now >= a.startAt.addingTimeInterval(-15 * 60) { PrimaryButton(title: "Geldi", loading: busy) { run("MARK_ARRIVED") } }
            if now >= a.startAt { Button("Gelmedi") { run("MARK_NO_SHOW") }.buttonStyle(SecondaryButtonStyle()).disabled(busy) }
            if now < a.startAt.addingTimeInterval(-15 * 60) {
                Text("“Geldi” en erken başlangıçtan 15 dakika önce, “Gelmedi” başlangıç saatinden sonra işaretlenebilir.").font(.caption).foregroundStyle(Palette.muted)
            }
            Button("Gerekçeyle İptal Et") { reason = ""; reasonFor = "CANCEL_BY_STAFF" }.buttonStyle(SecondaryButtonStyle()).disabled(busy)
        case .inProgress:
            PrimaryButton(title: "İşlemi Tamamla", loading: busy) { confirmComplete = true }
            Button("Gerekçeyle İptal Et") { reason = ""; reasonFor = "CANCEL_BY_STAFF" }.buttonStyle(SecondaryButtonStyle()).disabled(busy)
        default:
            EmptyView()
        }
        if st.isActive, !a.customerPhoneE164.isEmpty, let url = URL(string: "tel:\(a.customerPhoneE164)") {
            Link(destination: url) { Label("Müşteriyi Ara", systemImage: "phone") }.buttonStyle(SecondaryButtonStyle())
        }
    }

    private func run(_ action: String, reason: String? = nil) {
        if (action == "REJECT" || action == "CANCEL_BY_STAFF") && (reason ?? "").trimmingCharacters(in: .whitespaces).count < 3 {
            message = ErrorMessages.messages["REASON_REQUIRED"]; return
        }
        busy = true
        message = nil
        Task {
            do {
                let st = try await BusinessRepo.transition(appointmentId, action: action, reason: reason)
                message = st == "EXPIRED" ? "Talebin onay süresi dolmuştu; otomatik düştü." : "İşlem kaydedildi; müşteri bilgilendirildi."
            } catch { message = ErrorMessages.text(error) }
            busy = false
        }
    }
}

// MARK: - Elden randevu

struct WalkInView: View {
    let membership: Membership
    @Environment(\.dismiss) private var dismiss
    @State private var staff: [StaffMember] = []
    @State private var staffUid: String?
    @State private var services: [SalonService] = []
    @State private var selected: Set<String> = []
    @State private var date = Date()
    @State private var name = ""
    @State private var phone = ""
    @State private var busy = false
    @State private var error: String?
    @State private var key = UUID().uuidString

    var body: some View {
        Form {
            Section { InfoBanner(text: "Telefonla arayan veya salona gelen, uygulaması olmayan müşteriler için. Kayıt doğrudan onaylanır ve uzmanın takviminde o saat kapanır.") }
            if membership.role == "OWNER" {
                Picker("Uzman", selection: $staffUid) {
                    Text("Seçin").tag(String?.none)
                    ForEach(staff) { Text($0.fullName).tag(String?.some($0.id)) }
                }
            }
            Section("Hizmetler") {
                if services.isEmpty { Text("Seçilen uzmanın aktif hizmeti yok.").foregroundStyle(Palette.muted) }
                ForEach(services) { s in
                    Button {
                        if selected.contains(s.id) { selected.remove(s.id) } else { selected.insert(s.id) }
                    } label: {
                        HStack {
                            Image(systemName: selected.contains(s.id) ? "checkmark.circle.fill" : "circle").foregroundStyle(Palette.accent)
                            Text("\(s.name) · \(formatDuration(s.durationMinutes))").foregroundStyle(Palette.text)
                            Spacer()
                            Text(s.price.formatted).foregroundStyle(Palette.muted)
                        }
                    }
                }
            }
            Section("Zaman") {
                DatePicker("Başlangıç", selection: $date, in: Date().addingTimeInterval(-3600)..., displayedComponents: [.date, .hourAndMinute])
                    .environment(\.timeZone, BusinessTime.zone)
                    .environment(\.locale, BusinessTime.locale)
            }
            Section("Müşteri") {
                TextField("Ad soyad", text: $name)
                TextField("Telefon (isteğe bağlı)", text: $phone).keyboardType(.phonePad)
            }
            if let error { Text(error).foregroundStyle(Brand.cancel) }
            Button { submit() } label: { if busy { ProgressView() } else { Text("Randevuyu Kaydet").bold() } }
                .disabled(busy || selected.isEmpty || staffUid == nil)
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .navigationTitle("Elden Randevu")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if membership.role == "OWNER" { staff = (try? await SalonRepo.staff(membership.salonId)) ?? [] }
            if staffUid == nil { staffUid = SessionRepo.uid }
        }
        .onChange(of: staffUid) { uid in
            selected = []
            Task { services = uid == nil ? [] : ((try? await SalonRepo.services(membership.salonId, staffUid: uid!)) ?? []) }
        }
    }

    private func submit() {
        guard NameRules.isValid(name) else { error = "Müşterinin adını ve soyadını yazın."; return }
        let p = phone.trimmingCharacters(in: .whitespaces)
        if !p.isEmpty && PhoneNumbers.normalizeTR(p) == nil { error = ErrorMessages.messages["INVALID_PHONE"]; return }
        // 5 dakikalık ızgaraya yuvarla
        let cal = BusinessTime.calendar
        var comps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        comps.minute = ((comps.minute ?? 0) / 5) * 5
        let start = cal.date(from: comps) ?? date
        busy = true
        error = nil
        Task {
            do {
                _ = try await BusinessRepo.walkIn(salonId: membership.salonId, staffUid: staffUid!, serviceIds: Array(selected), startAt: start,
                                                  name: name.trimmingCharacters(in: .whitespaces), phone: p.isEmpty ? nil : p, key: key)
                dismiss()
            } catch { self.error = ErrorMessages.text(error) }
            busy = false
        }
    }
}
