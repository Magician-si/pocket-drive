# Pocket Drive

A standalone local-network file-sharing app for Android, written in a single
`MainActivity.java` (no third-party libraries). Point it at a folder, and any
device on the same LAN can browse, preview, upload and download files through
a browser, or mount it as a WebDAV drive.

独立网页文件共享：把手机 / 电视上的一个文件夹共享到局域网，浏览器可浏览、
预览、上传、下载，也支持 WebDAV 挂载。

## Features / 功能

- HTTP server on port 8080, HTTPS server on port 8443 (self-signed cert)
- WebDAV: PROPFIND / PUT / MKCOL / DELETE / MOVE / COPY / LOCK / UNLOCK
- Browser UI: file list, image / audio / video preview, upload, download
- QR code for the share address, plus a downloadable certificate (`/cert.cer`)
- Trash with 7-day / 30-day auto-cleanup (files go to the share folder's `.trash`)
- Wake lock + Wi-Fi lock while sharing, so the device does not sleep mid-transfer
- Web settings page: change the account password and the trash retention

## Two builds / 两个版本

Same code, same package (`com.magician.pocketdrive`), two manifests:

| Build | File | Difference |
|---|---|---|
| Phone / 手机 | `pocketdrive-phone.apk` | Normal `LAUNCHER`, touchscreen required, first run asks you to pick a share folder |
| TV / 电视 | `pocketdrive-tv.apk` | `LEANBACK_LAUNCHER` + banner, touchscreen not required, landscape, HTTP enabled by default, uses the built-in folder on first run |

Because both share one package name, install only one of them on the same
device — installing the second will replace the first.

同一包名，同一台设备只能装一个，装第二个会覆盖第一个。

## Install / 安装

1. Sideload the matching APK (phone build on a phone, TV build on a TV).
2. Open Pocket Drive, tap 开启共享.
3. On a phone, pick a share folder on first run. On a TV, the default folder
   inside the app's external files directory is used.
4. Scan the QR code, or open the address shown (for example
   `https://192.168.1.10:8443`).

## Security — read this before sharing / 安全提示

- **Default account is `admin` / `admin123456`.** Change it on the web
  settings page immediately after the first start. This default is in the
  source code, so once this repo is public, everyone knows it.
  默认账号 `admin` / `admin123456`，首次启动后请立即在网页设置里改掉。
  开源后这个默认值就是公开的，千万不要用默认密码长期共享。
- The HTTPS certificate is self-signed and generated fresh at build time
  (see below). Browsers will warn about it until you install the cert from
  `/cert.cer` on the client device.
- Only share on a trusted home LAN. Do not expose ports 8080 / 8443 to the
  internet. 仅限可信的家庭局域网，不要把端口暴露到公网。

## Build / 编译

No Gradle is required. You need JDK 17 and Android SDK build-tools 34.0.0
with platform android-34:

```sh
./build.sh phone   # dist/pocketdrive-phone.apk
./build.sh tv      # dist/pocketdrive-tv.apk
./build.sh both    # both
```

Pipeline: `aapt2` compile + link → `javac` → `d8` → `zipalign` → `apksigner`
(debug key). The APKs are debug-signed, suitable for sideloading, not for
Google Play release signing.

### TLS certificate (generated locally, never committed)

The code loads `assets/pocketdrive_tls.p12` (alias and password
`pocketdrive`). `build.sh` generates a fresh self-signed `.p12` with
`keytool` on first build and keeps it under `build/`, which is gitignored.
**Do not commit a `.p12` or a keystore to this repo** — anyone with the
private key could impersonate your server's certificate.

## Project layout

```
app/src/main/java/com/magician/pocketdrive/MainActivity.java  # the whole app
app/src/main/AndroidManifest-phone.xml
app/src/main/AndroidManifest-tv.xml
app/src/main/res/              # launcher icon + TV banner
build.sh                       # manual aapt2/javac/d8 build
```

## License

MIT — see `LICENSE`.
