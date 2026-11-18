import SwiftUI
import CoreImage.CIFilterBuiltins

// MARK: - Raporlar

struct ReportsView: View {
    let membership: Membership
    enum Period: String, CaseIterable { case DAY = "Gün", WEEK = "Hafta", MONTH = "Ay" }
    @State private var period: Period = .DAY
    @State private var report: Report?
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        List {
            Section { Picker("Dönem", selection: $period) { ForEach(Period.allCases, id: \.self) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented) }
            if loading { ProgressView().frame(maxWidth: .infinity) }
            if let error { Text(error).foregroundStyle(Brand.cancel) }
            if let r = report {
                if let s = r.salon { Section("Salon toplamı") { metrics(s) } }
                Section(membership.role == "OWNER" ? "Uzman bazında" : "Benim işlemlerim") {
                    if r.staff.isEmpty { Text("Bu dönemde kayıt yok.").foregroundStyle(Palette.muted) }
                    ForEach(r.staff) { s in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack { Text(s.name + (s.role == "OWNER" ? " (Sahip)" : "")).font(.subheadline.weight(.semibold)); Spacer(); Text(s.line.amount.formatted).font(.subheadline.weight(.semibold)) }
                            Text("\(s.line.completedCount) işlem" + (s.line.commission.value != 0 ? " · prim \(s.line.commission.formatted)" : "")).font(.caption).foregroundStyle(Palette.muted)
                        }
                    }
                }
            }
            Section {
                Text("Yalnızca “Tamamlandı” olarak kapatılan randevular sayılır. Tutarlar gerçekleşen hizmet bedelidir; ödeme uygulama üzerinden alınmaz. Dönemler İstanbul saatine göre yenilenir.")
                    .font(.caption).foregroundStyle(Palette.muted)
            }
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .navigationTitle("Raporlar")
        .toolbar { ToolbarItem(placement: .navigationBarLeading) { BizAccountMenu() } }
        .task(id: period) { await load() }
        .refreshable { await load() }
    }

    private func metrics(_ l: ReportLine) -> some View {
        HStack(spacing: Spacing.m) {
            metric("Tamamlanan işlem", "\(l.completedCount)")
            metric("Gerçekleşen bedel", l.amount.formatted)
        }
    }

    private func metric(_ t: String, _ v: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(t).font(.caption).foregroundStyle(Palette.muted)
            Text(v).font(.title3.bold()).foregroundStyle(Palette.text)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func load() async {
        loading = true
        do { report = try await BusinessRepo.report(salonId: membership.salonId, periodType: "\(period)", anchorDate: BusinessTime.today()); error = nil }
        catch { self.error = ErrorMessages.text(error) }
        loading = false
    }
}

// MARK: - Salonum (gruplu menü)

struct SalonumView: View {
    let membership: Membership
    var isOwner: Bool { membership.role == "OWNER" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                section("Müşteri kazan", "Salonunuzu paylaşın ve görünür olun") {
                    tile("qrcode", "Link ve QR Kod", "WhatsApp, yazdır", .share)
                }
                section("Benim çalışma düzenim", "Sadece sizin takviminiz ve hizmetleriniz") {
                    tile("scissors", "Hizmetlerim", "Fiyat ve süre", .services)
                    tile("clock", "Çalışma Saatleri", "Vardiya, mola, izin", .schedule)
                }
                if isOwner {
                    section("Salon yönetimi", "Tüm salonu etkileyen ayarlar") {
                        tile("person.3", "Ekibim", "Uzmanlar ve davet", .team)
                    }
                }
                Text("Dükkan bilgileri, fotoğraflar, kampanyalar, kapalı günler ve salon devri şimdilik Android uygulamasından yönetilir.")
                    .font(.caption).foregroundStyle(Palette.muted)
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Salonum")
        .toolbar { ToolbarItem(placement: .navigationBarLeading) { BizAccountMenu() } }
    }

    private func section<C: View>(_ title: String, _ sub: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text(title).font(.headline).foregroundStyle(Palette.text)
            Text(sub).font(.caption).foregroundStyle(Palette.muted)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: Spacing.s), GridItem(.flexible(), spacing: Spacing.s)], spacing: Spacing.s) { content() }
        }
    }

    private func tile(_ icon: String, _ title: String, _ sub: String, _ route: BizRoute) -> some View {
        NavigationLink(value: route) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                ZStack { Circle().fill(Palette.accent.opacity(0.18)); Image(systemName: icon).foregroundStyle(Palette.accent) }.frame(width: 40, height: 40)
                Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.text)
                Text(sub).font(.caption).foregroundStyle(Palette.muted)
            }
            .frame(maxWidth: .infinity, minHeight: 104, alignment: .topLeading)
            .padding(Spacing.m)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.outline))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Hizmetlerim

struct ServiceEdit: Identifiable {
    var id: String { serviceId ?? "new" }
    var serviceId: String?
    var name = ""
    var description = ""
    var price = ""
    var minutes = 30
    var active = true
}

struct ServicesView: View {
    let membership: Membership
    @State private var items: [(SalonService, Bool)] = []
    @State private var editing: ServiceEdit?
    @State private var error: String?

    var body: some View {
        List {
            if let error { Text(error).foregroundStyle(Brand.cancel) }
            if items.isEmpty { EmptyStateView(systemImage: "scissors", title: "Henüz hizmet yok", message: "Müşterilerin randevu alabilmesi için en az bir hizmet ekleyin.") }
            ForEach(items, id: \.0.id) { (s, active) in
                Button {
                    editing = ServiceEdit(serviceId: s.id, name: s.name, description: s.description, price: String(format: "%.2f", Double(s.price.value) / 100).replacingOccurrences(of: ".", with: ","), minutes: s.durationMinutes, active: active)
                } label: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(s.name).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.text)
                            Text(formatDuration(s.durationMinutes) + (active ? "" : " · pasif")).font(.caption).foregroundStyle(Palette.muted)
                        }
                        Spacer()
                        Text(s.price.formatted).foregroundStyle(Palette.text)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .navigationTitle("Hizmetlerim")
        .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button { editing = ServiceEdit() } label: { Image(systemName: "plus") } } }
        .sheet(item: $editing) { e in ServiceEditor(membership: membership, initial: e) { Task { await load() } } }
        .task { await load() }
    }

    private func load() async {
        guard let uid = SessionRepo.uid else { return }
        do { items = try await BusinessRepo.allMyServicesRaw(salonId: membership.salonId, uid: uid); error = nil } catch { self.error = ErrorMessages.text(error) }
    }
}

struct ServiceEditor: View {
    let membership: Membership
    @State var initial: ServiceEdit
    let onSaved: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var busy = false
    @State private var error: String?
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            Form {
                TextField("Hizmet adı", text: $initial.name)
                TextField("Açıklama (isteğe bağlı)", text: $initial.description)
                TextField("Fiyat (TL)", text: $initial.price).keyboardType(.decimalPad)
                Stepper("Süre: \(formatDuration(initial.minutes))", value: $initial.minutes, in: 5...600, step: 5)
                Toggle("Aktif (randevuya açık)", isOn: $initial.active)
                if let error { Text(error).foregroundStyle(Brand.cancel) }
                if initial.serviceId != nil {
                    Button("Hizmeti sil", role: .destructive) { confirmDelete = true }
                }
            }
            .navigationTitle(initial.serviceId == nil ? "Yeni hizmet" : "Hizmeti düzenle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Kaydet") { save() }.disabled(busy) }
            }
            .confirmationDialog("Hizmet silinsin mi?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Sil", role: .destructive) { delete() }
            } message: { Text("Geçmiş randevu kayıtları etkilenmez.") }
        }
    }

    private func save() {
        let name = initial.name.trimmingCharacters(in: .whitespaces)
        let priceText = initial.price.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: ".")
        guard (2...60).contains(name.count) else { error = "Hizmet adı 2–60 karakter olmalı."; return }
        guard let price = Double(priceText), price >= 1, price <= 1_000_000 else { error = "Geçerli bir fiyat girin (en az 1 TL)."; return }
        guard let uid = SessionRepo.uid else { return }
        busy = true
        Task {
            do {
                try await BusinessRepo.saveService(salonId: membership.salonId, uid: uid, id: initial.serviceId, name: name,
                                                   description: String(initial.description.prefix(500)), price: Kurus(Int64((price * 100).rounded())),
                                                   minutes: initial.minutes, active: initial.active)
                onSaved(); dismiss()
            } catch { self.error = ErrorMessages.text(error) }
            busy = false
        }
    }

    private func delete() {
        guard let id = initial.serviceId else { return }
        Task {
            do { try await BusinessRepo.deleteService(salonId: membership.salonId, id: id); onSaved(); dismiss() } catch { self.error = ErrorMessages.text(error) }
        }
    }
}

// MARK: - Çalışma saatleri

struct ScheduleView: View {
    let membership: Membership
    @State private var plan: [Int: DayPlan] = [:]
    @State private var busy = false
    @State private var message: String?
    @State private var conflicts: [ScheduleConflict] = []
    @State private var askConfirm = false
    private let names = ["", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"]

    var body: some View {
        Form {
            Section { Text("Her gün için çalışma saatinizi ve isterseniz bir molayı girin (SS:DD). Kapalı günler haftalık izin sayılır.").font(.caption).foregroundStyle(Palette.muted) }
            ForEach(1...7, id: \.self) { d in
                Section(names[d]) {
                    Toggle("Çalışıyorum", isOn: Binding(get: { !(plan[d]?.off ?? true) }, set: { plan[d]?.off = !$0 }))
                    if plan[d]?.off == false {
                        timeRow("Başlangıç", d, \.start)
                        timeRow("Bitiş", d, \.end)
                        timeRow("Mola başlangıç", d, \.breakStart)
                        timeRow("Mola bitiş", d, \.breakEnd)
                    }
                }
            }
            if let message { Section { Text(message).foregroundStyle(Palette.text) } }
            Button { save(acknowledge: false) } label: { if busy { ProgressView() } else { Text("Kaydet").bold() } }.disabled(busy || plan.isEmpty)
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .navigationTitle("Çalışma Saatleri")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            guard let uid = SessionRepo.uid else { return }
            do { plan = try await BusinessRepo.loadSchedule(salonId: membership.salonId, uid: uid) } catch { message = ErrorMessages.text(error) }
        }
        .alert("Etkilenen randevular var", isPresented: $askConfirm) {
            Button("Yine de uygula", role: .destructive) { save(acknowledge: true) }
            Button("Vazgeç", role: .cancel) {}
        } message: {
            Text(conflicts.prefix(5).map { "\($0.localDate) \($0.start) · \($0.customerName)" }.joined(separator: "\n")
                 + "\nBu randevular “ilgilenilmeli” olarak işaretlenir; saatini değiştirmeniz veya iptal etmeniz gerekir.")
        }
    }

    private func timeRow(_ label: String, _ d: Int, _ kp: WritableKeyPath<DayPlan, String>) -> some View {
        HStack {
            Text(label)
            Spacer()
            TextField("SS:DD", text: Binding(get: { plan[d]?[keyPath: kp] ?? "" }, set: { plan[d]?[keyPath: kp] = String($0.prefix(5)) }))
                .multilineTextAlignment(.trailing).keyboardType(.numbersAndPunctuation).frame(width: 80)
        }
    }

    private func save(acknowledge: Bool) {
        busy = true
        message = nil
        Task {
            do {
                if !acknowledge {
                    let preview = try await BusinessRepo.saveSchedule(salonId: membership.salonId, plan: plan, dryRun: true, acknowledge: false)
                    if !preview.conflicts.isEmpty { conflicts = preview.conflicts; askConfirm = true; busy = false; return }
                }
                _ = try await BusinessRepo.saveSchedule(salonId: membership.salonId, plan: plan, dryRun: false, acknowledge: acknowledge)
                message = "Çalışma saatleriniz kaydedildi; müsaitlik hemen güncellendi."
            } catch { message = ErrorMessages.text(error) }
            busy = false
        }
    }
}

// MARK: - Ekibim

struct TeamView: View {
    let membership: Membership
    @State private var members: [(StaffMember, String, String)] = []
    @State private var inviting = false
    @State private var error: String?

    var body: some View {
        List {
            if let error { Text(error).foregroundStyle(Brand.cancel) }
            ForEach(members, id: \.0.id) { (m, role, status) in
                VStack(alignment: .leading, spacing: 2) {
                    Text(m.fullName + (role == "OWNER" ? " (Sahip)" : "")).font(.subheadline.weight(.semibold))
                    Text("\(m.specialty) · \(status == "ACTIVE" ? "aktif" : status == "INACTIVE" ? "pasif" : "ayrıldı")").font(.caption).foregroundStyle(Palette.muted)
                }
            }
            Section { Text("Uzman düzenleme, pasife alma ve prim oranı şimdilik Android uygulamasında.").font(.caption).foregroundStyle(Palette.muted) }
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .navigationTitle("Ekibim")
        .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button { inviting = true } label: { Label("Davet et", systemImage: "person.badge.plus") } } }
        .sheet(isPresented: $inviting) { InviteSheet(salonId: membership.salonId) }
        .task { await load() }
    }

    private func load() async {
        do { members = try await BusinessRepo.members(salonId: membership.salonId) } catch { self.error = ErrorMessages.text(error) }
    }
}

struct InviteSheet: View {
    let salonId: String
    @Environment(\.dismiss) private var dismiss
    @State private var first = ""
    @State private var last = ""
    @State private var specialty = ""
    @State private var phone = ""
    @State private var busy = false
    @State private var error: String?
    @State private var done = false
    @State private var key = UUID().uuidString

    var body: some View {
        NavigationStack {
            Form {
                if done {
                    Text("Davet gönderildi. Uzman bu numarayla “Kuaför / Uzman Girişi” yaptığında daveti görür.").foregroundStyle(Palette.text)
                } else {
                    TextField("Ad", text: $first)
                    TextField("Soyad", text: $last)
                    TextField("Uzmanlık (ör. Saç Kesimi)", text: $specialty)
                    TextField("Cep telefonu", text: $phone).keyboardType(.phonePad)
                    if let error { Text(error).foregroundStyle(Brand.cancel) }
                }
            }
            .navigationTitle("Uzman Davet Et")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(done ? "Kapat" : "Vazgeç") { dismiss() } }
                if !done { ToolbarItem(placement: .confirmationAction) { Button("Gönder") { send() }.disabled(busy) } }
            }
        }
    }

    private func send() {
        guard NameRules.isValid(first), NameRules.isValid(last) else { error = ErrorMessages.messages["INVALID_NAME"]; return }
        guard (2...60).contains(specialty.trimmingCharacters(in: .whitespaces).count) else { error = "Uzmanlık alanını yazın (2–60 karakter)."; return }
        guard let e164 = PhoneNumbers.normalizeTR(phone, mobileOnly: true) else { error = ErrorMessages.messages["INVALID_PHONE"]; return }
        busy = true
        Task {
            do {
                try await BusinessRepo.invite(salonId: salonId, firstName: first.trimmingCharacters(in: .whitespaces), lastName: last.trimmingCharacters(in: .whitespaces),
                                              specialty: specialty.trimmingCharacters(in: .whitespaces), phone: e164, key: key)
                done = true
            } catch { self.error = ErrorMessages.text(error) }
            busy = false
        }
    }
}

// MARK: - Link ve QR

struct ShareSalonView: View {
    let membership: Membership
    @State private var salon: Salon?

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.l) {
                if let s = salon {
                    let link = BusinessRepo.salonLink(code: s.code)
                    Text(s.name).font(.title3.bold()).foregroundStyle(Palette.text)
                    if let img = qr(link) {
                        Image(uiImage: img).interpolation(.none).resizable().scaledToFit().frame(width: 220, height: 220)
                            .padding(Spacing.l).background(Color.white, in: RoundedRectangle(cornerRadius: 16))
                    }
                    Text("Salon kodu: \(s.code)").font(.headline).foregroundStyle(Palette.text)
                    Text(link).font(.footnote).foregroundStyle(Palette.muted).textSelection(.enabled)
                    ShareLink(item: URL(string: link)!, message: Text("\(s.name) için online randevu: \(link)")) {
                        Label("Paylaş (WhatsApp, SMS…)", systemImage: "square.and.arrow.up")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    InfoBanner(text: "Müşteri linke veya QR koda dokunduğunda uygulamada salon sayfanız açılır; uygulama yoksa indirme sayfasına yönlendirilir.")
                } else { ProgressView() }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Link ve QR Kod")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            do { for try await snap in SalonRepo.observe(membership.salonId) { salon = Salon(snap) } } catch {}
        }
    }

    private func qr(_ text: String) -> UIImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(text.utf8)
        f.correctionLevel = "M"
        guard let out = f.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)),
              let cg = CIContext().createCGImage(out, from: out.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}
