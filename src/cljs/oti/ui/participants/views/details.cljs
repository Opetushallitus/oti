(ns oti.ui.participants.views.details
  (:require [re-frame.core :as re-frame]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]
            [reagent.core :as r]
            [cognitect.transit :as transit]
            [meta-merge.core :refer [meta-merge]]
            [oti.spec :as os]
            [clojure.string :as s]
            [cljs-time.core :as time]
            [cljs-time.format :as ctf]
            [oti.routing :as routing]
            [oti.db-states :as states]))

(defn- exam-label [score-ts accepted?]
  (cond
    accepted? "Hyväksytty"
    (and score-ts (not accepted?)) "Hylätty"
    :else "Ei arvosteltu"))

(defn- exam-noscore-result-label [score-ts accepted?]
  (cond
    accepted? "Hyväksytty"
    (and score-ts (not accepted?)) "\u2014"
    :else "\u2014"))

(defn- format-number [number]
  (when (transit/bigdec? number)
    (-> number .-rep)))

(defn- format-price [number]
  (when-let [rep (format-number number)]
    (str  rep " €")))

(defn- past-session? [date]
  (cond
    (string? date) (-> (ctf/parse (ctf/formatters :date) date)
                       (time/before? (time/now)))
    :else (-> (time/to-default-time-zone date)
              (time/before? (time/now)))))

(defn- session-rows
  [module-titles
   participant-id
   section-id
   open-rows
   rows
   {:keys [session-date start-time end-time session-id registration-state street-address city score-ts accepted modules registration-id]}]
  (let [open? (@open-rows session-id)
        editable? (and (not (past-session? session-date)) (#{states/reg-ok states/reg-incomplete} registration-state))
        click-op (if open? disj conj)
        click-fn #(swap! open-rows click-op session-id)
        icon-class (if open? "icon-up-open" "icon-down-open")]
    (->> [[:tr
           (cond-> {:key (str session-id "-" registration-id)
                    :class (cond
                             (#{states/reg-cancelled states/reg-absent states/reg-absent-approved} registration-state) "cancelled"
                             open? "open-row")}
             editable? (assoc :on-click click-fn))
           [:td.date
            (when (not= states/reg-ok registration-state)
              [:i.icon-attention {:class (if (= states/reg-incomplete registration-state) "warn" "error")
                                  :title (if (= states/reg-incomplete registration-state) "Ilmoittautuminen maksamatta" "Ilmoittautuminen peruutettu")}])
            (let [text (str (unparse-date session-date) " " start-time " - " end-time)]
              (if (past-session? session-date)
                [:a {:href (routing/v-route "/tutkintotulokset/" registration-id)} text]
                [:span text]))]
           [:td.location (str city ", " street-address)]
           [:td.section-result (exam-label score-ts accepted)]
           (for [{:keys [id name]} module-titles]
             [:td.score {:key id :title name} (or (format-number (get-in modules [id :points])) (exam-noscore-result-label  score-ts accepted))])
           [:td.show-functions
            (when editable?
              [:i {:class icon-class :on-click click-fn :title "Näytä toiminnot"}])]]
          [:tr.session-functions
           {:key (str session-id "-" registration-id "-functions")
            :style {"display" (if (and editable? open?) "table-row" "none")}}
           [:td {:colSpan (str (+ 3 (count module-titles)))}
            (when (= states/reg-incomplete registration-state)
              [:button.button-small.button-danger
               {:on-click #(re-frame/dispatch
                            [:launch-confirmation-dialog "Haluatko varmasti poistaa ilmoittautumisen?" "Poista"
                             :cancel-registration registration-id states/reg-cancelled participant-id])}
               "Poista ilmoittautuminen"])
            (when (= states/reg-ok registration-state)
              [:span
               [:button.button-small.button-danger
                {:on-click #(re-frame/dispatch
                             [:launch-confirmation-dialog
                              "Haluatko varmasti merkitä ilmoittautumisen perutuksi hyväksytyllä syyllä?"
                              "Peru ilmoittautuminen"
                              :cancel-registration-by-section registration-id states/reg-absent-approved participant-id section-id])}
                "Peruutettu hyväksytysti"]])]]]
         (concat rows))))

(defn session-table [sessions module-titles participant-id section-id]
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
              [:th.module-name {:key id :title name} name]))]]
         [:tbody
          (reduce (partial session-rows module-titles participant-id section-id open-rows) [] sessions)]]
        [:div "Ei ilmoittautumisia"]))))

(defn- format-payment-state [state]
  (condp = state
    states/pmt-unpaid "Ei maksettu"
    states/pmt-error "Peruutettu"
    states/pmt-ok "Maksettu"
    "Tuntematon"))

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
           [:td.state {:class (if (= state states/pmt-error) "error")} (format-payment-state state)]
           [:td.functions
            (when (= state states/pmt-error)
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

(defn accreditation-inputs [form-data accreditation-types form-key id participant-id]
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
    (for [{:keys [name accreditation-requested? sessions id module-titles accredited-modules accreditation-date]} sections]
      [:div.section {:key id}
       [:h3 (str "Osa " name)]
       (when accreditation-requested?
         [:div.accreditations
          [accreditation-inputs form-data accreditation-types :accredited-sections id participant-id]
          (when (= accreditation-date nil)
            [:button.button-small.button-danger
             {:on-click #(re-frame/dispatch
                          [:launch-confirmation-dialog
                           "Haluatko varmasti poistaa korvaavuuden?"
                           "Poista korvaavuus"
                           :delete-section-accreditation participant-id id])}
             "Poista korvaavuus"])])
       (when (seq accredited-modules)
         [:div.accreditations
          [:h4 "Osa-alueiden korvaavudet"]
          [:div.module-accreditations
           (doall
            (for [{:keys [id]} accredited-modules]
              [:div.module {:key id}
               [accreditation-inputs form-data accreditation-types :accredited-modules id participant-id]]))]])
       [session-table sessions module-titles participant-id id]]))])

(defn format-address [{::os/keys [registration-street-address registration-zip registration-post-office]}]
  (if (s/blank? registration-street-address)
    "Ei osoitetietoa saatavilla"
    (str registration-street-address ", " registration-zip " " registration-post-office)))

(defn editable-email-address [email]
  (let [email (r/atom email)]
    (fn []
      [:form
       [:div.row
        [:span.label "Sähköpostiosoite"]
        [:input {:type "email"
                 :name "email"
                 :value @email
                 :on-change #(reset! email (.-value (.-target %)))}]]
       [:div.row
        [:button.button-primary
         {:on-click #(.preventDefault %)}
         "Tallenna sähköpostiosoite"]]])))

(defn person-details [{:keys [hetu etunimet sukunimi address email]}]
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
   [editable-email-address email]])

(defn participant-main-component [initial-form-data accreditation-types]
  (let [form-data (r/atom initial-form-data)]
    (fn [participant-data initial-form-data]
      (let [{:keys [id sections payments language]} participant-data]
        [:div.participant-details
         [person-details participant-data]
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
