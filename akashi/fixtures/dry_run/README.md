# dry_run fixture

Golden output for `adapters/dry_run_fixtures.cljc`.

This fixture is a regression guard for local parsing only. It does not represent
live collection, platform authorization, or kotoba writes. The same dry-run can
print deterministic Datomic/DataScript EDN tx-data with `--emit-edn`; storage in
git, DataLad/git-annex, or kotoba-git/kotoba-rad is an explicit caller action.
