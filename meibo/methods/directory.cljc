(ns meibo.methods.directory
  "meibo 名簿 — verified legal-institution directory registry (ADR-2607062200).

  Institution-level LINKS ONLY (G1) — never individual professional records.
  meibo never adjudicates a professional's competence or standing (G2); it
  points at the authoritative place to check. Every entry's :dir/url was
  verified live before being recorded (G10 — never guessed/remembered).

  House style: ':…' strings stay strings; pure fns; I/O at #?(:clj) edges."
  (:require [meibo.methods.edn :as edn]))

#?(:clj
   (defn load-directory
     ([] (load-directory (clojure.java.io/file (edn/here) "data" "legal-directory.edn")))
     ([path] (->> (edn/load-edn path) (filter #(contains? % ":dir/id")) vec))))

(defn- entry [d]
  {"id" (get d ":dir/id") "jurisdiction" (get d ":dir/jurisdiction")
   "kind" (get d ":dir/kind") "label" (get d ":dir/label")
   "url" (get d ":dir/url") "note" (get d ":dir/note")
   ;; whether the law itself requires this registry to be publicly disclosed,
   ;; vs mandatory-registration-but-voluntary-web-listing, vs unconfirmed —
   ;; see data/legal-directory.edn header for the full value taxonomy + why
   "disclosure_basis" (get d ":dir/disclosure-basis")
   "disclosure_note" (get d ":dir/disclosure-note")})

(defn by-jurisdiction
  "All directory entries for a declared jurisdiction (e.g. \":jp\") — empty
  vector for an uncovered jurisdiction (G10 honest degrade, never guessed)."
  ([juris-id] (by-jurisdiction juris-id (load-directory)))
  ([juris-id entries]
   (vec (map entry (filterv #(= (get % ":dir/jurisdiction") juris-id) entries)))))

(defn jurisdictions-covered
  ([] (jurisdictions-covered (load-directory)))
  ([entries] (vec (sort (distinct (map #(get % ":dir/jurisdiction") entries))))))
