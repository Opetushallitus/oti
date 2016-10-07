(ns oti.ui.exam-registrations.handlers
  (:require [re-frame.core :as re-frame]
            [ajax.core :as ajax]
            [oti.routing :as routing]))

(re-frame/reg-event-db
  :exam-session-saved
  (fn [_ _]
    (re-frame/dispatch [:redirect routing/virkailija-root])
    (re-frame/dispatch [:show-flash :success "Tallennettu"])))

(re-frame/reg-event-fx
  :load-registrations
  (fn [_ [_ exam-session-id]]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/exam-sessions")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-sessions-response]
                  :on-failure      [:bad-response]}}))
