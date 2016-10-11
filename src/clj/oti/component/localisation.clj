(ns oti.component.localisation
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [taoensso.timbre :as log]))

(defprotocol LocalisationQuery
  "Localisation query protocol"
  (translations-by-lang [this lang] "Query translation by language")
  (refresh-translations [this] "Refresh all translations by fetching from the external localisation service"))

(defn- parse-translations [response-body]
  (into {} (->> response-body
                (group-by :key)
                (map (fn [[key translation]]
                       {key (reduce (fn [ts t]
                                      (assoc ts (keyword (:locale t)) (:value t))) {} translation)})))))

(defn- fetch-translations [uri parameters]
  (log/debug "Fetching translations from:" uri "with query parameters:" parameters)
  (let [{:keys [status body]} @(http/get uri {:query-params parameters})]
    (if (= status 200)
      (try
        (parse-translations (parse-string body true))
        (catch Exception e
          (log/error "Could not parse response:" (.getMessage e))))
      (do (log/error "Error loading translations, HTTP status:" status)
          (throw (Exception. "Could not load translations"))))))

(defrecord Localisation [config]
  component/Lifecycle
  (start [this] (assoc this :translations (atom (fetch-translations (:service-base-uri config)
                                                                    (:default-parameters config)))))
  (stop [this] (assoc this :translations (atom {})))

  LocalisationQuery
  (translations-by-lang [this lang]
    (->> @(:translations this)
         (map (fn [[k v]] [k ((keyword lang) v)]))
         (into {})))
  (refresh-translations [this]
    (when-let [new-translations (fetch-translations (:service-base-uri config)
                                                    (:default-parameters config))]
      (reset! (:translations this) new-translations))))

(defn localisation [config]
  (->Localisation config))
