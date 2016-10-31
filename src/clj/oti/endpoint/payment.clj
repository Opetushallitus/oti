(ns oti.endpoint.payment
  (:require [compojure.core :refer :all]
            [taoensso.timbre :refer [error]]
            [ring.util.response :as resp]
            [oti.service.payment :as payment-service]
            [oti.util.logging.audit :as audit]
            [clojure.string :as str]))

(defn- registration-response [status text {:keys [participant] :as session} & [lang]]
  (let [body (cond-> {:registration-message text
                      :registration-status status})
        new-participant (-> (dissoc participant :oti.spec/payment-form-data)
                            (merge body))
        url (if (= "sv" lang) "/oti/anmala" "/oti/ilmoittaudu")]
    (-> (resp/redirect url :see-other)
        (assoc :session (assoc session :participant new-participant)))))

(defn- confirm-payment [config {:keys [params session]}]
  (let [{lang :LG payment-id :PAYID} params]
    (if (payment-service/confirm-payment! config params)
      ;; FIX: Not entirely sure how this should be audited
      (do (audit/log :app :participant
                     :who "SYSTEM"
                     :on :payment
                     :op :update
                     :before {:id (when-not (str/blank? payment-id)
                                    payment-id)
                              :state "UNPAID"}
                     :after {:id (when-not (str/blank? payment-id)
                                   payment-id)
                             :state "OK"}
                     :msg "Payment state is confirmed.")
          (registration-response :success "registration-complete" session lang))
      (registration-response :error "registration-payment-error" session))))

(defn- cancel-payment [config {:keys [params session]} cancellation?]
  (let [{lang :LG} params
        t-key (if cancellation? "registration-payment-cancel" "registration-payment-error")]
    (if (payment-service/cancel-payment! config params)
      (registration-response :error t-key session lang)
      (registration-response :error t-key session))))

(defn payment-endpoint [config]
  (context "/oti/vetuma" []
    (POST "/success" request
      (confirm-payment config request))
    (POST "/error" request
      (cancel-payment config request false))
    (POST "/cancel" request
      (cancel-payment config request :cancellation?))))
