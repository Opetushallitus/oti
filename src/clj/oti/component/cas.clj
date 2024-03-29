(ns oti.component.cas
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [clojure.java.io :as io]
            [oti.util.http :refer [http-default-headers]]
            [oti.component.url-helper :refer [url]]
            [clojure.tools.logging :as log]))

(defrecord Cas []
  component/Lifecycle
  (start [this]
    (assoc this :sessions (atom {})))
  (stop [this] this))

(defprotocol CasAccess
  (request [cas service-path request-options]
    "Do a HTTP request to a service requiring CAS authentication")
  (username-from-valid-service-ticket [cas service-uri ticket]
    "Validate user's CAS ticket. Note that service-uri must be equal to the URI that was provided as the service
     parameter when redirecting user to the CAS login service, including any query parameters. The service-uri
     MUST NOT be URL-encoded, as the implementation will encode it when making the request."))

(defn cas-config [options]
  (map->Cas options))

(def tgt-pattern  #"(.*TGT-.*)")

(defn- error [^String msg]
  (throw (RuntimeException. msg)))

(defn- decode-tgt [{:keys [status headers]}]
  (if (= status 201)
    (if-let [location (:location headers)]
      (or (last (re-find tgt-pattern location))
          (error "TGT URI not found from Location header"))
      (error "Location header missing from TGT response"))
    (error (str "Invalid HTTP status " status " for TGT request"))))

(defn- fetch-ticket-granting-ticket [{:keys [url-helper user]}]
  (-> @(http/post (url url-helper "cas.tickets") {:form-params user
                                                  :headers (http-default-headers)})
      decode-tgt))

(defn- fetch-service-ticket [service-uri tgt-uri]
       (let [{:keys [body status]} @(http/post tgt-uri {:form-params {:service service-uri}
                                                        :headers (http-default-headers) :as :text })]
        (cond
          (not= 200 status) (error (str "Received status indicates failure, status: " status))
          (str/blank? body) (str "Blank body received for service ticket request. HTTP status: " status)
          :else (or (last (re-find #"(ST-.*)" body))
                    (error "No service ticket found from response")))))

(defn- decode-jsession [{:keys [status headers]}]
  (if (< status 400)
    (let [cookies (get headers :set-cookie [])]
      (->> (if (seq? cookies) cookies [cookies])
           (map #(re-find #"JSESSIONID=([^;]+)" %))
           (some last)))
    (error (str "Error fetching JSESSIONID. HTTP status: " status))))

(defn- fetch-jsessionid [service-uri service-ticket]
  (-> @(http/get service-uri {:query-params {:ticket service-ticket}
                              :headers (http-default-headers)
                              :follow-redirects false})
      decode-jsession))

(defn- fetch-cas-session [{:keys [url-helper] :as cas} service-path]
  (let [service-uri (url url-helper "cas.service-uri" [service-path])]
    (->> (fetch-ticket-granting-ticket cas)
         (fetch-service-ticket service-uri)
         (fetch-jsessionid service-uri))))

(defn- session-expired? [{:keys [status headers]}]
  (and (= status 302) (str/includes? (:location headers) "/cas/login")))

(defn- get-cas-session [{:keys [sessions] :as cas} service-path]
  (or (get @sessions service-path)
      (if-let [session-id (fetch-cas-session cas service-path)]
        (do (swap! sessions assoc service-path session-id)
            session-id)
        (error "No JSESSIONID found. Cannot execute request."))))

(defn- pick-tag [elements tag]
  (when (seq elements)
    (-> (filter #(= (:tag %) tag) elements)
        first)))

(extend-type Cas
  CasAccess
  (request [cas service-path request-options]
    (let [session (get-cas-session cas service-path)
          headers (merge {"Cookie" (str "JSESSIONID=" session "; csrf=csrf")} (http-default-headers))
          response @(http/request (-> (assoc request-options :follow-redirects false)
                                      (update :headers merge headers)))]
      (if (session-expired? response)
        (do (update cas :sessions swap! dissoc service-path)
            (request cas service-path request-options))
        response)))
  (username-from-valid-service-ticket [{:keys [url-helper]} service-uri ticket]
    (let [uri (url url-helper "cas.service-validate")
          {:keys [status body]} @(http/get uri {:query-params {:ticket ticket :service service-uri}
                                                :headers (http-default-headers)})]
      (when (= status 200)
        (with-open [in (io/input-stream (.getBytes body))]
          (let [parsed (xml/parse in)]
            (-> (pick-tag [parsed] :cas:serviceResponse)
                :content
                (pick-tag :cas:authenticationSuccess)
                :content
                (pick-tag :cas:user)
                :content
                first)))))))

(defn parse-ticket-from-logout-request [^String xml-str]
  (try
    (with-open [in (io/input-stream (.getBytes xml-str))]
      (let [parsed (xml/parse in)]
        (-> (pick-tag [parsed] :samlp:LogoutRequest)
            :content
            (pick-tag :samlp:SessionIndex)
            :content
            first)))
    (catch Throwable t
      (log/error t))))

