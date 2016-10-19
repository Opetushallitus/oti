(ns oti.ui.registration.handlers
  (:require [re-frame.core :as re-frame :refer [trim-v debug]]
            [ajax.core :as ajax]
            [oti.routing :as routing]))

(re-frame/reg-event-fx
  :set-language
  (fn [{:keys [db]} [_ lang]]
    {:db         (assoc db :language lang)
     :http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/translations")
                  :params          {:lang (name lang)}
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :translations]
                  :on-failure      [:bad-response]}
     :loader true}))

(re-frame/reg-event-fx
  :load-participant-data
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/authenticated/participant-data")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :participant-data]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :load-available-sessions
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/exam-sessions")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :exam-sessions]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :load-registration-options
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/authenticated/registration-options")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :registration-options]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :store-registration
  (fn [_ [_ data]]
    {:http-xhrio {:method          :post
                  :uri             (routing/p-a-route "/authenticated/register")
                  :params          data
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:registration-saved]
                  :on-failure      [:registration-error]}}))

(re-frame/reg-event-db
  :registration-saved
  [trim-v debug]
  (fn [db [response]]
    (if-let [payment-form-data (:oti.spec/payment-form-data response)]
      (update db :participant-data assoc :oti.spec/payment-form-data payment-form-data)
      (update db :participant-data merge {:registration-status :success
                                          :registration-message (:registration-message response)}))))

(re-frame/reg-event-db
  :registration-error
  [trim-v debug]
  (fn
    [db [{:keys [response]}]]
    (update db :participant-data merge {:registration-status :error
                                        :registration-message (:registration-message response)})))
