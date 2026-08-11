#!/usr/bin/env bash
set -euo pipefail

markdown_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tiqian_dir="${TIQIAN_CHECKOUT:-$markdown_dir/../tiqian}"
math_dir="${TIQIAN_MATH_CHECKOUT:-$markdown_dir/../tiqian-math}"
suite_version="${TIQIAN_VERSION:-0.0.0-maven-smoke}"
repository="${MAVEN_LOCAL_REPOSITORY:-$markdown_dir/build/maven-local-smoke/repository}"

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

"$tiqian_dir/gradlew" -p "$tiqian_dir" publishTiqianToMavenLocal "${publish_args[@]}"
"$math_dir/gradlew" -p "$math_dir" publishMathComposeToMavenLocal "${publish_args[@]}"
"$markdown_dir/gradlew" -p "$markdown_dir" \
    jvmTest compileAndroidMain :preview:compileKotlinJvm \
    "${consume_args[@]}"
"$markdown_dir/gradlew" -p "$markdown_dir" publishToMavenLocal \
    "${consume_args[@]}"
python3 "$markdown_dir/scripts/verify-published-metadata.py" "$repository" "$suite_version"
"$markdown_dir/gradlew" -p "$markdown_dir/smoke-test" compileKotlin "${consume_args[@]}"
