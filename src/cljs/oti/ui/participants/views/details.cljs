(ns oti.ui.participants.views.details
  (:require [re-frame.core :as re-frame]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]
            [reagent.core :as r]
            [cognitect.transit :as transit]
            [meta-merge.core :refer [meta-merge]]
            [oti.spec :as os]
            [clojure.string :as s]
            [cljs-time.core :as time]
            [oti.routing :as routing]))

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

(defn- format-number [number]
  (when (transit/bigdec? number)
    (-> number .-rep)))

(defn- format-price [number]
  (when-let [rep (format-number number)]
    (str  rep " €")))

(defn- format-state [state]
  (condp = state
    "UNPAID" "Ei maksettu"
    "ERROR" "Peruutettu"
    "OK" "Maksettu"
    "Tuntematon"))

(defn- past-session? [date]
  (-> (time/to-default-time-zone date)
      (time/before? (time/now))))

(defn- session-rows
  [module-titles
   participant-id
   open-rows
   rows
   {:keys [session-date start-time end-time session-id registration-state street-address city score-ts accepted modules registration-id]}]
  (let [open? (@open-rows session-id)
        editable? (not (past-session? session-date))
        click-op (if open? disj conj)
        click-fn #(swap! open-rows click-op session-id)
        icon-class (if open? "icon-up-open" "icon-down-open")]
    (->> [[:tr
           (cond-> {:key session-id
                    :class (cond
                             (= "ERROR" registration-state) "cancelled"
                             open? "open-row")}
                   editable? (assoc :on-click click-fn))
           [:td.date
            (when (not= "OK" registration-state)
              [:i.icon-attention {:class (if (= "INCOMPLETE" registration-state) "warn" "error")
                                  :title (if (= "INCOMPLETE" registration-state) "Ilmoittautuminen maksamatta" "Ilmoittautuminen peruutettu")}])
            (let [text (str (unparse-date session-date) " " start-time " - " end-time)]
              (if (past-session? session-date)
                [:a {:href (routing/v-route "/tutkintotulokset/" registration-id)} text]
                [:span text]))]
           [:td.location (str city ", " street-address)]
           [:td.section-result (exam-label score-ts accepted)]
           (for [{:keys [id name]} module-titles]
             [:td.score {:key id :title name} (or (format-number (get-in modules [id :points])) "\u2014")])
           [:td.show-functions
            (when editable?
              [:i {:class icon-class :on-click click-fn :title "Näytä toiminnot"}])]]
          [:tr.session-functions
           {:key (str session-id "-functions")
            :style {"display" (if (and editable? open?) "table-row" "none")}}
           [:td {:colSpan (str (+ 3 (count module-titles)))}
            (when (= "INCOMPLETE" registration-state)
              [:button.button-small.button-danger
               {:on-click #(re-frame/dispatch
                             [:launch-confirmation-dialog "Haluatko varmasti poistaa ilmoittautumisen?" "Poista"
                              :cancel-registration registration-id participant-id])}
               "Poista ilmoittautuminen"])
            (when (= "OK" registration-state)
              [:span
               [:button.button-small.button-danger {} "Peruutettu hyväksytysti"]
               [:button.button-small.button-danger {} "Peruutettu"]])]]]
         (concat rows))))

(defn session-table [sessions module-titles participant-id]
  (let [open-rows (r/atom #{})]
    (fn [sessions module-titles participant-id]
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
          (reduce (partial session-rows module-titles participant-id open-rows) [] sessions)]]
        [:div "Ei ilmoittautumisia"]))))

(defn payment-section [payments participant-id language]
  [:div.section.payments
   [:h3 "Maksutiedot"]
   (if (seq payments)
     [:table
      [:thead
       [:tr
        [:th.date "Maksupäivä"]
        [:th.amount "Summa"]
        [:th.state "Maksun tila"]
        [:th.functions "Toiminnot"]]]
      [:tbody
       (doall
         (for [{:keys [id created amount state order-number]} payments]
           [:tr {:key id}
            [:td.date (unparse-date created)]
            [:td.amount (format-price amount)]
            [:td.state {:class (if (= state "ERROR") "error")} (format-state state)]
            [:td.functions
             (when (= state "ERROR")
               [:button.button-small
                {:on-click #(re-frame/dispatch [:confirm-payment order-number participant-id language])}
                "Merkitse maksetuksi"])]]))]]
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

(defn participation-section [sections accreditation-types form-data participant-id]
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
        [session-table sessions module-titles participant-id]]))])

(defn format-address [{::os/keys [registration-street-address registration-zip registration-post-office]}]
  (if (s/blank? registration-street-address)
    "Ei osoitetietoa saatavilla"
    (str registration-street-address ", " registration-zip " " registration-post-office)))

(defn participant-main-component [participant-data initial-form-data accreditation-types]
  (let [form-data (r/atom initial-form-data)]
    (fn [participant-data initial-form-data]
      (let [{:keys [id etunimet sukunimi hetu email sections payments address language]} participant-data]
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
           [:span.label "Katuosoite"]
           [:span.value (format-address address)]]
          [:div.row
           [:span.label "Sähköpostiosoite"]
           [:span.value email]]]
         [participation-section sections accreditation-types form-data id]
         [payment-section payments id language]
         [:div.buttons
          [:div.left
           [:button {:on-click #(-> js/window .-history .back)} "Peruuta"]]
          (when (or (seq (:accredited-sections @form-data)) (seq (:accredited-modules @form-data)))
            [:div.right
             [:button.button-primary {:on-click #(re-frame/dispatch [:save-accreditation-data id @form-data])
                                      :disabled (= initial-form-data @form-data)}
              "Tallenna"]])]]))))

(defn participant-details-panel [participant-id]
  (re-frame/dispatch [:load-participant-details participant-id])
  (let [all-participants (re-frame/subscribe [:participant-details])
        accreditation-types (re-frame/subscribe [:accreditation-types])]
    (fn [participant-id]
      (let [{:keys [sections] :as participant-data} (get @all-participants participant-id)]
        (when (and (seq sections) (seq @accreditation-types))
          [participant-main-component participant-data (prepare-form-data sections @accreditation-types) @accreditation-types])))))
