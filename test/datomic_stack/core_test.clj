(ns datomic-stack.core-test
  (:require [datomic-stack.core :as core]
            [datomic-stack.schema :as schema]
            [clojure.core.async :as async :refer [<!! <!]]
            [clojure.test :as t]
            [datomic.api :as d]
            [datomic-stack.r-datomic :as rd]))

(declare andrej alex put!-await)

(t/deftest scratch
  (let [db-conn (core/fresh-db schema/datomic)
        in (core/app db-conn)
        alex-out (async/chan)
        andrej-out (async/chan)]

    (t/testing "register same user twice"

      (t/is (= {:user/logged-in? true :user/name "andrej"}
               (put!-await in {:cmd "register" :kv-args andrej :out andrej-out})))

      (t/is (= {:user/logged-in? false
                :error.user/name "name already taken"}
               (put!-await in {:cmd "register" :kv-args andrej :out alex-out})))

      (t/is (= {:user/logged-in? true :user/name "alex"}
               (put!-await in {:cmd "register" :kv-args alex :out alex-out}))))


    (t/testing "login and logout"

      (t/is (= {:user/logged-in? true :user/name "andrej"}
               (put!-await in {:cmd "login" :kv-args andrej :out andrej-out})))

      (t/is (= {:user/logged-in? false}
               (put!-await in {:cmd "logout" :out andrej-out}))))

    (t/testing "re-login with unregistered user"
      (t/is (= {:user/logged-in? false :user/name "boel"}
               (put!-await in {:cmd "login"
                         :kv-args {:user/name "boel"
                                   :user/password "zxc"} :out andrej-out}))))

    (t/testing "create permission and share it"
      (put!-await in {:cmd "login" :kv-args andrej :out andrej-out})

      (put!-await in {:cmd "transact"
                      :out andrej-out
                      :kv-args {:data {:group/name "andrej/room1" :group/owner "andrej"}}})

      (t/is (= #{["andrej/andrej"] ["andrej/room1"]} (d/q '[:find ?name
                          :where
                          [_ :group/owner "andrej"]
                          [_ :group/name ?name]] (rd/view db-conn "andrej"))))

      )

    ))

;; Helpers
(def andrej
  {:user/name "andrej"
   :user/first-name "Andrej"
   :user/last-name "Lamov"
   :user/password "abc"
   :user/email "andrej.lamov@gmail.com"
   :group/name "andrej/andrej"
   :group/owner "andrej"})

(def alex
  {:user/name "alex"
   :user/first-name "Alexander"
   :user/last-name "Wingård"
   :user/password "123"
   :user/email "alexander.wingard@gmail.com"
   :group/name "alex/alex"
   :group/owner "alex"})

(defn put!-await [in {:keys [out] :as data}]
  (async/put! in data)
  (<!! out))

