#!/usr/bin/env python3
"""
Generate Hisn.xcodeproj.

The project is generated rather than hand-maintained because three of the four
targets share the same source files — `BlocklistStore`, `LockStore` and
`PartnerService` are compiled into the app, the network extension and the
native-messaging bridge alike. In a hand-edited project that sharing is invisible
and easy to break: add a file to the app, forget to tick it for the extension,
and the extension silently keeps compiling against a stale view of the lock.
Here the membership is one list, in one place, checked at generation time.

    python3 generate_xcodeproj.py

Object ids are derived from a hash of the object's role, so regenerating an
unchanged tree produces a byte-identical file and the diff stays readable.
"""

from __future__ import annotations

import hashlib
import shutil
from pathlib import Path

ROOT = Path(__file__).parent
PROJECT = ROOT / "Hisn.xcodeproj"

DEPLOYMENT_TARGET = "13.0"
SWIFT_VERSION = "5.0"

APP = "Hisn"
FILTER = "HisnFilter"
BRIDGE = "HisnBridge"
TESTS = "HisnTests"

APP_BUNDLE_ID = "app.hisn.Hisn"
# The system extension's identifier must be prefixed by the app's, and must
# match `FilterController.extensionIdentifier` exactly — a mismatch fails at
# activation time with an error that does not name the cause.
FILTER_BUNDLE_ID = "app.hisn.Hisn.HisnFilter"
TESTS_BUNDLE_ID = "app.hisn.HisnTests"

# Sources shared between targets. The app owns them; the other two compile the
# same files rather than linking a framework, which keeps the extension a
# single self-contained bundle with no embedded dylib to sign and load.
SHARED = ["Hisn/BlocklistStore.swift", "Hisn/LockStore.swift",
          "Hisn/PartnerService.swift", "Hisn/SiteLists.swift"]

APP_SOURCES = SHARED + [
    "Hisn/ContentView.swift",
    "Hisn/FilterController.swift",
    "Hisn/HisnApp.swift",
    "Hisn/ListUpdater.swift",
    "Hisn/LockManager.swift",
    "Hisn/NativeMessagingInstaller.swift",
]
FILTER_SOURCES = SHARED + ["HisnFilter/FilterDataProvider.swift",
                           "HisnFilter/main.swift"]
BRIDGE_SOURCES = SHARED + ["HisnBridge/main.swift"]
TEST_SOURCES = ["HisnTests/BlocklistStoreTests.swift"]

# The signed seed list, bundled into the extension so a machine that has never
# completed a list update still enforces something. Verified on the same path as
# a downloaded list — being bundled buys it no trust.
FILTER_RESOURCES = ["../seed/manifest.json", "../seed/manifest.json.sig",
                    "../seed/domains_core.txt"]

ALL_FILES = sorted(set(APP_SOURCES + FILTER_SOURCES + BRIDGE_SOURCES + TEST_SOURCES) | {
    "Hisn/Info.plist", "Hisn/Hisn.entitlements",
    "HisnFilter/Info.plist", "HisnFilter/HisnFilter.entitlements",
    "HisnBridge/HisnBridge.entitlements",
}) + FILTER_RESOURCES


def oid(*parts: str) -> str:
    """Stable 24-hex-character object id."""
    return hashlib.sha1("::".join(parts).encode()).hexdigest()[:24].upper()


def q(value: str) -> str:
    """Quote a pbxproj scalar when it is not a bare identifier."""
    if value and all(c.isalnum() or c in "_." for c in value):
        return value
    return '"{}"'.format(value.replace("\\", "\\\\").replace('"', '\\"'))


class Project:
    def __init__(self) -> None:
        self.objects: list[str] = []

    def add(self, ident: str, isa: str, body: dict, comment: str = "") -> str:
        lines = [f"\t\t{ident} {('/* ' + comment + ' */') if comment else ''} = {{"]
        lines.append(f"\t\t\tisa = {isa};")
        for key, value in body.items():
            lines.append(f"\t\t\t{key} = {value};")
        lines.append("\t\t};")
        self.objects.append("\n".join(lines))
        return ident

    def render(self, root: str) -> str:
        return (
            "// !$*UTF8*$!\n{\n"
            "\tarchiveVersion = 1;\n"
            "\tclasses = {\n\t};\n"
            "\tobjectVersion = 56;\n"
            "\tobjects = {\n"
            + "\n".join(self.objects)
            + "\n\t};\n"
            f"\trootObject = {root};\n"
            "}\n"
        )


def plist_array(values: list[str]) -> str:
    return "(\n" + "".join(f"\t\t\t\t{q(v)},\n" for v in values) + "\t\t\t)"


def build_settings(pairs: dict) -> str:
    lines = ["{"]
    for key, value in pairs.items():
        lines.append(f"\t\t\t\t{key} = {value};")
    lines.append("\t\t\t}")
    return "\n".join(lines)


def main() -> int:
    p = Project()

    # ---------------------------------------------------------------- files
    file_refs: dict[str, str] = {}
    for path in ALL_FILES:
        kind = {
            ".swift": "sourcecode.swift",
            ".plist": "text.plist.xml",
            ".entitlements": "text.plist.entitlements",
            ".json": "text.json",
            ".sig": "text",
            ".txt": "text",
        }[Path(path).suffix]
        ref = oid("fileref", path)
        file_refs[path] = ref
        p.add(ref, "PBXFileReference", {
            "lastKnownFileType": kind,
            "path": q(Path(path).name),
            "sourceTree": '"<group>"',
        }, Path(path).name)

    products = {
        APP: (oid("product", APP), f"{APP}.app", "wrapper.application"),
        FILTER: (oid("product", FILTER), f"{FILTER}.systemextension",
                 '"wrapper.system-extension"'),
        BRIDGE: (oid("product", BRIDGE), BRIDGE, '"compiled.mach-o.executable"'),
        TESTS: (oid("product", TESTS), f"{TESTS}.xctest", "wrapper.cfbundle"),
    }
    for name, (ref, filename, kind) in products.items():
        p.add(ref, "PBXFileReference", {
            "explicitFileType": kind,
            "includeInIndex": "0",
            "path": q(filename),
            "sourceTree": "BUILT_PRODUCTS_DIR",
        }, filename)

    # --------------------------------------------------------------- groups
    def group(ident: str, children: list[str], name: str | None,
              path: str | None = None) -> str:
        body = {"children": "(\n" + "".join(
            f"\t\t\t\t{c},\n" for c in children) + "\t\t\t)"}
        if name:
            body["name"] = q(name)
        if path:
            body["path"] = q(path)
        body["sourceTree"] = '"<group>"'
        return p.add(ident, "PBXGroup", body, name or path or "")

    dir_groups = []
    for folder in (APP, FILTER, BRIDGE, TESTS):
        members = [file_refs[f] for f in ALL_FILES if f.startswith(folder + "/")]
        dir_groups.append(group(oid("group", folder), members, None, folder))
    dir_groups.append(group(oid("group", "seed"),
                            [file_refs[f] for f in FILTER_RESOURCES],
                            "seed", "../seed"))

    products_group = group(oid("group", "Products"),
                           [ref for ref, _, _ in products.values()], "Products")
    main_group = group(oid("group", "main"), dir_groups + [products_group], None)

    # ------------------------------------------------------------- settings
    common = {
        "CLANG_ENABLE_MODULES": "YES",
        "CLANG_ENABLE_OBJC_ARC": "YES",
        "COPY_PHASE_STRIP": "NO",
        "ENABLE_STRICT_OBJC_MSGSEND": "YES",
        "GCC_NO_COMMON_BLOCKS": "YES",
        "MACOSX_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
        "SDKROOT": "macosx",
        "SWIFT_VERSION": SWIFT_VERSION,
        "ALWAYS_SEARCH_USER_PATHS": "NO",
        "CLANG_WARN_DOCUMENTATION_COMMENTS": "YES",
        "GCC_WARN_UNDECLARED_SELECTOR": "YES",
        "SWIFT_EMIT_LOC_STRINGS": "YES",
        # Signing is left to the builder. The two entitlements this product
        # cannot work without — content-filter-provider and
        # system-extension.install — are granted per Apple Developer account, so
        # there is no team id worth committing here.
        "CODE_SIGN_STYLE": "Automatic",
        "DEVELOPMENT_TEAM": '""',
    }
    debug = dict(common, **{
        "DEBUG_INFORMATION_FORMAT": "dwarf",
        "ENABLE_TESTABILITY": "YES",
        "GCC_OPTIMIZATION_LEVEL": "0",
        "ONLY_ACTIVE_ARCH": "YES",
        "SWIFT_ACTIVE_COMPILATION_CONDITIONS": "DEBUG",
        "SWIFT_OPTIMIZATION_LEVEL": '"-Onone"',
    })
    release = dict(common, **{
        "DEBUG_INFORMATION_FORMAT": '"dwarf-with-dsym"',
        "ENABLE_NS_ASSERTIONS": "NO",
        "SWIFT_COMPILATION_MODE": "wholemodule",
        "SWIFT_OPTIMIZATION_LEVEL": '"-O"',
    })

    def config_list(owner: str, settings_debug: dict, settings_release: dict) -> str:
        ids = []
        for cname, settings in (("Debug", settings_debug), ("Release", settings_release)):
            ident = p.add(oid("config", owner, cname), "XCBuildConfiguration", {
                "buildSettings": build_settings(settings),
                "name": cname,
            }, cname)
            ids.append(ident)
        return p.add(oid("configlist", owner), "XCConfigurationList", {
            "buildConfigurations": "(\n" + "".join(f"\t\t\t\t{i},\n" for i in ids) + "\t\t\t)",
            "defaultConfigurationIsVisible": "0",
            "defaultConfigurationName": "Release",
        }, f"Build configuration list for {owner}")

    project_configs = config_list("PBXProject", debug, release)

    app_settings = {
        "CODE_SIGN_ENTITLEMENTS": "Hisn/Hisn.entitlements",
        "CURRENT_PROJECT_VERSION": "1",
        "MARKETING_VERSION": "1.0",
        "ENABLE_HARDENED_RUNTIME": "YES",
        "INFOPLIST_FILE": "Hisn/Info.plist",
        "PRODUCT_BUNDLE_IDENTIFIER": APP_BUNDLE_ID,
        "PRODUCT_NAME": '"$(TARGET_NAME)"',
        "COMBINE_HIDPI_IMAGES": "YES",
        "LD_RUNPATH_SEARCH_PATHS": '(\n\t\t\t\t"$(inherited)",\n\t\t\t\t"@executable_path/../Frameworks",\n\t\t\t)',
        "SWIFT_EMIT_LOC_STRINGS": "YES",
    }
    filter_settings = {
        "CODE_SIGN_ENTITLEMENTS": "HisnFilter/HisnFilter.entitlements",
        "CURRENT_PROJECT_VERSION": "1",
        "MARKETING_VERSION": "1.0",
        "ENABLE_HARDENED_RUNTIME": "YES",
        "INFOPLIST_FILE": "HisnFilter/Info.plist",
        "PRODUCT_BUNDLE_IDENTIFIER": FILTER_BUNDLE_ID,
        "PRODUCT_NAME": '"$(TARGET_NAME)"',
        "SKIP_INSTALL": "YES",
        "LD_RUNPATH_SEARCH_PATHS": '(\n\t\t\t\t"$(inherited)",\n\t\t\t\t"@executable_path/../Frameworks",\n\t\t\t)',
    }
    bridge_settings = {
        "CODE_SIGN_ENTITLEMENTS": "HisnBridge/HisnBridge.entitlements",
        "CURRENT_PROJECT_VERSION": "1",
        "MARKETING_VERSION": "1.0",
        "ENABLE_HARDENED_RUNTIME": "YES",
        "GENERATE_INFOPLIST_FILE": "YES",
        "PRODUCT_NAME": '"$(TARGET_NAME)"',
        "SKIP_INSTALL": "YES",
    }
    test_settings = {
        "BUNDLE_LOADER": '"$(TEST_HOST)"',
        "GENERATE_INFOPLIST_FILE": "YES",
        "PRODUCT_BUNDLE_IDENTIFIER": TESTS_BUNDLE_ID,
        "PRODUCT_NAME": '"$(TARGET_NAME)"',
        "TEST_HOST": f'"$(BUILT_PRODUCTS_DIR)/{APP}.app/Contents/MacOS/{APP}"',
        "SWIFT_EMIT_LOC_STRINGS": "NO",
    }

    # -------------------------------------------------------------- targets
    def phase_sources(target: str, sources: list[str]) -> str:
        build_files = []
        for src in sources:
            ident = p.add(oid("buildfile", target, src), "PBXBuildFile", {
                "fileRef": file_refs[src],
            }, f"{Path(src).name} in Sources")
            build_files.append(ident)
        return p.add(oid("phase-sources", target), "PBXSourcesBuildPhase", {
            "buildActionMask": "2147483647",
            "files": "(\n" + "".join(f"\t\t\t\t{b},\n" for b in build_files) + "\t\t\t)",
            "runOnlyForDeploymentPostprocessing": "0",
        }, "Sources")

    def phase_frameworks(target: str) -> str:
        # Swift autolinks every framework these sources import, so this phase
        # stays empty on purpose rather than duplicating that list by hand.
        return p.add(oid("phase-frameworks", target), "PBXFrameworksBuildPhase", {
            "buildActionMask": "2147483647",
            "files": "(\n\t\t\t)",
            "runOnlyForDeploymentPostprocessing": "0",
        }, "Frameworks")

    def phase_resources(target: str, resources: list[str] = []) -> str:
        build_files = []
        for res in resources:
            ident = p.add(oid("resource", target, res), "PBXBuildFile", {
                "fileRef": file_refs[res],
            }, f"{Path(res).name} in Resources")
            build_files.append(ident)
        return p.add(oid("phase-resources", target), "PBXResourcesBuildPhase", {
            "buildActionMask": "2147483647",
            "files": "(\n" + "".join(f"\t\t\t\t{b},\n" for b in build_files) + "\t\t\t)",
            "runOnlyForDeploymentPostprocessing": "0",
        }, "Resources")

    def phase_copy(name: str, dst_path: str, subfolder: int,
                   embedded: list[str]) -> str:
        build_files = []
        for target_name in embedded:
            ident = p.add(oid("embed", name, target_name), "PBXBuildFile", {
                "fileRef": products[target_name][0],
                "settings": "{ATTRIBUTES = (RemoveHeadersOnCopy, ); }",
            }, f"{products[target_name][1]} in {name}")
            build_files.append(ident)
        return p.add(oid("phase-copy", name), "PBXCopyFilesBuildPhase", {
            "buildActionMask": "2147483647",
            "dstPath": q(dst_path),
            "dstSubfolderSpec": str(subfolder),
            "files": "(\n" + "".join(f"\t\t\t\t{b},\n" for b in build_files) + "\t\t\t)",
            "name": q(name),
            "runOnlyForDeploymentPostprocessing": "0",
        }, name)

    project_id = oid("project", "root")

    def dependency(target: str, on: str) -> str:
        proxy = p.add(oid("proxy", target, on), "PBXContainerItemProxy", {
            "containerPortal": project_id,
            "proxyType": "1",
            "remoteGlobalIDString": oid("target", on),
            "remoteInfo": q(on),
        }, "PBXContainerItemProxy")
        return p.add(oid("dependency", target, on), "PBXTargetDependency", {
            "target": oid("target", on),
            "targetProxy": proxy,
        }, "PBXTargetDependency")

    def native_target(name: str, product_type: str, sources: list[str],
                      settings: dict, extra_phases: list[str],
                      dependencies: list[str]) -> str:
        phases = [phase_sources(name, sources), phase_frameworks(name)] + extra_phases
        ref, filename, _ = products[name]
        return p.add(oid("target", name), "PBXNativeTarget", {
            "buildConfigurationList": config_list(
                name, dict(debug, **settings), dict(release, **settings)),
            "buildPhases": "(\n" + "".join(f"\t\t\t\t{ph},\n" for ph in phases) + "\t\t\t)",
            "buildRules": "(\n\t\t\t)",
            "dependencies": "(\n" + "".join(
                f"\t\t\t\t{d},\n" for d in dependencies) + "\t\t\t)",
            "name": q(name),
            "productName": q(name),
            "productReference": ref,
            "productType": q(product_type),
        }, name)

    native_target(FILTER, "com.apple.product-type.system-extension",
                  FILTER_SOURCES, filter_settings,
                  [phase_resources(FILTER, FILTER_RESOURCES)], [])
    native_target(BRIDGE, "com.apple.product-type.tool",
                  BRIDGE_SOURCES, bridge_settings, [], [])

    # A system extension lives in Contents/Library/SystemExtensions inside the
    # app that installs it; the bridge is an executable the browser launches, so
    # it goes next to the app binary in Contents/MacOS.
    #
    # Both use dstSubfolderSpec 1 ("Wrapper") with an explicit dstPath, not the
    # documented-nowhere numeric spec for "Executables" (commonly cited as 6).
    # That value produced an Embed Bridge phase that built cleanly and copied
    # nothing — no Contents/Executables/ directory, no error, no log line — so
    # NativeMessagingInstaller pointed at a path that could never exist. Both
    # phases now use the one form actually verified against a real build.
    embed_sysext = phase_copy("Embed System Extensions",
                              "Contents/Library/SystemExtensions", 1, [FILTER])
    embed_bridge = phase_copy("Embed Bridge", "Contents/MacOS", 1, [BRIDGE])

    native_target(APP, "com.apple.product-type.application", APP_SOURCES,
                  app_settings, [phase_resources(APP), embed_sysext, embed_bridge],
                  [dependency(APP, FILTER), dependency(APP, BRIDGE)])
    # The tests get the seed too, so they can assert that the artifacts actually
    # committed to this repo verify against the key actually shipped in the app.
    native_target(TESTS, "com.apple.product-type.bundle.unit-test", TEST_SOURCES,
                  test_settings, [phase_resources(TESTS, FILTER_RESOURCES)],
                  [dependency(TESTS, APP)])

    target_ids = [oid("target", n) for n in (APP, FILTER, BRIDGE, TESTS)]
    p.add(project_id, "PBXProject", {
        "attributes": "{\n\t\t\t\tBuildIndependentTargetsInParallel = 1;\n"
                      "\t\t\t\tLastSwiftUpdateCheck = 1600;\n"
                      "\t\t\t\tLastUpgradeCheck = 1600;\n"
                      "\t\t\t\tTargetAttributes = {\n"
                      f"\t\t\t\t\t{oid('target', TESTS)} = {{TestTargetID = {oid('target', APP)};}};\n"
                      "\t\t\t\t};\n\t\t\t}",
        "buildConfigurationList": project_configs,
        "compatibilityVersion": q("Xcode 14.0"),
        "developmentRegion": "en",
        "hasScannedForEncodings": "0",
        "knownRegions": "(\n\t\t\t\ten,\n\t\t\t\tBase,\n\t\t\t)",
        "mainGroup": main_group,
        "productRefGroup": products_group,
        "projectDirPath": '""',
        "projectRoot": '""',
        "targets": "(\n" + "".join(f"\t\t\t\t{t},\n" for t in target_ids) + "\t\t\t)",
    }, "Project object")

    # ---------------------------------------------------------------- write
    if PROJECT.exists():
        shutil.rmtree(PROJECT)
    (PROJECT / "xcshareddata" / "xcschemes").mkdir(parents=True)
    (PROJECT / "project.pbxproj").write_text(p.render(project_id), encoding="utf-8")
    (PROJECT / "xcshareddata" / "xcschemes" / f"{APP}.xcscheme").write_text(
        scheme(), encoding="utf-8")

    print(f"wrote {PROJECT.relative_to(ROOT.parent)}")
    print(f"  targets: {APP} (app), {FILTER} (system extension), "
          f"{BRIDGE} (tool), {TESTS} (unit tests)")
    return 0


def scheme() -> str:
    """A shared scheme, so `xcodebuild -scheme Hisn` works on a fresh clone."""
    app_id, filter_id = oid("target", APP), oid("target", FILTER)
    tests_id = oid("target", TESTS)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion = "1600" version = "1.7">
   <BuildAction parallelizeBuildables = "YES" buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry buildForTesting = "YES" buildForRunning = "YES"
                           buildForProfiling = "YES" buildForArchiving = "YES"
                           buildForAnalyzing = "YES">
            <BuildableReference BuildableIdentifier = "primary"
               BlueprintIdentifier = "{app_id}" BuildableName = "{APP}.app"
               BlueprintName = "{APP}" ReferencedContainer = "container:{APP}.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
         <BuildActionEntry buildForTesting = "YES" buildForRunning = "YES"
                           buildForProfiling = "YES" buildForArchiving = "YES"
                           buildForAnalyzing = "YES">
            <BuildableReference BuildableIdentifier = "primary"
               BlueprintIdentifier = "{filter_id}" BuildableName = "{FILTER}.systemextension"
               BlueprintName = "{FILTER}" ReferencedContainer = "container:{APP}.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      shouldUseLaunchSchemeArgsEnv = "YES">
      <Testables>
         <TestableReference skipped = "NO">
            <BuildableReference BuildableIdentifier = "primary"
               BlueprintIdentifier = "{tests_id}" BuildableName = "{TESTS}.xctest"
               BlueprintName = "{TESTS}" ReferencedContainer = "container:{APP}.xcodeproj">
            </BuildableReference>
         </TestableReference>
      </Testables>
   </TestAction>
   <LaunchAction buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      launchStyle = "0" useCustomWorkingDirectory = "NO"
      ignoresPersistentStateOnLaunch = "NO" debugDocumentVersioning = "YES"
      debugServiceExtension = "internal" allowLocationSimulation = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         <BuildableReference BuildableIdentifier = "primary"
            BlueprintIdentifier = "{app_id}" BuildableName = "{APP}.app"
            BlueprintName = "{APP}" ReferencedContainer = "container:{APP}.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction buildConfiguration = "Release" shouldUseLaunchSchemeArgsEnv = "YES"
      savedToolIdentifier = "" useCustomWorkingDirectory = "NO"
      debugDocumentVersioning = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         <BuildableReference BuildableIdentifier = "primary"
            BlueprintIdentifier = "{app_id}" BuildableName = "{APP}.app"
            BlueprintName = "{APP}" ReferencedContainer = "container:{APP}.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction buildConfiguration = "Debug"></AnalyzeAction>
   <ArchiveAction buildConfiguration = "Release" revealArchiveInOrganizer = "YES"></ArchiveAction>
</Scheme>
"""


if __name__ == "__main__":
    raise SystemExit(main())
