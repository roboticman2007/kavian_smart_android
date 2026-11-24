# اپلیکیشن اندروید «سامانه هوشمند کاویان» (v1.0.0)

- **نام نمایشی برنامه:** سامانه هوشمند کاویان
- **شناسه بسته (Package ID):** `ir.kaviancopytrade.app`
- **دامنه متصل:** `https://kaviancopytrade.ir`
- **محدوده دسترسی:** فقط پرتال کاربران (مسیرهای `/admin` به‌صورت کامل در لایه نیتیو مسدود شده‌اند)
- **لوگو:** دقیقاً برگرفته از لوگوی رسمی پنل کاویان (`LOGO_SVG`) در تمام سایزهای اندروید (`mdpi` تا `xxxhdpi` + Adaptive Icon)

---

## روش ۱: ساخت آنلاین و رایگان فایل APK با GitHub Actions (پیشنهادی — بدون نیاز به Android Studio)

1. در حساب **GitHub** خود یک مخزن جدید (مثلاً `kavian-android-app`، حتی به‌صورت **Private**) بسازید.
2. تمام فایل‌های داخل این فایل ZIP را در مخزن آپلود/Push کنید (حتماً پوشه `.github/workflows/build-apk.yml` هم آپلود شود).
3. به تب **Actions** در GitHub بروید، روی **Build Kavian Smart Android APK** کلیک کرده و دکمه **Run workflow** را بزنید (یا پس از Push خودکار اجرا می‌شود).
4. بعد از حدود ۲ دقیقه، در پایین صفحه همان اجرا، فایل **`KavianSmart-Android-APK-v1.0`** را دانلود و از حالت فشرده خارج کنید تا فایل **`KavianSmart-v1.0.apk`** را تحویل بگیرید.

---

## روش ۲: قرار دادن فایل APK روی سرور برای دانلود مستقیم کاربران

پس از نصب نسخه `v5.9.5` روی سرور، فایل `KavianSmart-v1.0.apk` را در مسیر زیر کپی کنید:

```bash
cp KavianSmart-v1.0.apk /opt/kaviancopytrade/static/kavian-smart-app.apk
```

به‌محض قرار گرفتن این فایل، دکمه **«دانلود اپلیکیشن اندروید»** به‌صورت خودکار در صفحه ورود سایت `https://kaviancopytrade.ir` فعال می‌شود و لینک مستقیم دانلود نیز `https://kaviancopytrade.ir/download/android` خواهد بود.

---

## روش ۳: بیلد محلی با Android Studio

کافی است پوشه پروژه را در Android Studio باز کنید، منتظر اتمام Gradle Sync بمانید و از منوی `Build -> Build Bundle(s) / APK(s) -> Build APK(s)` خروجی بگیرید.
