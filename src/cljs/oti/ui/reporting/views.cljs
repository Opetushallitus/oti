(ns oti.ui.reporting.views
  (:require [oti.ui.views.common :refer [default-pikaday-opts]]
            [cljs-pikaday.reagent :as pikaday]
            [reagent.core :as r]))

(defn- paid-payments-report []
  (let [default-start (-> (js/moment.) (.subtract 1 "month") (.startOf "day") .toDate)
        default-end   (-> (js/moment.) (.startOf "day") (.toDate))
        start-date    (r/atom default-start)
        end-date      (r/atom default-end)
        query         (r/atom "")]
    (fn []
      [:div.paid-payments-report
        [:h2 "Suoritetut maksut"]
        [:div.filters
          [:span.label "Päivämäärä"]
          [:span.pikaday-input
            [pikaday/date-selector (assoc default-pikaday-opts :date-atom start-date)]]
          [:span.dash "\u2014"]
          [:span.pikaday-input
            [pikaday/date-selector (assoc default-pikaday-opts :date-atom end-date)]]
          [:span.label "Nimi"]
          [:input
            {:type "text"
             :name "query"
             :value @query
             :on-change #(reset! query (-> % .-target .-value))}]]
        [:div.buttons
          (let [start-date-epoch (when (inst? @start-date) (.getTime @start-date))
                end-date-epoch (when (inst? @end-date) (.setHours @end-date 23 59 59 999))]
            [:a {:href (str "/oti/api/virkailija/payments?start-date=" start-date-epoch "&end-date=" end-date-epoch "&query=" @query)} "Lataa raportti"])]])))

(defn reporting-panel []
  (fn []
    paid-payments-report))
