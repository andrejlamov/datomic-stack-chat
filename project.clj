(defproject datomic-stack "0.1.0-SNAPSHOT"
  :min-lein-version "2.7.1"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.datomic/datomic-free "0.9.5656"]
                 [org.clojure/clojurescript "1.9.494"]
                 [org.clojure/core.async  "0.3.465"]
                 [com.rpl/specter "1.0.5"]

                 [http-kit "2.2.0"]
                 [ring-cljsjs "0.1.0"]
                 [compojure "1.5.2"]
                 [ring "1.6.3"]
                 [ring-cljsjs "0.1.0"]

                 [datascript "0.16.3"]
                 [reagent "0.7.0"]]

  :plugins [[lein-figwheel "0.5.15-SNAPSHOT"]
            [lein-cljsbuild "1.1.7"]]

  :source-paths ["src"]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]

                :figwheel {:on-jsload "datomic-stack.core/on-js-reload"
                           :open-urls ["http://localhost:3449/index.html"]}

                :compiler {:main datomic-stack.core
                           :asset-path "js/out"
                           :output-to "resources/public/js/main.js"
                           :output-dir "resources/public/js/out"
                           :source-map-timestamp true
                           :preloads [devtools.preload]}}
               ;; Build this with: lein cljsbuild once min
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/main.js"
                           :main datomic-stack.core
                           :optimizations :advanced
                           :pretty-print false}}]}

  :figwheel { :ring-handler datomic-stack.server/handler}

  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.4"]
                                  [figwheel-sidecar "0.5.15-SNAPSHOT"]
                                  [com.cemerick/piggieback "0.2.2"]]
                   :source-paths ["src"]
                   :plugins [[cider/cider-nrepl "0.16.0-SNAPSHOT"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :clean-targets ^{:protect false} ["resources/public/js"
                                                     :target-path]}})


