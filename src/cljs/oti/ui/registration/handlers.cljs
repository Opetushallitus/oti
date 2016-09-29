(ns oti.ui.registration.handlers
  (:require [re-frame.core :as re-frame]
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
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :load-participant-data
  (fn [{:keys [db]} [_ lang]]
    {:db         (assoc db :language lang)
     :http-xhrio {:method          :get
                  :uri             (routing/p-a-route "/participant-data")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :partipant-data]
                  :on-failure      [:bad-response]}}))
