import SwiftUI

// MARK: - Salon sayfası: bilgiler + uzman ve hizmet seçimi

struct SalonDetailView: View {
    let salonId: String
    @State private var salon: Salon?
    @State private var staff: [StaffMember] = []
    @State private var selectedStaff: StaffMember?
    @State private var services: [SalonService] = []
    @State private var selected: Set<String> = []
    @State private var error: String?
    @State private var loadingServices = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                if let salon {
                    header(salon)
                    Text("1. Uzman seçin").font(.headline).foregroundStyle(Palette.text)
                    if staff.isEmpty {
                        Text("Bu salonda şu anda randevu alan uzman yok.").font(.subheadline).foregroundStyle(Palette.muted)
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: Spacing.s) { ForEach(staff) { m in staffCard(m) } }
                        }
                    }
                    if let selectedStaff {
                        Text("2. Hizmet seçin").font(.headline).foregroundStyle(Palette.text)
                        if loadingServices { ProgressView() }
                        else if services.isEmpty { InfoBanner(text: "\(selectedStaff.fullName) henüz hizmet eklememiş. Başka bir uzman seçebilirsiniz.") }
                        ForEach(services) { s in serviceRow(s) }
                    }
                } else if let error {
                    Text(error).foregroundStyle(Brand.cancel)
                } else {
                    ProgressView().frame(maxWidth: .infinity)
                }
            }
            .padding(Spacing.screen)
            .padding(.bottom, 96)
        }
        .screenBackground()
        .navigationTitle(salon?.name ?? "Salon")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) { bottomBar }
        .task { await observe() }
    }

    private func header(_ s: Salon) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            if s.verified { Label("Onaylı salon", systemImage: "checkmark.seal.fill").font(.caption.weight(.semibold)).foregroundStyle(Palette.accent) }
            Text(s.name).font(.title2.bold()).foregroundStyle(Palette.text)
            Text(s.type?.label ?? "").font(.subheadline).foregroundStyle(Palette.muted)
            Label(s.fullAddress, systemImage: "mappin.and.ellipse").font(.footnote).foregroundStyle(Palette.text)
            HStack(spacing: 4) {
                Image(systemName: "star.fill").foregroundStyle(Brand.gold)
                Text("\(s.rating.label) · \(s.rating.count) değerlendirme").font(.footnote).foregroundStyle(Palette.muted)
            }
            if let l = s.loyaltyText { Text(l).font(.footnote).foregroundStyle(Palette.accent) }
            if !s.description.isEmpty { Text(s.description).font(.footnote).foregroundStyle(Palette.muted) }
            HStack(spacing: Spacing.s) {
                if let tel = URL(string: "tel:\(s.businessPhoneE164)"), !s.businessPhoneE164.isEmpty {
                    Link(destination: tel) { Label("Ara", systemImage: "phone") }.buttonStyle(SecondaryButtonStyle())
                }
                if let maps = URL(string: "http://maps.apple.com/?q=\(s.fullAddress.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")") {
                    Link(destination: maps) { Label("Yol tarifi", systemImage: "map") }.buttonStyle(SecondaryButtonStyle())
                }
            }
        }
        .card()
    }

    private func staffCard(_ m: StaffMember) -> some View {
        let isSel = selectedStaff?.id == m.id
        return Button { select(m) } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(m.fullName).font(.subheadline.weight(.semibold)).lineLimit(1)
                Text(m.specialty).font(.caption).foregroundStyle(Palette.muted).lineLimit(1)
                HStack(spacing: 2) { Image(systemName: "star.fill").font(.caption2).foregroundStyle(Brand.gold); Text(m.rating.label).font(.caption) }
            }
            .foregroundStyle(Palette.text)
            .padding(Spacing.m)
            .frame(width: 150, alignment: .leading)
            .background(isSel ? Palette.accent.opacity(0.18) : Palette.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(isSel ? Palette.accent : Palette.outline, lineWidth: isSel ? 2 : 1))
        }
        .buttonStyle(.plain)
    }

    private func serviceRow(_ s: SalonService) -> some View {
        let on = selected.contains(s.id)
        return Button {
            if on { selected.remove(s.id) } else { selected.insert(s.id) }
        } label: {
            HStack(spacing: Spacing.m) {
                Image(systemName: on ? "checkmark.circle.fill" : "circle").font(.title3).foregroundStyle(Palette.accent)
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.name).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.text)
                    Text(formatDuration(s.durationMinutes) + (s.description.isEmpty ? "" : " · \(s.description)")).font(.caption).foregroundStyle(Palette.muted).lineLimit(2)
                }
                Spacer()
                Text(s.price.formatted).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.text)
            }
            .card()
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var bottomBar: some View {
        if let salon, let st = selectedStaff, !selected.isEmpty {
            let draft = BookingDraft(salonId: salon.id, salonName: salon.name, staffUid: st.id, staffName: st.fullName, services: services.filter { selected.contains($0.id) })
            VStack(spacing: Spacing.s) {
                Text("\(draft.services.count) hizmet · \(formatDuration(draft.minutes)) · \(draft.total.formatted)").font(.headline).foregroundStyle(Palette.text)
                NavigationLink(value: CustomerRoute.slots(draft)) { Text("Saat Seç") }.buttonStyle(PrimaryButtonStyle())
            }
            .padding(Spacing.l)
            .background(.ultraThinMaterial)
        }
    }

    private func select(_ m: StaffMember) {
        guard selectedStaff?.id != m.id else { return }
        selectedStaff = m
        selected = []
        services = []
        loadingServices = true
        Task {
            services = (try? await SalonRepo.services(salonId, staffUid: m.id)) ?? []
            loadingServices = false
        }
    }

    private func observe() async {
        async let staffList = try? SalonRepo.staff(salonId)
        do {
            var first = true
            for try await snap in SalonRepo.observe(salonId) {
                salon = Salon(snap)
                if salon == nil || salon?.active == false { error = "Salon bulunamadı veya şu anda aktif değil."; salon = nil }
                if first { staff = await staffList ?? []; first = false }
            }
        } catch { self.error = ErrorMessages.text(error) }
    }
}

// MARK: - Tarih ve saat

struct SlotPickerView: View {
    let draft: BookingDraft
    @State private var forSelf = true
    @State private var availability: Availability?
    @State private var selectedDate: String?
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                Picker("Kimin için", selection: $forSelf) {
                    Text("Kendim için").tag(true)
                    Text("Başkası için").tag(false)
                }
                .pickerStyle(.segmented)
                if forSelf { Text("Başka randevunuz olan saatler gösterilmez.").font(.caption).foregroundStyle(Palette.muted) }
                Text("Toplam \(formatDuration(draft.minutes)) · \(draft.total.formatted)").font(.headline).foregroundStyle(Palette.text)

                if loading { ProgressView().frame(maxWidth: .infinity) }
                else if let error { Text(error).foregroundStyle(Brand.cancel) }
                else if let a = availability {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: Spacing.s) { ForEach(a.days) { d in dayChip(d) } }
                    }
                    let slots = a.days.first { $0.date == selectedDate }?.slots ?? []
                    if slots.isEmpty {
                        EmptyStateView(systemImage: "calendar.badge.exclamationmark", title: "Bu gün için uygun saat yok",
                                       message: "Uzman o gün çalışmıyor, salon kapalı veya tüm saatler dolu olabilir. Başka bir gün seçin.")
                    } else {
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.s), count: 4), spacing: Spacing.s) {
                            ForEach(slots, id: \.self) { slot in
                                NavigationLink(value: CustomerRoute.summary(draft, slot, forSelf: forSelf)) {
                                    Text(slot.label).font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity, minHeight: 44)
                                        .foregroundStyle(Palette.text)
                                        .background(Palette.card, in: RoundedRectangle(cornerRadius: 10))
                                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.accent.opacity(0.5), lineWidth: 1))
                                }
                            }
                        }
                    }
                }
                InfoBanner(text: "Yalnızca hizmetlerin toplam süresinin tamamen sığdığı başlangıç saatleri gösterilir. Seçtiğiniz saat, onayladığınız anda sunucuda yeniden kontrol edilir.")
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Tarih ve Saat")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: forSelf) { await load() }
    }

    private func dayChip(_ d: AvailabilityDay) -> some View {
        let sel = d.date == selectedDate
        let date = BusinessTime.isoDate.date(from: d.date) ?? Date()
        return Button { selectedDate = d.date } label: {
            VStack(spacing: 2) {
                Text(BusinessTime.weekdayShort.string(from: date)).font(.caption)
                Text(BusinessTime.dayShort.string(from: date)).font(.subheadline.weight(.semibold))
                Text(d.slots.isEmpty ? "Dolu" : "\(d.slots.count) saat").font(.caption2)
            }
            .foregroundStyle(sel ? Palette.onPrimary : Palette.text)
            .frame(width: 68).padding(.vertical, Spacing.s)
            .background(sel ? Palette.primary : (d.slots.isEmpty ? Palette.cardHigh : Palette.card), in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        loading = true
        error = nil
        do {
            let a = try await BookingRepo.availability(salonId: draft.salonId, staffUid: draft.staffUid, serviceIds: draft.serviceIds,
                                                       from: BusinessTime.today(), days: 14, forSelf: forSelf)
            availability = a
            if selectedDate == nil || !a.days.contains(where: { $0.date == selectedDate }) {
                selectedDate = (a.days.first { !$0.slots.isEmpty } ?? a.days.first)?.date
            }
        } catch { self.error = ErrorMessages.text(error) }
        loading = false
    }
}

// MARK: - Özet ve onay

struct BookingSummaryView: View {
    let draft: BookingDraft
    let slot: Slot
    let forSelf: Bool
    let onCreated: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var guestName = ""
    @State private var code = ""
    @State private var appliedCode: String?
    @State private var useLoyalty = false
    @State private var preview: PricePreview?
    @State private var codeError: String?
    @State private var submitting = false
    @State private var error: String?
    @State private var key = UUID().uuidString

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                VStack(alignment: .leading, spacing: Spacing.s) {
                    Text(draft.salonName).font(.title3.bold())
                    row("Uzman", draft.staffName)
                    row("Tarih", BusinessTime.dayLong.string(from: slot.startAt))
                    row("Saat", "\(BusinessTime.time.string(from: slot.startAt))–\(BusinessTime.time.string(from: slot.startAt.addingTimeInterval(Double(draft.minutes) * 60)))")
                    Divider()
                    ForEach(draft.services) { s in row("\(s.name) · \(formatDuration(s.durationMinutes))", s.price.formatted) }
                    Divider()
                    row("Toplam", draft.total.formatted, bold: preview?.discount.value ?? 0 == 0)
                    if let p = preview, p.discount.value > 0 {
                        row(useLoyalty ? "Sadakat ödülü" : "İndirim (\(appliedCode ?? ""))", "−\(p.discount.formatted)").foregroundStyle(Palette.accent)
                        row("Ödenecek", p.finalTotal.formatted, bold: true)
                    }
                }
                .foregroundStyle(Palette.text)
                .card()

                if !forSelf { LabeledField(title: "Hizmeti alacak kişinin adı soyadı", text: $guestName) }

                VStack(alignment: .leading, spacing: Spacing.s) {
                    Text("İndirim").font(.headline)
                    if let p = preview, p.loyaltyAvailable > 0 {
                        Toggle("Sadakat ödülünü kullan (%\(p.loyaltyPercent))", isOn: $useLoyalty)
                    } else if let hint = preview?.loyaltyHint {
                        Text(hint).font(.caption).foregroundStyle(Palette.muted)
                    }
                    if !useLoyalty {
                        if let appliedCode {
                            HStack { Text("“\(appliedCode)” kodu uygulandı").foregroundStyle(Palette.accent); Spacer(); Button("Kaldır") { self.appliedCode = nil; code = ""; refresh() } }
                        } else {
                            HStack(alignment: .bottom) {
                                LabeledField(title: "İndirim kodu", text: $code, error: codeError)
                                Button("Uygula") { appliedCode = code.uppercased(); refresh() }.disabled(code.count < 4)
                            }
                        }
                    }
                }
                .card()

                InfoBanner(text: "İptal politikası: Randevunuzu başlangıçtan en geç 2 saat öncesine kadar uygulamadan iptal edebilirsiniz; son 2 saatte iptal için salonu aramanız gerekir. Talebiniz salon onayına gönderilir. Ödeme salonda yapılır.")
                if let error { Text(error).font(.footnote).foregroundStyle(Brand.cancel) }
                PrimaryButton(title: "Randevuyu Onayla", loading: submitting) { submit() }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .navigationTitle("Randevu Özeti")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: useLoyalty) { _ in if useLoyalty { appliedCode = nil }; refresh() }
        .task { refresh() }
    }

    private func row(_ k: String, _ v: String, bold: Bool = false) -> some View {
        HStack { Text(k).font(bold ? .headline : .subheadline); Spacer(); Text(v).font(bold ? .headline : .subheadline) }
    }

    private func refresh() {
        Task {
            do {
                let p = try await BookingRepo.preview(salonId: draft.salonId, staffUid: draft.staffUid, serviceIds: draft.serviceIds, code: appliedCode, useLoyalty: useLoyalty)
                preview = p
                if let e = p.codeError, appliedCode != nil { codeError = ErrorMessages.messages[e] ?? e; appliedCode = nil } else { codeError = nil }
            } catch { self.error = ErrorMessages.text(error) }
        }
    }

    private func submit() {
        let guest = guestName.trimmingCharacters(in: .whitespaces)
        if !forSelf && !NameRules.isValid(guest) { error = "Hizmeti alacak kişinin adını ve soyadını yazın."; return }
        submitting = true
        error = nil
        Task {
            do {
                let id = try await BookingRepo.create(salonId: draft.salonId, staffUid: draft.staffUid, serviceIds: draft.serviceIds, startAt: slot.startAt,
                                                      expectedTotal: draft.total, key: key, guestName: forSelf ? nil : guest,
                                                      discountCode: appliedCode, useLoyalty: useLoyalty)
                onCreated(id)
            } catch {
                let e = AppError.from(error)
                self.error = ErrorMessages.text(e)
                if ["SLOT_TAKEN", "SLOT_UNAVAILABLE", "CUSTOMER_BUSY"].contains(e.reason) {
                    self.error = (self.error ?? "") + " Başka bir saat seçmek için geri dönün."
                }
            }
            submitting = false
        }
    }
}
