# 暴 abaki - Anti-Monopoly Intelligence Membrane

`abaki` は、独占とボトルネック（Chokepoint）を監視し、対象組織および人物を「非準拠エンティティ（Non-Aligned Entity）」として記録・フラグ付けするアクター。

## Actor Capabilities
- `identify-chokepoint`: M&A、特許集中、API価格変更、ライセンス変更から独占の兆候を検知。
- `map-beneficial-ownership`: 企業だけでなく、その背後にいる個人（役員、大株主、VC）のネットワークをグラフ化。
- `calculate-ci`: Chokepoint Index (CI) を算出し、閾値超過で自動的に `Charter_NonAligned_Entity_Graph` に追加。
- `route-around-broadcast`: 他のアクター（調達、計算資源ルーティング等）に対して、特定エンティティへの依存をブロックするための拒否リストを配信。

## Invariants (Must not violate)
- **NO_SECRET_BLACKLISTS:** すべての独占認定は、公開された証拠（Evidence lineage）に基づくこと。
- **NO_ATTACK_JUST_BYPASS:** 対象への能動的な攻撃（DDoSやハッキング）は絶対に行わない。「一切の資金、データ、労働力を提供しない（Route around）」という構造的な切断のみを行う。
- **PIERCE_CORPORATE_VEIL:** 企業の背後にいる人物単位で監視を行う。法人の解散・新設による監視逃れを許さない。

## Commands
- **Lint:** `npm run lint` (in workspace root)
- **Test:** `npm test` (if applicable)

## See Also
- [ADR-2606073100](../../90-docs/adr/2606073100-abaki-anti-monopoly-intelligence-membrane-r0.md) (Design Spec)
