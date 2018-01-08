(ns datomic-stack.test
  (:require  [clojure.test :as t]
             [datascript.core :as ds]
             [datomic.api :as d]
             [datomic-stack.schema :as sch]
             [datascript.db :as db]))

(def schema-permission [;; Message

             {:db/ident :message/text
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Text payload of a message."}

             ;; Transaction metadata

             {:db/ident :user/name
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "User that performed the transcation."}

             {:db/ident :permission/groups
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/many
              :db/doc "Groups thah can apply associated transaction."}])

(defn fresh-db [schema]
  (let [db-name (gensym)
        db-uri (str "datomic:mem://" db-name)]
    (d/create-database db-uri)
    (let [conn (d/connect db-uri)]
      @(d/transact conn schema)
      conn)))


(defn filter-db [conn pred]
  (d/filter (d/db conn)
            (fn [db datom]
              (pred (d/entity db (.tx datom))))))

(t/deftest permission
  (let [conn (fresh-db schema-permission)
        query       '[:find ?text :where [_ :message/text ?text]]
        client-data  {:message/text "hello from Andrej"}
        tx-meta      {:db/id "datomic.tx"
                      :permission/groups #{"room1"}
                      :user/name "andrej"}]
    @(d/transact conn [client-data tx-meta])
    (t/is (= #{["hello from Andrej"]}
             (d/q query
                  (filter-db conn #(contains? (:permission/groups %) "room1")))))
    (t/is (= #{}
             (d/q query
                  (filter-db conn #(contains? (:permission/groups %) "room2")))))))

;; Try to syncing data between datomic and datascript with pull
(defn pull-d->ds [pattern eid d-conn ds-conn]
  (let [e (d/pull (d/db d-conn) pattern eid)
        _ (ds/transact! ds-conn [e])]
    (ds/pull (ds/db ds-conn) pattern eid)))

(defn pull-d<-ds
  ([pattern eid d-conn ds-conn]
   (pull-d<-ds pattern eid d-conn ds-conn []))
  ([pattern eid d-conn ds-conn excludes]
   (let [e (ds/pull (ds/db ds-conn) pattern eid)
         e2 (apply dissoc (flatten [e excludes]))
         _ (d/transact d-conn [e2])]
     (d/pull (d/db d-conn) pattern eid))))

(t/deftest pull-d<->ds
  (let [d-conn  (fresh-db sch/user-schema-be)
        ds-conn (ds/create-conn sch/user-schema-fe)
        user    {:user/name "andrej"
                 :user/firstName "Andrej"
                 :user/lastName "Lamov"
                 :user/password "abc"
                 :user/email "andrej.lamov@gmail.com"}
        _       (d/transact d-conn [user])
        _       (pull-d->ds '[*] [:user/name "andrej"] d-conn ds-conn)
        _       (ds/transact! ds-conn [{:user/name "andrej" :user/password "secret" :user/password-repeat "secret"}])
        _       (pull-d<-ds '[*] [:user/name "andrej"] d-conn ds-conn [:user/password-repeat])
        new-pw  (d/pull (d/db d-conn) '[:user/password] [:user/name "andrej"])]
    (t/is (= {:user/password "secret"} new-pw))))
