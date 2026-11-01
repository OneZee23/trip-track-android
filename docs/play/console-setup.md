# Owner's Play Console checklist

Prepared for the first Android closed test. No Console fields have been changed and no release
has been uploaded. Complete the steps below in order on the final merged release. Policy links
were checked on 31 October 2026; Console wording may differ by account.

## Account and app

1. Use the existing personal developer account specified in `docs/port-plan.md`. Complete identity,
   contact, payment-profile and any Android-device verification shown on its dashboard. Do not
   choose Organization unless a real registered organization owns the app; that path requires its
   legal details and normally a D-U-N-S number. For distribution in the EEA, complete the Console's
   trader/non-trader declaration truthfully. Keep legal identity/address/phone details in Console,
   outside this public repository.
2. Create app: default language English (United States), app name from
   `listing/en-US/title.txt`, App, Free. Accept the developer-policy/export declarations after
   review. The release package is `com.onezee.triptrack`, never `.dev`. The app is free at launch;
   there are no ads or in-app purchases and PRO stays hidden.
3. Store settings: category Travel & Local. Use only relevant available tags for trip recording/
   travel; there is no turn-by-turn navigation claim. Enter a monitored support email, the real
   developer website and any required phone contact. `privacy@trip-track.app` is already the
   published privacy/deletion contact. The owner supplies other public/legal details privately.
   See [account setup](https://support.google.com/googleplay/android-developer/answer/13628312?hl=en).
4. Upload the 13 translations from [listing](listing/README.md), the 512 px icon and 1024×500
   feature graphic from [graphics](graphics/README.md). Capture real Android phone screenshots
   listed there; the existing iPhone screenshots and Robolectric map placeholders cannot serve
   as Android captures. Check the Console's minimum image requirements before saving.
5. Publish the [Android policy additions](privacy-policy-android.md) in both languages and make
   sure the actual in-app policy links and the store link open them over HTTPS. Publish an
   external account-deletion request page and enter that URL under Data safety. Use the real
   published URL, not a placeholder or an invented route.

Suggested deletion-page text for publication:

> Delete your TripTrack account and data. In the Android app, open Me → Settings → Account & Sync → Delete account and confirm. If you cannot use the app, email privacy@trip-track.app with the subject “Delete my TripTrack account” and the email used for Google sign-in. We verify ownership before deletion; do not send passwords or sign-in tokens. We respond to verified requests within 30 days. Account deletion removes live account data; database backups rotate within 30 days, access logs within 14 days and moderation records within two years. A non-identifying deletion audit record may remain. The in-app action also erases this phone's local library after the server confirms deletion.

A working email request mechanism on an explanatory web page is sufficient; it must be usable
without reinstalling/signing into the app. Name TripTrack and link the retention terms. See
[account-deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en-EN).

## App content forms

| Form | Answer / action |
|---|---|
| Privacy policy | The published HTTPS policy including Android additions; accessible without login |
| Ads | No |
| App access | Core diary works as a guest. Explain the optional Google account path below; account/sync features are restricted to signed-in users |
| Data safety | Use [data-safety.md](data-safety.md), no-Sentry case for this checkout. Reconcile social-branch additions and every active track |
| Background location | Declare the single automatic-recording feature; paste [permissions.md](permissions.md) text and attach the real video |
| Foreground services | Declare location, with its own user-visible recording demonstration; no other FGS type in this manifest |
| Target audience | Adults, 18 and over, consistent with a driving diary and the public tile provider's adult audience; not designed for children. Existing policy minimum is 16, so select 16–17 as well only if the owner deliberately targets them and resolves provider terms first |
| News and magazines | No; trip posts do not make it a news publisher |
| Health apps | No health/medical functionality. Activity Recognition is for driving, not health/fitness tracking |
| Financial features | None; vehicle fuel/energy estimates are trip calculations, not financial services |
| Government apps | No |
| Advertising ID | No advertising ID use and no AD_ID permission |
| Photo/video permissions | No broad media-library permission; system picker only |

Target audience describes intended users, not the IARC age rating. Do not select younger ages
merely because a questionnaire gives a low content rating. Google reviews the declared audience
against the actual product. [Target audience guidance](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en-en).

Content rating: provide the owner's monitored questionnaire email, choose the non-game/general
app category closest to a travel utility, then answer for the binary actually submitted:

| Questionnaire topic | Current answer |
|---|---|
| Built-in violence, fear/horror, sexual content/nudity, profanity, drugs/alcohol/tobacco | No authored content of those kinds |
| Gambling, simulated gambling, prize contests or cash rewards | No |
| Paid random items, in-app purchases | No |
| User-generated content / online content exchange | Yes if the submitted build includes public trip/profile/photo surfaces. Profile photos and the existing public service already require a truthful UGC assessment; never copy the old iPhone “no comments” answer after w4-social |
| Users can communicate/share user content | Yes for public trips/photos and comments when merged; no private direct-message chat in this checkout |
| Report/block users/content | Account blocked list exists; verify actual report and block actions on each merged social surface before answering Yes. Do not ship public UGC without the required controls/terms acceptance |
| Location sharing | Yes when users publish routes; no unsolicited live location sharing or people-nearby discovery |
| Unrestricted web browser | No; external links open the user's browser |
| Digital goods/cryptocurrency/financial transactions | No |

Let IARC generate regional ratings; do not enter a guessed PEGI/ESRB age. Questionnaire details
can change with the social merge. See [content ratings](https://support.google.com/googleplay/android-developer/answer/9859655?hl=en&rd=2),
[online content exchange](https://support.google.com/googleplay/android-developer/answer/7021383?hl=en)
and [UGC requirements](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en-GB).

## Review access instructions

Paste this in App access / review instructions:

> No sign-in, subscription, invitation or payment is required for recording, local trip history, trip details, Atlas, Garage and settings. Complete onboarding; each permission may be denied and the diary remains accessible. To record a route, grant precise location; automatic background recording additionally needs “Allow all the time” and the configured trigger permissions. To test Cloud Sync as a guest, open Me and use Sign in with Google. After sign-in, open Me → Settings → Account & Sync, turn on Cloud Sync and confirm. Cloud Sync is off by default. It and account deletion require a Google account and an internet connection. There is no email/password login or special membership. Android accounts are currently separate from iPhone accounts.

“Everything works without signing in” accurately describes access to the core local diary, but
not Cloud Sync, profile uploads or account deletion. If Console asks whether *any* functionality
is restricted, select some functionality restricted and include the instructions above. If a
reviewer-owned Google account is not accepted by Google's review-access rules, the owner must
provide a reusable review account/access mechanism privately in Console and verify it works
without OTP, geographic restrictions or a paid membership. Do not publish credentials here or
assume third-party sign-in exempts optional account features from review.
[Google's review-access requirements](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en).

## Signed candidate and first installation

6. Complete the Google OAuth setup in [sign-in.md](../sign-in.md): Web client ID as the build's
   `GOOGLE_WEB_CLIENT_ID`, release Android client for the Play app-signing SHA-1/package,
   branding/audience, and the Google-login backend branch deployed with its allowed Web client
   IDs. OAuth test-user lists are separate from Play tester lists. The current build can launch
   without OAuth configured, but that does not make sign-in ready for review.
7. Follow [release.md](../release.md) for parity, increasing `versionCode`, upload-key storage and
   signing. Enable Play App Signing. Android Studio's Generate Signed Bundle → Android App
   Bundle → release uses the owner's external keystore. `:app:bundleRelease` alone is currently
   unsigned. Keep passwords and keys outside git. Record the final source commit, iPhone
   reference, version, AAB hash, tests and manifest. This task does not change signing or versions.
8. Prefer a first internal-test installation through Play. Test the production package/signature,
   Google login, background record after screen lock, notification Pause/Resume/Finish, restart
   recovery, stereo start/stop, denial paths, privacy-zone uploads, Cloud Sync on two Android
   phones, photo upload/deletion, account deletion and upgrades. Review pre-launch report and
   Android vitals warnings; a Robolectric pass does not establish GPS/map/OEM behaviour.
9. Create a closed-testing release from the verified signed AAB, select countries, release notes,
   tester list and feedback contact, resolve Console errors, send the release for review and roll
   it out when approved. Keep external links and declarations accessible to reviewers throughout.

## Closed test

For a personal account created after 13 November 2023, production access requires at least
12 testers opted in continuously for the preceding 14 days. Internal testing does not count;
being entered on an email list does not count as opting in. An organization account does not
have this specific new-personal-account gate. This project's plan expects the personal-account
gate. [Google's closed-test requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-mt).

1. Closed testing → Testers → create/select an email list. Enter each tester's Google-account
   email (or import the supported CSV); keep the list private in Console. A Google Group is an
   alternative. Save and attach the list to the closed track.
2. After the release is available, copy the opt-in link from that track. Send it yourself to the
   selected testers with instructions and the feedback channel. This task sends no messages.
3. Each tester opens the link while signed into the exact listed Google account, accepts joining,
   then installs from Play under that same account. A different browser/Play account commonly
   causes “not available”. Confirm the correct country and an Android 10+ supported device.
4. Keep at least 12 people opted in continuously for 14 full days. Recruit a reserve; someone who
   leaves and rejoins starts their own continuous period again. Ask testers to exercise real
   features and report results; a dormant email list is not testing.
5. Collect feedback privately: device/OS, permission decisions, background start/stop, battery
   behaviour, route correctness, map rendering, denied permissions, offline diary, sync and delete
   results. Use consented/synthetic routes, not testers' home coordinates in public bug reports.
6. After the dashboard shows eligibility, apply for production access. Answer how testers were
   recruited and engaged, what they tested, what feedback changed, and why the app is ready.
   Fourteen days is a minimum, not automatic approval. Google may request more testing.

Google reviews policy compliance and app access, the actual core background-location feature
and its video/disclosure, FGS use, data handling/deletion, honest listing/graphics, target audience,
UGC controls, technical quality and the test/production-access answers. Closed-test approval,
production access and a production release are separate steps. Check the actual track/review
status after every submission.
