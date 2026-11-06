# Ruler

**A measuring toolkit for Android: measure objects, find a ring size, check a level, and keep your measurements together.**

Created by [blue0orbit](https://x.com/blue0orbit). Open source under the [MIT license](LICENSE).

[Follow the project on X](https://x.com/blue0orbit) · [Explore the features](#features) · [Build it yourself](#build-and-run) · [Contribute](#get-involved)

## Features

### Ruler and calibration

- Measure on the screen by tapping or dragging the measuring line to the end of an object.
- Switch between millimetres, centimetres, and inches, including decimal and fractional inch readouts.
- Calibrate against a payment card, a supported coin, or an object of a known length.
- See whether the scale is estimated from the display or calibrated against a physical reference, with an accuracy estimate.
- Use simple mode for everyday measuring, or enable precise controls for label size, bezel inset, and calibration details.
- Adjust the size of ruler labels without changing the physical measuring scale.
- Enable or disable haptic tick feedback.

### Ring sizing

- Match an on-screen circle to the inside of an existing ring.
- Measure a marked paper strip wrapped around a finger.
- View sizes in US/Canada, UK/Australia, EU/ISO, and Japanese systems.
- See between-size readings and calibration guidance, then save the result to the measurement log.

### Measurement log

- Save readings with a name, note, and date.
- Search saved measurements by name or note.
- Edit entries, delete them, and undo a deletion.
- Export the complete log as CSV or share it through another app.
- Keep measurements on the device using a local database.

### Pro tools in the app

The existing app has a one-time Pro unlock through Google Play. Its Pro features include:

| Feature | What it does |
| --- | --- |
| Extend mode | Add successive measured segments for objects longer than the screen, undo the last mark, and see a total with an error estimate. |
| Bubble level | Check tilt, set a reference surface as flat, and clear that reference. Sensor availability and accuracy affect the reading. |
| Protractor | Set reference edges and measure their angle with a guided flow and a full-circle dial. |
| Custom colours | Choose additional palettes, including sepia and high contrast. |
| Log tags | Add tags to saved measurements. |
| Ad removal | Remove the occasional ad shown in the free version. |

The ruler, all units and fractions, calibration, ring sizing, basic log, CSV export, and sharing are free in the app. The source for both free and Pro features is included under MIT; forks can adapt the app to their own needs.

### Everyday use

- Light, dark, and system theme choices.
- English and German interface text.
- Introductory guidance and adjustable ruler labels.
- Measuring, saved measurements, and export work offline. Advertising, consent services, and Google Play purchases use network services.
- Android 8.0 (API 26) and later.

**Accuracy matters:** calibrate before measuring. Screen dimensions, physical alignment, and device sensors affect results. Ring sizes and extended measurements are estimates.

## Built locally with Qwen and Strata

I built this project using **Qwen3.8-Flash-Next**, running locally through [Strata Engine](https://github.com/Niko1221/Strata).

My setup used **IQ3_XXS quantization**, a **256K-token context window**, and **CPU image processing** for screenshots and other image inputs.

This repository is the app source. Model weights, local engine settings, private working notes, and development screenshots are not included.

I’m sharing the app so others can explore the code, build their own version, and follow what I make next.

## Get involved

- **Follow [@blue0orbit on X](https://x.com/blue0orbit)** for project updates and more local-model experiments.
- **Tell me what you would add next:** a new measuring tool, a usability improvement, or another language.
- **Found a bug?** Open an issue with your Android version, device model, and steps to reproduce it. For measuring problems, include the calibration method and expected result.
- **Want to contribute?** Open an issue to discuss larger changes, or send a focused pull request with a clear explanation and relevant checks.
- **Made a fork or shipped your own version?** Share it with me on X. I’d like to see what you build.
- If this project is useful, a GitHub star helps other people discover it.

## Build and run

Open this folder in Android Studio and install Android SDK 36. Use JDK 17 or a compatible newer JDK. The app supports Android 8.0 (API 26) and later.

Let Android Studio configure your local SDK location in `local.properties`, then run:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

On Windows, use `gradlew.bat` instead of `./gradlew`. Install the debug APK from `app/build/outputs/apk/debug/` or run through Android Studio.

The project uses Kotlin, Jetpack Compose, Room, and DataStore. Source and tests are in `app/src/`; `app/schemas/` contains Room database schemas for migrations.

## Release configuration

The app includes Google Mobile Ads, consent handling, and Google Play Billing. Default builds use Google's test ad IDs. Debug builds use a test entitlement implementation; release purchases require your own Google Play configuration.

Supply your own `admobAppId` and `admobInterstitialId` through private user Gradle properties. Keep production configuration and signing credentials out of the repository.

To sign locally, create an untracked `keystore.properties` containing `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`, pointing to your private signing key. Without it, `assembleRelease` produces an unsigned APK. Configure your own store listing, billing products, consent settings, and privacy information before distribution.

## Author

Created by **blue0orbit**.

- Email: [blue0orbit@gmail.com](mailto:blue0orbit@gmail.com)
- X: [@blue0orbit](https://x.com/blue0orbit)

## License

[MIT License](LICENSE). Copyright (c) 2026 blue0orbit.

You are welcome to fork, modify, reuse, redistribute, and publish this app, including on the Play Store, under the MIT license. Please retain the copyright notice and license. A visible credit to blue0orbit with a link to https://x.com/blue0orbit is appreciated but optional.
