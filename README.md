# Grocery Accountant (Android)

A shopping list that keeps a running total. You can add prices, quantities, sales tax, coupons, WIC and SNAP, and it shows what you will actually pay at the register. Everything is stored on the phone. Signing in to a GodsKingdomNow.com Family account is optional and lets you share lists with your family.

- Package: `com.godskingdomnow.groceryaccountant`
- Kotlin 2.0.21 · Jetpack Compose (Material 3) · Room · DataStore · OkHttp
- Icon: `app/src/main/res/mipmap-*/ic_launcher_foreground.png` (Play Store version: `playstore-icon-512.png`)
- Android 8.0 (API 26) and up · target/compile SDK 35

## Build and install

1. Install **Android Studio** (Ladybug 2024.2 or newer).
2. Choose **File → Open** and pick this `GroceryAccountant` folder. Let Gradle sync. The first time, it downloads Gradle 8.11.1 and the libraries.
3. Plug in your phone with USB debugging on, or start an emulator. Then press **Run ▶**.

To get an installable APK: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The file is saved as `app/build/outputs/apk/debug/app-debug.apk`.

For the Play Store: **Build → Generate Signed App Bundle / APK…**. Keep your keystore safe, because you need the same one for every update.

From a terminal (with the Android SDK installed): `./gradlew assembleDebug`. To run the calculator tests: `./gradlew test`.

## How the math works (same as the website)

All money is worked in cents. At the register, things are applied in this order:

1. **Coupons** — dollars off (per item or per line), percent off, or buy-one-get-one. Store coupons lower the taxed price. Manufacturer coupons are taxed on the full price unless you turn that setting off.
2. **WIC foods** — WIC covers whole items while the food is still on your card (for example, 36 oz of cereal). WIC items are not taxed.
3. **WIC fruits & vegetables money** — anything over the limit goes to SNAP or to you.
4. **SNAP** — covers eligible items, taxable ones first (SNAP purchases aren't taxed).
5. **You pay** the rest, plus tax.

**Done shopping** takes what the checked items used off your balances and removes those items.

## Import from a text message

- In **Messages**, long-press the text (for example, your wife's list), then tap **Share → Grocery Accountant**. Or copy the text and use the 💬 button on the home screen, or **Add from a text message…** in a list's menu.
- The app finds the items in casual texts ("Can you grab milk, 2 dozen eggs and bread? Thanks!"), lists with one item per line, bullets and checkboxes, numbered lists, and lists copied from store apps with prices.
- It picks up quantities (`2 milk`, `bananas x2`, `(3)`), sizes (`2 lb`, `1 gallon`, `3 cans`) and prices (`$3.48`). It leaves out chat like "ok" and "love you", and shows sentences that don't look like items unticked.
- Before adding, you tick or untick each item and choose a new or existing list. Items already on that list are marked so you don't add them twice.
- The app can't read your texts by itself: only default messaging apps are allowed to, so you choose what to share.

## Send and shop

- **Send list as text…** (list menu) shares what's still left to get, with quantities, prices and the estimated total, by text, email or notes.
- **Find it online → Walmart / Kroger** (item details) searches that store for the item, in the store's app if it's installed.

## Family sync (optional)

Sign in on the **Account** screen with your Family app username and password.

- The phone gets its own sign-in key. The website saves only a scrambled (hashed) copy of it. Signing out cancels the key.
- Only lists marked **shared** sync. These are the same lists as the Shopping page on the website. Lists that aren't shared stay on the phone only.
- Changes sync about 3 seconds after you make them, and again each time you open the app. Shopping with no signal is fine; the app catches up later.
- If the same thing was changed on the website since this phone last synced, **the website's version is kept**.
- WIC and SNAP balances are shared with the family. Only parents can change them; kids see them read-only.

The website needs the **sync update**: `server/family/grocery_app_api.php` and `server/family/schema_v27.sql` in this repo. See `server/INSTALL.txt`.

The app calls `family/grocery_app_api.php`. Older versions called `family/app_api.php`, a name the Family Manager app also uses. Family Manager's `app_api.php` now passes those older Grocery Accountant requests on to `grocery_app_api.php`, so both apps work side by side. If a site doesn't have `grocery_app_api.php` yet, the app falls back to the old address by itself.

To point the app at a different address, tap "Use a different website address" on the sign-in screen. The default is `https://www.godskingdomnow.com/family/`.

## Project layout

```
app/src/main/java/com/godskingdomnow/groceryaccountant/
  calc/ShopEngine.kt     register math (port of shop_engine.js, unit-tested)
  data/                  Room database, entities, DAO, repository
  sync/                  sign-in storage, website API, sync manager
  ui/                    Compose screens: lists, list, benefits, account
server/family/           the website side of sync (grocery_app_api.php, schema_v27.sql)
```
