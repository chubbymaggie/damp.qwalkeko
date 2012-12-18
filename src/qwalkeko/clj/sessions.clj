(ns qwalkeko.clj.sessions
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic :as logic])
  (:use [qwalkeko.clj.reification :as reification]))
 

(def ^:dynamic *current-session*)

(defn open-session []
  #{})

(defn close-session [session]
  (doall
    (map reification/ensure-delete session)))


(defmacro in-session [ & body]
  (let [result (gensym "result")]
  `(binding [*current-session* (open-session)]
     (let [~result (do ~@body)]
       (close-session *current-session*)
       ~result))))



(defmacro scurrent [[version] & goals]
  (let [graph (gensym "graph")
        next (gensym "next")]
  `(fn [~graph ~version ~next]
     (when (not (bound? #'*current-session*))
       (throw (new qwalkeko.SessionUnboundException)))
     (logic/project [~version]
       (logic/all
         (reification/ensure-checkouto ~version)
         (logic/== nil ;;isnt she pretty?
             (do 
               (set! *current-session* (conj *current-session* ~version))
               nil))
         ~@goals
         (logic/== ~version ~next))))))


(defn set-current [version]
  (all
    (== true 
        (do
          (swap! damp.ekeko.ekekomodel/*queried-project-models* 
                 (fn [previous] 
                   (list (.getJavaProjectModel (damp.ekeko.EkekoModel/getInstance) (.getEclipseProject version)))))
          true))))




(defmacro vcurrent [[version] & goals]
  "Opens and sets the current version, and will evaluate all the goals in the current version.

  To ensure that the goals are always evaluated in the correct version (for example when backtracking) an additional
  conde is added to the end that resets the current version."
  `(fn [graph# ~version next#]
     (logic/project [~version]
                    (all
                      (set-current ~version)
                      (ensure-checkouto ~version)
                      ~@goals
                      (logic/== ~version next#)
                      ;;by first succeeding we just "skip" this conde
                      ;;when backtracking the alternative branch will be tried, restoring the current version
                      ;;as we immediately fail we will try other solutions for the provided goals
                      ;;these may provide new solutions, and we repeat the above structure.
                      (conde [logic/succeed]
                             [(set-current ~version)
                              logic/fail])))))
     
                            
                      
                      