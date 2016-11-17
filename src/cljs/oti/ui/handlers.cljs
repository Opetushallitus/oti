(ns oti.ui.handlers
  (:require [day8.re-frame.http-fx]
            [re-frame.core :as re-frame :refer [trim-v debug]]
            [oti.ui.db :as db]
            [ajax.core :as ajax]
            [oti.ui.routes :as routes]
            [oti.routing :as routing]
            [oti.utils :as utils]))

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
  :set-active-panel
  [trim-v]
  (fn [db [active-panel params]]
   (let [id (when (sequential? params) (first params))]
     (assoc db :active-panel active-panel :active-panel-data (utils/parse-int id)))))

(re-frame/reg-event-fx
  :store-response-to-db
  [trim-v]
  (fn
    [{:keys [db]} [key response]]
    {:db (if (= :root key)
           (merge db response)
           (assoc db key response))
     :loader false}))

(defn redirect-to-auth []
  (let [location (-> js/window .-location)
        host (.-host location)
        protocol (.-protocol location)]
    (->> (str protocol "//" host (routing/auth-route "/cas?path=") (js/encodeURIComponent (.-href location)))
         (set! (.-href location)))))

(re-frame/reg-event-fx
  :bad-response
  [trim-v]
  (fn
    [{:keys [db]} [response]]
    (if (= 401 (:status response))
      (redirect-to-auth)
      {:db (assoc db :error response)
       :show-flash [:error "Tietojen lataus palvelimelta epäonnistui"]
       :loader false})))

(re-frame/reg-fx
  :redirect
  (fn [target]
    (routes/redirect target)))

(re-frame/reg-fx
  :show-flash
  (fn [[type text]]
    (swap! re-frame.db/app-db assoc :flash-message {:type type :text text})
    (js/setTimeout #(swap! re-frame.db/app-db assoc :flash-message {}) 3000)))

(re-frame/reg-fx
  :loader
  (fn [state]
    (swap! re-frame.db/app-db assoc :loading? state)))

(re-frame/reg-event-fx
  :load-frontend-config
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/frontend-config")
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :root]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :launch-confirmation-dialog
  [trim-v]
  (fn [db [question button-text & rf-event]]
    (assoc db :confirmation-dialog {:question question
                                    :button-text button-text
                                    :event (vec rf-event)})))

(re-frame/reg-event-db
  :confirmation-cancel
  [trim-v]
  (fn [db _]
    (assoc db :confirmation-dialog nil)))
