(ns oti.ui.exam-sessions.handlers
  (:require [re-frame.core :as re-frame]
            [ajax.core :as ajax]))

(re-frame/reg-event-db
  :exam-session-added
  (fn [_ _]
    (re-frame/dispatch [:redirect "/oti/virkailija"])
    (re-frame/dispatch [:show-flash :success "Tallennettu"])))

(re-frame/reg-event-fx
  :add-exam-session
  (fn [_ [_ data]]
    {:http-xhrio {:method          :post
                  :uri             "/oti/api/virkailija/exam-sessions"
                  :params          data
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-session-added]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :load-exam-sessions
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             "/oti/api/virkailija/exam-sessions"
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-sessions-response]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :exam-sessions-response
  (fn
    [db [_ response]]
    (assoc db :exam-sessions response)))
