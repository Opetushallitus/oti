(ns oti.component.localisation
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [taoensso.timbre :as log]))

(defprotocol LocalisationQuery
  "Localisation query protocol"
  (translations-by-lang [this lang] "Query translation by language")
  (refresh-translations [this] "Refresh all translations by fetching from the external localisation service")
  (t [this lang key]))

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
                                                                    (:default-parameters config)))
                            :translations-by-lang (atom {})))
  (stop [this] (assoc this :translations (atom {}) :translations-by-lang (atom {})))

  LocalisationQuery
  (translations-by-lang [{:keys [translations translations-by-lang]} lang]
    (let [t-key (keyword lang)]
      (if-let [t-map (t-key @translations-by-lang)]
        t-map
        (->> @translations
             (map (fn [[k v]] [k (t-key v)]))
             (into {})
             (swap! translations-by-lang assoc t-key)
             t-key))))
  (refresh-translations [{:keys [translations translations-by-lang]}]
    (when-let [new-translations (fetch-translations (:service-base-uri config)
                                                    (:default-parameters config))]
      (reset! translations-by-lang {})
      (reset! translations new-translations)))
  (t [this lang key]
    (get (translations-by-lang this lang) key key)))

(defn localisation [config]
  (->Localisation config))
