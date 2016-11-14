(ns oti.ui.exam-sessions.reporting
  (:require [reagent.core :as r]
            [re-frame.core :as re-frame]))

(def month-options
  {"A" {:start-month 1 :end-month 6}
   "B" {:start-month 7 :end-month 12}
   "C" {:start-month 1 :end-month 12}})

(def current-year (.getFullYear (js/Date.)))

(defn format-month [month]
  (-> (js/moment. month "M") (.format "MMMM")))

(defn- format-month-opt [{:keys [start-month end-month]}]
  (str (format-month start-month) " - " (format-month end-month)))

(defn- reset-diploma-count []
  ; Reset diploma-count on form changes so that we don't show confusing results
  (re-frame/dispatch [:store-response-to-db :diploma-count nil]))

(defn- month-select [data]
  [:select.month-option
   {:value (:month-option @data)
    :on-change (fn [e]
                 (swap! data assoc :month-option (-> e .-target .-value))
                 (reset-diploma-count))}
   (doall
     (for [[id opts] month-options]
       [:option {:key id :value id} (format-month-opt opts)]))])

(defn- year-input [data]
  [:input {:type "number" :name "year" :min 2016 :max (inc current-year)
           :value (:year @data)
           :on-change (fn [e]
                        (swap! data assoc :year (-> e .-target .-value))
                        (reset-diploma-count))}])

(defn- data-valid? [{:keys [month-option year]}]
  (and (seq (get month-options month-option))
       (>= year 2016)
       (<= year (inc current-year))))

(defn- construct-dates [{:keys [year month-option]}]
  (let [{:keys [start-month end-month]} (get month-options month-option)]
    {:start-date (-> (js/Date. year start-month 1) (.getTime))
     :end-date   (-> (js/moment. (clj->js [year end-month])) (.endOf "month") (.valueOf))}))

(defn reporting []
  (reset-diploma-count)
  (let [data (r/atom {:month-option "A" :year current-year})
        diploma-count (re-frame/subscribe [:diploma-count])]
    (fn []
      [:div.reporting
       [month-select data]
       [year-input data]
       [:button {:disabled (not (data-valid? @data))
                 :on-click #(re-frame/dispatch [:load-diploma-count (construct-dates @data)])}
        "Hae suorittaneiden määrä"]
       (when @diploma-count
         [:span.result @diploma-count " suorittanutta"])])))
