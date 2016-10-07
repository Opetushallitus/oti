(ns oti.ui.exam-sessions.handlers
  (:require [re-frame.core :as re-frame]
            [ajax.core :as ajax]
            [oti.routing :as routing]))

(re-frame/reg-event-db
  :exam-session-saved
  (fn [_ _]
    (re-frame/dispatch [:redirect routing/virkailija-root])
    (re-frame/dispatch [:show-flash :success "Tallennettu"])))

(re-frame/reg-event-fx
  :add-exam-session
  (fn [_ [_ data]]
    {:http-xhrio {:method          :post
                  :uri             (routing/v-a-route "/exam-sessions")
                  :params          data
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-session-saved]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :save-exam-session
  (fn [_ [_ id data]]
    {:http-xhrio {:method          :put
                  :uri             (routing/v-a-route "/exam-sessions/" id)
                  :params          data
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-session-saved]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :load-exam-sessions
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/exam-sessions")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-sessions-response]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :exam-sessions-response
  (fn
    [db [_ response]]
    (assoc db :exam-sessions response)))

(re-frame/reg-event-fx
  :load-exam-session-editor
  (fn [{:keys [db]}  [_ params]]
    {:db         (assoc db :active-panel-data nil)
     :http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/exam-sessions/" (first params))
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-session-loaded]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :exam-session-loaded
  (fn
    [db [_ response]]
    (assoc db :active-panel :edit-exam-session-panel :active-panel-data response)))

(re-frame/reg-event-fx
  :delete-exam-session
  (fn [_ [_ id]]
    {:http-xhrio {:method          :delete
                  :uri             (routing/v-a-route "/exam-sessions/" id)
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-session-deleted]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :exam-session-deleted
  (fn [_ _]
    (re-frame/dispatch [:redirect routing/virkailija-root])
    (re-frame/dispatch [:show-flash :success "Poistettu"])))
