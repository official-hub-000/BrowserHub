# Getting a Titanium clone APK

This repository produces a Titanium clone. It **cannot produce the APK inside
the workspace it was prepared in** — that is a hardware and network limit, not a
code problem:

| Need | Sandbox |
|---|---|
| Chromium source (~100 GB+) | `chromium.googlesource.com` unreachable |
| ~100–150 GB free disk | 20 GB |
| 16 GB+ RAM | shared |
| `sudo` for `install-build-deps.sh` | uid 1001, no root |
| 3–6 hours | — |

Everything below is for a machine that has those. Pick one row.

---

## Option A — your own Linux machine (fastest if you have the disk)

```bash
sudo apt-get install -y git python3 imagemagick librsvg2-bin
git clone https://github.com/official-hub-000/BrowserHub.git
cd BrowserHub/titanium

git submodule update --init --recursive        # Vanadium — required, ~1 min
./verify-identity.sh                           # 20 cheap checks first

# choose features (optional; default = every Titanium feature on)
./feature-picker.sh --list

# Signing is REQUIRED, not optional: set_keys decodes both variables
# unconditionally and sign_apk exits 1 if apksigner fails.
cp local.properties.example local.properties      # then fill in
keytool -genkeypair -v -keystore release.jks -alias titanium \
        -keyalg RSA -keysize 2048 -validity 10000
export LOCAL_TEST_JKS=$(base64 -w0 local.properties)
export STORE_TEST_JKS=$(base64 -w0 release.jks)
./build.sh
```

Output: `out/release/<version>-arm64-v8a.apk` (and `-armeabi-v7a.apk`).

`build.sh` clones Chromium, applies ~200 Vanadium patches plus 22 Titanium
feature blocks, and builds both ABIs. Expect 3–6 hours on 8–16 cores.

---

## Option B — Docker (no toolchain setup)

```bash
docker build -f Dockerfile.build -t titanium-build .
docker run --rm -v "$PWD:/src" -v "$PWD/out:/out" titanium-build \
    bash -lc 'cd /src/titanium && ./build.sh && cp out/release/* /out/'
```

Make sure `/var/lib/docker` has ~150 GB, or you will run out of disk mid-checkout.

---

## Option C — a cloud VM (rent by the hour)

Any provider; the shape that works:

| | |
|---|---|
| Instance | 8–16 vCPU, 32 GB RAM |
| Disk | **200 GB** gp3/SSD |
| Image | Ubuntu 24.04 |
| Cost | roughly a few dollars for one build |

Then run Option A's commands. Delete the instance afterwards.

---

## Option D — your own GitHub Actions runner (free, repeatable)

Upstream's workflow already expects this and is unchanged here:

`.github/workflows/build.yml` → `runs-on: self-hosted`.

Register a machine as a runner (repo → Settings → Actions → Runners → New
self-hosted runner), add the two secrets, then trigger **Build** from the Actions
tab. It publishes signed APK/AAB as a GitHub Release automatically.

> A **hosted** `ubuntu-latest` runner cannot do this: 14 GB free disk vs 100–150
> GB needed. That is why upstream pins `self-hosted`.

---

## Verifying what you get is Titanium

The clone is exact only if no feature was disabled:

```bash
./verify-identity.sh          # package id, no foreign identity, fragment integrity
ls features/enabled.env       # absent == stock (all features on)
```

The package installs as `io.github.jqssun.helium`. Because that is Titanium's own
id, it will **conflict with an installed Titanium** — uninstall that first, or
change `chrome_public_manifest_package` in `args.gn` if you need both side by
side (in which case it is a fork, not an exact clone).

## Why not ship a prebuilt APK instead

The upstream project publishes signed APKs (v157.0.8103.78, 320 MB arm64). They
could not be transferred into this workspace: GitHub release assets are served
from `objects.githubusercontent.com`, which is blocked here, the API caps binary
blobs at 100 MB, and the smallest APK across all 61 releases is 179.6 MB.
Downloading one yourself from
<https://github.com/jqssun/android-titanium-browser/releases> is the one-step
alternative if you simply want a working Titanium — but note that is *upstream
Titanium*, with no feature switches. Only a build from this tree gives you the
control layer.
