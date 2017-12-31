(ns datomic-stack.r-datascript-test
  (:require [datomic-stack.r-datascript :as rds]
            [datomic-stack.util :refer [create-atom]]
            [com.rpl.specter :as s]
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(t/deftest scratch
  (let [cache (create-atom {'a 1 'b 2 'c 3})
        res   (s/transform [s/ATOM s/ALL] (fn [[k v]] [k (inc v)]) cache)]
    (t/is (= {'a 2 'b 3 'c 4} @cache))))
