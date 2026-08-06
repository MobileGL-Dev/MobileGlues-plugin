MobileGlues-plugin
====

> [!WARNING]
> 
> **This repository may contain unreleased Dev versions**
>
> If you are a regular user, **do not use these versions**, as they may cause **serious rendering issues**.  
> Please visit [MobileGlues-release](https://github.com/MobileGL-Dev/MobileGlues-release) to get the latest stable release.

Please see [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues) and [MobileGlues-release](https://github.com/MobileGL-Dev/MobileGlues-release) to get more information.

License
====
MobileGlues is licensed under **GNU LGPL-2.1 License**.

Please see [LICENSE](https://github.com/MobileGL-Dev/MobileGlues-plugin/blob/main/LICENSE).

Third party components
====
For the components used by the renderer itself, please see [MobileGlues-release](https://github.com/MobileGL-Dev/MobileGlues-release).

The plugin application additionally uses:

**Miuix** by **compose-miuix-ui** - [Apache License 2.0](https://github.com/compose-miuix-ui/miuix/blob/main/LICENSE): [github](https://github.com/compose-miuix-ui/miuix)

**Jetpack Compose** by **Android Open Source Project (AOSP)** - [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt): [Android Developers](https://developer.android.com/jetpack/compose)

**AndroidX** by **Android Open Source Project (AOSP)** - [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt): [Android Developers](https://developer.android.com/jetpack/androidx)

**kotlinx.coroutines** by **JetBrains** - [Apache License 2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt): [github](https://github.com/Kotlin/kotlinx.coroutines)

**Gson** by **Google** - [Apache License 2.0](https://github.com/google/gson/blob/main/LICENSE): [github](https://github.com/google/gson)

Check signature of your release
====
This portion is a guide to help you identify if your apk is an official release from
MobileGlues dev.

In your Android build-tools, find `apksigner`. Then run the following command:
```bash
apksigner verify --print-certs path/to/MobileGlues-plugin.apk
```

It should print out:
```bash
Signer #1 certificate DN: CN=MGDev, OU=MGDev, O=MGDev, L=Unknown, ST=Unknown, C=CN
Signer #1 certificate SHA-256 digest: 324f4efaff81632373dec9bc714a904b64740249410b551b61805340e42ff5d5
Signer #1 certificate SHA-1 digest: 615bc8b2741c24e7e5847b0c5c1d6816d5b0763a
Signer #1 certificate MD5 digest: 320ede9d22c709fe3792c804d5e00153
```

Check whether the `certificate DN` and `certificate digest` portion matches exactly like above.

In order that you may want to check against public key file, `pub.cer` and `pub.pem` are also provided.
You can use your utility as you like to check your apk against those files.
