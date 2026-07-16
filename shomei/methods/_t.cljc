(ns shomei.methods._t
  "_t.cljc — tiny shared test helper. 1:1 Clojure port of `methods/_t.py`. ADR-2606072100.

  The Python harness builds a list of (name, fn) and `run`s them (a case passes if it returns
  without raising). The Clojure ports drive `clojure.test/deftest` directly (the house style of
  the gold exemplars), so the load-bearing piece to share is `expect-raises` — the assertion
  `expect_raises(fn, contains=…)`: the body MUST raise, and (when `contains` is given) the
  message MUST include the substring. `run` is preserved as a thin `clojure.test` wrapper so a
  `(name, fn)` case list can still be executed verbatim."
  (:require [clojure.string :as str]
            [clojure.test :as t]))

(defn expect-raises*
  "Functional form: run `f` (0-arg thunk); assert it raises, and if `contains` is non-empty that
  the message includes it. Mirrors `_t.expect_raises`. Returns nil on success, throws otherwise."
  ([f] (expect-raises* f ""))
  ([f contains]
   (let [r (try (do (f) ::no-throw)
                (catch #?(:clj Throwable :cljs :default) ex
                  (#?(:clj #(.getMessage ^Throwable %) :cljs ex-message) ex)))]
     (cond
       (= r ::no-throw)
       (throw (ex-info "expected an exception, none raised" {}))
       (and (seq contains) (not (and (string? r) (str/includes? r contains))))
       (throw (ex-info (str "raised but missing " (pr-str contains) ": " r) {}))
       :else nil))))

(defmacro expect-raises
  "Macro sugar: `(expect-raises \"substr\" body...)` asserts `body` throws with a message
  containing `substr`. With no leading string it only asserts that `body` throws."
  [contains-or-form & body]
  (if (string? contains-or-form)
    `(expect-raises* (fn [] ~@body) ~contains-or-form)
    `(expect-raises* (fn [] ~contains-or-form ~@body))))

(defn run
  "Run a `[[name fn] …]` case list under clojure.test semantics (the `_t.run` analogue). Each fn
  is a 0-arg thunk that passes by not raising. Returns the clojure.test summary map."
  [suite cases]
  (let [v (atom {:pass 0 :fail 0})]
    (doseq [[nm f] cases]
      (try (f) (swap! v update :pass inc)
           (catch #?(:clj Throwable :cljs :default) ex
             (swap! v update :fail inc)
             (println (str "  FAIL " nm))
             (println (#?(:clj #(.getMessage ^Throwable %) :cljs ex-message) ex)))))
    (let [{:keys [pass fail]} @v
          total (+ pass fail)]
      (println (str "[" suite "] " pass "/" total " passed"))
      {:pass pass :fail fail :total total})))
