# w17: avatars of our own, the same on both phones

The owner, 4 Nov 2026: «Я пока что, всё также вижу эти андроид аватарки, хотя хочу чтобы на андроиде были такие же как
на IOS. Либо надо сгенерировать что там что там одинаковые». An avatar is an emoji string; the iPhone drew Apple's
glyph and Android Google's, and Apple's artwork cannot ship on Android.

- Codex generated two style options on four characters; the owner chose «soft 3D» (rounded volumes, soft light, no
  outline), original artwork, not a redraw of any company's emoji. The full set is 18: the picker's 12 (😎 🙂 😄 🤠 🥸
  🧔 👨‍🦳 👩 👩‍🦰 👱‍♀️ 👵 🐶) and the 6 retired picks people still wear (🌅 🎸 📷 🎮 🚀 🚙). Masters (512 px) live in the
  iPhone repository, `Tools/Avatars`.
- `tools/avatars/install_avatars.py` writes them as 256 px WebP drawables (230 KB in all).
- `AvatarGlyph(emoji, fontSize)` replaces `Text(emoji, style = inter(fontSize))` at every avatar: feed and trip cards,
  the trip screen, comments, reactions, companions, the inbox, followers, blocked people, Discover, both profiles, the
  avatar picker and Cloud Sync. The stored value is still the emoji, so nothing changes on the server or for older
  clients; an emoji outside the set keeps the font. The art is drawn at 1.15 / 0.86 of the font size, the box the
  glyph took.

Tests: `AvatarArtTests` (every pickable and retired avatar has art, the variation selector is optional, others fall
back), 86 re-recorded screenshots.
