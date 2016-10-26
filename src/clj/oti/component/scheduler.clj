(ns oti.component.scheduler
  (:require [com.stuartsierra.component :as component]
            [overtone.at-at :as at]
            [taoensso.timbre :as timbre]
            [oti.service.payment :as payment]
            [oti.component.email-service :as email]))

(def payment-poll-interval-minutes 5)

(def email-poll-interval-minutes 10)

(defrecord Scheduler [options]
  component/Lifecycle
  (start [this]
    (let [pool (at/mk-pool :cpu-count 1)]
      (timbre/info "Running payment status poller with an interval of" payment-poll-interval-minutes "minutes.")
      (at/every
        (* payment-poll-interval-minutes 60000)
        #(payment/check-and-process-unpaid-payments! (select-keys this [:db :vetuma-payment]))
        pool
        :desc "Payment status poller"
        :initial-delay 2000)
      (timbre/info "Running email queue poller with an interval of" email-poll-interval-minutes "minutes.")
      (at/every
        (* email-poll-interval-minutes 60000)
        #(email/send-queued-mails! (:email-service this) (:db this))
        pool
        :desc "Email queue poller"
        :initial-delay 30000)
      (assoc this :pool pool)))
  (stop [{:keys [pool] :as this}]
    (when pool
      (at/stop-and-reset-pool! pool))
    (assoc this :pool nil)))

(defn scheduler [options]
  (->Scheduler options))
