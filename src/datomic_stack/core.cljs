(ns datomic-stack.core
  (:require [reagent.core :as r]))

(enable-console-print!)

(defonce app-state (r/atom {:text "Hello"}))

(defn hello-world []
  [:div
   [:h1 (:text @app-state)]
   [:h1 "World!"]])

(r/render-component [hello-world]
                    (. js/document (getElementById "app")))

(defn on-js-reload []
  (swap! app-state assoc :text "Hello")
  (js/console.log @app-state))
