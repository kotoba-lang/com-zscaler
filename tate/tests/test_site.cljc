(ns tate.tests.test-site
  "tate 盾 — static site generator tests (wave 35).
  1:1 Clojure port of tests/test_site.py (stdlib asserts → clojure.test).

  Generates into a temp dir at load time (mirroring the module-level _PAGES in Python),
  then asserts page set / disclaimer / no-tracking / FAQ JSON-LD / sitemap / SEO /
  track pages / deploy-copy parity. File I/O behind #?(:clj …)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set :as set]
            [tate.methods.terms-scan :as ts]
            [tate.methods.respond-plan :as rp]
            [tate.methods.site-gen :as sg]))

;; ── minimal JSON reader (string-keyed maps) — for the FAQ JSON-LD assertion only ──
(declare json-value)
(defn- skip-ws [^String s i]
  (loop [i i]
    (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
      (recur (inc i)) i)))
(defn- json-string [^String s i]
  (loop [i (inc i), sb (StringBuilder.)]
    (let [c (nth s i)]
      (cond
        (= c \") [(.toString sb) (inc i)]
        (= c \\) (let [e (nth s (inc i))]
                   (case e
                     \" (do (.append sb \") (recur (+ i 2) sb))
                     \\ (do (.append sb \\) (recur (+ i 2) sb))
                     \/ (do (.append sb \/) (recur (+ i 2) sb))
                     \n (do (.append sb \newline) (recur (+ i 2) sb))
                     \t (do (.append sb \tab) (recur (+ i 2) sb))
                     \u (do (.append sb (char (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)))
                            (recur (+ i 6) sb))
                     (do (.append sb e) (recur (+ i 2) sb))))
        :else (do (.append sb c) (recur (inc i) sb))))))
(defn- json-number [^String s i]
  (let [end (loop [j i] (if (and (< j (count s))
                                 (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                          (recur (inc j)) j))
        tok (subs s i end)]
    [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))
(defn- json-array [^String s i]
  (loop [i (skip-ws s (inc i)), out []]
    (if (= (nth s i) \]) [out (inc i)]
        (let [[v i] (json-value s i) i (skip-ws s i)]
          (if (= (nth s i) \,) (recur (skip-ws s (inc i)) (conj out v)) [(conj out v) (inc i)])))))
(defn- json-object [^String s i]
  (loop [i (skip-ws s (inc i)), out {}]
    (if (= (nth s i) \}) [out (inc i)]
        (let [[k i] (json-string s i) i (skip-ws s i)
              [v i] (json-value s (skip-ws s (inc i))) out (assoc out k v) i (skip-ws s i)]
          (if (= (nth s i) \,) (recur (skip-ws s (inc i)) out) [out (inc i)])))))
(defn- json-value [^String s i]
  (let [i (skip-ws s i) c (nth s i)]
    (cond (= c \{) (json-object s i) (= c \[) (json-array s i) (= c \") (json-string s i)
          (= c \t) [true (+ i 4)] (= c \f) [false (+ i 5)] (= c \n) [nil (+ i 4)]
          :else (json-number s i))))
(defn- parse-json [text] (first (json-value text 0)))

(def ^:private tmp
  (let [d (java.nio.file.Files/createTempDirectory "tate-site" (make-array java.nio.file.attribute.FileAttribute 0))]
    (clojure.java.io/file (.toFile d) "site")))

(def ^:private pages (sg/generate tmp "https://example.test/tate"))

(defn- slurp-page [name] (slurp (clojure.java.io/file tmp name)))

(deftest test-one-page-per-jurisdiction
  (let [juris (rp/load-jurisdictions)]
    (is (= (count pages) (+ (count juris) 1 5)))
    (doseq [jid (keys juris)]
      (is (.exists (clojure.java.io/file tmp (str (subs jid 1) ".html"))) jid))))

(deftest test-disclaimer-on-every-page
  (doseq [p pages]
    (let [text (slurp-page p)]
      (is (and (str/includes? text "法的助言") (str/includes? text "非裁定")) p)
      (is (not (str/includes? text "無効です")) p))))

(deftest test-no-tracking-no-external-assets
  (doseq [p pages]
    (let [text (str/lower-case (slurp-page p))]
      (doseq [bad ["gtag" "analytics" "googletagmanager" "facebook" "pixel"
                   "<script src=" "cdn."]]
        (is (not (str/includes? text bad)) [p bad])))))

(deftest test-faq-jsonld-valid-and-critical-marked
  (let [de (slurp-page "de.html")
        marker "application/ld+json\">"
        start (+ (str/index-of de marker) (count marker))
        end (str/index-of de "</script>" start)
        ld (parse-json (subs de start end))]
    (is (and (= (get ld "@type") "FAQPage") (>= (count (get ld "mainEntity")) 4)))
    (is (and (str/includes? de "⚠") (str/includes? de "class=\"crit\"")))))

(deftest test-sitemap-lists-all-pages
  (let [sm (slurp-page "sitemap.xml")]
    (doseq [p pages]
      (is (str/includes? sm (str "https://example.test/tate/" p "</loc>"))))
    (is (str/starts-with? (slurp-page "robots.txt") "User-agent: *"))))

(deftest test-native-keywords-in-titles
  (let [checks {"de.html" "Mahnbescheid" "es.html" "desahucio"
                "kr.html" "지급명령" "nl.html" "dagvaarding" "fr.html" "licenciement"}]
    (doseq [[page kw] checks]
      (let [head (str/lower-case (first (str/split (slurp-page page) #"</head>")))]
        (is (str/includes? head (str/lower-case kw)) [page kw])))))

(deftest test-track-pages
  (let [tp (slurp-page "track-labor.html")]
    (is (and (str/includes? tp "解雇・労働") (>= (count (re-seq #"<tr>" tp)) 29)))
    (is (and (str/includes? tp "⚠") (str/includes? tp "🛡")))))

(deftest test-deploy-copy-in-sync
  (let [deploy (clojure.java.io/file (ts/here) ".." ".." "50-infra" "etzhayyim-did-web" "public" "tate")]
    (is (.exists deploy) "deploy copy missing — run site_gen --out .../public/tate")
    (let [deployed (set (for [f (.listFiles deploy)
                              :when (str/ends-with? (.getName f) ".html")]
                          (.getName f)))
          fresh (set (filter #(str/ends-with? % ".html") pages))]
      (is (= deployed fresh) [(sort (set/difference fresh deployed))
                              (sort (set/difference deployed fresh))])
      (let [sm (slurp (clojure.java.io/file deploy "sitemap.xml"))]
        (is (str/includes? sm "https://etzhayyim.com/tate/index.html"))
        (is (not (.exists (clojure.java.io/file deploy "robots.txt"))))))))

#?(:clj (defn -main [& _] (run-tests 'tate.tests.test-site)))
