(defproject oti "0.1.0-SNAPSHOT"
  :description "Opetushallinnon tutkintoon ilmoittautuminen"
  :url "http://www.oph.fi/koulutus_ja_tutkinnot/opetushallinnon_tutkinto"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]
                 [org.clojure/tools.reader "1.0.0-beta3"]
                 [org.clojure/core.async "0.2.391"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [compojure "1.5.1"]
                 [duct "0.8.0"]
                 [environ "1.1.0"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-devel "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [duct/hikaricp-component "0.1.0"]
                 [org.postgresql/postgresql "9.4.1211"]
                 [ragtime "0.6.3"]
                 [duct/ragtime-component "0.1.4"]
                 [suspendable "0.1.1"]
                 [http-kit "2.2.0"]
                 [org.clojars.pntblnk/clj-ldap "0.0.12"]
                 [cheshire "5.6.3"]
                 [com.taoensso/timbre "4.7.4"]
                 [yesql "0.5.3"]

                 ;; Frontend
                 [org.clojure/clojurescript "1.9.229"]
                 [reagent "0.6.0"]]
  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.7"]
            [test2junit "1.2.2"]]
  :main ^:skip-aot oti.main
  :target-path "target/%s/"
  :source-paths ["src/clj"]
  :resource-paths ["resources" "target/cljsbuild"]
  :cljsbuild
  {:builds
   {:dev {:source-paths ["src/cljs" "dev"]
          :figwheel     {:on-jsload "oti.ui.app/start"}
          :compiler     {:optimizations :none
                         :main "oti.ui.app"
                         :asset-path "/oti/js"
                         :output-to  "target/figwheel/oti/public/js/main.js"
                         :output-dir "target/figwheel/oti/public/js"
                         :source-map true
                         :source-map-path "/oti/js"}}
    :main {:jar true
           :source-paths ["src/cljs"]
           :compiler {:output-to "target/cljsbuild/oti/public/js/main.js"
                      :optimizations :advanced}}}}
  :aliases {"setup"  ["run" "-m" "duct.util.repl/setup"]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :repl {:resource-paths ^:replace ["resources" "dev/resources" "target/figwheel"]
          :prep-tasks     ^:replace [["javac"] ["compile"]]}
   :uberjar {:aot :all
             :prep-tasks ^:replace ["clean"
                                    ["cljsbuild" "once" "main"]
                                    "javac"
                                    "compile"]
             :uberjar-name "oti.jar"}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [[duct/generate "0.8.0"]
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
