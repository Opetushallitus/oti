(ns oti.component.email-service
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(defprotocol EmailSender
  (send-email [this email-params]))

(defn- send-email-via-service [{:keys [email-service-url]} {:keys [from recipients subject body]}]
  {:pre [(every? #(identity %) [from recipients subject body])]}
  (log/info "Trying to send email" subject "to" recipients)
  (let [wrapped-recipients (mapv (fn [rcp] {:email rcp}) recipients)
        {:keys [status]} @(http/post email-service-url {:headers {"content-type" "application/json"}
                                                        :body    (json/generate-string {:email     {:from    from
                                                                                                    :subject subject
                                                                                                    :isHtml  false
                                                                                                    :body    body}
                                                                                        :recipient wrapped-recipients})})]
    (if (= 200 status)
      (log/info "Successfully sent email to" recipients)
      (log/error "Could not send email to" recipients ", HTTP status from email service was" status))))

(defrecord EmailService []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  EmailSender
  (send-email [this email-params]
    (send-email-via-service this email-params)))

(defn email-service [config]
  (map->EmailService config))
