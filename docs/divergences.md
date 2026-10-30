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
| Recording boundary application | `Trip.withRecordingBreaks` in core:logic normalizes breaks and annotates points; recording uses it, and metric calculations derive indices before filtering | Trip initialization and the recordingBreaks setter do this automatically | core:model cannot depend on core:logic; storage adapters must call the helper after decoding/loading and when replacing breaks |
| Location/time representation | Immutable LocationFix and Instant; non-finite timestamps cannot be represented | CLLocation and Date; continuity/normalization reject non-finite dates | Pure JVM recording math; unknown measurement fields retain -1 |
| Geodesic distance | WGS84 inverse iteration; spherical fallback only when the iteration does not converge near antipodes | CLLocation.distance(from:), whose implementation is supplied by Apple | CoreLocation is unavailable on the JVM; the port passes the unchanged iOS distance tolerances, but does not promise bitwise equality with Apple's distance implementation |
| Badge metadata | Color and unlock callback are deferred | Badge combines metadata, SwiftUI Color and unlock predicate | Design and award rules belong to their owning modules |
