(ns oti.endpoint.payment
  (:require [compojure.core :refer :all]
            [oti.boundary.payment :as payment-util]
            [oti.boundary.db-access :as dba]
            [taoensso.timbre :refer [error]]
            [ring.util.response :as resp]
            [oti.component.localisation :refer [t]]))

(defn- registration-response [status text {:keys [participant] :as session} & [lang]]
  (let [body (cond-> {:registration-message text
                      :registration-status status})
        new-participant (-> (dissoc participant :oti.spec/payment-form-data)
                            (merge body))
        url (if (= "sv" lang) "/oti/anmala" "/oti/ilmoittaudu")]
    (-> (resp/redirect url :see-other)
        (assoc :session (assoc session :participant new-participant)))))

(defn- confirm-payment [{:keys [vetuma-payment db localisation]} {:keys [params session]}]
  (let [{order-number :ORDNR pay-id :PAYID lang :LG} params]
    (if (and (payment-util/authentic-response? vetuma-payment params) order-number pay-id)
      (do (dba/confirm-registration-and-payment! db order-number pay-id)
          (registration-response :success (t localisation lang "registration-complete") session lang))
      (do
        (error "Could not verify payment response message:" params)
        (registration-response :error (t localisation (or lang :fi) "registration-payment-error") session)))))

(defn payment-endpoint [{:keys [localisation] :as config}]
  (context "/oti/vetuma" []
    (POST "/success" request
      (confirm-payment config request))
    (POST "/error" {session :session}
      (registration-response :error (t localisation :fi "registration-payment-error") session))
    (POST "/cancel" {session :session}
      (registration-response :error (t localisation :fi "registration-payment-cancel") session))))
