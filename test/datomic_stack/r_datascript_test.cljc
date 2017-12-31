(ns datomic-stack.r-datascript-test
  (:require [datomic-stack.r-datascript :as rds]
            [datomic-stack.util :refer [create-atom]]
            [com.rpl.specter :as s]
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(t/deftest transact-and-query []
  (let [query        '[:find ?n :where [_ :name ?n]]
        local-db     (rds/create-db)
        _            (rds/transact! local-db [{:name "Andrej"}])
        res1         @(rds/q query local-db)
        query-cache1 @(:query-cache local-db)
        _            (rds/transact! local-db [{:name "Alex"}])
        res2         @(rds/q query local-db)]
    (t/is  (not (= #{query #{}} query-cache1)))
    (t/is (= #{["Andrej"]} res1))
    (t/is (= #{["Andrej"] ["Alex"]} res2))))
