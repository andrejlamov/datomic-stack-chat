(ns datomic-stack.util
  (:require [reagent.core :as r]))

(defn create-atom [data]
  #?(:cljs (r/atom data)
     :clj (atom data)))

(defn cursorlike [a path]
  #?(:cljs (r/cursor a path)
     :clj  (atom (get-in @a path))))

