# Play Store assets

| File | Play Console field | Spec |
|---|---|---|
| `icon-512.png` | Store listing → App icon | 512×512 PNG, full-bleed square (Play rounds the corners itself) |
| `feature-graphic-1024x500.png` | Store listing → Feature graphic | 1024×500 PNG, no transparency |
| _phone screenshots: TODO_ | Store listing → Phone screenshots | 2–8 images, 9:16 (e.g. 1080×1920 or the device's native size) |

The sources are in `src/` (plain HTML). After editing one, re-render it with headless Chrome:

```bash
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --hide-scrollbars --force-device-scale-factor=1 --window-size=1024,500 --screenshot="C:\GitHub\Fitter\store-assets\feature-graphic-1024x500.png" "file:///C:/GitHub/Fitter/store-assets/src/feature-graphic-1024x500.html"
```

The meal card in the feature graphic is an illustrative example, not a real scan result. Its macros add up (48 g × 4 + 64 g × 4 + 16 g × 9 = 592 kcal).

## Screenshots to take (on your phone or the emulator)

Take them in this order; 4 to 6 is plenty:
1. Camera screen, ready to scan
2. Review screen with an estimate (meal name, items, macros, the "estimate" banner)
3. Editing a portion (weight changed, totals updated)
4. Dashboard / daily totals
5. Food Library
6. Settings → Privacy (shows the data controls)

Phone screenshots must not show test ads or debug UI. Use a release build, or crop the ads out. Don't add device frames or claims like "#1".
