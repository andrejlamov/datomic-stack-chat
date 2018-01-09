(ns datomic-stack.test
  (:require  [clojure.test :as t]
             [datascript.core :as ds]
             [datomic.api :as d]
             [datomic-stack.schema :as schema]
             [datascript.db :as db]))

(defn fresh-db [schemaema]
  (let [db-name (gensym)
        db-uri (str "datomic:mem://" db-name)]
    (d/create-database db-uri)
    (let [conn (d/connect db-uri)]
      @(d/transact conn schemaema)
      conn)))


(defn filter-db [conn pred]
  (d/filter (d/db conn)
            (fn [db datom]
              (pred (d/entity db (.tx datom))))))

(defn view [conn name]
  (filter-db conn (fn [d]
                    (contains? (:tx/can-read d) name))))

;; Try to syncing data between datomic and datascript with pull
(defn pull-d->ds [pattern eid d-db ds-conn]
  (let [e (d/pull d-db pattern eid)
        _ (ds/transact! ds-conn [e])]
    (ds/pull (ds/db ds-conn) pattern eid)))

(defn pull-d<-ds
  ([pattern eid d-conn ds-conn tx-meta]
   (pull-d<-ds pattern eid d-conn ds-conn [] tx-meta))
  ([pattern eid d-conn ds-conn excludes tx-meta]
   (let [e (ds/pull (ds/db ds-conn) pattern eid)
         e2 (apply dissoc (flatten [e excludes]))
         _ (d/transact d-conn [e2 tx-meta])]
     (d/pull (d/db d-conn) pattern eid))))


(t/deftest pull-d<->ds
  (let [d-conn    (fresh-db schema/datomic)
        ds-conn   (ds/create-conn schema/datascript)

        andrej-tx {:db/id "datomic.tx" :tx/can-read #{"andrej"}}
        alex-tx   {:db/id "datomic.tx" :tx/can-read #{"alex"}}

        andrej    {:user/name "andrej"
                   :user/first-name "Andrej"
                   :user/last-name "Lamov"
                   :user/password "abc"
                   :user/email "andrej.lamov@gmail.com"}
        ;; Init Andrej
        _                (d/transact d-conn [andrej andrej-tx])

        ;; Pull from datomic
        {:keys [:db/id]} (pull-d->ds '[*] [:user/name "andrej"] (view d-conn "andrej") ds-conn)

        ;; Change password
        _                (ds/transact! ds-conn
                                       [{:db/id id
                                         :user/password "secret"
                                         :user/password-repeat "secret"}])
        ;; Push to datomic
        _                (pull-d<-ds '[*] [:user/name "andrej"] d-conn ds-conn [:user/password-repeat] andrej-tx)

        ;; Alex tries to change my password
        _                (d/transact d-conn [{:user/name "andrej" :user/password "123"} alex-tx])
        other-pw         (d/pull (view d-conn "alex")   [:user/password] id)
        andrej-pw          (d/pull (view d-conn "andrej") [:user/password] id)]
    (t/is (= other-pw {:user/password "123"}))
    (t/is (= andrej-pw  {:user/password "secret"}))))
