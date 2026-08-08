#!/usr/bin/env bash
set -euo pipefail

markdown_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tiqian_dir="${TIQIAN_CHECKOUT:-$markdown_dir/../Tiqian}"
math_dir="${MATH_COMPOSE_CHECKOUT:-$markdown_dir/../math-compose}"
suite_version="${TIQIAN_VERSION:-0.0.0-maven-smoke}"
repository="${MAVEN_LOCAL_REPOSITORY:-$markdown_dir/build/maven-local-smoke/repository}"

mkdir -p "$repository"

gradle_args=(
    --no-daemon
    "-PtiqianVersion=$suite_version"
    "-Dmaven.repo.local=$repository"
)

"$tiqian_dir/gradlew" -p "$tiqian_dir" publishTiqianToMavenLocal "${gradle_args[@]}"
"$math_dir/gradlew" -p "$math_dir" publishMathComposeToMavenLocal "${gradle_args[@]}"
"$markdown_dir/gradlew" -p "$markdown_dir" publishToMavenLocal \
    -PuseLocalTiqianCheckouts=false \
    "${gradle_args[@]}"
python3 "$markdown_dir/scripts/verify-published-metadata.py" "$repository" "$suite_version"
"$markdown_dir/gradlew" -p "$markdown_dir/smoke-test" compileKotlin "${gradle_args[@]}"
