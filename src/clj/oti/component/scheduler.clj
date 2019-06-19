(ns oti.component.scheduler
  (:require [com.stuartsierra.component :as component]
            [overtone.at-at :as at]
            [clojure.tools.logging :as log]
            [oti.service.payment :as payment]
            [oti.component.email-service :as email]))

(def email-poll-interval-minutes 10)

(def payment-cancel-interval-minutes 30)

(defrecord Scheduler [options]
  component/Lifecycle
  (start [this]
    (let [pool (at/mk-pool :cpu-count 1)]
      (log/info "Running email queue poller with an interval of" email-poll-interval-minutes "minutes.")
      (at/every
        (* email-poll-interval-minutes 60000)
        #(email/send-queued-mails! (:email-service this) (:db this))
        pool
        :desc "Email queue poller"
        :initial-delay 30000)
      (log/info "Running payment canceller with an interval of" payment-cancel-interval-minutes "minutes.")
      (at/every
        (* payment-cancel-interval-minutes 60000)
        #(payment/cancel-obsolete-payments! (:db this))
        pool
        :desc "Payment canceller"
        :initial-delay 15000)
      (assoc this :pool pool)))
  (stop [{:keys [pool] :as this}]
    (when pool
      (at/stop-and-reset-pool! pool))
    (assoc this :pool nil)))

(defn scheduler [options]
  (->Scheduler options))
