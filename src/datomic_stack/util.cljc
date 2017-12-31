(ns datomic-stack.util
  (:require [reagent.core :as r]))

(defn create-atom [data]
  #?(:cljs (r/atom data)
     :clj (atom data)))

(defn cursorlike [atom path]
  #?(:cljs (r/cursor atom path)
     :clj  (atom (get-in atom path))))

