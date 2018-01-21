(ns datomic-stack.core
  (:require
   [datomic.api :as d]
   [datomic-stack.util :as u]
   [datomic-stack.schema :as schema]
   [datomic-stack.r-datomic :as rd]
   [clojure.core.async :as async :refer :all]
   [clojure.set :as set]))

(declare fresh-db handle)

(defn app
  ([db-conn]
   (app db-conn
        (atom {})
        (async/chan)))
  ([db-conn
    out->user-name
    in]

   ;; handle incoming request
   (async/go-loop []
     (let [data (<! in)]
       (handle db-conn out->user-name data)
       (recur)))

   ;; TODO: react on db change

   ;; input channel
   in))

(declare login logout logged-in? register transact share-group)

(defn handle [db-conn out->user-name {:keys [cmd kv-args out]}]
  (let [user-name (get @out->user-name out)]
    (if (logged-in? out->user-name out)
      (case cmd
        "transact" (transact db-conn kv-args user-name out)
        "subscribe" nil
        "share-group" (share-group db-conn kv-args user-name out)

        "logout" (logout db-conn out->user-name out)
        "register" (register db-conn kv-args out->user-name out)
        "login" (login db-conn kv-args out->user-name out)
        (go (>! out {:user/logged-in? true :cmd.error/name (str cmd " command not found") })))
      (case cmd
        "register" (register db-conn kv-args out->user-name out)
        "login" (login db-conn kv-args out->user-name out)
        (go (>! out {:user/logged-in? false :cmd.error/name (str cmd " command not found") }))
        ))))

(defn share-group [db-conn {:keys [:user/name :group/name]} user-name out]
  (let [] (d/q '[:find ?name
                 :where
                 [_ :group/owner user-name]
                 [_ :group/name ?name]]
               (rd/view db-conn (str user-name "/" user-name))))
  )

(defn transact [db-conn
                {:keys [data can-read can-upsert] :as kv-args}
                user-name out]
    (rd/restricted-transact db-conn
                            data
                            user-name
                            (rd/tx-data
                             user-name
                             (conj (or can-read #{}) user-name)
                             (conj (or can-upsert #{}) user-name)))
      (go (>! out {:tx/completed true})))

(defn logged-in? [out->user-name out]
  (contains? @out->user-name out))

(defn logout [db-conn out->user-name out]
  (go
    (let [res (swap! out->user-name dissoc out)]
      (>! out {:user/logged-in? (contains? res out)}))))

(defn login [db-conn
             {:keys [:user/name :user/password] :as expected}
             out->user-name
             out]
  (let [actual (d/pull (d/db db-conn)
                       '[*]
                       [:user/name name])
        logged-in? (= expected
                      (u/kv-intersection expected actual))]
    (when logged-in?
      (swap! out->user-name assoc out name)
      )
    (go (>! out {:user/logged-in? logged-in?
                 :user/name name}))))

(defn register [db-conn
                {:keys [:user/name] :as user}
                out->user-name
                out]
  (go
    (let [{:keys [:db/id] :as existing} (d/pull
                                         (d/db db-conn)
                                         [:db/id]
                                         [:user/name name])]
      (if id
        (>! out {:user/logged-in? false
                 :error.user/name "name already taken"})
        (do
          (rd/restricted-transact db-conn
                                  user
                                  name
                                  (rd/tx-data name
                                              #{name}
                                              #{name}))
          (login db-conn user out->user-name out)))
      )))

(defn fresh-db [schema]
  (let [db-name (gensym)
        db-uri (str "datomic:mem://" db-name)]
    (d/create-database db-uri)
    (let [conn (d/connect db-uri)]
      @(d/transact conn schema)
      conn)))
