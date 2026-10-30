# Where Android differs from the iPhone app

Every intentional difference is listed here with its reason. Anything not listed is expected to
behave exactly like the iPhone app.

| Area | Android | iPhone | Why |
|---|---|---|---|
| Purchases | No in-app purchases at launch; PRO is hidden like in the iOS Russian storefront | StoreKit PRO and tips | Google Play pays nothing to a payout bank in Russia (Dec 2024) |
| Sign-in | Google only | Sign in with Apple only | Owner decision 30 Oct 2026: separate accounts per platform for now |
| Domain representations | GeoPoint, UUID, Instant; associated enums use sealed types | CLLocationCoordinate2D, UUID, Date, associated Swift enums | Pure JVM domain types; JSON shapes and units stay the same |
| Snapshot equality | Kotlin data class equality, including TripRouteLocator.Fix; compare ByteArray contents explicitly | Trip and map models use selective SwiftUI equality; route Fix compares only its index | UI diffing optimizations belong to the Android presentation layer |
| Model initialization | Currency is supplied explicitly; unconfigured fallback is € | Vehicle reads FuelCurrency.current from UserDefaults | Keep settings access outside pure JVM domain types |
| Places sort spelling | Kotlin entry `PlacesSort.byName`, raw/JSON value `name` | Swift case `PlacesSort.name` | Kotlin enums inherit a `name` property; persisted values stay the same |
| Recording boundary application | `Trip.withRecordingBreaks` in core:logic normalizes breaks and annotates points; recording uses it, and metric calculations derive indices before filtering | Trip initialization and the recordingBreaks setter do this automatically | core:model cannot depend on core:logic; storage adapters must call the helper after decoding/loading and when replacing breaks |
| Location/time representation | Immutable LocationFix and Instant; non-finite timestamps cannot be represented | CLLocation and Date; continuity/normalization reject non-finite dates | Pure JVM recording math; unknown measurement fields retain -1 |
| Geodesic distance | WGS84 inverse iteration; spherical fallback only when the iteration does not converge near antipodes | CLLocation.distance(from:), whose implementation is supplied by Apple | CoreLocation is unavailable on the JVM; the port passes the unchanged iOS distance tolerances, but does not promise bitwise equality with Apple's distance implementation |
| Badge metadata | Color and unlock callback are deferred | Badge combines metadata, SwiftUI Color and unlock predicate | Design and award rules belong to their owning modules |
| Credential storage | Android Keystore AES-256-GCM key, credential-encrypted AtomicFile in noBackupFilesDir; package + API host scope | AfterFirstUnlockThisDeviceOnly Keychain items | Native Android protection, no backup or device transfer; locked/corrupt/unreadable storage returns unavailable |
| Credential writes | One encrypted snapshot commits tokens and session markers together; write failures propagate | Keychain updates each item separately; TokenStore suppresses save errors | Preserve the prior pair when encryption or writing fails and prevent reporting an unpersisted rotation as recovered |
| Session identity | Successful login's backend account UUID identifies the account | Apple credential subject plus backend account UUID | Google proof is verified by the server; the client does not trust locally decoded token claims |
| Legacy credentials | No migration from the historical iOS Keychain service | Copies legacy Keychain items into app/host scope | Android has no previous Keychain installation or transferable session |
| HTTP transport | OkHttp HTTP/1.1 and HTTP/2; cancellable coroutine calls | URLSession can also choose HTTP/3 | The selected Android network stack has no HTTP/3 transport |
| Network failures | EOF/reset maps to connectionLost; socket timeout to timedOut; connect failure to cannotConnect; DNS/no-route to offline | URLError has platform-specific error codes | Preserve retry classes with the exceptions Android exposes; DNS/no-route cannot distinguish offline from an unavailable host |
| Redirects | API and photo redirects fail with their HTTP status | URLSession follows redirects | Prevent forwarding x-access-token or bypassing the photo host allowlist on a redirected destination |
| Diagnostic logs | Mask sensitive values with a bounded linear scan; URLs lose IDs/query; response logging contains only status | APILogger uses regex and includes response previews | Java regex over long escaped strings can overflow its stack; status-only responses avoid logging server content |
| Me response encoding | bioProvided stays local; an explicitly cleared bio round-trips as null | Synthesized Swift response encoding can include its local presence flag | Follow docs/api-contract.md's explicit rule that the presence flag is never a wire key; Me is a response, not an outgoing account patch |
| Polyline preview cache | Synchronized access-order cache, at most 200 trips | NSCache with countLimit 200 and system memory-pressure eviction | NSCache is unavailable on the JVM; coordinate values and explicit invalidation are preserved |
| Places text comparison | Locale-aware JVM case folding, Unicode normalization and JDK Collator | Foundation case/diacritic-insensitive search and localizedCaseInsensitiveCompare | Foundation is unavailable on the JVM; platform Unicode/collation tables can differ. The port keeps stable ties and the iOS fixtures |
| Geo presentation dependencies | Callers supply resource formatters/checkpoint words, locale, ZoneId and persisted sort raw value | AppStrings, LanguageManager, Calendar and UserDefaults | core:logic is pure JVM; translations and settings stay in their owning adapters |
