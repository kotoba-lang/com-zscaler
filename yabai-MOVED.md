# yabai は独立リポジトリに移設されました

`20-actors/yabai/` に vendored されていた yabai actor のコピーは撤去されました
（2026-07-16、ADR-2607170900）。

**yabai の唯一の source of truth は独立リポジトリです:**

- GitHub: `github.com/etzhayyim/com-etzhayyim-yabai`
- west project path: `orgs/etzhayyim/com-etzhayyim-yabai`

## 経緯

`20-actors/yabai/` はこのリポジトリに直接 tracked された vendored copy でしたが、
独立リポ `com-etzhayyim-yabai` と手動ミラーが drift していました（vendored 側は
2026-07-09 で停止、ADR-2607170800 の Cloudflare zone scanner ingest 配線・
`methods/cf_sweep.cljc`・新 IOC データを持たない状態でした）。source of truth を
1箇所に集約するため撤去しました。

## 消費者への影響

- fleet の稼働 yabai セル `YabaiTorTorrentCtiPersistenceCell`
  （`50-infra/cluster/murakumo/cell-runner/cells.edn`）は module
  `kotodama.primitives.yabai_murakumo` を呼び、この vendored `methods/*.cljc` は
  参照しないため**無影響**です。
- 将来このリポジトリの tooling が yabai コードを要する場合は、**再 vendoring せず**
  west sibling checkout（`orgs/etzhayyim/com-etzhayyim-yabai`）を classpath / deps
  依存として参照してください。

詳細は superproject の `90-docs/adr/2607170900-yabai-consolidate-to-independent-repo.md`。
