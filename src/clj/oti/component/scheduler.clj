(ns oti.component.scheduler
  (:require [com.stuartsierra.component :as component]
            [overtone.at-at :as at]
            [taoensso.timbre :as timbre]
            [oti.service.payment :as payment]))

(def payment-poll-interval-minutes 5)

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
      (assoc this :pool pool)))
  (stop [{:keys [pool] :as this}]
    (when pool
      (at/stop-and-reset-pool! pool))
    (assoc this :pool nil)))

(defn scheduler [options]
  (->Scheduler options))
