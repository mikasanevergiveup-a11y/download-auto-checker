# ဖုန်းမှာ APK ရယူနည်း (GitHub Actions)

PC / Android Studio မလိုပါ။ GitHub က build လုပ်ပေးပြီး ဖုန်းက APK ဆွဲပါ။

## ၁) GitHub Account
1. https://github.com ဝင်ပါ (ဖုန်း browser)
2. Account မရှိရင် Sign up

## ၂) Repo အသစ်
1. **+** → **New repository**
2. Name: `download-auto-checker`
3. Public ရွေးပါ
4. **Create repository**

## ၃) ဖိုင်တင်ခြင်း (အလွယ်)
### နည်း A — PC ရှိရင်
zip ကို extract → folder အားလုံးကို repo သို့ upload

### နည်း B — ဖုန်းသီးသန့်
1. GitHub app သို့မဟုတ် browser
2. Repo → **Add file** → **Upload files**
3. `download-auto-checker.zip` ထဲက ဖိုင်များ တင်ပါ  
   (အထူးသဖြင့် `android/` နှင့် `.github/` folder တစ်ခုလုံး)

အရေးကြီး: ဒီ structure ရှိရမည်

```
download-auto-checker/
  .github/workflows/build-apk.yml
  android/
    gradlew
    app/
    ...
  README.md
```

## ၄) Build စတင်
1. Repo → **Actions** tab
2. **Build APK** workflow ကို ရွေး
3. **Run workflow** → **Run workflow**
4. စိမ်းရောင် check အောင်သည်အထိ စောင့် (၅–၁၅ မိနစ်)

ပထမဆုံး push ရင်လည်း auto build ဖြစ်သည်။

## ၅) ဖုန်းက APK ဆွဲ
1. Actions → အောင်မြင်သော run ကို နှိပ်
2. အောက်ဆုံး **Artifacts**
3. **download-checker-apk** ကို နှိပ် download
4. zip ထဲက `.apk` ဖိုင် ဖွင့်
5. Android → **Unknown sources / Install unknown apps** ခွင့်ပြု
6. Install

## ၆) ပြဿနာရှိရင်
- Actions နီရောင် = build fail → log ဖတ်ပါ
- Artifact မရှိ = APK path မှား / build မအောင်
- Install မရ = unknown sources ပိတ်နေ

## API / HTTPS မှတ်ချက်
အခု APK က server ချိတ်ရန် `API_BASE` လိုသည်။  
HTTPS gateway မရှိသေးရင် login/code/reward API မရနိုင်သေးပါ။  
လိုရင် နောက်တစ်ဆင့် **Bot message နဲ့ claim** (gateway မလို) ပြင်ပေးနိုင်သည်။
