(ns oti.boundary.api-client-access
  (:require [oti.component.cas :as cas-api]
            [cheshire.core :as json]
            [taoensso.timbre :refer [error]]
            [oti.component.api-client])
  (:import [oti.component.api_client ApiClient]))

(defprotocol ApiClientAccess
  (get-person [client external-user-id])
  (get-person-by-hetu [client hetu])
  (add-person! [client person]))

(defn- request-map [{:keys [authentication-service-path cas]} & path-parts]
  {:url (apply str (:virkailija-lb-uri cas) authentication-service-path path-parts)})

(defn- parse [{:keys [status body] :as response}]
  (if (= status 200)
    (json/parse-string body true)
    (error "API request error:" response)))

(defn- parse-oid [{:keys [status body] :as response}]
  (if (= status 200)
    body
    (error "API request error:" response)))

(extend-protocol ApiClientAccess
  ApiClient
  (get-person [{:keys [authentication-service-path cas] :as client} external-user-id]
    (->> (request-map client "/resources/henkilo/" external-user-id)
         (cas-api/request cas authentication-service-path)
         parse))
  (get-person-by-hetu [{:keys [authentication-service-path cas] :as client} hetu]
    (->> (request-map client "/resources/henkilo?p=false&q=" hetu)
         (cas-api/request cas authentication-service-path)
         parse
         :results
         first))
  (add-person! [{:keys [authentication-service-path cas] :as client} person]
    (->> (request-map client "/resources/henkilo")
         (merge {:method :post
                 :body (json/encode person)
                 :headers {"Content-Type" "application/json; charset=utf-8"}})
         (cas-api/request cas authentication-service-path)
         parse-oid)))
