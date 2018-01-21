(ns datomic-stack.server
  (:require [clojure.core.async :as as]
            [compojure.core :refer [defroutes GET]]
            [compojure.handler :refer [site]]
            [compojure.route :refer [resources]]
            [org.httpkit.server :refer [with-channel on-close on-receive with-channel send!]]
            [ring.middleware.cljsjs :refer [wrap-cljsjs]] ))

(defn ws-handler [request]
  (with-channel request channel
    (on-close channel (fn [status] '(println "on-close" status)))
    (on-receive channel (fn [data] (send! channel data)))))

(defroutes routes
  (GET "/ws" [] ws-handler)
  (wrap-cljsjs (resources "/")))

(def handler
  (site #'routes))
