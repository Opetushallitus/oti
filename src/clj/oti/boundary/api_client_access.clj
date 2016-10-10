(ns oti.boundary.api-client-access
  (:require [oti.component.cas :as cas-api]
            [cheshire.core :as json]
            [taoensso.timbre :refer [error]])
  (:import [oti.component.api_client ApiClient]))

(defprotocol ApiClientAccess
  (get-person [client external-user-id])
  (add-person! [client person]))

(defn- request-map [{:keys [authentication-service-path cas]} & path-parts]
  {:url (apply str (:virkailija-lb-uri cas) authentication-service-path path-parts)})

(defn- parse [{:keys [status body] :as response}]
  (if (= status 200)
    (json/parse-string body)
    (error response)))

(extend-protocol ApiClientAccess
  ApiClient
  (get-person [{:keys [authentication-service-path cas] :as client} external-user-id]
    (->> (request-map client "/resources/henkilo/" external-user-id)
         (cas-api/request cas authentication-service-path)
         parse))
  (add-person! [{:keys [authentication-service-path cas]} person]))
