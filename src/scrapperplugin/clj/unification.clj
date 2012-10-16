(ns scrapperplugin.clj.unification
  (:use scrapperplugin.clj.logic)
  (:require [clojure.core.logic :as logic])
  (:require [damp.ekeko.jdt.reification :as jdt]))
 


;;helper macro
(defmacro and-let [bindings & body]
  (if (not (empty? bindings))
    `(if-let [~(first bindings) ~(second bindings)]
       (and-let ~(apply vector (next (next bindings))) ~@body)
       false)
    `(do ~@body)))

;;helpers
(defn loop-parents [class instance]
  (loop [curr instance]
    (when-not (nil? instance)
      (if (instance? class curr)
        curr
        (recur (.getParent curr))))))


(defn get-compilation-unit [ast]
  (loop-parents org.eclipse.jdt.core.dom.CompilationUnit ast))

(defn get-package [ast]
  (.getPackage
    (get-compilation-unit ast)))




(defn get-type-class [ast]
  (loop-parents org.eclipse.jdt.core.dom.TypeDeclaration ast))


;;macros because I got tired of copy pasting it
(defmacro generate-unification [class vars & body ]
  (let [classname (last (clojure.string/split (str class) #"\."))
        ;;de-camelcase classname, not pretty 
        decameld (apply str (rest 
                              (clojure.string/join 
                                "-" 
                                (map clojure.string/lower-case (clojure.string/split  classname #"(?=[A-Z])")))))
        protname (symbol (str "IUnifyWith" classname))
        methodname (symbol (str "unify-with-" decameld))]
  `(do
     (defprotocol ~protname
       (~methodname ~vars))
     (extend-protocol logic/IUnifyTerms
       ~class
       (:unify-terms [u# v# s#]  ;;lost 3 hours, use keyword here and not a symbol
                    (~methodname v# u# s#)))
     (extend-protocol ~protname
       nil
       (~methodname ~vars false)
       Object
       (~methodname ~vars false)
       ~class
       (~methodname ~vars ~@body)))))

(defmacro defunification [& unifications]
  (if-not (empty? unifications)
    (let [unification (first unifications)
          [name vars & body] unification
          restunification (rest unifications)]
      `(do
         (generate-unification ~name ~vars ~@body)
         (defunification ~@restunification)))
    '()))


(defunification
  [org.eclipse.jdt.core.dom.PrimitiveType
   [v prim s]
   (logic/unify s 
                (.getPrimitiveTypeCode v)
                (.getPrimitiveTypeCode prim))]
  [org.eclipse.jdt.core.dom.ArrayType
   [v arr s]
   (logic/unify s 
                (.getComponentType v)
                (.getComponentType arr))]
  [org.eclipse.jdt.core.dom.ParameterizedType
   [v par s]
   (logic/unify s 
                (.getType v)
                (.getType par))]
  [org.eclipse.jdt.core.dom.SimpleType
   [v stype s]
   (logic/unify s 
                (.getName v)
                (.getName stype))]
  [org.eclipse.jdt.core.dom.Name
   [v name s]
   (logic/unify s 
                (.getFullyQualifiedName v)
                (.getFullyQualifiedName name))]
  [org.eclipse.jdt.core.dom.TypeDeclaration
   [v tdecl s]
   (and-let [s 
             (logic/unify s 
                          (.getName v)
                          (.getName tdecl))
             s
             (logic/unify s
                          (.isInterface v)
                          (.isInterface tdecl))]
            s)]
  [org.eclipse.jdt.core.dom.PackageDeclaration
   [v pdecl s]
   (logic/unify s 
                (.getName v)
                (.getName pdecl))]
  [org.eclipse.jdt.core.dom.MethodDeclaration
   [v mdecl s]
   (and-let [s
             (logic/unify s
                          (.getName v)
                          (.getName mdecl))
             s
             (logic/unify s
                          (.getReturnType2 v)
                          (.getReturnType2 mdecl))
             s
             (logic/unify s
                          (.typeParameters v)
                          (.typeParameters mdecl))
             s
             (logic/unify s
                          (map (fn [x]
                                 (.getType x))
                               (.parameters v))
                          (map (fn [x]
                                 (.getType x))
                               (.parameters mdecl)))]
            s)]
  )