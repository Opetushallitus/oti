(ns oti.component.email-service-test
  (:require [clojure.test :refer :all]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as response]
            [oti.component.email-service :as eml-service]
            [oti.component.url-helper :refer [UrlResolver oph-properties]]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [oti.spec :as os])
  (:use clj-http.fake))

(defn- resolve-url [{:keys [oph-properties]} key params]
  (cond
    (= key "viestinvalitys.endpoint") "http://localhost:8090"
    (= key "cas.base") "http://localhost:8090/cas"
    :else (.url oph-properties (name key) (to-array (if (sequential? params) params [params])))))

(defrecord MockUrlHelper []
  UrlResolver
  (url [this key]
    (resolve-url this key [])))

(defn mock-url-helper [config]
  (map->MockUrlHelper config))

(defn tgt [_]
  {:status 201
   :headers {"location" "http://localhost:8090/cas/v1/tickets/TGT-123"}})

(defn st [_]
  {:status 200
   :body "ST-1234"})

(defn session [_]
  {:status 200
   :headers {"set-cookie" "JSESSIONID=foobar"}
   :body ""})

(defn lahetys-mock [_]
  {:status  200
   :headers {}
   :body    "{\"lahetysTunniste\":\"0181a38f-0883-7a0e-8155-83f5d9a3c226\"}"})

(defn viestit-mock [_]
  {:status  200
   :headers {}
   :body    "{\"viestiTunniste\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"lahetysTunniste\":\"0181a38f-0883-7a0e-8155-83f5d9a3c226\"}"})

(defmacro with-mock-server [& body]
  `(let [routes# {"/cas/v1/tickets" tgt
                  "/cas/v1/tickets/TGT-123" st
                  "/lahetys/login/j_spring_cas_security_check" session
                  "/lahetys/v1/lahetykset" lahetys-mock
                  "/lahetys/v1/viestit" viestit-mock}
         handlers# (fn [request#]
                     (if-let [handler# (routes# (:uri request#))]
                       (handler# request#)
                       {:status 404
                        :body "Not Found"}))
         jetty# (jetty/run-jetty handlers# {:port 8090 :daemon? true :join? false})]
    ~@body
    (.stop jetty#)))

(def config
  (-> (io/resource "dev.edn")
      slurp
      (clojure.edn/read-string)
      :config
      (assoc :oph-properties (oph-properties {:virkailija-host "localhost:8090"
                                              :oti-host "http://localhost:8090"
                                              :tunnistus-host "localhost:8090"
                                              :alb-host "http://localhost:8090"
                                              :oppija-host "localhost:8090"}))))

(def component
  (let [c (assoc config :url-helper (mock-url-helper config))]
  (eml-service/email-service
    (assoc c :viestinvalityspalvelu-client (atom (eml-service/viestinvalitys-client c))))))

(deftest test-send-email
  (let [email {:recipient "foo@oph.fi"
               :subject "Testimaili"
               :body "Tämä on testimaili"
               :ext-reference-id "1.2.3"}]
    (with-mock-server
      (is (true? (eml-service/send-email-via-service! component email))))))
