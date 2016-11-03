(ns oti.boundary.api-client-access
  (:require [oti.component.cas :as cas-api]
            [cheshire.core :as json]
            [taoensso.timbre :refer [error]]
            [oti.component.api-client]
            [taoensso.timbre :refer [info]])
  (:import [oti.component.api_client ApiClient]))

(defprotocol ApiClientAccess
  (get-persons [client ids])
  (get-person-by-id [client external-user-id])
  (get-person-by-hetu [client hetu])
  (add-person! [client person])
  (update-person! [client external-user-id address]))

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

(defn- json-req [method data]
  {:method method
   :body (json/encode data)
   :headers {"Content-Type" "application/json; charset=utf-8"}})

(extend-protocol ApiClientAccess
  ApiClient
  (get-persons [{:keys [authentication-service-path cas] :as client} ids]
    (info "Requesting user details for" (count ids) "user ids")
    (->> (request-map client "/resources/henkilo/henkilotByHenkiloOidList")
         (merge (json-req :post ids))
         (cas-api/request cas authentication-service-path)
         parse))
  (get-person-by-id [{:keys [authentication-service-path cas] :as client} external-user-id]
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
         (merge (json-req :post person))
         (cas-api/request cas authentication-service-path)
         parse-oid))
  (update-person! [{:keys [authentication-service-path cas] :as client} external-user-id address]
    (let [{:keys [body status]} (->> (request-map client "/resources/henkilo/" external-user-id)
                                     (merge (json-req :put address))
                                     (cas-api/request cas authentication-service-path))]
      (when-not (= status 200)
        (throw (Exception. (str "Could not update person in API. HTTP status: " status ", message: " body)))))))
