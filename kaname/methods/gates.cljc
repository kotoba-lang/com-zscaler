(ns kaname.methods.gates
  "kaname 要 — constitutional gate assertions (ADR-2606172100).

  These are throwing guards (ex-info) shared by route.cljc + osekkai.cljc. They make the
  charter invariants STRUCTURAL — a caller cannot construct a capture-plan or a person-target,
  the function raises. Tests assert each one fires.

    G1 — no natural person, no coordinate. The 要 is a structural POSITION; only public ROLEs
         (:sos/role) and structural nodes are addressable. A node flagged :person/private, or
         carrying a coordinate, is refused.
    G2 — OPENING-only routing. The route enum has no capture/seize/control member; any such
         intent is refused.
    G5 — no thought-policing. A belief-content score (:belief/wrongness / :faith/rank) is
         unrepresentable; :influences must carry an on-the-record :en/basis."
  (:require [clojure.string :as str]))

(def route-enum
  "The ONLY admissible routings — all dissolve concentration (G2)."
  #{":open" ":route-around" ":add-redundancy" ":decentralize" ":insufficient-evidence"})

(def route-forbidden
  "Concentration-INCREASING routings — structurally unrepresentable (G2)."
  #{":capture" ":seize" ":control" ":exploit" ":corner" ":monopolize"})

(defn assert-route!
  "G2 — a route MUST dissolve concentration. Refuses any capture/seize/control intent."
  [route]
  (when (contains? route-forbidden route)
    (throw (ex-info "G2 violation: kaname routes the 要 to OPENING only — capture/seize/control is unrepresentable"
                    {:gate :G2 :route route})))
  (when-not (contains? route-enum route)
    (throw (ex-info "G2 violation: route not in the opening-only enum"
                    {:gate :G2 :route route :allowed route-enum})))
  route)

(defn assert-not-person!
  "G1 — refuse a natural person or a coordinate. Public ROLEs (:sos/role) are allowed;
  private profiles + lat/lon are not."
  [node]
  (when (true? (get node ":person/private"))
    (throw (ex-info "G1 violation: kaname is person-excluded — a natural-person target is unrepresentable"
                    {:gate :G1 :id (get node ":organism/id")})))
  (when (or (contains? node ":coord/lat") (contains? node ":coord/lon"))
    (throw (ex-info "G1 violation: no coordinates — the 要 is a structural position, not a place to target"
                    {:gate :G1 :id (get node ":organism/id")})))
  node)

(defn assert-no-belief-score!
  "G5 — a belief-content verdict is unrepresentable. kaname names STRUCTURAL interfaces, never
  ranks faiths/ideas. Refuses :belief/wrongness / :faith/rank on a node."
  [node]
  (doseq [k [":belief/wrongness" ":faith/rank" ":idea/wrongness"]]
    (when (contains? node k)
      (throw (ex-info "G5 violation: no thought-policing — belief-content scoring is unrepresentable"
                      {:gate :G5 :id (get node ":organism/id") :attr k}))))
  node)

(defn assert-influence-basis!
  "G5 — every :influences 縁 (ideology/religion projection) MUST carry an on-the-record :en/basis."
  [edge]
  (when (= ":influences" (get edge ":en/kind"))
    (let [b (get edge ":en/basis")]
      (when (or (nil? b) (and (string? b) (str/blank? b)))
        (throw (ex-info "G5 violation: an :influences 縁 needs an on-the-record :en/basis (no unbasis'd ideology/religion edge)"
                        {:gate :G5 :from (get edge ":en/from") :to (get edge ":en/to")})))))
  edge)
