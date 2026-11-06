import SwiftUI

/// The three verifiable content layers plus the removal-protection level.
/// The overview answers "what is on?" with one progress bar and compact rows;
/// the full evidence stays in Setup.
struct ProtectionPlanCard: View {
    @ObservedObject var protection: ProtectionController
    let removal: MobileProtectionPolicy.RemovalProtection
    let onSetup: () -> Void

    private var layerCount: Int { 3 }
    private var enabledSafariParts: Int { protection.safari.filter { $0 == true }.count }

    var body: some View {
        HisnCard {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("plan.title")
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                    Text("plan.layers \(protection.configuredContentLayers)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("plan.configuredLayers")
                }

                HisnProgressBar(
                    value: Double(protection.configuredContentLayers) / Double(layerCount),
                    tint: protection.configuredContentLayers == layerCount
                        ? HisnPalette.good : Color.accentColor
                )
                .accessibilityLabel(Text("plan.layers \(protection.configuredContentLayers)"))

                VStack(spacing: 0) {
                    layerRow(
                        icon: "safari",
                        title: "safari.title",
                        detail: Text("plan.safariParts \(enabledSafariParts)"),
                        ready: protection.safariConfigured
                    )
                    HisnDivider()
                    layerRow(
                        icon: "network",
                        title: "dns.title",
                        detail: Text(dnsLabel),
                        ready: protection.dns == .enabled
                    )
                    HisnDivider()
                    layerRow(
                        icon: "hourglass",
                        title: "screentime.layer",
                        detail: Text(screenTimeDetail),
                        ready: protection.screenTimeConfigured
                    )
                    HisnDivider()
                    removalRow
                }

                if protection.configuredContentLayers < layerCount || removal.evidence == nil {
                    Button(action: onSetup) {
                        HStack {
                            Text("plan.openSetup")
                            Spacer()
                            Image(systemName: "arrow.forward.circle.fill")
                                .accessibilityHidden(true)
                        }
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                }
            }
        }
    }

    private func layerRow(icon: String, title: LocalizedStringKey, detail: Text?, ready: Bool) -> some View {
        HisnAdaptiveRow(title: title, detail: detail) {
            HisnStatusPill(
                text: ready ? "plan.configured" : "plan.needsSetup",
                systemImage: ready ? "checkmark" : "circle",
                tint: ready ? HisnPalette.good : .secondary
            )
        }
        .padding(.vertical, 11)
    }

    private var removalRow: some View {
        HisnAdaptiveRow(
            title: "lock.title",
            detail: Text(removalLevel)
        ) {
            switch removal.evidence {
            case .verified?:
                HisnStatusPill(text: "lock.evidence.verified", systemImage: "checkmark.seal",
                               tint: HisnPalette.good)
            case .attested?:
                HisnStatusPill(text: "lock.evidence.attested", systemImage: "person.fill.checkmark",
                               tint: .secondary)
            case nil:
                HisnStatusPill(text: "lock.evidence.none", systemImage: "lock.open", tint: .secondary)
            }
        }
        .padding(.vertical, 11)
        .accessibilityIdentifier("plan.removalProtection")
    }

    private var dnsLabel: LocalizedStringKey {
        switch protection.dns {
        case .unknown: return "status.unknown"
        case .absent: return "dns.absent"
        case .saved: return "dns.saved"
        case .enabled: return "dns.enabled"
        case .differentConfiguration: return "dns.different"
        case .unavailable: return "dns.unavailable"
        }
    }

    /// The overview stays short: the full explanation of an uncheckable
    /// simulator layer is in Setup, not under a plan row.
    private var screenTimeDetail: LocalizedStringKey {
        switch protection.screenTimeState {
        case .unavailable: return "overview.screentimeUnavailable"
        default: return LocalizedStringKey(protection.screenTimeState.rawValue)
        }
    }

    private var removalLevel: LocalizedStringKey {
        switch removal.level {
        case .selfOnly: return "overview.lock.level.selfOnly"
        case .passcodeVault: return "overview.lock.level.vault"
        case .familyGuardian: return "overview.lock.level.guardian"
        case .supervised: return "overview.lock.level.supervised"
        }
    }
}

/// Anything that has stopped working since the last check. It is a separate
/// card only when there is something to say, so the normal overview stays calm.
struct ProtectionWarningsCard: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    let onSetup: () -> Void

    var body: some View {
        if !protection.historyStorageHealthy
            || !protection.unconfirmedContentLayers.isEmpty
            || usage.health.needsAttention {
            HisnCard {
                VStack(alignment: .leading, spacing: 10) {
                    if !protection.historyStorageHealthy {
                        Label("plan.historyUnavailable", systemImage: "exclamationmark.triangle")
                            .foregroundStyle(HisnPalette.warning)
                    }
                    if !protection.unconfirmedContentLayers.isEmpty {
                        Label("plan.lost", systemImage: "exclamationmark.triangle")
                            .foregroundStyle(HisnPalette.warning)
                        ForEach(protection.unconfirmedContentLayers.sorted(), id: \.self) { key in
                            Text(LocalizedStringKey(key))
                                .font(.callout)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if usage.health.needsAttention {
                        Label(LocalizedStringKey(usage.health.rawValue), systemImage: "exclamationmark.triangle")
                            .foregroundStyle(HisnPalette.warning)
                        Text("apps.health.recovery")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Button(action: onSetup) {
                        Text("setup.continue")
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }
}

/// The commitment is a compact card on the overview; the duration picker and
/// the acknowledgement live in Setup, where they cannot be tapped by accident.
struct ProtectionLockCard: View {
    @ObservedObject var protection: ProtectionController
    let onOpen: () -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        HisnCard {
            VStack(alignment: .leading, spacing: 12) {
                if dynamicTypeSize.isAccessibilitySize {
                    VStack(alignment: .leading, spacing: 12) {
                        lockBadge
                        lockText
                    }
                } else {
                    HStack(alignment: .top, spacing: 14) {
                        lockBadge
                        lockText
                        Spacer(minLength: 0)
                    }
                }

                if protection.clockAssessment != .consistent {
                    Label("commitment.clockUncertain", systemImage: "clock.badge.exclamationmark")
                        .font(.footnote)
                        .foregroundStyle(HisnPalette.warning)
                }
                if !protection.commitmentStorageHealthy {
                    Label("commitment.storageFailed", systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundStyle(HisnPalette.warning)
                }
                if protection.commitmentActive, protection.screenTimeState == .accessRemoved {
                    Label("commitment.screenTimeLostSetup", systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundStyle(HisnPalette.warning)
                }

                if protection.commitmentActive {
                    Button(action: onOpen) {
                        Text("overview.lock.manage")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                } else {
                    Button(action: onOpen) {
                        Text("overview.lock.set")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                }
            }
        }
    }

    private var lockText: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(LocalizedStringKey(protection.commitmentActive
                                    ? "commitment.active" : "overview.lock.none"))
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            Text(LocalizedStringKey(protection.commitmentActive
                                    ? "overview.lock.activeDetail" : "overview.lock.noneDetail"))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            if protection.commitmentActive, let session = protection.commitment {
                (Text("overview.lock.until") + Text(verbatim: " ") + dateText(session.deadline))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder private var lockBadge: some View {
        if protection.commitmentActive {
            HisnIconBadge(systemName: "lock.fill", tint: HisnPalette.good, size: 46)
        } else {
            HisnIconBadge(systemName: "lock.open", tint: .accentColor, size: 46)
        }
    }

    private func dateText(_ date: Date) -> Text {
        Text(date, format: .dateTime.day().month(.abbreviated).year().hour().minute())
    }
}

/// The person's own lists live one tap away, with their current count visible.
struct RulesCard: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    let onSites: () -> Void
    let onApps: () -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        HisnCard {
            VStack(alignment: .leading, spacing: 0) {
                Text("overview.rules.title")
                    .font(.headline)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.bottom, 4)
                ruleButton(
                    icon: "nosign",
                    tint: .secondary,
                    title: "overview.rules.sites",
                    detail: Text("overview.rules.siteCount \(protection.customSites.count)"),
                    action: onSites
                )
                HisnDivider()
                ruleButton(
                    icon: "hourglass",
                    tint: Color.accentColor,
                    title: "overview.rules.apps",
                    detail: Text(LocalizedStringKey(usage.configuration == nil
                                                    ? "overview.rules.notSet" : "plan.configured")),
                    action: onApps
                )
                HisnDivider()
                ruleButton(
                    icon: "safari",
                    tint: HisnPalette.good,
                    title: "overview.rules.guard",
                    detail: guardDetail,
                    action: onApps
                )
            }
        }
    }

    private func ruleButton(icon: String, tint: Color, title: LocalizedStringKey,
                            detail: Text, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Group {
                if dynamicTypeSize.isAccessibilitySize {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 12) {
                            HisnIconBadge(systemName: icon, tint: tint, size: 38)
                            Text(title).font(.subheadline.weight(.medium))
                            Spacer(minLength: 8)
                            chevron
                        }
                        detail.font(.caption).foregroundStyle(.secondary)
                    }
                } else {
                    HStack(spacing: 12) {
                        HisnIconBadge(systemName: icon, tint: tint, size: 38)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(title).font(.subheadline.weight(.medium))
                            detail.font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 8)
                        chevron
                    }
                }
            }
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var chevron: some View {
        Image(systemName: "chevron.forward")
            .font(.caption.weight(.semibold))
            .foregroundStyle(.tertiary)
            .accessibilityHidden(true)
    }

    private var guardDetail: Text {
        guard let guardConfiguration = usage.guardConfiguration else {
            return Text(LocalizedStringKey("overview.rules.notSet"))
        }
        let count = guardConfiguration.selection.applicationTokens.count
            + guardConfiguration.selection.categoryTokens.count
            + guardConfiguration.selection.webDomainTokens.count
        return Text("overview.rules.guardCount \(count)")
    }
}
