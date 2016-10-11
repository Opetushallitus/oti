(ns oti.ui.handlers
  (:require [day8.re-frame.http-fx]
            [re-frame.core :as re-frame :refer [trim-v debug]]
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
 [trim-v]
 (fn [db [active-panel params]]
   (let [id (when (sequential? params) (first params))]
     (assoc db :active-panel active-panel :active-panel-data id))))

(re-frame/reg-event-fx
  :load-user
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/user-info")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :user]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :store-response-to-db
  [trim-v]
  (fn
    [{:keys [db]} [key response]]
    {:db (assoc db key response)
     :loader false}))

(re-frame/reg-event-db
  :bad-response
  [trim-v]
  (fn
    [db [response]]
    (assoc db :error response)))

(re-frame/reg-event-db
  :redirect
  [trim-v]
  (fn [_ [target]]
    (routes/redirect target)))

(re-frame/reg-event-db
  :show-flash
  [trim-v]
  (fn [db [type text]]
    (js/setTimeout #(re-frame/dispatch [:hide-flash]) 3000)
    (assoc db :flash-message {:type type :text text})))

(re-frame/reg-event-db
  :hide-flash
  (fn [db _]
    (assoc db :flash-message {})))

(re-frame/reg-fx
  :loader
  (fn [state]
    (swap! re-frame.db/app-db assoc :loading? state)))
