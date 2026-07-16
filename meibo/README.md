# meibo 名簿 — verified legal-institution directory registry

**弁護士会・裁判所・(日本の)司法書士会/行政書士会など、実在する機関の公式検索窓口への
リンク集。** 10法域(日本・米国・英国・独国・韓国・仏国・豪州・加国・伊国・西国)、22件
— すべて実際にWeb検索・fetchで存在確認済み。個人の弁護士・裁判官の記録は一切保持しない
(機関レベルのリンクのみ、G1)。

- 📚 設計と不変条件: [`CLAUDE.md`](CLAUDE.md) · ADR-2607062200
- 🔗 これは gftdcojp ADR-0016 が計画しながら一度も実装されなかった
  judge/bengoshi/adr/legal-aid actor群の、誠実な代替実装です。詳細は ADR 参照。
- 🔗 姉妹actor: [`../saisei/`](../saisei/) (この`legal_directory`パターンの発祥元),
  [`../tate/`](../tate/) (30法域の`:juris/referrals`— 将来このactorのURLで補強予定)

**Hard lines**: 機関レベルのみ(個人の弁護士・裁判官記録は保持しない、G1) · 非裁定
(「この人は良い弁護士」とは判断しない、G2) · 管轄正直(未収載法域は推測せず宣言する、G10)。

License: Apache 2.0 + etzhayyim Charter Compliance Rider (see repo root).
