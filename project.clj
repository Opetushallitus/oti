(defproject oti "0.1.0-SNAPSHOT"
  :description "Opetushallinnon tutkintoon ilmoittautuminen"
  :url "http://www.oph.fi/koulutus_ja_tutkinnot/opetushallinnon_tutkinto"
  :min-lein-version "2.4.0"
  :repositories [["oph-sade-releases" "https://artifactory.opintopolku.fi/artifactory/oph-sade-release-local"]
                 ["oph-sade-snapshots" "https://artifactory.opintopolku.fi/artifactory/oph-sade-snapshot-local"]]
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/spec.alpha "0.2.194"]
                 [org.clojure/tools.reader "1.3.5"]
                 [org.clojure/core.async "1.3.610"]
                 [org.clojure/core.match "1.0.0"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.clojure/data.csv "1.0.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [com.stuartsierra/component "1.0.0"]
                 [compojure "1.6.2"]
                 [duct "0.8.2"]
                 [environ "1.2.0"]
                 [ring/ring-core "1.9.1"]
                 [ring/ring-devel "1.9.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.0"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.cognitect/transit-cljs "0.8.264"]
                 [ring-middleware-format "0.7.4"]
                 [duct/hikaricp-component "0.1.2" :exclusions [org.slf4j/slf4j-nop]]
                 [org.postgresql/postgresql "42.2.19"]
                 [ragtime "0.6.4"]
                 [duct/ragtime-component "0.1.4"]
                 [suspendable "0.1.1"]
                 [http-kit "2.5.3"]
                 [clj-http "3.10.3"]
                 [cheshire "5.10.0"]
                 [webjure/jeesql "0.4.7"]
                 [ring-logger "0.7.7"]
                 [org.clojure/core.cache "1.0.207"]
                 [overtone/at-at "1.2.0"]
                 [hiccup "1.0.5"]
                 [selmer "1.12.33"]
                 [org.clojure/data.xml "0.0.8"]

                 ;; Frontend
                 [org.clojure/clojurescript "1.10.773"]
                 [reagent "1.0.0"]
                 [binaryage/devtools "1.0.2"]
                 [re-frame "1.2.0"]
                 [day8.re-frame/http-fx "0.2.3"]
                 [kibu/pushy "0.3.8"]
                 [secretary "1.2.3"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [cljsjs/moment "2.24.0-0"]
                 [cljs-pikaday "0.1.4"]

                 ;; Logging
                 [com.taoensso/timbre "5.1.2"]
                 #_[com.fzakaria/slf4j-timbre "0.3.2"]

                 [fi.vm.sade/auditlogger "8.2.0-SNAPSHOT"]
                 [fi.vm.sade.java-utils/java-http "0.1.4-SNAPSHOT"]
                 [fi.vm.sade.java-utils/java-properties "0.1.0-SNAPSHOT"]

                 ;; Security checks
                 [buddy/buddy-auth "2.2.0"]]

  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.20"]
            [test2junit "1.2.2"]
            [lein-less "1.7.5"]
            [lein-ancient "0.6.15"]]

  :main ^:skip-aot oti.main
  :target-path "target/%s/"
  :clean-targets [:target-path "out_devluokka"
                  :target-path "out_qa"
                  :target-path "out_prod"]
  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]
  :less {:source-paths ["src/less"]
         :target-path "resources/oti/public/css"}
  :figwheel {:css-dirs ["resources/oti/public/css"]}
  :cljsbuild
  {:builds
   {:dev {:source-paths ["src/cljs" "src/cljc" "dev"]
          :figwheel     {:on-jsload "oti.ui.app/reload-hook"}
          :compiler     {:optimizations :none
                         :main "oti.ui.app"
                         :asset-path "/oti/js"
                         :output-to  "target/figwheel/oti/public/js/main-dev.js"
                         :output-dir "target/figwheel/oti/public/js"
                         :source-map true
                         :source-map-path "/oti/js"}}

    :dev-luokka {:jar true
                 :source-paths ["src/cljs" "src/cljc"]
                 :compiler {:output-to "target/cljsbuild/oti/public/js/main-dev.js"
                            :output-dir "out_devluokka"
                            :optimizations :simple
                            :closure-defines {"goog.DEBUG" true}}}
    :qa {:jar true
         :source-paths ["src/cljs" "src/cljc"]
         :compiler {:output-to "target/cljsbuild/oti/public/js/main-qa.js"
                    :output-dir "out_qa"
                    :optimizations :advanced
                    :closure-defines {"goog.DEBUG" true}}}
    :prod {:jar true
           :source-paths ["src/cljs" "src/cljc"]
           :compiler {:output-to "target/cljsbuild/oti/public/js/main-prod.js"
                      :output-dir "out_prod"
                      :optimizations :advanced
                      :closure-defines {"goog.DEBUG" false}}}}}
  :aliases {"setup"  ["run" "-m" "duct.util.repl/setup"]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :repl {:resource-paths ^:replace ["resources" "dev/resources" "target/figwheel"]
          :prep-tasks     ^:replace [["javac"] ["compile"]]}
   :uberjar {:aot :all
             :prep-tasks ^:replace ["clean"
                                    ["cljsbuild" "once" "dev-luokka" "qa" "prod"]
                                    ["less" "once"]
                                    "javac"
                                    "compile"]
             :uberjar-name "oti.jar"}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [[duct/generate "0.8.2"]
                                  [reloaded.repl "0.2.4"]
                                  [org.clojure/tools.namespace "1.1.0"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [eftest "0.5.9"]
                                  [clj-http-fake "1.0.3"]
                                  [com.gearswithingears/shrubbery "0.4.1"]
                                  [kerodon "0.9.1"]
                                  [cider/cider-nrepl "0.25.9"]
                                  [figwheel-sidecar "0.5.20"]]
                   :source-paths   ["dev/src"]
                   :resource-paths ["dev/resources"]
                   :repl-options {:init-ns user}
                   :jvm-opts ["-Doti.baseUrl=http://localhost:3000"]
                   :env {:port "3000"
                         :dev? "true"}}
   :project/test  {}})
