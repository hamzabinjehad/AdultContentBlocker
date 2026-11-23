import SwiftUI

/// Which step of "Lock Unrot" the person is on, from the vault and their confirmation.
/// Pure, so the flow is tested without Keychain or Screen Time.
enum UnrotLockFlow {
    enum Step: Equatable {
        /// No sealed passcode: pick how long.
        case choose
        /// The passcode is on screen (15 minutes): type it into Settings.
        case enterCode(passcode: String, hidesAt: Date)
        /// Sealed but not confirmed: tick the two Settings switches.
        case confirm(until: Date)
        /// Sealed and confirmed: Unrot cannot be deleted until the date.
        case locked(until: Date)
        /// The date passed: the passcode can be shown.
        case due
    }

    static func step(phase: PasscodeVaultPolicy.Phase?, confirmed: Bool) -> Step {
        switch phase {
        case nil, .revealed?: return .choose
        case .setup(let passcode, let hidesAt)?: return .enterCode(passcode: passcode, hidesAt: hidesAt)
        case .sealed(let until)?: return confirmed ? .locked(until: until) : .confirm(until: until)
        case .due?: return .due
        }
    }

    /// The level Unrot may claim, never higher than it can show.
    static func isLocked(_ level: MobileProtectionPolicy.LockLevel) -> Bool { level > .selfOnly }
}

/// "Make Unrot hard to delete": the passcode vault as one guided flow. Unrot makes
/// a Screen Time passcode, shows it for 15 minutes, the person types it into
/// Settings and turns off deleting apps, then Unrot hides it until their date.
/// With someone else (a guardian) or a supervised iPhone, the lock is stronger.
struct UnrotLockView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var vault: PasscodeVaultController
    @ObservedObject var lockDevice: LockConfirmationDevice
    @AppStorage(MobileProtectionPolicy.lockAttestationKey) private var attestationData = Data()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var days = 30
    @State private var codeSet = false
    @State private var deletingOff = false
    @State private var holdSwitches = true
    @State private var saveFailed = false
    @State private var confirmCreate = false

    private var attestation: MobileProtectionPolicy.LockAttestation? {
        MobileProtectionPolicy.LockAttestation(storage: attestationData)
    }

    private var removal: MobileProtectionPolicy.RemovalProtection {
        MobileProtectionPolicy.removalProtection(removal: protection.removal, attestation: attestation,
                                                 vault: vault.facts, now: vault.now, deviceMarker: lockDevice.marker)
    }

    private var step: UnrotLockFlow.Step {
        UnrotLockFlow.step(phase: vault.phase, confirmed: removal.level == .passcodeVault)
    }

    var body: some View {
        ZStack {
            UBackdrop(mood: UnrotLockFlow.isLocked(removal.level) ? .day : .warn)
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if removal.level > .passcodeVault {
                        strongerLock
                    } else {
                        switch step {
                        case .choose: choose
                        case .enterCode(let code, let hidesAt): enterCode(code, hidesAt: hidesAt)
                        case .confirm(let until): confirm(until)
                        case .locked(let until): locked(until)
                        case .due: due
                        }
                    }
                    if let key = vault.messageKey {
                        Text(LocalizedStringKey(key)).font(.footnote).foregroundStyle(UPalette.warn)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    stronger
                    honesty
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)
        }
        .preferredColorScheme(.dark)
        .toolbar(.visible, for: .navigationBar)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { vault.reload(); lockDevice.reload() }
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in vault.tick() }
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(UnrotLockFlow.isLocked(removal.level) ? "glass_gold" : "glass_sprout")
                .resizable().scaledToFit().frame(maxWidth: 200)
                .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 0, endRadius: 100))
                .frame(maxWidth: .infinity)
                .accessibilityHidden(true)
            Text(UnrotLockFlow.isLocked(removal.level) ? "u.lock.titleOn" : "u.lock.title")
                .font(.uDisplay(30)).foregroundStyle(UPalette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text(LocalizedStringKey(removal.level.rawValue))
                .font(.footnote.weight(.semibold))
                .foregroundStyle(UnrotLockFlow.isLocked(removal.level) ? UPalette.leaf : UPalette.warnText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - 1. Choose

    private var choose: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("u.lock.body").font(.subheadline).foregroundStyle(UPalette.ink)
                .fixedSize(horizontal: false, vertical: true)
            ULabel("u.lock.howLong")
            HStack(spacing: 8) {
                ForEach(PasscodeVaultPolicy.durations, id: \.self) { value in
                    Button { days = value } label: {
                        VStack(spacing: 0) {
                            Text(verbatim: "\(value)").font(.uDisplay(22))
                            Text(value == 1 ? "u.lock.day" : "u.lock.days").font(.caption2)
                        }
                        .foregroundStyle(days == value ? UPalette.onGold : UPalette.ink)
                        .frame(maxWidth: .infinity, minHeight: 64)
                        .background {
                            if days == value { RoundedRectangle(cornerRadius: 18).fill(UPalette.goldFill) }
                            else { RoundedRectangle(cornerRadius: 18).fill(UPalette.card) }
                        }
                    }
                    .buttonStyle(UPressStyle())
                    .accessibilityLabel(Text("u.lock.daysA11y \(value)"))
                    .accessibilityAddTraits(days == value ? .isSelected : [])
                }
            }
            numbered(1, "u.lock.step1")
            numbered(2, "u.lock.step2")
            numbered(3, "u.lock.step3")
            UGoldButton(title: "u.lock.make", systemImage: "lock.fill", wide: true) { confirmCreate = true }
                .disabled(!vault.storageHealthy || vault.busy)
                .alert("u.lock.makeTitle", isPresented: $confirmCreate) {
                    Button("u.lock.makeGo") { vault.create(days: days) }
                    Button("cancel", role: .cancel) {}
                } message: { Text("u.lock.makeBody \(days)") }
        }
    }

    // MARK: - 2. Type it into Settings

    private func enterCode(_ code: String, hidesAt: Date) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            UCard(highlight: UPalette.gold.opacity(0.6)) {
                ULabel("u.lock.yourCode")
                Text(verbatim: PasscodeVaultPolicy.displayed(code, sceneActive: scenePhase == .active))
                    .font(.system(size: 54, weight: .bold, design: .monospaced))
                    .tracking(14)
                    .foregroundStyle(UPalette.goldLight)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel(Text(verbatim: code.map(String.init).joined(separator: " ")))
                    .privacySensitive()
                let left = UnrotFormat.clock(seconds: hidesAt.timeIntervalSince(vault.now))
                Text("u.lock.hidesIn \(left)")
                    .font(.caption.weight(.semibold)).foregroundStyle(UPalette.dim)
                    .frame(maxWidth: .infinity)
            }
            ULabel("u.lock.inSettings")
            numbered(1, "u.lock.do1")
            numbered(2, "u.lock.do2")
            numbered(3, "u.lock.do3")
            numbered(4, "u.lock.do4")
            UGoldButton(title: "u.lock.didIt", systemImage: "checkmark", wide: true) { vault.seal() }
            Text("u.lock.sealNote").font(.caption).foregroundStyle(UPalette.dim)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - 3. Confirm

    private func confirm(_ until: Date) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("u.lock.confirmBody \(until.formatted(date: .long, time: .omitted))")
                .font(.subheadline).foregroundStyle(UPalette.ink).fixedSize(horizontal: false, vertical: true)
            UOption(title: "u.lock.check1", selected: codeSet, multiple: true) { codeSet.toggle() }
            UOption(title: "u.lock.check2", selected: deletingOff, multiple: true) { deletingOff.toggle() }
            if !protection.commitmentActive {
                UOption(title: "u.lock.hold", detail: "u.lock.holdDetail", selected: holdSwitches, multiple: true) {
                    holdSwitches.toggle()
                }
            }
            if saveFailed {
                Text("lock.saveFailed").font(.footnote).foregroundStyle(UPalette.warn)
            }
            UGoldButton(title: "u.lock.lockIt", systemImage: "lock.fill", wide: true) { lockIt() }
                .disabled(!(codeSet && deletingOff)).opacity(codeSet && deletingOff ? 1 : 0.45)
            Text("lock.confirm.hint").font(.caption).foregroundStyle(UPalette.dim)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func lockIt() {
        guard let facts = vault.facts, facts.holdsPasscode(at: vault.now) else { saveFailed = true; return }
        guard let marker = lockDevice.establish() else { saveFailed = true; return }
        saveFailed = false
        attestationData = MobileProtectionPolicy.LockAttestation(
            claim: .passcodeVault, vaultCreatedAt: facts.createdAt, confirmedAt: Date(), deviceMarker: marker).storage
        if holdSwitches, !protection.commitmentActive {
            // The same length as the vault, rounded to a commitment Unrot offers.
            let left = facts.revealAt.timeIntervalSince(vault.now) / 86400
            let length = [1, 7, 30, 90].last { Double($0) <= left + 0.01 } ?? 1
            Task { await protection.startCommitment(days: length) }
        }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    // MARK: - 4. Locked

    private func locked(_ until: Date) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            UCard(highlight: UPalette.leaf.opacity(0.5)) {
                Text("u.lock.until \(until.formatted(date: .long, time: .omitted))")
                    .font(.uDisplay(22)).foregroundStyle(UPalette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text("u.lock.lockedBody").font(.footnote).foregroundStyle(UPalette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
            UOption(title: "u.lock.test", detail: "u.lock.testDetail", systemImage: "hand.tap",
                    selected: attestation?.selfTested == true, multiple: true) {
                guard var next = attestation else { return }
                next.selfTested.toggle()
                attestationData = next.storage
            }
            HStack(spacing: 10) {
                UPillButton(title: "u.lock.plus7", systemImage: "plus") { vault.extend(days: 7) }
                UPillButton(title: "u.lock.plus30", systemImage: "plus") { vault.extend(days: 30) }
            }
            Text("u.lock.extendNote").font(.caption).foregroundStyle(UPalette.dim)
        }
    }

    private var due: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("u.lock.dueBody").font(.subheadline).foregroundStyle(UPalette.ink)
                .fixedSize(horizontal: false, vertical: true)
            UGoldButton(title: "vault.unseal", systemImage: "lock.open", wide: true) { Task { await vault.unseal() } }
        }
    }

    private var strongerLock: some View {
        UCard(highlight: UPalette.leaf.opacity(0.5)) {
            Text(LocalizedStringKey(removal.level.rawValue)).font(.headline).foregroundStyle(UPalette.ink)
                .fixedSize(horizontal: false, vertical: true)
            Text("u.lock.strongerOn").font(.footnote).foregroundStyle(UPalette.dim)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Stronger, and what no lock stops

    private var stronger: some View {
        VStack(alignment: .leading, spacing: 10) {
            ULabel("u.lock.strongerTitle").padding(.top, 8)
            UCard(padding: 14) {
                Label("u.lock.guardian", systemImage: "person.2.fill").font(.headline).foregroundStyle(UPalette.ink)
                Text("u.lock.guardianBody").font(.footnote).foregroundStyle(UPalette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
            UCard(padding: 14) {
                Label("u.lock.supervised", systemImage: "desktopcomputer").font(.headline).foregroundStyle(UPalette.ink)
                Text("u.lock.supervisedBody").font(.footnote).foregroundStyle(UPalette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
            NavigationLink {
                List { LockCenterView(protection: protection, vault: vault, lockDevice: lockDevice) }
                    .listStyle(.insetGrouped)
                    .navigationTitle(Text("u.lock.allLevels"))
                    .preferredColorScheme(.dark)
            } label: {
                Text("u.lock.allLevels").font(.footnote.weight(.bold)).foregroundStyle(UPalette.goldLight)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
        }
    }

    private var honesty: some View {
        Text("u.lock.honesty").font(.caption).foregroundStyle(UPalette.dim)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func numbered(_ n: Int, _ key: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(verbatim: "\(n)").font(.footnote.weight(.bold)).foregroundStyle(UPalette.goldLight)
                .frame(width: 26, height: 26)
                .overlay { Circle().strokeBorder(UPalette.gold, lineWidth: 1.5) }
            Text(key).font(.subheadline).foregroundStyle(UPalette.ink).fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }
}
