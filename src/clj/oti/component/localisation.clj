(ns oti.component.localisation
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [taoensso.timbre :refer [debug error]]))

(defprotocol LocalisationQuery
  "Localisation query protocol"
  (by-lang [this lang] "Query translation by language")
  (refresh [this] "Refresh all translations by fetching from the external localisation service"))

(defn- parse-translations [response-body]
  (into {} (->> response-body
                (group-by :key)
                (map (fn [[key translation]]
                       {key (reduce (fn [ts t]
                                      (assoc ts (->kebab-case-keyword (:locale t)) (:value t))) {} translation)})))))

(defn- fetch-translations [uri parameters]
  (debug "Fetching translations from:" uri "with query parameters:" parameters)
  (let [{:keys [headers status error body]} @(http/get uri {:query-params parameters})]
    (try
      (parse-translations (parse-string body ->kebab-case-keyword))
      (catch Exception e
        (error "Could not parse response:" (.getMessage e))))))

(defrecord Localisation [config]
  component/Lifecycle
  (start [this] (assoc this :translations (atom (fetch-translations (:service-base-uri config)
                                                                    (:default-parameters config)))))
  (stop [this] (assoc this :translations (atom {})))

  LocalisationQuery
  (by-lang [this lang]
    (->> @(:translations this)
         (map (fn [[k v]] [k ((keyword lang) v)]))
         (into {})))
  (refresh [this]
    (when-let [new-translations (fetch-translations (:service-base-uri config)
                                                    (:default-parameters config))]
      (reset! (:translations this) new-translations))))

(defn localisation [config]
  (->Localisation config))
