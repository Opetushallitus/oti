(ns oti.component.email-service
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [oti.boundary.db-access :as dba]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [oti.service.email-templates :as templates]
            [oti.util.http :refer [http-default-headers]]
            [oti.component.url-helper :refer [url]])
  (:import (java.util Optional)
           (fi.oph.viestinvalitys ClientBuilder)
           (fi.oph.viestinvalitys.vastaanotto.model ViestinvalitysBuilder LuoViestiSuccessResponse)))

(defprotocol EmailSender
  (send-email-to-participant! [this db params])
  (send-queued-mails! [this db])
  (email-sent? [this db params]))

(defn viestinvalitys-client [{:keys [url-helper cas]}]
  (log/info "Creating viestinvälityspalvelu client pointing to:" (url url-helper "viestinvalitys.endpoint"))
  (-> (ClientBuilder/viestinvalitysClientBuilder)
      (.withEndpoint (url url-helper "viestinvalitys.endpoint"))
      (.withUsername (-> cas :user :username))
      (.withPassword (-> cas :user :password))
      (.withCasEndpoint (url url-helper "cas.base"))
      (.withCallerId "1.2.246.562.10.00000000001.oti")
      (.build)))

(defn send-email-via-service! [{:keys [url-helper viestinvalityspalvelu-client]} {:keys [recipient subject body ext-reference-id]}]
  {:pre [(every? #(identity %) [recipient subject body]) (s/valid? :oti.spec/email recipient)]}
  (if-let [client @viestinvalityspalvelu-client]
    (do (log/info "Trying to send email" subject "to" recipient)
      (let [lahetys-response (-> client
                                 (.luoLahetys (-> (ViestinvalitysBuilder/lahetysBuilder)
                                                  (.withOtsikko subject)
                                                  (.withLahettavaPalvelu "oti")
                                                  (.withLahettaja (Optional/of "Opetushallitus") "no-reply@opintopolku.fi")
                                                  (.withNormaaliPrioriteetti)
                                                  (.withSailytysaika 1825)
                                                  (.build))))
            viesti-response (-> client
                                (.luoViesti (-> (ViestinvalitysBuilder/viestiBuilder)
                                                (.withOtsikko subject)
                                                (.withHtmlSisalto body)
                                                (.withVastaanottajat
                                                  (-> (ViestinvalitysBuilder/vastaanottajatBuilder)
                                                      (.withVastaanottaja (Optional/empty) recipient)
                                                      (.build)))
                                                (.withKayttooikeusRajoitukset
                                                  (-> (ViestinvalitysBuilder/kayttooikeusrajoituksetBuilder)
                                                      (.withKayttooikeus "APP_VIESTINVALITYS_OPH_PAAKAYTTAJA" "1.2.246.562.10.00000000001")
                                                      (.build)))
                                                (.withMetadatat
                                                  (cond-> (ViestinvalitysBuilder/metadatatBuilder)
                                                    (some? ext-reference-id) (.withMetadata "henkiloOid" [ext-reference-id])
                                                    true (.build)))
                                                (.withLahetysTunniste (str (.getLahetysTunniste lahetys-response)))
                                                (.build))))]
        (log/info "Got response" (str viesti-response) "from viestinvälityspalvelu")
        (when-not (instance? LuoViestiSuccessResponse viesti-response)
          (throw (Exception. (str "Could not send email to " recipient))))
        true))
    (throw (Exception. "Viestinvälityspalvelu client not initialized"))))

(defn- add-email-to-queue! [db {:keys [participant-id email-type exam-session-id template-id lang template-values]}]
  {:pre [(every? #(identity %) [participant-id template-id lang template-values])]}
  (log/info "Adding email to queue for participant" participant-id)
  (->> (templates/prepare-email template-id lang template-values)
       (merge {:participant-id participant-id
               :exam-session-id exam-session-id
               :email-type email-type})
       (dba/add-email-by-participant-id! db)))

(defn- send-emails! [this db]
  (jdbc/with-db-transaction [tx (:spec db) {:isolation :serializable}]
    (let [unsent-emails (dba/unsent-emails-for-update db tx)]
      (log/info "Sending emails with ids:" (->> unsent-emails (map :id) vec))
      (doseq [{:keys [id recipient subject body ext_reference_id]} unsent-emails]
        (when (send-email-via-service! this {:recipient recipient :body body :subject subject :ext-reference-id ext_reference_id})
          (dba/set-email-sent! db tx id))))))

(defrecord EmailService []
  component/Lifecycle
  (start [this] (assoc this :viestinvalityspalvelu-client (atom (viestinvalitys-client this))))
  (stop [this] (assoc this :viestinvalityspalvelu-client (atom nil)))
  EmailSender
  (send-email-to-participant! [this db template-params]
    (add-email-to-queue! db template-params))
  (send-queued-mails! [this db]
    (send-emails! this db))
  (email-sent? [this db {:keys [email-type] :as params}]
    (if-let [email (case email-type
                     "SCORES" (dba/scores-email db params))]
      email
      false)))

(defn email-service [config]
  (map->EmailService config))
