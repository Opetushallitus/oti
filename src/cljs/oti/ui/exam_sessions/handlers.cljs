(ns oti.ui.exam-sessions.handlers
  (:require [re-frame.core :as re-frame]
            [ajax.core :as ajax]
            [oti.http :refer [http-default-headers]]
            [oti.routing :as routing]))

(re-frame/reg-event-fx
  :exam-session-saved
  (fn [_ _]
    {:redirect routing/virkailija-root
     :show-flash [:success "Tallennettu"]}))

(re-frame/reg-event-fx
  :exam-session-deleted
  (fn [_ _]
    {:redirect routing/virkailija-root
     :show-flash [:success "Poistettu"]}))

(re-frame/reg-event-fx
  :add-exam-session
  (fn [_ [_ data]]
    {:http-xhrio {:method          :post
                  :uri             (routing/v-a-route "/exam-sessions")
                  :params          data
                  :headers         (http-default-headers)
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
                  :headers         (http-default-headers)
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-session-saved]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :load-exam-sessions
  [re-frame/trim-v]
  (fn [_ [start-date end-date]]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/exam-sessions")
                  :params          {:start-date (when (inst? start-date) (.getTime start-date))
                                    :end-date (when (inst? end-date) (.getTime end-date))}
                  :headers         (http-default-headers)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :exam-sessions]
                  :on-failure      [:bad-response]}
     :loader     true}))

(re-frame/reg-event-fx
  :load-exam-session-editor
  (fn [{:keys [db]}  [_ params]]
    {:db         (assoc db :active-panel-data nil)
     :http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/exam-sessions/" (first params))
                  :headers         (http-default-headers)
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
                  :headers         (http-default-headers)
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:exam-session-deleted]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :load-diploma-count
  [re-frame/trim-v]
  (fn [_ [dates]]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/diplomas/count")
                  :params          dates
                  :headers         (http-default-headers)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :diploma-count]
                  :on-failure      [:bad-response]}}))
