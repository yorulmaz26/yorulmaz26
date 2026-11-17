import SwiftUI

// MARK: - Randevularım

struct MyAppointmentsView: View {
    @State private var items: [Appointment] = []
    @State private var loading = true
    @State private var error: String?
    @State private var now = Date()

    var body: some View {
        List {
            if loading { ProgressView().frame(maxWidth: .infinity) }
            else if let error { Text(error).foregroundStyle(Brand.cancel) }
            else if items.isEmpty {
                EmptyStateView(systemImage: "calendar", title: "Henüz randevunuz yok", message: "Randevu Al sekmesinden salon seçip ilk randevunuzu oluşturun.")
            }
            let active = items.filter { $0.effectiveStatus(now).isActive }
            let past = items.filter { !$0.effectiveStatus(now).isActive }
            if !active.isEmpty {
                Section("Yaklaşan") { ForEach(active) { a in NavigationLink(value: CustomerRoute.appointment(a.id)) { AppointmentRow(a: a, now: now) } } }
            }
            if !past.isEmpty {
                Section("Geçmiş") { ForEach(past) { a in NavigationLink(value: CustomerRoute.appointment(a.id)) { AppointmentRow(a: a, now: now) } } }
            }
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .navigationTitle("Randevularım")
        .task { await observe() }
    }

    private func observe() async {
        guard let uid = SessionRepo.uid else { return }
        do {
            for try await snap in AppointmentRepo.observeMine(uid: uid) {
                items = snap.documents.compactMap { Appointment($0) }
                now = Date()
                loading = false
            }
        } catch { self.error = ErrorMessages.text(error); loading = false }
    }
}

struct AppointmentRow: View {
    let a: Appointment
    let now: Date
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack { Text(a.salonName).font(.headline); Spacer(); StatusChip(status: a.effectiveStatus(now)) }
            Text("\(BusinessTime.dayLong.string(from: a.startAt)) · \(BusinessTime.time.string(from: a.startAt))").font(.subheadline).foregroundStyle(Palette.muted)
            Text("\(a.staffName) · \(a.items.map(\.name).joined(separator: ", "))").font(.caption).foregroundStyle(Palette.muted).lineLimit(1)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Randevu bileti

struct AppointmentDetailView: View {
    let appointmentId: String
    @State private var a: Appointment?
    @State private var error: String?
    @State private var busy = false
    @State private var askCancel = false
    @State private var cancelReason = ""
    @State private var contacts: [Contact]?
    @State private var message: String?
    @State private var now = Date()
    private let ticker = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                if let a {
                    ticket(a)
                    actions(a)
                } else if let error { Text(error).foregroundStyle(Brand.cancel) }
                else { ProgressView().frame(maxWidth: .infinity) }
                if let message { InfoBanner(text: message) }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Randevu Bileti")
        .navigationBarTitleDisplayMode(.inline)
        .onReceive(ticker) { now = $0 }
        .task { await observe() }
        .alert("Randevu iptal edilsin mi?", isPresented: $askCancel) {
            TextField("Gerekçe (isteğe bağlı)", text: $cancelReason)
            Button("İptal Et", role: .destructive) { cancel() }
            Button("Vazgeç", role: .cancel) {}
        } message: { Text("Salon ve uzman bilgilendirilir; saat yeniden müsait olur.") }
    }

    private func ticket(_ a: Appointment) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            HStack { Text(a.number).font(.caption.weight(.semibold)).foregroundStyle(Palette.muted); Spacer(); StatusChip(status: a.effectiveStatus(now)) }
            Text(a.salonName).font(.title2.bold())
            Text(a.salonAddress).font(.footnote).foregroundStyle(Palette.muted)
            Divider()
            Text(a.whenLabel).font(.headline)
            Text("Uzman: \(a.staffName)").font(.subheadline)
            if let g = a.guestName { Text("Hizmeti alacak: \(g)").font(.subheadline) }
            ForEach(a.items, id: \.self) { i in
                HStack { Text("\(i.name) · \(formatDuration(i.durationMinutes))").font(.subheadline); Spacer(); Text(i.price.formatted).font(.subheadline) }
            }
            Divider()
            if a.discount.value > 0, let l = a.discountLabel {
                HStack { Text(l); Spacer(); Text("−\(a.discount.formatted)") }.font(.subheadline).foregroundStyle(Palette.accent)
            }
            HStack { Text("Toplam · \(formatDuration(a.totalMinutes))").font(.headline); Spacer(); Text(a.finalTotal.formatted).font(.headline) }
            if let r = a.cancelReason, a.status == .cancelledByCustomer || a.status == .cancelledByStaff {
                Text("İptal gerekçesi: \(r)").font(.caption).foregroundStyle(Brand.cancel)
            }
        }
        .foregroundStyle(Palette.text)
        .card()
    }

    @ViewBuilder
    private func actions(_ a: Appointment) -> some View {
        let status = a.effectiveStatus(now)
        if a.customerCanCancel(now) {
            Button("Randevuyu İptal Et") { askCancel = true }.buttonStyle(SecondaryButtonStyle()).disabled(busy)
            Text("Başlangıçtan en geç 2 saat öncesine kadar iptal edebilirsiniz.").font(.caption).foregroundStyle(Palette.muted)
        } else if a.customerMustCall(now) {
            InfoBanner(text: "Randevuya 2 saatten az kaldığı için uygulamadan iptal edilemez. Gelemeyecekseniz uzmanı veya salon sahibini arayın; iptali salon yapar.")
            if let contacts {
                ForEach(contacts, id: \.self) { c in
                    if let url = URL(string: "tel:\(c.phoneE164)") {
                        Link(destination: url) { Label("\(c.roleLabel): \(c.name) — Ara", systemImage: "phone") }.buttonStyle(SecondaryButtonStyle())
                    }
                }
            } else {
                Button { loadContacts() } label: { Label("İptal için salonla iletişime geç", systemImage: "phone") }.buttonStyle(SecondaryButtonStyle()).disabled(busy)
            }
        } else if status == .expired {
            InfoBanner(text: "Salon talebinizi süresi içinde yanıtlamadığı için talep düştü. Yeni randevu alabilirsiniz.")
        }
        if status == .completed {
            if a.reviewId == nil {
                NavigationLink(value: CustomerRoute.review(a.id)) { Label("Hizmeti / Kuaförü Puanla", systemImage: "star") }.buttonStyle(PrimaryButtonStyle())
            } else {
                InfoBanner(text: "Bu randevuyu değerlendirdiniz. Teşekkürler!")
            }
        }
        if !status.isActive {
            NavigationLink(value: CustomerRoute.salon(a.salonId)) { Label("Tekrar Randevu Al", systemImage: "arrow.clockwise") }.buttonStyle(SecondaryButtonStyle())
        }
    }

    private func observe() async {
        do {
            for try await snap in AppointmentRepo.observe(appointmentId) {
                a = Appointment(snap)
                if a == nil { error = "Randevu bulunamadı." }
            }
        } catch { self.error = ErrorMessages.text(error) }
    }

    private func cancel() {
        busy = true
        Task {
            do {
                let st = try await AppointmentRepo.cancelByCustomer(appointmentId, reason: cancelReason.trimmingCharacters(in: .whitespaces))
                message = st == "EXPIRED" ? "Talebinizin onay süresi dolmuştu; randevu zaten düşmüş." : "Randevunuz iptal edildi. Salon bilgilendirildi."
            } catch { message = ErrorMessages.text(error) }
            busy = false
        }
    }

    private func loadContacts() {
        busy = true
        Task {
            do { contacts = try await AppointmentRepo.contacts(appointmentId) } catch { message = ErrorMessages.text(error) }
            busy = false
        }
    }
}

// MARK: - Puanlama

struct ReviewView: View {
    let appointmentId: String
    @Environment(\.dismiss) private var dismiss
    @State private var salon = 0
    @State private var staff = 0
    @State private var service = 0
    @State private var comment = ""
    @State private var busy = false
    @State private var done = false
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                if done {
                    EmptyStateView(systemImage: "checkmark.seal.fill", title: "Değerlendirmeniz için teşekkürler", message: "Puanınız salon ve uzman puanlarına eklendi.")
                    Button("Geri dön") { dismiss() }.buttonStyle(SecondaryButtonStyle())
                } else {
                    StarPicker(label: "Salon deneyimi", value: $salon)
                    StarPicker(label: "Uzman", value: $staff)
                    StarPicker(label: "Hizmet (isteğe bağlı)", value: $service)
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text("Kısa yorum (isteğe bağlı)").font(.caption).foregroundStyle(Palette.muted)
                        TextEditor(text: $comment).frame(minHeight: 100).padding(4)
                            .background(Palette.card, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.outline))
                    }
                    InfoBanner(text: "Yorumunuz adınız ve soyadınızın baş harfiyle yayınlanır. Değerlendirme gönderildikten sonra değiştirilemez.")
                    if let error { Text(error).font(.footnote).foregroundStyle(Brand.cancel) }
                    PrimaryButton(title: "Gönder", loading: busy, enabled: salon > 0 && staff > 0) { submit() }
                }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Puanla")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func submit() {
        busy = true
        error = nil
        Task {
            do {
                try await AppointmentRepo.review(appointmentId: appointmentId, salon: salon, staff: staff, service: service > 0 ? service : nil,
                                                 comment: String(comment.prefix(500)))
                done = true
            } catch {
                let e = AppError.from(error)
                if e.reason == "ALREADY_REVIEWED" { done = true } else { self.error = ErrorMessages.text(e) }
            }
            busy = false
        }
    }
}
