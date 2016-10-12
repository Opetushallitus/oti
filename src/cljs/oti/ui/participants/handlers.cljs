(ns oti.ui.participants.handlers
  (:require [ajax.core :as ajax]
            [oti.routing :as routing]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  :do-participant-search
  [re-frame/trim-v]
  (fn [{:keys [db]} _]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/participant-search")
                  :params          {:q      (get-in db [:participant-search-query :query])
                                    :filter (name (get-in db [:participant-search-query :filter]))}
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :participant-search-results]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-db
  :set-participant-search-query
  (fn
    [db [_ key value]]
    (assoc-in db [:participant-search-query key] value)))
