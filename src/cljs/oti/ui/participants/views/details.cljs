(ns oti.ui.participants.views.details
  (:require [re-frame.core :as re-frame]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]
            [reagent.core :as r]
            [cognitect.transit :as transit]
            [meta-merge.core :refer [meta-merge]]))

(defn- exam-label [score-ts accepted?]
  (cond
    accepted? "Hyväksytty"
    (and score-ts (not accepted?)) "Hylätty"
    :else "Ei arvosteltu"))

(defn- trim-module-name [name]
  (if (> (count name) 6)
    (str (apply str (take 6 name)) ".")
    name)
  name)

(defn- format-price [number]
  (when (transit/bigdec? number)
    (-> number .-rep (str " €"))))

(defn- format-state [state]
  (condp = state
    "UNPAID" "Ei maksettu"
    "ERROR" "Peruutettu"
    "OK" "Maksettu"
    "Tuntematon"))

(defn session-table [sessions module-titles]
  (if (seq sessions)
    [:table
     [:thead
      [:tr
       [:th "Päivämäärä ja aika"]
       [:th "Katuosoite"]
       [:th "Koe"]
       (doall
         (for [{:keys [id name]} module-titles]
           [:th.module-name {:key id :title name} (trim-module-name name)]))]]
     [:tbody
      (doall
        (for [{:keys [session-date start-time end-time session-id registration-state
                      street-address city score-ts accepted? modules]} sessions]
          [:tr {:key session-id}
           [:td.date
            (when (not= "OK" registration-state)
              [:i.icon-attention {:class (if (= "INCOMPLETE" registration-state) "warn" "error")
                                  :title (if (= "INCOMPLETE" registration-state) "Ilmoittautuminen kesken" "Ilmoittautuminen peruuntunut")}])
            [:span (str (unparse-date session-date) " " start-time " - " end-time)]]
           [:td.location (str city ", " street-address)]
           [:td.section-result (exam-label score-ts accepted?)]
           (for [{:keys [id name]} module-titles]
             [:td.score {:key id :title name} (or (get-in modules [id :points]) "\u2014")])]))]]
    [:div "Ei ilmoittautumisia"]))

(defn payment-section [payments]
  [:div.section.payments
   [:h3 "Maksutiedot"]
   (if (seq payments)
     [:table
      [:thead
       [:tr
        [:th "Maksupäivä"]
        [:th "Summa"]
        [:th "Maksun tila"]]]
      [:tbody
       (doall
         (for [{:keys [id created amount state]} payments]
           [:tr {:key id}
            [:td (unparse-date created)]
            [:td (format-price amount)]
            [:td (format-state state)]]))]]
     [:div "Ei maksutietoja"])])

(defn accreditation-map [accreditation-types items]
  (->> items
       (map (fn [{:keys [id accreditation-date accreditation-type name]}]
              [id {:approved? (not (nil? accreditation-date))
                   :type (or accreditation-type (-> accreditation-types last :id))
                   :name name}]))
       (into {})))

(defn prepare-form-data [sections accreditation-types]
  {:accredited-sections (->> (filter :accreditation-requested? sections)
                             (accreditation-map accreditation-types))
   :accredited-modules (->> (map :accredited-modules sections)
                            flatten
                            (filter :accreditation-requested?)
                            (accreditation-map accreditation-types))})

(defn accreditation-inputs [form-data accreditation-types form-key id]
  (let [{:keys [approved? type name]} (-> @form-data form-key (get id))
        label-text (-> (if (= :accredited-sections form-key)
                         "Osio"
                         name)
                       (str " on hyväksytty korvatuksi"))]
    [:div.accreditation
     [:div
      [:label
       [:input {:type "checkbox" :checked approved?
                :on-change (fn [e]
                             (let [value (cond-> (-> e .-target .-checked))]
                               (swap! form-data update form-key meta-merge {id {:approved? value}})))}]
       [:span label-text]]]
     [:div.accreditation-type
      [:label
       [:span "Tieto korvaavuudesta"]
       [:select {:value type
                 :on-change (fn [e]
                              (let [new-type (-> e .-target .-value js/parseInt)]
                                (swap! form-data update form-key meta-merge {id {:type new-type}})))}
        (doall
          (for [{:keys [id description]} accreditation-types]
            [:option {:value id :key id} description]))]]]]))

(defn participation-section [sections accreditation-types form-data]
  [:div.participation-section
   (doall
     (for [{:keys [name accreditation-requested? sessions id module-titles accredited-modules]} sections]
       [:div.section {:key id}
        [:h3 (str "Osa " name)]
        (when accreditation-requested?
          [:div.accreditations
           [accreditation-inputs form-data accreditation-types :accredited-sections id]])
        (when (seq accredited-modules)
          [:div.accreditations
           [:h4 "Osa-alueiden korvaavudet"]
           [:div.module-accreditations
            (doall
              (for [{:keys [id]} accredited-modules]
                [:div.module {:key id}
                 [accreditation-inputs form-data accreditation-types :accredited-modules id]]))]])
        [session-table sessions module-titles]]))])

(defn participant-main-component [participant-data initial-form-data accreditation-types]
  (let [{:keys [id etunimet sukunimi hetu email sections payments]} participant-data
        ;initial-form-data (prepare-form-data sections accreditation-types)
        form-data (r/atom initial-form-data)]
    (fn [participant-data initial-form-data]
      [:div.participant-details
       [:div.person
        [:h3 "Henkilötiedot"]
        [:div.row
         [:span.label "Henkilötunnus"]
         [:span.value hetu]]
        [:div.row
         [:span.label "Nimi"]
         [:span.value (str etunimet " " sukunimi)]]
        [:div.row
         [:span.label "Sähköpostiosoite"]
         [:span.value email]]]
       [participation-section sections accreditation-types form-data]
       [payment-section payments]
       [:div.buttons
        [:div.left
         [:button {:on-click #(-> js/window .-history .back)} "Peruuta"]]
        [:div.right
         [:button.button-primary {:on-click #(re-frame/dispatch [:save-accreditation-data id @form-data])
                                  :disabled (= initial-form-data @form-data)}
          "Tallenna"]]]])))

(defn participant-details-panel [participant-id]
  (re-frame/dispatch [:load-participant-details participant-id])
  (let [all-participants (re-frame/subscribe [:participant-details])
        accreditation-types (re-frame/subscribe [:accreditation-types])]
    (fn [participant-id]
      (let [{:keys [sections] :as participant-data} (get @all-participants participant-id)]
        (when (and (seq sections) (seq @accreditation-types))
          [participant-main-component participant-data (prepare-form-data sections @accreditation-types) @accreditation-types])))))
