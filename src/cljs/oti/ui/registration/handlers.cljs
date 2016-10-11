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
