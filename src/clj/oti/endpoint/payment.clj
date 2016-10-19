(ns oti.endpoint.payment
  (:require [compojure.core :refer :all]
            [oti.boundary.payment :as payment-util]
            [oti.boundary.db-access :as dba]
            [taoensso.timbre :refer [error]]
            [ring.util.response :as resp]))

(defn- registration-response [status text {:keys [participant] :as session} & [lang]]
  (let [body (cond-> {:registration-message text
                      :registration-status status})
        new-participant (-> (dissoc participant :oti.spec/payment-form-data)
                            (merge body))
        url (if (= "sv" lang) "/oti/anmala" "/oti/ilmoittaudu")]
    (-> (resp/redirect url :see-other)
        (assoc :session (assoc session :participant new-participant)))))

(defn- confirm-payment [{:keys [vetuma-payment db]} {:keys [params session]}]
  (let [{order-number :ORDNR pay-id :PAYID lang :LG} params]
    (if (and (payment-util/authentic-response? vetuma-payment params) order-number pay-id)
      (do (dba/confirm-registration-and-payment! db order-number pay-id)
          (registration-response :success "Ilmoittautumisesi on rekisteröity" session lang))
      (do
        (error "Could not verify payment response message:" params)
        (registration-response :error "Maksusanomaa ei voitu vahvistaa" session)))))

(defn payment-endpoint [config]
  (context "/oti/vetuma" []
    (POST "/success" request
      (confirm-payment config request))
    (POST "/error" {session :session}
      (registration-response :error "Ilmoittautuminen on peruttu maksun epäonnistumisen vuoksi" session))
    (POST "/cancel" {session :session}
      (registration-response :error "Ilmoittautuminen on peruttu maksun peruuntumisen vuoksi" session))))
