(ns oti.ui.reporting.handlers
  (:require [ajax.core :as ajax]
            [oti.http :refer [http-default-headers]]
            [oti.routing :as routing]
            [re-frame.core :as re-frame]
            [oti.spec :as os]))


(re-frame/reg-event-fx
  :load-reports
  [re-frame/trim-v]
  (fn [_ [start-date end-date query]]
    {:http-xhrio {:method          :get
                  :uri             (routing/v-a-route "/payments")
                  :params          {:start-date start-date
                                    :end-date end-date
                                    :query query}
                  :headers         (http-default-headers)
                  :response-format (ajax/raw-response-format)
                  :on-success      [(fn [response]
                         (let [file (js/Blob. (clj->js [response])
                                                   (clj->js {:type "text/csv"}))]
                           (.open js/window (.createObjectURL js/URL file) "_blank")))]
                  :on-failure      [:bad-response]}
     :loader     true}))