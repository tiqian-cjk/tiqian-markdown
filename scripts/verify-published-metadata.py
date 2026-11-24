#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile


arguments = sys.argv[1:]
# With --tiqian-from-registry the Tiqian modules, and with --math-from-registry the
# Tiqian Math modules, were resolved from the registry instead of being published
# into the repository, so they are not expected there. The lockstep check still
# reads every POM the repository holds, including the Tiqian Markdown ones.
options = {argument for argument in arguments if argument.startswith("--")}
unknown = options - {"--tiqian-from-registry", "--math-from-registry"}
if unknown:
    raise SystemExit(f"unknown option: {', '.join(sorted(unknown))}")
tiqian_from_registry = "--tiqian-from-registry" in options
math_from_registry = "--math-from-registry" in options
arguments = [argument for argument in arguments if not argument.startswith("--")]
repository = Path(arguments[0])
version = arguments[1]
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}

tiqian_modules = {
    ("org.tiqian", "tiqian-engine"),
    ("org.tiqian", "tiqian-jvm-shaping"),
    ("org.tiqian", "tiqian-jvm-skia"),
    ("org.tiqian", "tiqian-android-shaping"),
    ("org.tiqian", "tiqian-android-native-font"),
    ("org.tiqian", "tiqian-compose"),
    ("org.tiqian", "tiqian-compose-material3"),
}
math_modules = {
    ("org.tiqian", "math-engine"),
    ("org.tiqian", "math-font-stix"),
    ("org.tiqian", "math-font-tooling"),
    ("org.tiqian", "math-gradle-plugin"),
    ("org.tiqian", "math-android-font"),
    ("org.tiqian", "math-jvm-skia"),
    ("org.tiqian", "math-compose"),
}
expected = {("org.tiqian", "markdown-compose")}
if not tiqian_from_registry:
    expected |= tiqian_modules
if not math_from_registry:
    expected |= math_modules

errors: list[str] = []
for group, artifact in sorted(expected):
    artifact_dir = repository / Path(*group.split(".")) / artifact / version
    pom = artifact_dir / f"{artifact}-{version}.pom"
    if not pom.is_file():
        errors.append(f"missing root publication: {group}:{artifact}:{version}")

version_dirs = list((repository / "org" / "tiqian").rglob(version))
for version_dir in version_dirs:
    for pom in version_dir.glob("*.pom"):
        root = ET.parse(pom).getroot()
        for dependency in root.findall("m:dependencies/m:dependency", namespace):
            group = dependency.findtext("m:groupId", namespaces=namespace)
            dependency_version = dependency.findtext("m:version", namespaces=namespace)
            if group and group.startswith("org.tiqian") and dependency_version != version:
                errors.append(
                    f"non-lockstep dependency in {pom}: {group} uses {dependency_version!r}"
                )

    binaries = [
        path
        for path in version_dir.iterdir()
        if path.suffix in {".jar", ".aar"}
        and not path.name.endswith(("-sources.jar", "-javadoc.jar"))
    ]
    if binaries:
        if not list(version_dir.glob("*-sources.jar")):
            errors.append(f"missing sources archive: {version_dir}")
        if not list(version_dir.glob("*-javadoc.jar")):
            errors.append(f"missing javadoc archive: {version_dir}")

markdown_android_dir = repository / "org" / "tiqian" / "markdown-compose-android" / version
markdown_android_aar = (
    markdown_android_dir / f"markdown-compose-android-{version}.aar"
)
expected_consumer_rules = (
    b"-keep,allowobfuscation class org.tiqian.markdown.compose.MarkdownStyleKt",
    b"-keep,allowobfuscation class org.tiqian.markdown.compose.MaterialMarkdownStyleKt",
)
if not markdown_android_aar.is_file():
    errors.append(f"missing Android publication: {markdown_android_aar}")
else:
    with zipfile.ZipFile(markdown_android_aar) as archive:
        try:
            consumer_rules = archive.read("proguard.txt")
        except KeyError:
            errors.append(f"missing consumer keep rules: {markdown_android_aar}")
        else:
            for expected_consumer_rule in expected_consumer_rules:
                if expected_consumer_rule not in consumer_rules:
                    errors.append(
                        f"missing consumer keep rule {expected_consumer_rule!r}: "
                        f"{markdown_android_aar}"
                    )

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)

print(f"verified {len(expected)} root publications at lockstep version {version}")
