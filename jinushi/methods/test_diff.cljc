(ns jinushi.methods.test-diff
  "jinushi 地主 — as-of snapshot diff (差分) tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [jinushi.methods.diff :as d]))

(def old [{:parcel/id "A" :owner "x" :floors 2}
          {:parcel/id "B" :owner "y" :floors 3}
          {:parcel/id "C" :owner "z" :floors 1}])
(def new [{:parcel/id "A" :owner "x" :floors 2}          ;; unchanged
          {:parcel/id "B" :owner "y2" :floors 4}         ;; changed (owner + floors)
          {:parcel/id "D" :owner "w" :floors 9}])        ;; added; C removed

(deftest test-diff-basic
  (let [r (d/diff :parcel/id old new)]
    (is (= ["D"] (map :parcel/id (:added r))) "D added")
    (is (= ["C"] (map :parcel/id (:removed r))) "C removed")
    (is (= 1 (count (:changed r))) "B changed")
    (is (= 1 (:unchanged r)) "A unchanged")
    (is (= {:old 3 :new 3 :added 1 :removed 1 :changed 1} (:counts r)))))

(deftest test-diff-changed-fields
  (let [r (d/diff :parcel/id old new)
        b (first (:changed r))]
    (is (= "B" (:key b)))
    (is (= [:floors :owner] (:fields b)) "exactly the changed fields, sorted")))

(deftest test-compare-fields-restriction
  ;; only watch :floors → owner change on B is ignored, floors change still caught
  (let [r (d/diff :parcel/id old new [:floors])]
    (is (= 1 (count (:changed r))) "B floors changed")
    (is (= [:floors] (:fields (first (:changed r)))))))

(deftest test-snapshot-diff-auto-key
  (let [r (d/snapshot-diff {:records old :source-id "s"} {:records new :source-id "s"})]
    (is (= "s" (:source-id r)))
    (is (= 1 (get-in r [:counts :added])))))

(deftest test-summary
  (let [s (d/summary (d/diff :parcel/id old new))]
    (is (re-find #"\+1 added, -1 removed, ~1 changed" s))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-diff)]
    (System/exit (+ (or fail 0) (or error 0)))))
