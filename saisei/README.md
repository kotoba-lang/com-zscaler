# saisei 再生 — citizen self-filing debt-relief concierge

**自己破産・個人再生など「自分自身の」正式な債務整理手続きの申立てを、4法域
(日本・米国・英国・独国) について disclose する registry。** A jurisdiction-keyed
registry of eligibility signals, mandatory pre-filing steps, required documents,
fees, and discharge-timeline rules for a member's OWN formal insolvency petition —
non-adjudicating legal *information*, never legal advice.

- 📚 設計と不変条件: [`CLAUDE.md`](CLAUDE.md) · ADR-2607061800
- 🌐 Live API + interactive page: https://saisei-worker.04-feasts-minded.workers.dev
  (workers.dev subdomain — custom `saisei.etzhayyim.com` pending zone:write;
  see [`../../50-infra/saisei-worker/README.md`](../../50-infra/saisei-worker/README.md)
  for the API + anonymous-aggregate-analytics query)
- 🔗 姉妹actor: [`../tate/`](../tate/) (creditor-side 応答 — 第三者の倒産通知への対応),
  [`../toritsugi/`](../toritsugi/) (proactive 行政窓口手続き),
  [`../yobel/`](../yobel/) (voluntary 教義的debt release)

**Hard lines** (never relaxed): 非裁定 (適格性は SIGNAL として開示するのみ — 「あなたは
対象です」とは言わない) · UPL (申立書の作成代理・提出代行は構造的に表現不能 — member
本人が組み立てて提出する) · 期限正直 (裁判所の決定日を計算しない — 開示済みルール+
条文) · 前置き手続き正直 (法定の必須事前手続き(独: außergerichtlicher
Einigungsversuch 等)はスキップ不可なblocking stepとして表示) · 管轄正直 (未収載の
法域は推測せず宣言する) · 広告・トラッキングゼロ。

> 本 registry は一般的な法情報です。法令は改正されます — すべてのアンカーは
> `:verify-current-law` 付きで、現行条文での確認を前提とします。重要な判断は
> 各国の無料・公的相談窓口(法テラス・USTP-approved credit counseling・
> MoneyHelper・Schuldnerberatungsstelle)へ。

License: Apache 2.0 + etzhayyim Charter Compliance Rider (see repo root).
