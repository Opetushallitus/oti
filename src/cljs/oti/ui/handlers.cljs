(ns oti.ui.handlers
  (:require [day8.re-frame.http-fx]
            [re-frame.core :as re-frame]
            [oti.ui.db :as db]
            [ajax.core :as ajax]))

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
                  :uri             "/oti/api/virkailija/user-info"
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:process-response]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :process-response
  (fn
    [db [_ response]]
    (assoc db :user (js->clj response))))

(re-frame/reg-event-db
  :bad-response
  (fn
    [db [_ response]]
    (assoc db :user {})))

(re-frame/reg-event-fx
  :add-exam-session
  (fn [_ [_ data]]
    {:http-xhrio {:method          :post
                  :uri             "/oti/api/virkailija/exam-sessions"
                  :body            (ajax/write-json data)
                  :format          :json
                  :headers         {"Content-Type" "application/json; charset=utf8"}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:process-response]
                  :on-failure      [:bad-response]}}))
