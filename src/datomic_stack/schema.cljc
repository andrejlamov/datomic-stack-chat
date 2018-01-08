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
                     (fn [a b] {a (dissoc b :db/ident :db/valueType)})
                     schema)))

(def user-schema-be [
                     (fact :user/name      :db.type/string :db.cardinality/one :db.unique/identity)
                     (fact :user/password  :db.type/string :db.cardinality/one)
                     (fact :user/firstName :db.type/string :db.cardinality/one)
                     (fact :user/lastName  :db.type/string :db.cardinality/one)
                     (fact :user/email     :db.type/string :db.cardinality/one)])

(def user-schema-fe (-> user-schema-be
                        d->ds-schema
                        (merge {:user/password-repeat {:db.cardinaliry :db.cardinality/one}})))
