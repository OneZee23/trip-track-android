# TripTrack for Android

Every road you drive clears the fog on your own map. This is the Android version of
[TripTrack](https://github.com/OneZee23/trip-track-ios), a road trip diary that records your drives
and turns them into an atlas. Same account, same trips, same atlas on both phones.

The port is in progress. The plan and the current state are in [docs/port-plan.md](docs/port-plan.md)
and [docs/port-status.md](docs/port-status.md).

- Website: https://trip-track.app
- iPhone app: https://apps.apple.com/app/id6760650361

## Building

Requires JDK 21 and the Android SDK (platform 36).

```bash
./gradlew :app:assembleDebug
./gradlew test
```

## License

MIT, see [LICENSE](LICENSE).
