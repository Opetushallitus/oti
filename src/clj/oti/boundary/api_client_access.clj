(ns oti.boundary.api-client-access
  (:require [oti.component.cas :as cas-api]
            [cheshire.core :as json]
            [taoensso.timbre :refer [error info]]
            [oti.component.api-client]
            [oti.component.url-helper :refer [url]])
  (:import [oti.component.api_client ApiClient]))

(defprotocol ApiClientAccess
  (get-persons [client ids])
  (get-person-by-id [client external-user-id])
  (get-person-by-hetu [client hetu])
  (add-person! [client person])
  (update-person! [client external-user-id address]))

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
  (get-persons [{:keys [authentication-service cas url-helper]} ids]
    (info "Requesting user details for" (count ids) "user ids")
    (->> {:url (url url-helper "authentication-service.henkilot-by-oid-list")}
         (merge (json-req :post ids))
         (cas-api/request cas authentication-service)
         parse))
  (get-person-by-id [{:keys [authentication-service cas url-helper]} external-user-id]
    (->> {:url (url url-helper "authentication-service.henkilo-by-oid" [external-user-id])}
         (cas-api/request cas authentication-service)
         parse))
  (get-person-by-hetu [{:keys [authentication-service cas url-helper]} hetu]
    (->> {:url (url url-helper "authentication-service.henkilo-by-hetu") :query-params {:q hetu :p "false"}}
         (cas-api/request cas authentication-service)
         parse
         :results
         first))
  (add-person! [{:keys [authentication-service cas url-helper]} person]
    (->> {:url (url url-helper "authentication-service.henkilo")}
         (merge (json-req :post person))
         (cas-api/request cas authentication-service)
         parse-oid))
  (update-person! [{:keys [authentication-service cas url-helper]} external-user-id address]
    (let [{:keys [body status]} (->> {:url (url url-helper "authentication-service.henkilo-by-oid" [external-user-id])}
                                     (merge (json-req :put address))
                                     (cas-api/request cas authentication-service))]
      (when-not (= status 200)
        (throw (Exception. (str "Could not update person in API. HTTP status: " status ", message: " body)))))))
