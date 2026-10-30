# Where Android differs from the iPhone app

Every intentional difference is listed here with its reason. Anything not listed is expected to
behave exactly like the iPhone app.

| Area | Android | iPhone | Why |
|---|---|---|---|
| Purchases | No in-app purchases at launch; PRO is hidden like in the iOS Russian storefront | StoreKit PRO and tips | Google Play pays nothing to a payout bank in Russia (Dec 2024) |
| Sign-in | Google only | Sign in with Apple only | Owner decision 30 Oct 2026: separate accounts per platform for now |
| Domain representations | GeoPoint, UUID, Instant; associated enums use sealed types | CLLocationCoordinate2D, UUID, Date, associated Swift enums | Pure JVM domain types; JSON shapes and units stay the same |
| Snapshot equality | Kotlin data class equality; compare ByteArray contents explicitly | Trip and map models use selective SwiftUI equality | UI diffing optimizations belong to the Android presentation layer |
| Model initialization | Currency is supplied explicitly; unconfigured fallback is € | Vehicle reads FuelCurrency.current from UserDefaults | Keep settings access outside pure JVM domain types |
| Places sort spelling | Kotlin entry `PlacesSort.byName`, raw/JSON value `name` | Swift case `PlacesSort.name` | Kotlin enums inherit a `name` property; persisted values stay the same |
| Wave 0 model scope | recordingBreaks stores data; normalization/track annotation is pending core:logic | Trip normalizes breaks and annotates points during initialization | This task ports data shapes only; recording must wait for the algorithm port |
| Badge metadata | Color and unlock callback are deferred | Badge combines metadata, SwiftUI Color and unlock predicate | Design and award rules belong to their owning modules |
