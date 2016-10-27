(ns oti.component.email-service
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [oti.boundary.db-access :as dba]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec :as s]
            [oti.service.email-templates :as templates]))

(defprotocol EmailSender
  (send-email-to-participant! [this db params])
  (send-queued-mails! [this db]))

(defn- send-email-via-service! [{:keys [email-service-url]} {:keys [recipients subject body]}]
  {:pre [(every? #(identity %) [recipients subject body]) (s/valid? :oti.spec/email (first recipients))]}
  (log/info "Trying to send email" subject "to" recipients)
  (let [wrapped-recipients (mapv (fn [rcp] {:email rcp}) recipients)
        {:keys [status]} @(http/post email-service-url {:headers {"content-type" "application/json"}
                                                        :body    (json/generate-string {:email     {:subject subject
                                                                                                    :isHtml  false
                                                                                                    :body    body}
                                                                                        :recipient wrapped-recipients})})]
    (if (= 200 status)
      (do
        (log/info "Successfully sent email to" recipients)
        true)
      (log/error "Could not send email to" recipients ", HTTP status from email service was" status))))

(defn- add-email-to-queue! [db {:keys [participant-id template-id lang template-values]}]
  {:pre [(every? #(identity %) [participant-id template-id lang template-values])]}
  (->> (templates/prepare-email template-id lang template-values)
       (merge {:participant-id participant-id})
       (dba/add-email-by-participant-id! db)))

(defn- send-emails! [this db]
  (jdbc/with-db-transaction [tx (:spec db) {:isolation :serializable}]
    (doseq [{:keys [id recipient subject body]} (dba/unsent-emails-for-update db tx)]
      (when (send-email-via-service! this {:recipients [recipient] :body body :subject subject})
        (dba/set-email-sent! db tx id)))))

(defrecord EmailService []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  EmailSender
  (send-email-to-participant! [this db template-params]
    (add-email-to-queue! db template-params)
    (send-emails! this db))
  (send-queued-mails! [this db]
    (send-emails! this db)))

(defn email-service [config]
  (map->EmailService config))
