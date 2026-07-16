(ns keizu.methods._t
  "Tiny standalone test harness (no external test framework needed) — shared by keizu test_*.cljc.

   Each test file builds a list of [name fn] and calls run name cases. A case passes if it
   returns without raising; failures print and the process exits non-zero. Mirrors the
   ake/noroshi convention so `./run_tests.sh` can aggregate every suite."
  (:require [clojure.stacktrace :as st]))

(defn run
  "Run a suite of [name test-fn] cases. Prints summary and calls System/exit 1 on failure."
  [suite cases]
  (let [{:keys [passed failed]}
        (reduce (fn [acc [name test-fn]]
                  (try
                    (test-fn)
                    (update acc :passed inc)
                    (catch Exception e
                      (println "  FAIL" name)
                      (st/print-stack-trace e)
                      (update acc :failed inc))))
                {:passed 0 :failed 0}
                cases)]
    (let [total (+ passed failed)]
      (println (str "[" suite "] " passed "/" total " passed"))
      (when (pos? failed)
        (System/exit 1)))))

(defn expect-raises
  "Assert that (fn) raises an Exception. Optionally assert the message contains `contains`."
  ([fn] (expect-raises fn ""))
  ([fn contains]
   (try
     (fn)
     (throw (ex-info "expected an exception, none raised" {}))
     (catch Exception e
       (when (and (seq contains) (not (clojure.string/includes? (ex-message e) contains)))
         (throw (ex-info (str "raised but missing " (pr-str contains) ": " (ex-message e)) {})))))))
