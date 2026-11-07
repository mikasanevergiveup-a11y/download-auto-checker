# Download Auto Checker (Android + Bot API)

Premium users only. Auto-count new app installs:

1. Task 1 → 5 installs → reward  
2. Auto Task 2 → 5 more installs → reward  

## Folders

- `android/` — Android Studio project (Kotlin + Jetpack Compose)
- `backend/` — API reference copies
- Bot integration already copied into `../telegram-rewards-bot/tgcloud/`

## Bot endpoints (tgcloud)

| Endpoint | Purpose |
|----------|---------|
| `androidRequestCode` | Send 6-digit code to Telegram user (Premium only) |
| `androidVerifyCode` | Verify code → sessionToken |
| `androidClaimTask` | Claim Task 1 or 2 after 5 unique installs |
| `androidStatus` | Current stage + balances |

Deploy bot:

```bash
cd telegram-rewards-bot
npx tgcloud migrate
npx tgcloud push --force
```

## Android build

1. Open `android/` in **Android Studio Hedgehog+**
2. Let Gradle sync
3. Set API base in `app/build.gradle.kts` → `API_BASE`  
   (your public HTTPS gateway that routes to tgcloud endpoints)
4. Build → Build APK(s) / Run on device

### API gateway note

tgcloud Serverless endpoints are usually called via Telegram WebApp `Serverless.call`.  
For Android you need an HTTPS reverse path (or a small proxy) that forwards:

`POST /api/androidRequestCode` → tgcloud endpoint `androidRequestCode`

If your host already exposes REST for endpoints, point `API_BASE` there.

## User flow

1. User must be Premium in Telegram bot  
2. Open Android app → enter `@username` or Telegram ID  
3. Receive code in Telegram → enter in app  
4. Allow permissions / usage access  
5. Install 5 **new** apps → Claim Task 1  
6. App switches to Task 2 automatically  
7. Install 5 more **new** apps → Claim Task 2  

## Security

- Code TTL 10 minutes, single use  
- Session token device-bound  
- Idempotent claim keys  
- Server checks Premium on every claim  

## Play Store

Install-tracking reward apps may be restricted on Play. Prefer direct APK distribution for this product.

## Build APK without PC (GitHub Actions)

See **PHONE_BUILD_MY.md** (Burmese step-by-step).

1. Push this repo to GitHub
2. Actions → Build APK → wait
3. Download artifact `download-checker-apk` on your phone
4. Install the `.apk`
