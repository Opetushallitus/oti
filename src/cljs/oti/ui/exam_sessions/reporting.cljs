(ns oti.ui.exam-sessions.reporting
  (:require [reagent.core :as r]))

(def month-options
  {"A" {:start-month 1 :end-month 6}
   "B" {:start-month 7 :end-month 12}
   "C" {:start-month 1 :end-month 12}})

(def current-year (.getFullYear (js/Date.)))

(defn format-month [month]
  (-> (js/moment. month "M") (.format "MMMM")))

(defn- format-month-opt [{:keys [start-month end-month]}]
  (str (format-month start-month) " - " (format-month end-month)))

(defn- month-select [data]
  [:select.month-option
   {:value (:month-option @data) :on-change #(swap! data assoc :month-option (-> % .-target .-value))}
   (doall
     (for [[id opts] month-options]
       [:option {:key id :value id} (format-month-opt opts)]))])

(defn- year-input [data]
  [:input {:type "number" :name "year" :min 2016 :max (inc current-year)
           :value (:year @data) :on-change #(swap! data assoc :year (-> % .-target .-value))}])

(defn- data-valid? [{:keys [month-option year]}]
  (and (seq (get month-options month-option))
       (>= year 2016)
       (<= year (inc current-year))))

(defn reporting []
  (let [data (r/atom {:month-option "A" :year current-year})]
    (fn []
      [:div.reporting
       [month-select data]
       [year-input data]
       [:button {:disabled (not (data-valid? @data))} "Hae suorittaneiden määrä"]])))
