(ns datomic-stack.test
  (:require  [clojure.test :as t]
             [datascript.core :as ds]
             [datomic.api :as d]
             [datomic-stack.schema :as schema]
             [datascript.db :as db]
             [clojure.set :as set]))

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
        {:keys [:db/id]} (pull-d->ds (view d-conn "andrej") ds-conn '[*] [:user/name "andrej"])

        ;; Change password
        _                (ds/transact! ds-conn
                                       [{:db/id id
                                         :user/password "secret"
                                         :user/password-repeat "secret"}])
        ;; Push to datomic
        _                (pull-d<-ds d-conn ds-conn '[*] [:user/name "andrej"] [:user/password-repeat] andrej-tx)

        ;; Alex tries to change my password
        _                (d/transact d-conn [{:user/name "andrej" :user/password "123"} alex-tx])
        other-pw         (d/pull (view d-conn "alex")   [:user/password] id)
        andrej-pw        (d/pull (view d-conn "andrej") [:user/password] id)]
    (t/is (= other-pw {:user/password "123"}))
    (t/is (= andrej-pw  {:user/password "secret"}))))

;; TODO: Match on unique identities as well, use schema to know what to patternmatch?
;; TODO: How about nested collections?
(defn can-upsert? [db id groups]
  (if id
    (let [[tx-id] (first (d/q '[:find ?tx :in $ ?e :where [?e _ _ ?tx _]] db id))
          {:keys [tx/can-upsert]} (d/pull db '[:tx/can-upsert] tx-id)]
      (not (empty? (set/intersection (set groups) (set can-upsert)))))
    true))

(defn restricted-transact [conn {:keys [db/id] :as data} author tx-meta]
  (when (can-upsert? (d/db conn) id author)
    (d/transact conn [data tx-meta])
    ))

;; TODO: Pull metadata about message (author info, timestamp)
(t/deftest chat-room-test
  (let [d-conn (fresh-db schema/datomic)
        andrej-tx {:db/id "datomic.tx" :tx/can-read #{"andrej", "room1"} :tx/can-upsert #{"andrej"}}
        alex-tx   {:db/id "datomic.tx" :tx/can-read #{"alex", "room1"} :tx/can-upsert #{"alex"}}
        ;; I say hello
        _ (d/transact d-conn [{:message/text "hello alex"} andrej-tx])
        ;; Alex says hello
        _ (d/transact d-conn [{:message/text "hello andrej"} alex-tx])

        ;; Alex wants to change my message by finding its id and upsert
        [id] (first (d/q '[:find ?eid :where [?eid :message/text]] (view d-conn "room1")))
        _ (restricted-transact d-conn {:db/id id :message/text "lololol"} ["alex"] alex-tx)
        ;; it fails
        log (d/q '[:find ?m :where [_ :message/text ?m]] (view d-conn "room1"))
        _ (t/is (= #{["hello andrej"] ["hello alex"]} log))

        ;; But I can change my message
        _ (restricted-transact d-conn {:db/id id :message/text "lololol"} ["andrej"] andrej-tx)
        log (d/q '[:find ?m :where [_ :message/text ?m]] (view d-conn "room1"))
        _ (t/is (= #{["hello andrej"] ["lololol"]} log))
        ]))
