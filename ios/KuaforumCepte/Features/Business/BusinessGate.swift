import SwiftUI

struct InvitationsView: View {
    @EnvironmentObject var app: AppState
    @State private var items: [Invitation] = []
    @State private var loading = true
    @State private var busy: String?
    @State private var error: String?
    @State private var legal: LegalDoc?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                BackBar(title: "Salon Davetleri") { app.signOut() }
                Text("Sizi uzman olarak davet eden salonlar. Kabul ettiğinizde salonun randevu takviminde yer alırsınız.")
                    .font(.subheadline).foregroundStyle(Palette.muted)
                if loading { ProgressView().frame(maxWidth: .infinity) }
                ForEach(items) { i in
                    VStack(alignment: .leading, spacing: Spacing.s) {
                        Text(i.salonName).font(.headline)
                        Text("Uzmanlık: \(i.specialty)").font(.subheadline).foregroundStyle(Palette.muted)
                        HStack(spacing: Spacing.s) {
                            Button("Reddet") { act(i, accept: false) }.buttonStyle(SecondaryButtonStyle()).disabled(busy != nil)
                            PrimaryButton(title: "Kabul Et", loading: busy == i.id) { act(i, accept: true) }
                        }
                    }
                    .foregroundStyle(Palette.text)
                    .card()
                }
                Text("Kabul ederek KVKK Aydınlatma Metni'ni ve Kullanım Koşulları'nı onaylamış olursunuz.").font(.caption).foregroundStyle(Palette.muted)
                HStack { Button("KVKK") { legal = .kvkk }; Button("Kullanım Koşulları") { legal = .terms } }.font(.caption)
                if let error { Text(error).font(.footnote).foregroundStyle(Brand.cancel) }
                Button("Davetsiz devam et") { continueWithout() }.buttonStyle(SecondaryButtonStyle())
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
        .sheet(item: $legal) { LegalView(doc: $0) }
        .task { await load() }
    }

    private func load() async {
        do { items = try await BusinessRepo.invitations() } catch { self.error = ErrorMessages.text(error) }
        loading = false
        if items.isEmpty { continueWithout() }
    }

    private func act(_ i: Invitation, accept: Bool) {
        busy = i.id
        Task {
            do {
                if accept { try await BusinessRepo.accept(i) } else { try await BusinessRepo.decline(i) }
                if accept { app.lastSalonId = i.salonId }
                await refreshAndRoute(forceSalon: accept ? i.salonId : nil)
            } catch { self.error = ErrorMessages.text(error) }
            busy = nil
        }
    }

    private func continueWithout() { Task { await refreshAndRoute(forceSalon: nil, skipInvites: true) } }

    private func refreshAndRoute(forceSalon: String?, skipInvites: Bool = false) async {
        do {
            let snap = try await SessionRepo.bootstrap()
            app.session = snap
            let active = snap.memberships.filter(\.isActive)
            if let s = forceSalon, let m = active.first(where: { $0.salonId == s }) { app.go(.business(m)); return }
            if !skipInvites && snap.pendingInvitationCount > 0 { items = try await BusinessRepo.invitations(); return }
            if active.isEmpty { app.go(.businessNoSalon) } else if active.count == 1 { app.go(.business(active[0])) } else { app.go(.salonPicker) }
        } catch { self.error = ErrorMessages.text(error) }
    }
}

struct SalonPickerView: View {
    @EnvironmentObject var app: AppState
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.m) {
                BackBar(title: "Salon Seçin") { app.signOut() }
                ForEach((app.session?.memberships ?? []).filter(\.isActive)) { m in
                    Button {
                        app.lastSalonId = m.salonId
                        app.go(.business(m))
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(m.salonName).font(.headline)
                                Text(m.role == "OWNER" ? "Salon Sahibi" : "Uzman").font(.caption).foregroundStyle(Palette.muted)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(Brand.gold)
                        }
                        .foregroundStyle(Palette.text)
                        .card()
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(Spacing.screen)
        }
        .screenBackground()
    }
}
