#!/usr/bin/env python3
"""Generate the universal app, four blockers, web scanner and hosted tests.

Reuses the repository's deterministic pbxproj writer, not macOS targets or
entitlements. Does not delete existing projects or user Xcode settings.
"""
from __future__ import annotations

import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("hisn_project_writer", ROOT.parent / "macos/generate_xcodeproj.py")
writer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(writer)
Project, oid, q = writer.Project, writer.oid, writer.q


def main() -> None:
    p = Project()
    app, tests = "HisnMobile", "HisnMobileTests"
    blockers = [f"Hisn{i}" for i in range(1, 5)]
    monitor = "HisnActivityMonitor"
    scanner = "HisnText"
    names = [app, *blockers, monitor, scanner, tests]
    sources = {
        app: ["Hisn/HisnMobileApp.swift", "Hisn/MobileRootView.swift", "Hisn/ProtectionController.swift",
              "Hisn/MobileCommitmentStore.swift", "Hisn/CommitmentSection.swift",
              "Hisn/AppUsageController.swift", "Hisn/AppUsageSection.swift", "Hisn/BrowserFirstSection.swift",
              "Shared/AppUsageConfiguration.swift",
              "../shared/apple/RouterAppDomains.swift", "../shared/apple/RouterAppDomainsView.swift",
              "../shared/apple/CommitmentPolicy.swift", "../shared/apple/MobileProtectionPolicy.swift",
              "../shared/apple/MirroredRuleStore.swift", "../shared/apple/ReadinessHistory.swift"],
        tests: ["HisnTests/MobileProtectionTests.swift", "HisnTests/SafariWebScannerPackageTests.swift",
                "../shared/tests/MirroredRuleStoreTests.swift"],
        monitor: ["HisnActivityMonitor/ActivityMonitor.swift", "Shared/AppUsageConfiguration.swift",
                  "../shared/apple/MirroredRuleStore.swift"],
        scanner: ["HisnWebExtension/SafariWebExtensionHandler.swift"],
        **{name: ["HisnBlocker/ContentBlockerRequestHandler.swift"] for name in blockers},
    }
    resources = {app: ["Hisn/en.lproj/Localizable.strings", "Hisn/ar.lproj/Localizable.strings", "Hisn/Assets.xcassets"],
                 tests: ["fixtures/safari_cases.json"]}
    paths = sorted(set(sum(sources.values(), []) + sum(resources.values(), [])))
    for path in paths:
        if not (ROOT / path).exists():
            raise SystemExit(f"Missing project input: {path}")
    refs = {}
    for path in paths:
        kind = {".swift": "sourcecode.swift", ".strings": "text.plist.strings", ".json": "text.json", ".xcassets": "folder.assetcatalog"}[Path(path).suffix]
        refs[path] = p.add(oid("ios-file", path), "PBXFileReference", {
            "lastKnownFileType": kind, "path": q(path), "sourceTree": '"<group>"',
        })
    # Language resources need one variant group, otherwise Xcode flattens names.
    loc = p.add(oid("ios-localization"), "PBXVariantGroup", {
        "children": writer.plist_array([refs[path] for path in resources[app] if path.endswith(".strings")]),
        "name": "Localizable.strings", "sourceTree": '"<group>"',
    })
    for language in ["en", "ar"]:
        ref = refs[f"Hisn/{language}.lproj/Localizable.strings"]
        # The language name is required for localized variant membership.
        for index, object_text in enumerate(p.objects):
            if object_text.startswith(f"\t\t{ref} "):
                p.objects[index] = object_text.replace("\n\t\t\tisa =", f"\n\t\t\tname = {language};\n\t\t\tisa =")
    products = {}
    for name in names:
        suffix = ".app" if name == app else ".xctest" if name == tests else ".appex"
        products[name] = p.add(oid("ios-product", name), "PBXFileReference", {
            "explicitFileType": "wrapper.application" if name == app else "wrapper.cfbundle",
            "path": q(name + suffix), "sourceTree": "BUILT_PRODUCTS_DIR", "includeInIndex": "0",
        })
    product_group = p.add(oid("ios-products"), "PBXGroup", {
        "children": writer.plist_array(list(products.values())), "name": "Products", "sourceTree": '"<group>"',
    })
    group = p.add(oid("ios-main"), "PBXGroup", {
        "children": writer.plist_array([refs[path] for path in paths if not path.endswith(".strings")] + [loc, product_group]),
        "sourceTree": '"<group>"',
    })
    base = {"SDKROOT": "iphoneos", "IPHONEOS_DEPLOYMENT_TARGET": "16.0", "SWIFT_VERSION": "5.0",
            "TARGETED_DEVICE_FAMILY": q("1,2"), "CODE_SIGN_STYLE": "Automatic",
            "DEVELOPMENT_TEAM": '""', "CURRENT_PROJECT_VERSION": "1", "MARKETING_VERSION": "0.1.0",
            "CLANG_ENABLE_MODULES": "YES", "CLANG_ENABLE_OBJC_ARC": "YES",
            "ENABLE_USER_SCRIPT_SANDBOXING": "NO", "SUPPORTED_PLATFORMS": q("iphoneos iphonesimulator"),
            "SUPPORTS_MACCATALYST": "NO", "PRODUCT_NAME": q("$(TARGET_NAME)"),
            "LD_RUNPATH_SEARCH_PATHS": q("$(inherited) @executable_path/Frameworks @executable_path/../../Frameworks")}

    def configs(name: str, extra: dict) -> str:
        ids = []
        for mode in ["Debug", "Release"]:
            settings = dict(base, **extra)
            settings.update({"SWIFT_OPTIMIZATION_LEVEL": q("-Onone" if mode == "Debug" else "-O"),
                             "ENABLE_TESTABILITY": "YES" if mode == "Debug" else "NO"})
            if mode == "Debug": settings["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] = "DEBUG"
            ids.append(p.add(oid("ios-config", name, mode), "XCBuildConfiguration", {
                "name": mode, "buildSettings": writer.build_settings(settings),
            }))
        return p.add(oid("ios-config-list", name), "XCConfigurationList", {
            "buildConfigurations": writer.plist_array(ids), "defaultConfigurationIsVisible": "0",
            "defaultConfigurationName": "Release",
        })

    project_id = oid("ios-project")

    def phase(name: str, kind: str, members: list[str]) -> str:
        buildfiles = [p.add(oid("ios-build", name, kind, ref), "PBXBuildFile", {"fileRef": ref}) for ref in members]
        return p.add(oid("ios-phase", name, kind), kind, {"buildActionMask": "2147483647",
            "files": writer.plist_array(buildfiles), "runOnlyForDeploymentPostprocessing": "0"})

    def dependency(name: str, on: str) -> str:
        proxy = p.add(oid("ios-proxy", name, on), "PBXContainerItemProxy", {"containerPortal": project_id,
            "proxyType": "1", "remoteGlobalIDString": oid("ios-target", on), "remoteInfo": q(on)})
        return p.add(oid("ios-dependency", name, on), "PBXTargetDependency", {
            "target": oid("ios-target", on), "targetProxy": proxy})

    for name in names:
        phases = [phase(name, "PBXSourcesBuildPhase", [refs[path] for path in sources[name]]),
                  phase(name, "PBXFrameworksBuildPhase", []),
                  phase(name, "PBXResourcesBuildPhase", [loc, refs["Hisn/Assets.xcassets"]] if name == app else
                        [refs[path] for path in resources.get(name, [])])]
        extra = {"PRODUCT_BUNDLE_IDENTIFIER": q("app.hisn.mobile" if name == app else
                 "app.hisn.mobile.tests" if name == tests else "app.hisn.mobile.activity" if name == monitor
                 else "app.hisn.mobile.HisnText" if name == scanner
                 else f"app.hisn.mobile.blocker{blockers.index(name) + 1}")}
        deps = []
        if name in [app, *blockers]:
            part = 0 if name == app else blockers.index(name) + 1
            script = ('set -eu\nPYTHON="${HISN_PYTHON:-python3}"\n'
                      f'"$PYTHON" "$SRCROOT/prepare_rules.py" --part {part} '
                      '--output "$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"\n')
            phases.append(p.add(oid("ios-verify", name), "PBXShellScriptBuildPhase", {
                "name": q("Verify signed seed and compile Safari rules"), "buildActionMask": "2147483647",
                "files": "()", "inputPaths": "()", "outputPaths": "()", "alwaysOutOfDate": "1",
                "shellPath": "/bin/sh", "shellScript": q(script).replace("\n", "\\n"),
                "runOnlyForDeploymentPostprocessing": "0"}))
            extra["INFOPLIST_FILE"] = q("Hisn/Info.plist" if name == app else "HisnBlocker/Info.plist")
        if name == app:
            extra["CODE_SIGN_ENTITLEMENTS"] = "Hisn/Hisn.entitlements"
            extra["ASSETCATALOG_COMPILER_APPICON_NAME"] = "AppIcon"
            embedded = []
            for blocker in [*blockers, monitor, scanner]:
                embedded.append(p.add(oid("ios-embed", blocker), "PBXBuildFile", {
                    "fileRef": products[blocker], "settings": "{ATTRIBUTES = (RemoveHeadersOnCopy, );}"}))
                deps.append(dependency(name, blocker))
            phases.append(p.add(oid("ios-embed-phase"), "PBXCopyFilesBuildPhase", {
                "buildActionMask": "2147483647", "dstPath": '""', "dstSubfolderSpec": "13",
                "files": writer.plist_array(embedded), "name": q("Embed Safari Extensions"),
                "runOnlyForDeploymentPostprocessing": "0"}))
        elif name == monitor:
            extra.update({"INFOPLIST_FILE": "HisnActivityMonitor/Info.plist",
                          "CODE_SIGN_ENTITLEMENTS": "HisnActivityMonitor/HisnActivityMonitor.entitlements",
                          "APPLICATION_EXTENSION_API_ONLY": "YES", "SKIP_INSTALL": "YES"})
        elif name == scanner:
            extra.update({"INFOPLIST_FILE": "HisnWebExtension/Info.plist",
                          "APPLICATION_EXTENSION_API_ONLY": "YES", "SKIP_INSTALL": "YES"})
            script = ('set -eu\nPYTHON="${HISN_PYTHON:-python3}"\n'
                      '"$PYTHON" "$SRCROOT/prepare_web_extension.py" '
                      '--output "$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"\n')
            phases.append(p.add(oid("ios-verify-web-scanner", name), "PBXShellScriptBuildPhase", {
                "name": q("Verify signed terms and stage shared Safari scanner"), "buildActionMask": "2147483647",
                "files": "()", "inputPaths": "()", "outputPaths": "()", "alwaysOutOfDate": "1",
                "shellPath": "/bin/sh", "shellScript": q(script).replace("\n", "\\n"),
                "runOnlyForDeploymentPostprocessing": "0"}))
        elif name == tests:
            extra.update({"GENERATE_INFOPLIST_FILE": "YES", "BUNDLE_LOADER": q("$(TEST_HOST)"),
                          "TEST_HOST": q("$(BUILT_PRODUCTS_DIR)/HisnMobile.app/HisnMobile")})
            deps.append(dependency(name, app))
        else:
            extra.update({"APPLICATION_EXTENSION_API_ONLY": "YES", "SKIP_INSTALL": "YES"})
            extra["HISN_PART"] = str(blockers.index(name) + 1)
        p.add(oid("ios-target", name), "PBXNativeTarget", {
            "name": q(name), "productName": q(name), "productReference": products[name],
            "productType": q("com.apple.product-type.application" if name == app else
                             "com.apple.product-type.bundle.unit-test" if name == tests else "com.apple.product-type.app-extension"),
            "buildConfigurationList": configs(name, extra), "buildPhases": writer.plist_array(phases),
            "dependencies": writer.plist_array(deps), "buildRules": "()"})
    p.add(project_id, "PBXProject", {"attributes": "{LastUpgradeCheck = 1600; BuildIndependentTargetsInParallel = 1;}",
        "buildConfigurationList": configs("project", {}), "compatibilityVersion": q("Xcode 14.0"),
        "developmentRegion": "en", "knownRegions": "(en, ar, Base)", "hasScannedForEncodings": "0",
        "mainGroup": group, "productRefGroup": product_group, "projectDirPath": '""', "projectRoot": '""',
        "targets": writer.plist_array([oid("ios-target", name) for name in names])})
    project = ROOT / "HisnMobile.xcodeproj"
    schemes = project / "xcshareddata/xcschemes"
    schemes.mkdir(parents=True, exist_ok=True)
    (project / "project.pbxproj").write_text(p.render(project_id))

    def reference(name: str) -> str:
        ext = "app" if name == app else "xctest"
        return (f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{oid("ios-target", name)}" '
                f'BuildableName="{name}.{ext}" BlueprintName="{name}" ReferencedContainer="container:HisnMobile.xcodeproj"/>')
    scheme = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.7">
 <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
  <BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{reference(app)}</BuildActionEntry>
 </BuildActionEntries></BuildAction>
 <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{reference(tests)}</TestableReference></Testables></TestAction>
 <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{reference(app)}</BuildableProductRunnable></LaunchAction>
 <ProfileAction buildConfiguration="Release"><BuildableProductRunnable runnableDebuggingMode="0">{reference(app)}</BuildableProductRunnable></ProfileAction>
 <AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
'''
    (schemes / f"{app}.xcscheme").write_text(scheme)
    print("Generated ios/HisnMobile.xcodeproj: universal app, four Safari parts, text scanner, tests")


if __name__ == "__main__":
    main()
