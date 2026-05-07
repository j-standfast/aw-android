# Building the QLife fork of aw-android

End-to-end recipe to produce a signed APK identical in behavior to upstream
v0.12.1, built from a clean clone. Tracked under
[QLI-554](https://linear.app/standfast/issue/QLI-554).

## Prerequisites

- JDK 17 (Gradle 8.1 + AGP 8.1.1 reject newer JDKs at build time).
- Android SDK with `platforms;android-34`, `build-tools;34.0.0`, `ndk;25.2.9519653`.
  Install via `~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager`.
- Rust stable + Android targets:
  `rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android`.
  (Upstream README says nightly; aw-server-rust CI uses stable, and stable
  is sufficient for the targets used here.)
- Node 20+ (Vue 2 vue-cli build).
- A QLife-owned signing keystore at `~/.qlife/secrets/aw-android.jks`.
  Passwords live in trunk's `packages/server/.env` under
  `AW_ANDROID_KEYSTORE_PATH`, `AW_ANDROID_KEYSTORE_PASSWORD`,
  `AW_ANDROID_KEY_ALIAS`. **Do not lose this key** — Android refuses
  upgrades from differently-signed APKs, so a lost key forces uninstall +
  reinstall (data loss) on every phone running the fork.

## Build

```sh
git clone --recurse-submodules -b qlife https://github.com/j-standfast/aw-android.git
cd aw-android

# The aw-server-rust submodule already points at j-standfast/aw-server-rust@qlife
# which carries the time-0.3.30 → 0.3.44 Cargo.lock bump. Building from upstream
# ActivityWatch/aw-android directly would hit `error[E0282]: type annotations
# needed for Box<_>` in time's parser on Rust 1.80+; the fork sidesteps that.

# Native libs (release mode, all 4 archs; ~5 min)
( cd aw-server-rust && \
    ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/25.2.9519653 RELEASE=true make android )

# Web UI (~30 s)
( cd aw-server-rust && ON_ANDROID=true make aw-webui )

# Wire libs into mobile/src/main/jniLibs
RELEASE=true make aw-server-rust

# APK
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=$JAVA_HOME/bin:$PATH \
ANDROID_HOME=$HOME/Android/Sdk \
ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/25.2.9519653 \
  ./gradlew assembleRelease
```

Output: `mobile/build/outputs/apk/release/mobile-release-unsigned.apk` (~106 MB).

## Sign

```sh
source <(grep AW_ANDROID_ ~/r/qlife/code/trunk/packages/server/.env)
mkdir -p dist

~/Android/Sdk/build-tools/34.0.0/zipalign -p 4 \
  mobile/build/outputs/apk/release/mobile-release-unsigned.apk \
  dist/mobile-release-aligned.apk

~/Android/Sdk/build-tools/34.0.0/apksigner sign \
  --ks "$AW_ANDROID_KEYSTORE_PATH" \
  --ks-key-alias "$AW_ANDROID_KEY_ALIAS" \
  --ks-pass "pass:$AW_ANDROID_KEYSTORE_PASSWORD" \
  --key-pass "pass:$AW_ANDROID_KEYSTORE_PASSWORD" \
  --out dist/aw-android-qlife-v0.12.1-qlife.N.apk \
  dist/mobile-release-aligned.apk

~/Android/Sdk/build-tools/34.0.0/apksigner verify -v \
  dist/aw-android-qlife-v0.12.1-qlife.N.apk
```

`pass:` is the same value for both `--ks-pass` and `--key-pass` because the
default PKCS12 keystore format does not support distinct passwords.

## Release

1. Bump the suffix (`qlife.N`) for each release tag.
2. `git tag -s v0.12.1-qlife.N` on the QLife fork remote.
3. `gh release create v0.12.1-qlife.N dist/aw-android-qlife-v0.12.1-qlife.N.apk`.
4. Obtainium on the phone tracks `j-standfast/aw-android` releases.

## First-time keystore generation

Done once; back the resulting `.jks` up alongside other QLife secrets.

```sh
mkdir -p ~/.qlife/secrets && chmod 700 ~/.qlife/secrets
STOREPASS=$(openssl rand -base64 24 | tr -d '/=+')
keytool -genkeypair -v \
  -keystore ~/.qlife/secrets/aw-android.jks \
  -storepass "$STOREPASS" \
  -alias qlife-aw-android \
  -keypass "$STOREPASS" \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=QLife AW-Android Fork,O=QLife,C=US"
chmod 600 ~/.qlife/secrets/aw-android.jks
echo "AW_ANDROID_KEYSTORE_PASSWORD=$STOREPASS"   # paste into packages/server/.env
```
