(ns tsumugi.methods.test-project-influence-posts
  "Cross-language oracle tests for tsumugi.methods.project-influence-posts.
  Ported from the REAL Python project_influence_posts (oracle values embedded verbatim).

  Pins the N2 mirror-only / observer-voice / dry-run discipline + the exact projected text for
  a flow post ('観察: 「Plato」 shaped 「Aristotle」 … weight +0.80 …') and a node post."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [tsumugi.methods.project-influence-posts :as pip]))

(def nodes
  {":a" {":organism/id" ":a" ":organism/label" "Plato" ":mirror/is-mirror" true}
   ":b" {":organism/id" ":b" ":organism/label" "Aristotle" ":mirror/is-mirror" true
         ":hist/subkind" ":figure" ":hist/tradition" [":hellenic" ":secular-philosophy"]}})
(def flow {":flow/id" "fl.x" ":flow/from" ":a" ":flow/to" ":b"
           ":flow/kind" ":influences" ":flow/signed-weight" 0.8})

(deftest flow-post-observer-voice-and-text
  (let [p (pip/project-post (get nodes ":a") flow nodes 3)]
    (is (= "post.x" (get p ":post/id")))
    (is (= ":observer" (get p ":post/voice")))             ; N2 locked
    (is (= false (get p ":post/published")))               ; G7 dry-run
    (is (= "fl.x" (get p ":post/about-flow")))
    (is (str/includes? (get p ":post/text") "観察: 「Plato」 shaped 「Aristotle」"))
    (is (str/includes? (get p ":post/text") "weight +0.80"))
    (is (str/includes? (get p ":post/text") "観察像 — 本人ではない"))))   ; disclaimer prefix

(deftest node-post-text
  (let [p (pip/project-post (get nodes ":b") nil nodes 5)]
    (is (= "post.node.:b" (get p ":post/id")))
    (is (nil? (get p ":post/about-flow")))
    (is (str/includes? (get p ":post/text") "「Aristotle」 — public influence-bearing node (figure; hellenic,secular-philosophy)"))))

(deftest n2-refuses-non-mirror
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"N2"
                        (pip/project-post {":organism/id" ":x"} nil nodes 1))))

(deftest edn-str-shapes-post
  (let [s (pip/edn-str {":post/id" "p" ":post/voice" ":observer" ":post/published" false ":post/tick" 3})]
    (is (str/includes? s ":post/id \"p\""))
    (is (str/includes? s ":post/voice :observer"))         ; keyword kept bare
    (is (str/includes? s ":post/published false"))
    (is (str/includes? s ":post/tick 3"))))
