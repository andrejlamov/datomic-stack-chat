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

(def message [(fact :message/text :db.type/string :db.cardinality/one)])

(def permission [(fact :tx/can-read :db.type/string :db.cardinality/many)])

(def user [
              (fact :user/name      :db.type/string :db.cardinality/one :db.unique/identity)
              (fact :user/password  :db.type/string :db.cardinality/one)
              (fact :user/first-name :db.type/string :db.cardinality/one)
              (fact :user/last-name  :db.type/string :db.cardinality/one)
              (fact :user/email     :db.type/string :db.cardinality/one)])

(def datomic (flatten [user message permission]))
(def datascript (-> [user]
                 flatten
                 d->ds-schema
                 (merge {:user/password-repeat {:db.cardinaliry :db.cardinality/one}})))
