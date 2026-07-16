 ;; ISCO Workforce Coordinator cell
;; kotoba-clj port of the open-isco coordinator
;; modes: 0=lookup 1=summarize 2=coverage 3=parent 4=children 5=materialize 6=ratio

(defn noop [state] state)

(defn lookup [state]
  (let [code (map-get state "code")
        objs (kqe-get-objects "open-isco" code "isco.occupation/name")]
    (if (= (kqe-count objs) 1)
      (map-assoc! state "result" (kqe-obj-nth objs 0))
      (map-assoc! state "result" "NOT-FOUND"))))

(defn summarize [state]
  (let [code (map-get state "code")]
    (map-assoc! state "result" (llm-infer "isco-summarizer" code))))

(defn summarize? [state]
  (= (map-get state "mode") 1))

(defn coverage? [state]
  (= (map-get state "mode") 2))

(defn parent? [state]
  (= (map-get state "mode") 3))

(defn children? [state]
  (= (map-get state "mode") 4))

(defn materialize? [state]
  (= (map-get state "mode") 5))

(defn ratio? [state]
  (= (map-get state "mode") 6))

(defn coverage [state]
  (map-assoc! state "result"
    (bytes-finish (cbor-enc-uint! (bytes-alloc 16)
                    (kqe-count (kqe-query "isco.occupation/name"))))))

(defn coverage-ratio [state]
  (let [names (kqe-count (kqe-query "isco.occupation/name"))
        parents (kqe-count (kqe-query "isco.occupation/parent"))
        b0 (cbor-enc-map-header! (bytes-alloc 64) 2)
        b1 (cbor-enc-text! b0 "names")
        b2 (cbor-enc-uint! b1 names)
        b3 (cbor-enc-text! b2 "parents")
        b4 (cbor-enc-uint! b3 parents)]
    (map-assoc! state "result" (bytes-finish b4))))

(defn parent-of [state]
  (let [objs (kqe-get-objects "open-isco" (map-get state "code") "isco.occupation/parent")]
    (if (= (kqe-count objs) 1)
      (map-assoc! state "result" (kqe-obj-nth objs 0))
      (map-assoc! state "result" "NO-PARENT"))))

(defn count-children [qs target]
  (loop [i 0 acc 0]
    (if (>= i (kqe-count qs))
      acc
      (recur (+ i 1)
             (if (str-eq? (kqe-quad-object qs i) target) (+ acc 1) acc)))))

(defn child-count [state]
  (let [code (map-get state "code")
        target (bytes-finish (cbor-enc-text! (bytes-alloc 32) code))
        qs (kqe-query "isco.occupation/parent")]
    (map-assoc! state "result"
      (bytes-finish (cbor-enc-uint! (bytes-alloc 16) (count-children qs target))))))

(defn materialize [state]
  (let [code (map-get state "code")
        target (bytes-finish (cbor-enc-text! (bytes-alloc 32) code))
        qs (kqe-query "isco.occupation/parent")
        n (count-children qs target)
        hdr (cbor-enc-array-header! (bytes-alloc 256) n)]
    (map-assoc! state "result"
      (bytes-finish
        (loop [i 0 b hdr]
          (if (>= i (kqe-count qs))
            b
            (recur (+ i 1)
                   (if (str-eq? (kqe-quad-object qs i) target)
                     (cbor-enc-text! b (kqe-quad-subject qs i))
                     b))))))))

(defgraph coordinator
  :entry :route
  :nodes {:route noop :pick noop :pick2 noop :pick3 noop :pick4 noop :pick5 noop
          :coverage coverage :parent parent-of :children child-count
          :materialize materialize :ratio coverage-ratio
          :summarize summarize :lookup lookup}
  :edges {:route (if-edge coverage? :coverage :pick)
          :pick  (if-edge parent? :parent :pick2)
          :pick2 (if-edge children? :children :pick3)
          :pick3 (if-edge materialize? :materialize :pick4)
          :pick4 (if-edge ratio? :ratio :pick5)
          :pick5 (if-edge summarize? :summarize :lookup)
          :coverage :end :parent :end :children :end :materialize :end
          :ratio :end :summarize :end :lookup :end})

(defn run [ctx]
  (let [r1 (cbor-reader ctx)
        code (if (= (cbor-map-seek r1 "code") 1) (cbor-text r1) "NONE")
        r2 (cbor-reader ctx)
        mode (if (= (cbor-map-seek r2 "mode") 1) (cbor-uint r2) 0)
        state (map-make 8)
        state (map-assoc! state "code" code)
        state (map-assoc! state "mode" mode)
        final (coordinator state)]
    (map-get final "result")))
