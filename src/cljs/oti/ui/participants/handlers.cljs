(ns oti.ui.participants.handlers
  (:require [ajax.core :as ajax]
            [oti.routing :as routing]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  :do-participant-search
  [re-frame/trim-v]
  (fn [_ [query-string filter-option]]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/participants")
                  :params          {:q      query-string
                                    :filter filter-option}
                  :response-format (ajax/transit-response-format)
                  :on-success      [:store-response-to-db :participant-search-results]
                  :on-failure      [:bad-response]}}))
