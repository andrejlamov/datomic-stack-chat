(ns datomic-stack.core
  (:require [reagent.core :as r]
            [clojure.browser.net :as net]
            [clojure.browser.event :as event]
            [datomic-stack.r-datascript :as rds]))

(enable-console-print!)

(defonce ws (let [conn (net/websocket-connection)]
              (net/connect conn "ws://localhost:3449/ws")
              conn))

(event/listen ws :opened (fn []
                           (println "opened")
                           (net/transmit ws "hello echo ")))
(event/listen ws :message (fn [d] (js/console.log "messsage" (.-message d) )))


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
