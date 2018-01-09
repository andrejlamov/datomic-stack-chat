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
                    (contains? (:tx/read d) name))))

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

        andrej-tx {:db/id "datomic.tx" :tx/read #{"andrej"} :tx/user "andrej"}
        alex-tx   {:db/id "datomic.tx" :tx/read #{"alex"}   :tx/user "alex"}

        user    {
                 :user/name "andrej"
                 :user/firstName "Andrej"
                 :user/lastName "Lamov"
                 :user/password "abc"
                 :user/email "andrej.lamov@gmail.com"}

        _       @(d/transact d-conn [user andrej-tx])
        _       (pull-d->ds '[*] [:user/name "andrej"] (view d-conn "andrej") ds-conn)
        _       (ds/transact! ds-conn [{:user/name "andrej" :user/password "secret" :user/password-repeat "secret"}])
        _       (pull-d<-ds '[*] [:user/name "andrej"] d-conn ds-conn [:user/password-repeat] andrej-tx)

        _       @(d/transact d-conn [(assoc user :user/password "123") alex-tx])

        new-pw  (d/pull (view d-conn "andrej") '[:user/password] [:user/name "andrej"])]
    (t/is (= {:user/password "secret"} new-pw))))
