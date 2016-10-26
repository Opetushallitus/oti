(ns oti.component.email-service
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [oti.boundary.db-access :as dba]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.spec :as s]))

(defprotocol EmailSender
  (send-email-by-payment-order-number! [this email-params])
  (send-queued-mails! [this]))

(defn- send-email-via-service! [{:keys [email-service-url]} {:keys [recipients subject body]}]
  {:pre [(every? #(identity %) [recipients subject body]) (s/valid? :oti.spec/email (first recipients))]}
  (log/info "Trying to send email" subject "to" recipients)
  (let [wrapped-recipients (mapv (fn [rcp] {:email rcp}) recipients)
        {:keys [status]} @(http/post email-service-url {:headers {"content-type" "application/json"}
                                                        :body    (json/generate-string {:email     {:subject subject
                                                                                                    :html  false
                                                                                                    :body    body}
                                                                                        :recipient wrapped-recipients})})]
    (if (= 200 status)
      (do
        (log/info "Successfully sent email to" recipients)
        true)
      (log/error "Could not send email to" recipients ", HTTP status from email service was" status))))

(defn- add-email-to-queue! [{:keys [db]} {:keys [order-number subject body] :as params}]
  {:pre [(every? #(identity %) [order-number subject body])]}
  (dba/add-email-by-payment-order-number! db params))

(defn- send-emails! [{:keys [db] :as this}]
  (jdbc/with-db-transaction [tx (:spec db) {:isolation :serializable}]
    (doseq [{:keys [id recipient subject body]} (dba/unsent-emails-for-update db tx)]
      (when (send-email-via-service! this {:recipients [recipient] :body body :subject subject})
        (dba/set-email-sent! db tx id)))))

(defrecord EmailService []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  EmailSender
  (send-email-by-payment-order-number! [this email-params]
    (add-email-to-queue! this email-params)
    (send-emails! this))
  (send-queued-mails! [this]
    (send-emails! this)))

(defn email-service [config]
  (map->EmailService config))
