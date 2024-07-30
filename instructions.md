Welcome to my spaghetti

- Create `./app/src/main/java/com/arn/scrobble/Tokens.kt`
```
package com.arn.scrobble
object Tokens {
    const val LAST_KEY = "" // https://www.last.fm/api/account/create
    const val LAST_SECRET = ""
    const val ACR_HOST = "" // https://console.acrcloud.com/
    const val ACR_KEY = ""
    const val ACR_SECRET = ""
    // https://developer.spotify.com/dashboard/
    const val SPOTIFY_REFRESH_TOKEN = "<base64 of spotify client id>:<base64 of spotify client secret>"
    const val PRO_PRODUCT_ID = "" // play store product ID for IAP
    const val SIGNATURE = "" // apk signature
    const val BASE_64_ENCODED_PUBLIC_KEY = "" // (of the signing key)
    const val EMBEDDED_SERVER_KEYSTORE_PASSWORD = "" // password for the embedded https server BKS keystore, used for importing settings over local network
}
```
- Remove or comment out the lines below `// remove if not needed` in app/build.gradle.kts and /build.gradle.kts

- Create app/version.txt and put a positive integer in it without a trailing newline. This will be your app version code.
The version name will be derived from this.

`echo -n 999 > ./app/version.txt`

- Create a Firebase project for Crashlytics and add google-services.json. Put it in `./app/src/google-services.json`.
Unless you intend on publishing the app as [`com.arn.scrobble`](https://play.google.com/store/apps/details?id=com.arn.scrobble) (you probably do not), change the `applicationId` in `./app/build.gradle.kts` to the one you will use with firebase.
See https://firebase.google.com/docs/android/setup

- Create a BKS keystore for the embedded https server with the password EMBEDDED_SERVER_KEYSTORE_PASSWORD and alias selfsigned.
Put it in `./app/src/main/res/raw/embedded_server_bks.bks`

- If you are not using Android Studio which may do this for you (for example you are building on the command line or CI),
create a keystore and reference it in the `./local.properties`.
```
release.keystore=keystore.kjs
release.storePassword=keystore.kjs
release.alias=keystore.kjs
release.password=keystore.kjs
```

`keytool -genkey -alias keystore.jks -keyalg RSA -keystore ./app/keystore.jks -keysize 2048`

- Obtain now playing notification strings and their translations by decompiling the resources of
the Android System Intelligence and Shazam apks respectively with ApkTool and then running [py-scripts/np-strings-extract.py](py-scripts/np-strings-extract.py) on them.

Usage: `python ./np-strings-extract.py <decompiled-dir> song_format_string np` for scrobbling Pixel Now Playing and

`python ./np-strings-extract.py <decompiled-dir> auto_shazam_now_playing sz` for scrobbling AutoShazam.
    
Alternatively, you can use this as a stub in `strings.xml`:
```
<string name="song_format_string">%1$s by %2$s</string>
<string name="auto_shazam_now_playing">%1$s by %2$s</string>
```

- If you want to generate the optional baseline profile for the app, which can improve its startup time,
create a file `/baselineprofile/src/main/java/com/arn/scrobble/baselineprofile/Secrets.kt`:
```
object Secrets {
    const val loginCreds = "<lastfmUsername>,<lastfmSessionKey>,"
}
```

lastfmSessionKey can be obtained by logging in to LastFM with this app and intercepting auth.getSession
or by looking into /data/data/com.arn.scrobble/files/harmony_prefs/main/prefs.data and searching for `authkey`
