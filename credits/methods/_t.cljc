(ns credits.methods._t
  "_t.cljc — tiny shared test helper, 1:1 shape borrowed from shomei.methods._t
  (ADR-2606072100 house style) so credits' first real test suite follows the same
  established convention as the already-implemented sibling actors. `expect-raises`:
  the body MUST raise, and (when a substring is given) the message MUST include it.
  Genuinely portable (:clj Throwable / :cljs :default) -- no JVM-only assumption."
  (:require [clojure.string :as str]))

(defn expect-raises*
  "Functional form: run `f` (0-arg thunk); assert it raises, and if `contains` is
  non-empty that the message includes it. Returns nil on success, throws otherwise."
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
  "Macro sugar: `(expect-raises \"substr\" body...)` asserts `body` throws with a
  message containing `substr`. With no leading string it only asserts `body` throws."
  [contains-or-form & body]
  (if (string? contains-or-form)
    `(expect-raises* (fn [] ~@body) ~contains-or-form)
    `(expect-raises* (fn [] ~contains-or-form ~@body))))
