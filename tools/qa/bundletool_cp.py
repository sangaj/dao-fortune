"""Assemble a bundletool classpath from the local Gradle cache.

The AGP-bundled `com.android.tools.build:bundletool` jar has no Main-Class, so the
CLI has to be launched by class name with its dependencies on the classpath. Those
dependencies are all already in the Gradle cache — nothing has to be downloaded.

    $cp = python tools\qa\bundletool_cp.py
    java -cp $cp com.android.tools.build.bundletool.BundleToolMain validate --bundle=dist\app-release.aab

One trap worth remembering: the cache may hold `guava-*-android.jar`, the stripped
down flavour used by AndroidX. Its `Predicates` does not implement
`java.util.function.Predicate`, and bundletool dies with
`IncompatibleClassChangeError` when it is on the classpath. This script prefers the
`-jre` flavour.
"""
import os
import sys

ROOT = os.environ.get(
    "GRADLE_USER_HOME",
    r"E:\Minimax\toolchain\gradle-home",
) + r"\caches\modules-2\files-2.1"

# group -> artifact, exactly as the bundletool jar's Class-Path header lists them
WANTED = [
    ("com.android.tools.build", "bundletool"),
    ("com.android.tools.build", "aapt2-proto"),
    ("com.android.tools.build", "apksig"),
    ("com.google.auto.value", "auto-value-annotations"),
    ("com.google.errorprone", "error_prone_annotations"),
    ("com.google.guava", "guava"),
    ("com.google.guava", "failureaccess"),
    ("com.google.guava", "listenablefuture"),
    ("com.google.protobuf", "protobuf-java"),
    ("com.google.protobuf", "protobuf-java-util"),
    ("com.google.dagger", "dagger"),
    ("javax.inject", "javax.inject"),
    ("org.bitbucket.b_c", "jose4j"),
    ("org.slf4j", "slf4j-api"),
    ("com.google.code.findbugs", "jsr305"),
    ("org.checkerframework", "checker-qual"),
    ("com.google.j2objc", "j2objc-annotations"),
    ("com.google.code.gson", "gson"),
    ("com.google.android", "annotations"),
]


def main() -> int:
    found = {}
    missing = []
    for group, artifact in WANTED:
        base = os.path.join(ROOT, group, artifact)
        if not os.path.isdir(base):
            missing.append(f"{group}:{artifact}")
            continue
        jars = []
        for dirpath, _dirs, files in os.walk(base):
            for name in files:
                if name.endswith(".jar") and not name.endswith("-sources.jar"):
                    jars.append(os.path.join(dirpath, name))
        jre = [j for j in jars if "-android" not in os.path.basename(j)]
        if jre:
            jars = jre
        if not jars:
            missing.append(f"{group}:{artifact} (no jar)")
            continue
        found[f"{group}:{artifact}"] = sorted(jars, key=os.path.getmtime)[-1]

    if missing:
        print("MISSING: " + ", ".join(missing), file=sys.stderr)
    print(os.pathsep.join(found.values()))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
