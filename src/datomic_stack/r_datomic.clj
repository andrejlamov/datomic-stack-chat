(ns datomic-stack.r-datomic
  (:require
   [clojure.set :as set]
   [datomic-stack.schema :as schema]
   [datomic.api :as d]))

(defn find-txid-for-eid [db id] (d/q '[:find ?tx :in $ ?e :where [?e _ _ ?tx _]] db id))

(defn can-upsert? [db eid groups]
  (if eid
    (let [[tx-id] (first (find-txid-for-eid db eid))
          {:keys [tx/can-upsert]} (d/pull db '[:tx/can-upsert] tx-id)]
      (not (empty? (set/intersection (set groups) (set can-upsert)))))
    true))

(defn restricted-transact [conn data author tx-meta]
  ;; TODO: What if several identities?
  (let [ident-value (first (schema/identities data))
        id (:db/id data)
        eid (or id
                (:db/id (d/pull (d/db conn) [:db/id] ident-value)))]
    (when (can-upsert? (d/db conn) eid author)
      (d/transact conn [data tx-meta]))))

(defn pull-tx [conn eid]
  (let [db (d/db conn)
        txid  (->> eid
                   (find-txid-for-eid db)
                   first
                   first)]
    (d/pull db '[*] txid)))
