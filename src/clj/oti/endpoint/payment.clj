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
  (let [{payment-id :PAYMENT_ID} params 
        lang "fi"]  ;; TODO: lang was prevously included in VETUMA params, now we have to determine it some other way
    (if (payment-service/confirm-payment! config params)
      (registration-response :success "registration-complete" session lang)
      (registration-response :error "registration-payment-error" session))))

(defn- cancel-payment [config {:keys [params session]}]
  (let [lang "fi"] ;; TODO: lang was previously included in VETUMA params, now we have to determine it some other way
    (if (payment-service/cancel-payment! config params)
      (registration-response :error "registration-payment-cancel" session lang)
      (registration-response :error "registration-payment-cancel" session))))

(defn payment-endpoint [config]
  (context "/oti/paytrail" []
    (GET "/success" request
      (confirm-payment config request))
    (GET "/cancel" request
      (cancel-payment config request))))
