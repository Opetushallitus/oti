(ns oti.endpoint.payment
  (:require [compojure.core :refer :all]
            [oti.boundary.payment :as payment-api]
            [oti.boundary.db-access :as dba]
            [taoensso.timbre :refer [error]]
            [ring.util.response :as resp]
            [oti.component.localisation :refer [t]]
            [oti.service.payment :as payment-service]))

(defn- registration-response [status text {:keys [participant] :as session} & [lang]]
  (let [body (cond-> {:registration-message text
                      :registration-status status})
        new-participant (-> (dissoc participant :oti.spec/payment-form-data)
                            (merge body))
        url (if (= "sv" lang) "/oti/anmala" "/oti/ilmoittaudu")]
    (-> (resp/redirect url :see-other)
        (assoc :session (assoc session :participant new-participant)))))

(defn- confirm-payment [{:keys [localisation] :as config} {:keys [params session]}]
  (let [{lang :LG} params]
    (if (payment-service/confirm-payment! config params)
      (registration-response :success (t localisation lang "registration-complete") session lang)
      (registration-response :error (t localisation (or lang :fi) "registration-payment-error") session))))

(defn- cancel-payment [{:keys [localisation] :as config} {:keys [params session]} cancellation?]
  (let [{lang :LG} params
        t-key (if cancellation? "registration-payment-cancel" "registration-payment-error")]
    (if (payment-service/cancel-payment! config params)
      (registration-response :error (t localisation lang t-key) session lang)
      (registration-response :error (t localisation (or lang :fi) "registration-payment-error") session))))

(defn payment-endpoint [{:keys [localisation] :as config}]
  (context "/oti/vetuma" []
    (POST "/success" request
      (confirm-payment config request))
    (POST "/error" request
      (cancel-payment config request false))
    (POST "/cancel" request
      (cancel-payment config request :cancellation?))))
