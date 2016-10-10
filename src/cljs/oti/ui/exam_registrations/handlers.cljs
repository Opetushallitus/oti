(ns oti.ui.exam-registrations.handlers
  (:require [re-frame.core :as re-frame]
            [ajax.core :as ajax]
            [oti.routing :as routing]))

(re-frame/reg-event-fx
  :load-registrations
  (fn [_ [_ exam-session-id]]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/exam-sessions/" exam-session-id "/registrations")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-registrations exam-session-id]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :store-registrations
  (fn
    [db [_ exam-session-id response]]
    (assoc-in db [:registrations exam-session-id] response)))
