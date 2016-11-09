(ns oti.ui.exam-registrations.handlers
  (:require [re-frame.core :as re-frame]
            [ajax.core :as ajax]
            [oti.routing :as routing]))

(re-frame/reg-event-fx
  :load-registrations
  [re-frame/trim-v]
  (fn [_ [exam-session-id]]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/exam-sessions/" exam-session-id "/registrations")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-registrations exam-session-id]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :store-registrations
  [re-frame/trim-v]
  (fn
    [db [exam-session-id response]]
    (assoc-in db [:registrations exam-session-id] response)))

(re-frame/reg-event-fx
  :generate-registrations-access-token
  [re-frame/trim-v]
  (fn [_ [exam-session-id]]
    {:http-xhrio {:method          :post
                  :uri             (routing/v-a-route "/exam-sessions/" exam-session-id "/token")
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-access-token exam-session-id]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :store-access-token
  [re-frame/trim-v]
  (fn
    [db [exam-session-id response]]
    (assoc-in db [:registrations exam-session-id :access-token] (:access-token response))))
