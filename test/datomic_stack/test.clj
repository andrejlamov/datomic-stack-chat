(ns datomic-stack.test
  (:require  [clojure.test :as t]
             [datascript.core :as ds]
             [datomic.api :as d]
             [datomic-stack.schema :as schema]
             [datomic-stack.r-datomic :as rd]
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
                         (rd/view d-conn "room1")))
        _ (rd/restricted-transact d-conn {:db/id id
                                          :message/text "lololol"
                                          :message/author andrej}
                                  "alex" alex-tx)

        ;; it fails
        log (d/q '[:find ?m
                   :where [_ :message/text ?m]]
                 (rd/view d-conn "room1"))
        _ (t/is (= #{["hello andrej"] ["hello alex"]} log))

        ;; Alex wants to change my password by unique identitiy :user/name
        _ (rd/restricted-transact d-conn {:user/name "andrej"
                                          :user/password "lololol"}
                                  "alex" alex-tx)
        {:keys [:user/password]} (d/pull (rd/view d-conn "room1")
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
                 (rd/view d-conn "room1"))
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
             (d/pull (rd/view conn "andrej") [:db/id] [:user/name "andrej"])
             :db/id)
        id2 (->> (d/q '[:find ?eid
                        :where [?eid :message/text]]
                      (rd/view conn "room1"))
                 (first)
                 (first))
        ]
        (t/is (= {:db/id nil} (rd/pull-tx conn -1)))
        (t/is (= andrej-tx
                 (u/kv-intersection andrej-tx (rd/pull-tx conn id1))
                 (u/kv-intersection andrej-tx (rd/pull-tx conn id2))))))
