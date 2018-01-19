(ns datomic-stack.test
  (:require  [clojure.test :as t]
             [datascript.core :as ds]
             [datomic.api :as d]
             [datomic-stack.schema :as schema]
             [datomic-stack.r-datomic :as rd]
             [datascript.db :as db]
             [com.rpl.specter :as s]
             [clojure.set :as set]
             [datomic-stack.util :as u]))

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

(defn view [conn name]
  (filter-db conn (fn [d]
                    (contains? (:tx/can-read d) name))))

;; Try to syncing data between datomic and datascript with pull
(defn pull-d->ds [d-db ds-conn pattern eid]
  (let [e (d/pull d-db pattern eid)
        _ (ds/transact! ds-conn [e])]
    (ds/pull (ds/db ds-conn) pattern eid)))

(defn pull-d<-ds
  ([d-conn ds-conn pattern eid tx-meta]
   (pull-d<-ds pattern eid [] d-conn ds-conn tx-meta))
  ([d-conn ds-conn pattern eid excludes tx-meta]
   (let [e (ds/pull (ds/db ds-conn) pattern eid)
         e2 (apply dissoc (flatten [e excludes]))
         _ (d/transact d-conn [e2 tx-meta])]
     (d/pull (d/db d-conn) pattern eid))))


(t/deftest pull-d<->ds
  (let [d-conn    (fresh-db schema/datomic)
        ds-conn   (ds/create-conn schema/datascript)

        andrej-tx {:db/id "datomic.tx"
                   :tx/can-read #{"andrej"}
                   :tx/can-upsert #{"andrej"}}
        alex-tx   {:db/id "datomic.tx"
                   :tx/can-read #{"alex"}
                   :tx/can-upsert #{"alex"}}

        andrej   {:user/name "andrej"
                  :user/first-name "Andrej"
                  :user/last-name "Lamov"
                  :user/password "abc"
                  :user/email "andrej.lamov@gmail.com"}
        ;; Init Andrej

        _ (rd/restricted-transact d-conn andrej "andrej" andrej-tx)
        ;; Pull from datomic
        {:keys [:db/id]} (pull-d->ds (view d-conn "andrej") ds-conn
                                     '[*]
                                     [:user/name "andrej"])

        ;; Change password
        _ (ds/transact! ds-conn
                        [{:db/id id
                          :user/password "secret"
                          :user/password-repeat "secret"}])
        ;; Push to datomic
        {:keys [:user/password]} (pull-d<-ds d-conn ds-conn
                                             '[*]
                                             [:user/name "andrej"]
                                             [:user/password-repeat] andrej-tx)]
    (t/is (= password  "secret"))))

(t/deftest identity-keys
  (let [schema-identities #{:user/name :other/identity}]
    (t/is
     (= [[:user/name "andrej"]]
        (schema/identities {:user/name "andrej" :other/value 123})))
    (t/is
     (= #{:user/name}
        (set schema/identity-keys)))))

(t/deftest restriction
  (let [d-conn (fresh-db schema/datomic)
        andrej-tx {:db/id "datomic.tx"
                   :tx/author "andrej"
                   :tx/can-read #{"andrej", "room1"}
                   :tx/can-upsert #{"andrej"}}
        alex-tx   {:db/id "datomic.tx"
                   :tx/author "alex"
                   :tx/can-read #{"alex", "room1"}
                   :tx/can-upsert #{"alex"}}

        alex    {:user/name "alex"
                 :user/first-name "Alexander"
                 :user/last-name "Wingård"
                 :user/password "123"
                 :user/email "alexander.wingard@gmail.com"}

        andrej  {:user/name "andrej"
                 :user/first-name "Andrej"
                 :user/last-name "Lamov"
                 :user/password "abc"
                 :user/email "andrej.lamov@gmail.com"}

        ;; Add users
        _ (rd/restricted-transact d-conn andrej "andrej" andrej-tx)
        _ (rd/restricted-transact d-conn alex "alex" alex-tx)

        ;; I say hello
        _ (rd/restricted-transact d-conn {:message/text "hello alex"
                                          :message/author andrej}
                                  "andrej" andrej-tx)

        ;; Alex says hello
        _ (rd/restricted-transact d-conn {:message/text "hello andrej"
                                          :message/author alex}
                                  "alex" alex-tx)

        ;; Alex wants to change my message by finding its id and upsert
        [id] (first (d/q '[:find ?eid
                           :where [?eid :message/text]]
                         (view d-conn "room1")))
        _ (rd/restricted-transact d-conn {:db/id id
                                          :message/text "lololol"
                                          :message/author andrej}
                                  "alex" alex-tx)

        ;; it fails
        log (d/q '[:find ?m
                   :where [_ :message/text ?m]]
                 (view d-conn "room1"))
        _ (t/is (= #{["hello andrej"] ["hello alex"]} log))

        ;; Alex wants to change my password by unique identitiy :user/name
        _ (rd/restricted-transact d-conn {:user/name "andrej"
                                          :user/password "lololol"}
                                  "alex" alex-tx)
        {:keys [:user/password]} (d/pull (view d-conn "room1")
                                         [:user/password]
                                         [:user/name "andrej"])

        ;; Still unchanged
        _ (t/is (= "abc" password))

        ;; But I can change my message
        _ (rd/restricted-transact d-conn {:db/id id
                                          :message/text "lololol"}
                                  ["andrej"] andrej-tx)
        log (d/q '[:find ?m
                   :where [_ :message/text ?m]]
                 (view d-conn "room1"))
        _ (t/is (= #{["hello andrej"] ["lololol"]} log))]))

(t/deftest pull-tx-for-eid
  (let [conn (fresh-db schema/datomic)

        andrej-tx {:db/id "datomic.tx"
                   :tx/author "andrej"
                   :tx/can-read #{"andrej", "room1"}
                   :tx/can-upsert #{"andrej"}}

        andrej {:user/name "andrej"
                :user/first-name "Andrej"
                :user/last-name "Lamov"
                :user/password "abc"
                :user/email "andrej.lamov@gmail.com"}

        _ (rd/restricted-transact conn {:message/text "Hello!"
                                        :message/author andrej}
                                  "andrej" andrej-tx)
        id1 (->>
             (d/pull (view conn "andrej") [:db/id] [:user/name "andrej"])
             :db/id)
        id2 (->> (d/q '[:find ?eid
                        :where [?eid :message/text]]
                      (view conn "room1"))
                 (first)
                 (first))
        ]
        (t/is (= {:db/id nil} (rd/pull-tx conn -1)))
        (t/is (= andrej-tx
                 (u/kv-intersection andrej-tx (rd/pull-tx conn id1))
                 (u/kv-intersection andrej-tx (rd/pull-tx conn id2))))))
