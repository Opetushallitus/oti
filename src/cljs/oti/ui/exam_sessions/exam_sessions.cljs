(ns oti.ui.exam-sessions.exam-sessions
  (:require [oti.ui.exam-sessions.handlers]
            [oti.ui.exam-sessions.subs]
            [oti.spec :as spec]
            [re-frame.core :as re-frame]
            [reagent.core :as r]
            [cljs-time.format :as ctf]
            [cljs-time.coerce :as ctc]
            [cljs-time.core :as time]
            [clojure.string :as str]
            [cljs.spec :as s]
            [oti.routing :as routing]))

(defn- get-in-lang [e lang]
  (get e lang))
(defn- in-fi [e]
  (get-in-lang e "FI"))
(defn- in-sv [e]
  (get-in-lang e "SV"))

(defn input-element [form-data invalids type key placeholder & [on-change-fn]]
  [:input {:class (when (key invalids) "invalid")
           :type type
           :value (key @form-data)
           :placeholder placeholder
           :required true
           :on-change (or on-change-fn
                          (fn [e]
                            (let [value (cond-> (-> e .-target .-value)
                                                (= "number" type) (js/parseInt))]
                              (swap! form-data assoc key value))))}])

(defn input-element-with-translations [form-data invalids type key lang placeholder & [on-change-fn]]
  [:input {:class       (when (key invalids) "invalid")
           :type        type
           :value       (get-in @form-data [key lang])
           :placeholder placeholder
           :required    true
           :on-change   (or on-change-fn
                            (fn [e]
                              (let [value (cond-> (-> e .-target .-value)
                                                  (= "number" type) (js/parseInt))]
                                (swap! form-data assoc-in [key lang] value))))}])

(defn input-row [form-data invalids {:keys [key type label placeholder translated?]
                                     :or {translated? false}}]
  [:div.row
   [:label
    [:span.label label]
    (if translated?
      [:div.input-group-inline
       (input-element-with-translations form-data invalids type key "FI" (or placeholder label))
       (input-element-with-translations form-data invalids type key "SV" (str (or placeholder label) " ruotsiksi"))]
      (input-element form-data invalids type key (or placeholder label)))]])

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

(defn invalid-keys [form-data]
  (let [keys (->> (s/explain-data ::spec/exam-session @form-data)
                  ::s/problems
                  (map #(first (:path %)))
                  (remove nil?)
                  set)]
    (cond-> keys
            (::spec/session-date keys) (conj keys ::spec/session-date-str))))

(defn new-exam-session-panel []
  (let [form-data (r/atom {::spec/exam-id 1
                           ::spec/published false})]
    (fn []
      (let [invalids (invalid-keys form-data)]
        [:div.exam-session-form
         [:h3 "Uusi tutkintotapahtuma"]
         [:form
          [:div.row
           [:label
            [:span.label "Päivämäärä ja kellonaika"]
            (input-element
              form-data
              invalids
              "text"
              ::spec/session-date-str
              "pp.kk.vvvv"
              (fn [e]
                (let [value (-> e .-target .-value)]
                  (swap! form-data assoc ::spec/session-date-str value ::spec/session-date (parse-date value)))))
            [:div.times
             (input-element form-data invalids "text" ::spec/start-time "hh.mm")
             [:span.dash "\u2014"]
             (input-element form-data invalids "text" ::spec/end-time "hh.mm")]]]
          (input-row form-data invalids {:key ::spec/city
                                         :type "text"
                                         :label "Koepaikan kaupunki"
                                         :placeholder "Kaupunki"
                                         :translated? true})
          (input-row form-data invalids {:key ::spec/street-address
                                         :type "text"
                                         :label "Koepaikan katuosoite"
                                         :placeholder "Katuosoite"
                                         :translated? true})
          (input-row form-data invalids {:key ::spec/other-location-info
                                         :type "text"
                                         :label "Koepaikan tilatieto"
                                         :placeholder "Tilatieto"
                                         :translated? true})
          (input-row form-data invalids {:key ::spec/max-participants
                                         :type "number"
                                         :label "Osallistujien enimmäismäärä"
                                         :placeholder "Määrä"})
          [:div.row
           [:label
            [:span.label "Julkaistu"]
            [:input {:type "checkbox"
                     :value (::spec/published @form-data)
                     :required true
                     :on-click (fn [e]
                                 (let [value (cond-> (-> e .-target .-checked))]
                                   (swap! form-data assoc ::spec/published value)))}]]]
          [:div.buttons
           [:button.button-primary
            {:disabled (not (s/valid? ::spec/exam-session @form-data))
             :on-click (fn [e]
                         (.preventDefault e)
                         (re-frame/dispatch [:add-exam-session @form-data]))}
            "Tallenna"]
           [:a.button
            {:href routing/virkailija-root}
            "Peruuta"]]]]))))

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
             [:td "Osoite"]
             [:td "Tilatieto"]
             [:td "Enimmäismäärä"]
             [:td "Ilmoittautuneet"]]]
           [:tbody
            (doall
              (for [{::spec/keys [id city start-time end-time session-date street-address other-location-info max-participants]} @exam-sessions]
                ^{:key id}
                [:tr
                 [:td (str (unparse-date session-date) " " start-time " - " end-time)]
                 [:td
                  (str (in-fi city) ", " (in-fi street-address))
                  [:br]
                  (str (in-sv city) ", " (in-sv street-address))]
                 [:td
                  (in-fi other-location-info)
                  [:br]
                  (in-sv other-location-info)]
                 [:td max-participants]
                 [:td "0"]]))]])]
       [:div.buttons
        [:div.right
         [:a.button {:href (routing/v-route "/tutkintotapahtuma")} "Lisää uusi"]]]])))
