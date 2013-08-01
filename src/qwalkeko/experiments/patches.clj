(ns qwalkeko.experiments.patches
  (:refer-clojure :exclude [== type])
  (:require [clojure.core.logic :as logic])
  (:require [damp.qwal :as q])
  (:require [qwalkeko.clj.logic :as l])
  (:require [qwalkeko.clj.reification :as r]))



;;Finding Potential Merge Conflicts
(defn all-successors [graph a-version]
  "gets all the successors of a version"
  (logic/run* [result]
              (q/qwal graph a-version result []
                      (q/q=>+))))


(defn find-branching-versions [graph a-root]
  "gets all the branching versions starting from a root"
  (filter r/was-branched (all-successors graph a-root)))


(defn find-corresponding-merges-of-versions [graph version-left version-right]
  "finds all the merging versions that are a successor of both the left and right version"
  (let [merging-left (filter r/was-merged (conj (all-successors graph version-left) version-left))
        merging-right (filter r/was-merged (conj (all-successors graph version-right) version-right))]
    (clojure.set/intersection (set merging-left) (set merging-right))))

(defn find-corresponding-merges [graph branching-version]
  "finds all the merging versions that are a successor of both the left and the right branch of the branching version"
  ;;TODO: generalize so it works with more than 2 successors
  (let [succ (r/successors branching-version)
        left (first succ)
        right (second succ)]
    (find-corresponding-merges-of-versions graph left right)))


(defn find-corresponding-merge [graph branching-version]
  "finds the first (based on timestamp) merge of both the left and right branch of the branching version"
  (first (sort-by #(.getTime %1)
                  (find-corresponding-merges graph branching-version))))


(defn find-corresponding-merge-of-versions [graph version-left version-right]
  "finds the first (based on timestamp) merge of both the left and the right version"
  (first (sort-by #(.getTime %1)
                  (find-corresponding-merges-of-versions graph version-left version-right))))


(defn find-cochanged-file [graph branching-version]
  "finds versions that appear in parallel branching that modify the same file, potentially resulting in a merge conflict"
  (let [first-merge (find-corresponding-merge graph branching-version)]
    (when (not (nil? first-merge))
      (let [succ (r/successors branching-version)
            left (first succ)
            right (second succ)
            left-limited-graph (l/make-graph-between graph left first-merge)
            right-limited-graph (l/make-graph-between graph right first-merge)
            changed-left
            (logic/run* [changed-file changed-version]
                        (q/qwal left-limited-graph left first-merge []
                                (q/q=>*)
                                (q/qcurrent [curr]
                                            (logic/membero changed-file 
                                                           (r/edited-files curr))
                                            (logic/== changed-version curr))
                                (q/q=>+)))] ;;a + here as we do not want to include the merging version
        (logic/run* [cochanged left-version right-version]
                   (q/qwal right-limited-graph right first-merge []
                          (q/q=>*)
                          (q/qcurrent [curr]
                                      (logic/membero cochanged (r/edited-files curr))
                                      (logic/membero [cochanged left-version] changed-left)
                                      (logic/== right-version curr))
                          (q/q=>+)))))))


(comment
  (def a-model (first (r/history-project-models)))
  (def a-project (first (.getMetaProjects (.getMetaProduct a-model))))
  (def a-graph (l/make-graph a-project))
  (def branching-versions (find-branching-versions a-graph (first (.getRoots a-project))))
  (def results (filter (fn [x] (= "java" (apply str (reverse (take 4 (reverse (first x))))))) (apply concat (filter #(not (empty? %1)) (pmap #(find-cochanged-file a-graph %1) branching-versions))))))



;;Updating patches

(defn update-patch [graph branching-version version-left version-right]
  (q/qwal graph version-left branching-version []
        (q/q<=* ;;collect changes
          ))
  )

