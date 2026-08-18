#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile


repository = Path(sys.argv[1])
version = sys.argv[2]
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}

expected = {
    ("org.tiqian", "tiqian-core"),
    ("org.tiqian", "tiqian-font"),
    ("org.tiqian", "tiqian-linebreak"),
    ("org.tiqian", "tiqian-clreq"),
    ("org.tiqian", "tiqian-layout"),
    ("org.tiqian", "tiqian-shaping-api"),
    ("org.tiqian", "tiqian-shaping-jvm"),
    ("org.tiqian", "tiqian-shaping-skia"),
    ("org.tiqian", "tiqian-shaping-android-adapter"),
    ("org.tiqian", "tiqian-shaping-android-native-font"),
    ("org.tiqian", "tiqian-compose"),
    ("org.tiqian", "tiqian-compose-material3"),
    ("org.tiqian", "markdown-compose"),
    ("org.tiqian", "math-core"),
    ("org.tiqian", "math-parser"),
    ("org.tiqian", "math-font-opentype"),
    ("org.tiqian", "math-font-stix"),
    ("org.tiqian", "math-font-tooling"),
    ("org.tiqian", "math-gradle-plugin"),
    ("org.tiqian", "math-font-android"),
    ("org.tiqian", "math-font-skia"),
    ("org.tiqian", "math-layout"),
    ("org.tiqian", "math-compose"),
}

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
