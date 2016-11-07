(ns oti.ui.scoring.handlers
  (:require [re-frame.core :as rf :refer [trim-v]]
            [oti.routing :as routing]
            [ajax.core :as ajax]))

(rf/reg-event-fx
 :load-exam-sessions-full
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             (routing/v-a-route "/exam-sessions/full")
                 :response-format (ajax/transit-response-format)
                 :on-success      [:store-response-to-db [:scoring :exam-sessions]]
                 :on-failure      [:bad-response]}
    :loader     true}))

(rf/reg-event-db
 :select-exam-session
 [trim-v]
 (fn [db [id]]
   (assoc-in db [:scoring :selected-exam-session] id)))
