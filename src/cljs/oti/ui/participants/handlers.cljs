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

(re-frame/reg-event-fx
  :store-participant-details
  [re-frame/trim-v]
  (fn [{:keys [db]} [participant-id response]]
    {:db (update db :participant-details assoc participant-id response)
     :loader false}))

(defn participant-load-xhrio [id]
  {:method          :get
   :uri             (routing/v-a-route "/participant/" id)
   :response-format (ajax/transit-response-format)
   :on-success      [:store-participant-details id]
   :on-failure      [:bad-response]})

(re-frame/reg-event-fx
  :load-participant-details
  [re-frame/trim-v]
  (fn [_ [id]]
    {:http-xhrio (participant-load-xhrio id)}))

(re-frame/reg-event-db
  :set-participant-search-query
  (fn
    [db [_ key value]]
    (assoc-in db [:participant-search-query key] value)))

(re-frame/reg-event-db
  :reset-search
  (fn
    [db _]
    (assoc db  :participant-search-query {:query "" :filter :all}
               :participant-search-results nil)))

(re-frame/reg-event-fx
  :save-accreditation-data
  [re-frame/trim-v]
  (fn [_ [participant-id data]]
    {:http-xhrio {:method          :post
                  :uri             (routing/v-a-route "/participant/" participant-id "/accreditations")
                  :params          data
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:accreditations-saved participant-id]
                  :on-failure      [:bad-response]}}))

(re-frame/reg-event-fx
  :accreditations-saved
  [re-frame/trim-v]
  (fn [_ [participant-id]]
    {:show-flash [:success "Tallennettu"]
     :http-xhrio (participant-load-xhrio participant-id)}))
