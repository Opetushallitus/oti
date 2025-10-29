(defproject oti "0.1.0-SNAPSHOT"
  :description "Opetushallinnon tutkintoon ilmoittautuminen"
  :url "http://www.oph.fi/koulutus_ja_tutkinnot/opetushallinnon_tutkinto"
  :min-lein-version "2.4.0"
  :repositories [["github"        {:url      "https://maven.pkg.github.com/Opetushallitus/packages"
                                   :username "private-token"
                                   :password :env/GITHUB_TOKEN}]
                 ["releases"      {:url           "https://artifactory.opintopolku.fi/artifactory/oph-sade-release-local"
                                   :sign-releases false
                                   :snapshots     false}]
                 ["snapshots"     {:url      "https://artifactory.opintopolku.fi/artifactory/oph-sade-snapshot-local"
                                   :releases {:update :never}}]
                 ["ext-snapshots" {:url      "https://artifactory.opintopolku.fi/artifactory/ext-snapshot-local"
                                   :releases {:update :never}}]]
  :managed-dependencies [[clj-commons/clj-yaml "1.0.29"]
                         [com.google.code.gson/gson "2.8.9"]
                         [org.apache.commons/commons-compress "1.21"]
                         [org.apache.commons/commons-fileupload2-core "2.0.0-M4"]]
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [org.clojure/spec.alpha "0.2.194"]
                 [org.clojure/tools.reader "1.3.5"]
                 [org.clojure/core.async "1.3.610"]
                 [org.clojure/core.match "1.0.0"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.clojure/data.csv "1.0.0"]
                 [ch.qos.logback/logback-classic "1.2.13"]
                 [com.stuartsierra/component "1.0.0"]
                 [compojure "1.7.0"]
                 [duct "0.8.2"]
                 [environ "1.2.0"]
                 [binaryage/devtools "1.0.7"]
                 [ring/ring-core "1.11.0"]
                 [ring/ring-devel "1.11.0"]
                 [ring/ring-jetty-adapter "1.11.0" :scope "test"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.0"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.cognitect/transit-cljs "0.8.264"]
                 [ring-middleware-format "0.7.5"]
                 [duct/hikaricp-component "0.1.2" :exclusions [org.slf4j/slf4j-nop]]
                 [org.postgresql/postgresql "42.2.28"]
                 [ragtime "0.6.4"]
                 [duct/ragtime-component "0.1.4"]
                 [suspendable "0.1.1"]
                 [http-kit "2.5.3"]
                 [clj-http "3.10.3"]
                 [cheshire "5.13.0"]
                 [webjure/jeesql "0.4.7"]
                 [ring-logger "0.7.7"]
                 [org.clojure/core.cache "1.0.207"]
                 [overtone/at-at "1.2.0"]
                 [hiccup "1.0.5"]
                 [selmer "1.12.33"]
                 [org.clojure/data.xml "0.0.8"]
                 [opiskelijavalinnat-utils.viestinvalitys/kirjasto "1.2.2-SNAPSHOT" :exclusions [fi.vm.sade.java-utils/java-cas]]
                 [opiskelijavalinnat-utils/java-cas "1.2.4-SNAPSHOT" :exclusions[org.slf4j/slf4j-simple]]
                 [com.thoughtworks.paranamer/paranamer "2.8.3"]

                 ;; Frontend
                 [org.clojure/clojurescript "1.11.132"]
                 [reagent "1.2.0"]
                 [binaryage/devtools "1.0.7"]
                 [re-frame "1.4.3"]
                 [cljsjs/react "18.2.0-1"]
                 [cljsjs/react-dom "18.2.0-1"]
                 [day8.re-frame/http-fx "0.2.4"]
                 [kibu/pushy "0.3.8"]
                 [clj-commons/secretary "1.2.4"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [cljsjs/moment "2.24.0-0"]
                 [cljs-pikaday "0.1.4"]

                 ;; Logging
                 [com.taoensso/timbre "6.5.0"]

                 [fi.vm.sade/auditlogger "8.2.0-SNAPSHOT"]
                 [fi.vm.sade.java-utils/java-http "1.0.1-SNAPSHOT"]
                 [fi.vm.sade.java-utils/java-properties "1.0.0-SNAPSHOT"]
                 [jakarta.servlet/jakarta.servlet-api "6.0.0"]

                 ;; Security checks
                 [buddy/buddy-auth "2.2.0"]]

  :plugins [[lein-environ "1.2.0"]
            [lein-cljsbuild "1.1.8"]
            [lein-figwheel "0.5.20"]
            [test2junit "1.2.2"]
            [deraen/lein-less4clj "0.8.0"]
            [lein-ancient "0.7.0"]]

  :main ^:skip-aot oti.main
  :target-path "target/%s/"
  :clean-targets [:target-path "out_qa"
                  :target-path "out_prod"]
  :auto-clean false
  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]
  :less4clj {:source-paths ["src/less"]
             :target-path "resources/oti/public/css"}
  :figwheel {:css-dirs ["resources/oti/public/css"]}
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs" "src/cljc" "dev"]
                        :figwheel     {:on-jsload "oti.ui.app/mount-root"}
                        :compiler     {:optimizations :none
                                       :preloads [devtools.preload]
                                       :main "oti.ui.app"
                                       :asset-path "/oti/js"
                                       :output-to  "target/figwheel/oti/public/js/main-dev.js"
                                       :output-dir "target/figwheel/oti/public/js"
                                       :source-map true
                                       :source-map-path "/oti/js"}}
                       {:id "qa"
                        :jar true
                        :source-paths ["src/cljs" "src/cljc"]
                        :compiler {:output-to "target/cljsbuild/oti/public/js/main-qa.js"
                                   :output-dir "out_qa"
                                   :main "oti.ui.app"
                                   :optimizations :advanced
                                   :closure-defines {goog.DEBUG true}
                                   :pretty-print false}}
                       {:id "prod"
                        :jar true
                        :source-paths ["src/cljs" "src/cljc"]
                        :compiler {:output-to "target/cljsbuild/oti/public/js/main-prod.js"
                                   :output-dir "out_prod"
                                   :main "oti.ui.app"
                                   :optimizations :advanced
                                   :closure-defines {goog.DEBUG false}
                                   :pretty-print false}}]}
  :aliases {"setup"  ["run" "-m" "duct.util.repl/setup"]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :repl {:resource-paths ^:replace ["resources" "dev/resources" "target/figwheel"]
          :prep-tasks     ^:replace [["javac"] ["compile"]]}
   :uberjar {:aot :all
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
