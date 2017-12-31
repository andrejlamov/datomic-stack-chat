(ns datomic-stack.r-datascript
  (:require [datascript.core :as d]
            [reagent.core :as r]
            [com.rpl.specter :as s]
            [datomic-stack.util :refer [create-atom cursorlike]]))

(defn transact! [{:keys [conn]} data]
  (d/transact! conn data))

(defn q [query {:keys [query-cache conn]}]
  (when (not (get-in @query-cache [query]))
    (swap! query-cache assoc query (d/q query (d/db conn))))
  (cursorlike query-cache [query]))

(defn update-query-cache [db cache]
  (s/transform [s/ATOM s/ALL]
               (fn [[query result]] [query (d/q query db)])
               cache))

(defn create-db []
  (let [conn (d/create-conn)
        query-cache (create-atom {})]

    (d/listen! conn
               (fn [{:keys [db-after]}]
                 (update-query-cache db-after query-cache)))

    {:query-cache query-cache
     :conn conn}))
