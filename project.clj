(defproject oti "0.1.0-SNAPSHOT"
  :description "Opetushallinnon tutkintoon ilmoittautuminen"
  :url "http://www.oph.fi/koulutus_ja_tutkinnot/opetushallinnon_tutkinto"
  :min-lein-version "2.0.0"
  :repositories [["oph-sade-releases" "https://artifactory.oph.ware.fi/artifactory/oph-sade-release-local"]
                 ["oph-sade-snapshots" "https://artifactory.oph.ware.fi/artifactory/oph-sade-snapshot-local"]]
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/tools.reader "1.0.0-beta3"]
                 [org.clojure/core.async "0.2.395"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [compojure "1.5.1"]
                 [duct "0.8.2"]
                 [environ "1.1.0"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-devel "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.4.0"]
                 [com.cognitect/transit-clj "0.8.290"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [ring-middleware-format "0.7.0"]
                 [duct/hikaricp-component "0.1.0" :exclusions [org.slf4j/slf4j-nop]]
                 [org.postgresql/postgresql "9.4.1212"]
                 [ragtime "0.6.3"]
                 [duct/ragtime-component "0.1.4"]
                 [suspendable "0.1.1"]
                 [http-kit "2.2.0"]
                 [org.clojars.pntblnk/clj-ldap "0.0.12"]
                 [cheshire "5.6.3"]
                 [org.clojars.mpenttila/yesql "0.6.1"]
                 [ring-logger "0.7.6"]
                 [org.clojure/core.cache "0.6.5"]
                 [overtone/at-at "1.2.0"]
                 [hiccup "1.0.5"]
                 [selmer "1.10.1"]

                 ;; Frontend
                 [org.clojure/clojurescript "1.9.293"]
                 [reagent "0.6.0"]
                 [binaryage/devtools "0.8.2"]
                 [re-frame "0.8.0"]
                 [day8.re-frame/http-fx "0.1.2"]
                 [kibu/pushy "0.3.6"]
                 [secretary "1.2.3"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [cljsjs/moment "2.15.2-2"]
                 [cljs-pikaday "0.1.3"]

                 ;; Logging
                 [com.taoensso/timbre "4.7.4"]
                 [com.fzakaria/slf4j-timbre "0.3.2"]

                 [fi.vm.sade/auditlogger "5.0.0-SNAPSHOT"]
                 [fi.vm.sade.java-utils/java-properties "0.1.0-SNAPSHOT"]]

  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.8"]
            [test2junit "1.2.2"]
            [lein-less "1.7.5"]]

  :main ^:skip-aot oti.main
  :target-path "target/%s/"
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
                                  [reloaded.repl "0.2.3"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [eftest "0.1.1"]
                                  [com.gearswithingears/shrubbery "0.4.1"]
                                  [kerodon "0.8.0"]]
                   :source-paths   ["dev/src"]
                   :resource-paths ["dev/resources"]
                   :repl-options {:init-ns user}
                   :env {:port "3000"
                         :dev? "true"}}
   :project/test  {}})
