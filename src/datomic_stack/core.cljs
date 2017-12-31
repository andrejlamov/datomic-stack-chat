(ns datomic-stack.core
  (:require [reagent.core :as r]
            [datomic-stack.r-datascript :as rds]))

(enable-console-print!)

(defonce local-db (rds/create-db))

(defn hello-world []
  (let [query '[:find ?n :where [_ :name ?n]]]
    [:div
     [:h1 (pr-str @(rds/q query local-db))]
     [:h1 "World!"]]))

(r/render-component [hello-world]
                    (. js/document (getElementById "app")))

(defn on-js-reload []
  (js/console.log
   (pr-str local-db)))

(rds/transact! local-db [{:name "Andrej"}])
