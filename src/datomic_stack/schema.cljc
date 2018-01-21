(ns datomic-stack.schema
  (:require [com.rpl.specter :as s]))

(defn fact
  ([ident type cardinality unique]
   (assoc (fact ident type cardinality) :db/unique unique))
  ([ident type cardinality]
   {:db/ident ident
    :db/valueType type
    :db/cardinality cardinality}))

(defn d->ds-schema [schema]
  (into {}
        (s/transform [s/ALL (s/collect-one :db/ident)]
                     (fn [a b] {a (dissoc
                                   b
                                   :db/ident
                                   :db/valueType)})
                     schema)))

(def datomic [
              (fact :message/text :db.type/string :db.cardinality/one)
              (fact :message/author :db.type/ref :db.cardinality/one)

              (fact :tx/can-read :db.type/string :db.cardinality/many)
              (fact :tx/can-upsert :db.type/string :db.cardinality/many)
              (fact :tx/author :db.type/string :db.cardinality/one)

              (fact :user/name :db.type/string :db.cardinality/one :db.unique/identity)
              (fact :user/password :db.type/string :db.cardinality/one)

              ;; should always be prefixed with username
              (fact :group/name :db.type/string :db.cardinality/one)
              (fact :group/owner :db.type/string :db.cardinality/one)

              (fact :user/first-name :db.type/string :db.cardinality/one)
              (fact :user/last-name :db.type/string :db.cardinality/one)
              (fact :user/email :db.type/string :db.cardinality/one)])

(def datascript (-> datomic
                    d->ds-schema
                    (merge {:user/password-repeat {:db.cardinality :db.cardinality/one}})))

(def identity-keys
  (s/select [s/ALL (s/if-path [:db/unique (s/pred= :db.unique/identity)] :db/ident)] datomic))

(defn identities [data]
  (vec (select-keys data identity-keys)))
