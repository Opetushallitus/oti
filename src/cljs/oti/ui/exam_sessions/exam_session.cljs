(ns oti.ui.exam-sessions.exam-session
  (:require [oti.ui.exam-sessions.handlers]
            [oti.ui.exam-sessions.subs]
            [oti.ui.exam-sessions.utils :refer [parse-date unparse-date invalid-keys]]
            [oti.utils :refer [parse-int]]
            [oti.spec :as spec]
            [re-frame.core :as re-frame]
            [reagent.core :as r]
            [cljs.spec :as s]
            [oti.routing :as routing]
            [cljsjs.moment]
            [cljsjs.moment.locale.fi]
            [cljs-pikaday.reagent :as pikaday]
            [oti.ui.i18n :as i18n]))

(defn input-element [form-data invalids type key lang placeholder & [on-change-fn]]
  (let [value-path (if lang [key lang] [key])]
    [:input {:class       (when (key invalids) "invalid")
             :name        (name key)
             :lang        (if lang (name lang) "fi")
             :type        type
             :value       (get-in @form-data value-path)
             :placeholder placeholder
             :required    true
             :on-change   (or on-change-fn
                              (fn [e]
                                (let [value (cond-> (-> e .-target .-value)
                                                    (= "number" type) (parse-int))]
                                  (swap! form-data assoc-in value-path value))))}]))

(defn input-row [form-data invalids {:keys [key type label placeholder translated?]
                                     :or {translated? false}}]
  [:div.row
   [:label
    [:span.label label]
    (if translated?
      [:div.input-group-inline
       (input-element form-data invalids type key :fi (or placeholder label))
       (input-element form-data invalids type key :sv (str (or placeholder label) " ruotsiksi"))]
      (input-element form-data invalids type key nil (or placeholder label)))]])

(def tomorrow (-> (js/moment.) (.add 1 "days") .toDate))

(def base-form-data
  {::spec/exam-id 1
   ::spec/published false
   ::spec/session-date tomorrow})

(defn registration-url [lang id]
  (let [location (-> js/window .-location)
        host (.-host location)
        protocol (.-protocol location)]
    (str protocol "//" host
         (if (= :sv lang)
           (routing/p-sv-route "/" id)
           (routing/p-route "/" id)))))

(defn exam-session-panel [existing-data]
  (let [pikaday-date (r/atom (or (::spec/session-date existing-data) tomorrow))
        form-data (r/atom (or existing-data base-form-data))
        edit-id (::spec/id existing-data)]
    (add-watch pikaday-date :update-form-data
               (fn [_ _ _ new-date]
                 (swap! form-data assoc ::spec/session-date new-date)))
    (fn []
      (let [invalids (invalid-keys form-data ::spec/exam-session)]
        [:div.exam-session-form
         [:h3 (if edit-id "Muokkaa tutkintotapahtumaa" "Uusi tutkintotapahtuma")]
         [:form
          [:div.row
           [:label
            [:span.label "Päivämäärä ja kellonaika"]
            [:div.pikaday-input
             [pikaday/date-selector {:date-atom pikaday-date
                                     :pikaday-attrs {:format "D.M.YYYY"
                                                     :i18n i18n/pikaday-i18n
                                                     :minDate tomorrow
                                                     :firstDay 1}
                                     :input-attrs {:type "text"
                                                   :id "pikaday-input"}}]
             [:i.icon-calendar.date-picker-icon]]
            [:div.times
             (input-element form-data invalids "text" ::spec/start-time nil "hh.mm")
             [:span.dash "\u2014"]
             (input-element form-data invalids "text" ::spec/end-time nil "hh.mm")]]]
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
          (when (::spec/registration-count existing-data)
            [:div.row
             [:span.label "Ilmoittautuneita tällä hetkellä"]
             [:span.value (::spec/registration-count existing-data)]])
          [:div.row
           [:label
            [:span.label "Julkaistu"]
            [:input {:type "checkbox"
                     :name "published"
                     :checked (::spec/published @form-data)
                     :on-change (fn [e]
                                  (let [value (cond-> (-> e .-target .-checked))]
                                    (swap! form-data assoc ::spec/published value)))}]]]
          (when (and edit-id (::spec/published @form-data))
            [:div
             (doall
               (for [lang [:fi :sv]]
                 [:div.row {:key lang}
                  [:span.label (str "Ilmoittautumislinkki (" (name lang) ")")]
                  (let [url (registration-url lang edit-id)]
                    [:a.value {:href url} url])]))])
          [:div.buttons
           [:button.button-primary
            {:disabled (or (not (s/valid? ::spec/exam-session @form-data))
                           (= existing-data @form-data))
             :type "submit"
             :on-click (fn [e]
                         (.preventDefault e)
                         (if edit-id
                           (re-frame/dispatch [:save-exam-session edit-id @form-data])
                           (re-frame/dispatch [:add-exam-session @form-data])))}
            (str "Tallenna" (when edit-id " muutokset"))]
           [:a.button
            {:href routing/virkailija-root}
            "Peruuta"]
           (when edit-id
             [:div.right
              [:button.button-danger {:on-click (fn [e]
                                                  (.preventDefault e)
                                                  (re-frame/dispatch [:delete-exam-session edit-id]))}
               "Poista tapahtuma"]])]]]))))

(defn new-exam-session []
  [exam-session-panel])

(defn edit-exam-session []
  (let [data (re-frame/subscribe [:active-panel-data])]
    (fn []
      [:div
       (when (seq @data)
         [exam-session-panel @data])])))
