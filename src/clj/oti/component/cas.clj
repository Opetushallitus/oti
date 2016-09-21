(ns oti.component.cas
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [clojure.java.io :as io]))

(defrecord Cas []
  component/Lifecycle
  (start [this]
    (assoc this :sessions (atom {})))
  (stop [this] this))

(defprotocol CasAccess
  (request [cas service-path request-options])
  (username-from-valid-service-ticket [cas service-uri ticket]))

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

(defn- fetch-ticket-granting-ticket [{:keys [virkailija-lb-uri user]}]
  (-> @(http/post (str virkailija-lb-uri "/cas/v1/tickets") {:form-params user})
      decode-tgt))

(defn- fetch-service-ticket [service-uri tgt-uri]
  (let [{:keys [body status]} @(http/post tgt-uri {:form-params {:service service-uri}})]
    (if-not (str/blank? body)
      (or (last (re-find #"(ST-.*)" body))
          (error "No service ticket found from response"))
      (error (str "Blank body received for service ticket request. HTTP status: " status)))))

(defn- decode-jsession [{:keys [status headers]}]
  (if (< status 400)
    (let [cookies (get headers :set-cookie [])]
      (->> (if (seq? cookies) cookies [cookies])
           (map #(re-find #"JSESSIONID=([^;]+)" %))
           (some last)))
    (error (str "Error fetching JSESSIONID. HTTP status: " status))))

(defn- fetch-jsessionid [service-uri service-ticket]
  (-> @(http/get service-uri {:query-params {:ticket service-ticket}
                              :follow-redirects false})
      decode-jsession))

(defn- fetch-cas-session [{:keys [virkailija-lb-uri] :as cas} service-path]
  (let [service-uri (str virkailija-lb-uri service-path "/j_spring_cas_security_check")]
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
          headers {"Cookie" (str "JSESSIONID=" session)}
          response @(http/request (-> (assoc request-options :follow-redirects false)
                                      (update :headers merge headers)))]
      (if (session-expired? response)
        (do (update cas :sessions swap! dissoc service-path)
            (request cas service-path request-options))
        response)))
  (username-from-valid-service-ticket [{:keys [virkailija-lb-uri]} service-uri ticket]
    (let [uri (str virkailija-lb-uri "/cas/serviceValidate")
          {:keys [status body]} @(http/get uri {:query-params {:ticket ticket :service service-uri}})]
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
    (catch Throwable _)))
