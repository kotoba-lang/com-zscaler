# tate 盾 — citizen legal-defense concierge (worldwide)

**受け取った法的通知 (支払督促・解雇・立退き・差押え・倒産・離婚) への応答期限・
防御選択肢・無料相談先を、30法域 + 米国全50州について開示する registry。**
A worldwide registry of response deadlines, protective moves and free-help
directories for legal notices an individual receives — non-adjudicating
legal *information*, never legal advice.

- 🌐 公開ガイド (crawlable HTML + FAQ JSON-LD): `https://etzhayyim.com/tate/`
- 📇 1 case = 1 actor (profile から checklist / case.json の DL と相談先):
  `https://etzhayyim.com/actor/tate/cases.json`
- 📚 設計と不変条件: [`CLAUDE.md`](CLAUDE.md) · ADR-2606112301 (R0) /
  2606112400 (worldwide) / 2606122000 (R2 status)
- 🔗 縁切り executor: [`../kaiyaku/`](../kaiyaku/) (解約・退会の実行系 —
  tate が検出した自動更新条項を handoff で受け取る)

**Hard lines** (every wave, never relaxed): 非裁定 (条項・手続きを開示済み法令
アンカーに対応付けるだけ — 有効/無効は判断しない) · UPL (代理は構造的に表現不能 —
member 本人が決めて提出する) · 期限正直 (日付を計算しない — ルール+条文+
「送達日は自分で確認」) · 管轄正直 (未収載の法域は推測せず宣言する) · 偽通知ガード
(本物の裁判所書類の送達経路を開示 — SMS/メールの『裁判所』は接触前に公的窓口へ) ·
広告・トラッキングゼロ。

> 本 registry は一般的な法情報です。法令は改正されます — すべてのアンカーは
> `:verify-current-law` 付きで、現行条文での確認を前提とします。重要な判断は
> 各ページ記載の無料・公的相談窓口へ。

License: Apache 2.0 + etzhayyim Charter Compliance Rider (see repo root).
