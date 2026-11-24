#!/usr/bin/env bash
set -euo pipefail

markdown_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tiqian_dir="${TIQIAN_CHECKOUT:-$markdown_dir/../tiqian}"
math_dir="${TIQIAN_MATH_CHECKOUT:-$markdown_dir/../tiqian-math}"
repository="${MAVEN_LOCAL_REPOSITORY:-$markdown_dir/build/maven-local-smoke/repository}"

# With TIQIAN_REGISTRY_VERSION set, Tiqian is not built: its modules come from the
# registry at that version. Tiqian Math is still built from its checkout and is
# published under the same version, so the suite keeps one version throughout.
# TIQIAN_MATH_FROM_REGISTRY=true takes Tiqian Math from the registry as well, as a
# release does once Tiqian and Tiqian Math are both released at that version.
registry_version="${TIQIAN_REGISTRY_VERSION:-}"
registry_repository="${TIQIAN_REGISTRY_REPOSITORY:-https://registry.tiqian.org/maven/}"
math_from_registry="${TIQIAN_MATH_FROM_REGISTRY:-false}"
if [[ "$math_from_registry" != true && "$math_from_registry" != false ]]; then
    echo "TIQIAN_MATH_FROM_REGISTRY must be true or false, not $math_from_registry" >&2
    exit 1
fi
if [[ "$math_from_registry" == true && -z "$registry_version" ]]; then
    echo "TIQIAN_MATH_FROM_REGISTRY=true needs TIQIAN_REGISTRY_VERSION" >&2
    exit 1
fi
if [[ -n "$registry_version" ]]; then
    if [[ -n "${TIQIAN_VERSION:-}" && "$TIQIAN_VERSION" != "$registry_version" ]]; then
        echo "TIQIAN_VERSION ($TIQIAN_VERSION) differs from TIQIAN_REGISTRY_VERSION ($registry_version)" >&2
        exit 1
    fi
    suite_version="$registry_version"
else
    suite_version="${TIQIAN_VERSION:-0.0.0-maven-smoke}"
fi

mkdir -p "$repository"

publish_args=(
    --no-daemon
    "-PtiqianVersion=$suite_version"
    "-Dmaven.repo.local=$repository"
)

consume_args=(
    --no-daemon
    "-PtiqianVersion=$suite_version"
    "-PtiqianDependencyVersion=$suite_version"
    "-PtiqianRepository=$repository"
    "-Dmaven.repo.local=$repository"
)

metadata_args=("$repository" "$suite_version")

if [[ -n "$registry_version" ]]; then
    consume_args+=("-PtiqianRegistryRepository=$registry_repository")
    metadata_args+=(--tiqian-from-registry)
else
    "$tiqian_dir/gradlew" -p "$tiqian_dir" publishTiqianToMavenLocal "${publish_args[@]}"
fi
if [[ "$math_from_registry" == true ]]; then
    metadata_args+=(--math-from-registry)
else
    "$math_dir/gradlew" -p "$math_dir" publishMathComposeToMavenLocal "${publish_args[@]}"
fi
"$markdown_dir/gradlew" -p "$markdown_dir" \
    jvmTest compileAndroidMain :preview:compileKotlinJvm \
    "${consume_args[@]}"
"$markdown_dir/gradlew" -p "$markdown_dir" publishToMavenLocal \
    "${consume_args[@]}"
python3 "$markdown_dir/scripts/verify-published-metadata.py" "${metadata_args[@]}"
"$markdown_dir/gradlew" -p "$markdown_dir/smoke-test" compileKotlin "${consume_args[@]}"
