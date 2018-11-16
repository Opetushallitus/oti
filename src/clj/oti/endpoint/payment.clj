(ns oti.endpoint.payment
  (:require [compojure.core :refer :all]
            [clojure.tools.logging :refer [error]]
            [ring.util.response :as resp]
            [oti.service.payment :as payment-service]
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
  (let [{order-number :ORDER_NUMBER} params
        lang (payment-service/get-participant-language-by-order-number config order-number) ]
    (if (payment-service/confirm-payment! config params lang)
      (registration-response :success "registration-complete" session lang)
      (registration-response :error "registration-payment-error" session))))

(defn- cancel-payment [config {:keys [params session]}]
  (let [{order-number :ORDER_NUMBER} params
        lang (payment-service/get-participant-language-by-order-number config order-number)]
    (if (payment-service/cancel-payment! config params)
      (registration-response :error "registration-payment-cancel" session lang)
      (registration-response :error "registration-payment-cancel" session))))

(defn payment-endpoint [config]
  (context "/oti/paytrail" []
    (GET "/success" request
      (confirm-payment config request))
    (GET "/cancel" request
      (cancel-payment config request))))
