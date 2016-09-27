(ns oti.ui.views.exam-sessions
  (:require [oti.spec]
            [re-frame.core :as re-frame]
            [reagent.core :as r]
            [cljs-time.format :as ctf]
            [cljs-time.coerce :as ctc]
            [cljs-time.core :as time]
            [cljs-time.local :as local]
            [clojure.string :as str]
            [cljs.spec :as s]))

(defn input-element [form-data type key & [on-change-fn]]
  [:input {:class type
           :type type
           :value (key @form-data)
           :required true
           :on-change (or on-change-fn
                          (fn [e]
                            (let [value (cond-> (-> e .-target .-value)
                                                (= "number" type) (js/parseInt))]
                              (swap! form-data assoc key value))))}])

(defn input-row [form-data key type label]
  [:div.row
   [:label
    [:span.label label]
    (input-element form-data type key)]])

(def date-format (ctf/formatter "d.M.yyyy"))

(defn parse-date [date-str]
  (when-not (str/blank? date-str)
    (try
      (-> (ctf/parse date-format date-str)
          (ctc/to-date))
      (catch js/Error _))))

(defn unparse-date [date]
  (->> (time/to-default-time-zone date)
       (ctf/unparse date-format)))

(defn new-exam-session-panel []
  (let [form-data (r/atom {::oti.spec/exam-id 1})]
    (fn []
      [:div.exam-session-form
       [:h3 "Uusi tutkintotapahtuma"]
       [:form
        [:div.row
         [:span.label "Päivämäärä ja kellonaika"]
         (input-element
           form-data
           "text"
           :oti.spec/session-date-str
           (fn [e]
             (let [value (-> e .-target .-value)]
               (swap! form-data assoc :oti.spec/session-date-str value :oti.spec/session-date (parse-date value)))))
         (input-element form-data "time" :oti.spec/start-time)
         (input-element form-data "time" :oti.spec/end-time)]
        (input-row form-data :oti.spec/city "text" "Koepaikan kaupunki")
        (input-row form-data :oti.spec/street-address "text" "Koepaikan katuosoite")
        (input-row form-data :oti.spec/other-location-info "text" "Koepaikan tilatieto")
        (input-row form-data :oti.spec/max-participants "number" "Osallistujien enimmäismäärä")
        [:div.buttons
         [:div.right
          [:button.button-primary
           {:disabled (not (s/valid? :oti.spec/exam-session @form-data))
            :on-click (fn [e]
                        (.preventDefault e)
                        (re-frame/dispatch [:add-exam-session @form-data]))}
           "Tallenna"]]]]])))

(defn exam-sessions-panel []
  (re-frame/dispatch [:load-exam-sessions])
  (let [exam-sessions (re-frame/subscribe [:exam-sessions])]
    (fn []
      [:div
       [:h2 "Tutkintotapahtumat"]
       [:div.exam-sessions
        (when (seq @exam-sessions)
          [:table
           [:thead
            [:tr
             [:td "Päivämäärä ja aika"]
             [:td "Katuosoite"]
             [:td "Tilatieto"]
             [:td "Enimmäismäärä"]]]
           [:tbody
            (doall
              (for [{:oti.spec/keys [id city start-time end-time session-date street-address other-location-info max-participants]} @exam-sessions]
                ^{:key id}
                [:tr
                 [:td (str (unparse-date session-date) " " start-time " - " end-time)]
                 [:td (str city ", " street-address)]
                 [:td other-location-info]
                 [:td max-participants]]))]])]
       [:div.buttons
        [:div.right
         [:a.button {:href "/oti/virkailija/tutkintotapahtuma"} "Lisää uusi"]]]])))

