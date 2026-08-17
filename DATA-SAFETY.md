# Google Play "Veri güvenliği" formu — Bildirim SDK'sının payı

Formu uygulamanız için siz doldurursunuz; SDK'nın topladığı ve ilettiği veriler şunlardır.
Kendi topladıklarınızı buna ekleyin.

| Veri türü | Toplanıyor mu | Paylaşılıyor mu | Amaç | Not |
|---|---|---|---|---|
| Cihaz veya diğer kimlikler | **Evet** | Hayır | Uygulama işlevselliği | FCM cihaz jetonu + SDK'nın ürettiği rastgele kurulum kimliği (UUID). Reklam kimliği **değil**. |
| Uygulama etkileşimleri | **Evet** | Hayır | Analiz | Bildirim gösterildi/tıklandı/kapatıldı; `track()` ile sizin gönderdiğiniz olaylar |
| Kişisel bilgi (kullanıcı kimliği) | Yalnız `login()` çağırırsanız | Hayır | Uygulama işlevselliği | Dış kimlik sizin verdiğiniz dizgedir; e-posta/ad göndermeyin |
| Yaklaşık konum | Hayır | — | — | Yalnız cihazın yerel ayarındaki **ülke kodu** (`Locale`) ve saat dilimi gönderilir; konum izni istenmez |
| Reklam kimliği (AAID) | **Hayır** | — | — | SDK okumaz |

- Veri aktarımda TLS ile şifrelenir.
- Kullanıcı `Bildirim.unsubscribe()` ile silme isteyebilir; siz de panelden abone silebilirsiniz.
- Veri işleyen: Bildirim (bildirim.io), KVKK kapsamında veri işleyen sıfatıyla; sunucular
  Türkiye'de. Ayrıntı: https://bildirim.io/gizlilik ve https://bildirim.io/veri-islenmesi
