(ns oti.ui.handlers
  (:require [day8.re-frame.http-fx]
            [re-frame.core :as re-frame]
            [oti.ui.db :as db]
            [ajax.core :as ajax]
            [oti.ui.routes :as routes]
            [oti.routing :as routing]))

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/reg-event-fx
  :load-user
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/user-info")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:user-response]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :user-response
  (fn
    [db [_ response]]
    (assoc db :user response)))

(re-frame/reg-event-db
  :bad-response
  (fn
    [db [_ response]]
    (assoc db :error response)))

(re-frame/reg-event-db
  :redirect
  (fn [_ [_ target]]
    (routes/redirect target)))

(re-frame/reg-event-db
  :show-flash
  (fn [db [_ type text]]
    (js/setTimeout #(re-frame/dispatch [:hide-flash]) 3000)
    (assoc db :flash-message {:type type :text text})))

(re-frame/reg-event-db
  :hide-flash
  (fn [db _]
    (assoc db :flash-message {})))
