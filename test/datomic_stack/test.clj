(ns datomic-stack.test
  (:require  [clojure.test :as t]
             [datomic.api :as d]))

(def schema [
             ;; Message

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

(defn fresh-db []
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

(t/deftest scratch
  (let [conn (fresh-db)
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
